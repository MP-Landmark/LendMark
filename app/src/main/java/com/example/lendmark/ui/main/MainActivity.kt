package com.example.lendmark.ui.main

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.example.lendmark.R
import com.example.lendmark.ui.auth.AuthActivity
import com.example.lendmark.ui.building.BuildingListFragment
import com.example.lendmark.ui.home.HomeFragment
import com.example.lendmark.ui.my.ManageFavoritesFragment
import com.example.lendmark.ui.my.MyPageFragment
import com.example.lendmark.ui.notification.NotificationListFragment
import com.example.lendmark.ui.reservation.ReservationMapFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    // ... (변수 선언부는 기존과 동일) ...
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var btnMenu: ImageButton
    private lateinit var btnNotification: ImageButton
    private lateinit var tvHeaderTitle: TextView
    private lateinit var btnCloseDrawer: ImageButton
    private lateinit var menuMyReservation: TextView
    private lateinit var menuClassReservation: TextView
    private lateinit var menuFavorites: TextView
    private lateinit var btnLogout: AppCompatButton
    private lateinit var tvUserName: TextView
    private lateinit var tvUserEmail: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        initViews()
        loadUserData()
        setupListeners() // 리스너 설정

        // 뒤로가기 콜백 설정 (OnBackPressedDispatcher)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        })

        if (savedInstanceState == null) {
            replaceFragment(HomeFragment(), "LendMark", addToBackStack = false)
        }
    }

    // ... (initViews, loadUserData는 기존과 동일) ...

    private fun setupListeners() {
        // [수정됨] 햄버거/뒤로가기 버튼 클릭 로직
        btnMenu.setOnClickListener {
            val currentFragment = supportFragmentManager.findFragmentById(R.id.main_container)
            val isHome = currentFragment is HomeFragment
            val isSubPage = supportFragmentManager.backStackEntryCount > 0

            // 홈 화면이 아니거나 상세 페이지라면 -> 뒤로가기 동작
            if (!isHome || isSubPage) {
                handleBackPress()
            } else {
                // 홈 화면 메인이라면 -> 드로어 열기
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        // ... (드로어 내부 버튼 리스너들은 기존과 동일) ...
        btnCloseDrawer.setOnClickListener { drawerLayout.closeDrawer(GravityCompat.START) }
        menuMyReservation.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            bottomNav.selectedItemId = R.id.nav_my
        }
        menuClassReservation.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            bottomNav.selectedItemId = R.id.nav_book
        }
        menuFavorites.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            openManageFavorites()
        }
        btnLogout.setOnClickListener {
            auth.signOut()
            Toast.makeText(this, "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, AuthActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
        btnNotification.setOnClickListener {
            replaceFragment(NotificationListFragment(), "Notifications")
        }

        // 백스택 변경 감지
        supportFragmentManager.addOnBackStackChangedListener {
            updateUiAfterNavigation()
        }

        // 바텀 네비게이션
        bottomNav.setOnItemSelectedListener { item ->
            val currentFragment = supportFragmentManager.findFragmentById(R.id.main_container)

            // 같은 탭 클릭 방지
            if (supportFragmentManager.backStackEntryCount == 0) {
                val isSameTab = when(item.itemId) {
                    R.id.nav_home -> currentFragment is HomeFragment
                    R.id.nav_book -> currentFragment is BuildingListFragment
                    R.id.nav_map -> currentFragment is ReservationMapFragment
                    R.id.nav_my -> currentFragment is MyPageFragment
                    else -> false
                }
                if (isSameTab) return@setOnItemSelectedListener false
            }

            supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)

            val (fragment, title) = when (item.itemId) {
                R.id.nav_home -> HomeFragment() to "LendMark"
                R.id.nav_book -> BuildingListFragment() to "Classroom Reservation"
                R.id.nav_map -> ReservationMapFragment() to "Map View"
                R.id.nav_my -> MyPageFragment() to "My Page"
                else -> return@setOnItemSelectedListener false
            }

            replaceFragment(fragment, title, addToBackStack = false)
            true
        }
    }

    fun replaceFragment(fragment: Fragment, title: String, addToBackStack: Boolean = true) {

        val transaction = supportFragmentManager.beginTransaction()
            .replace(R.id.main_container, fragment)

        if (addToBackStack) {
            transaction.addToBackStack(title)
        }

        transaction.commitAllowingStateLoss()  // ← 변경!

        tvHeaderTitle.text = title

        val isHome = fragment is HomeFragment
        btnMenu.setImageResource(
            if (isHome && !addToBackStack) R.drawable.ic_menu
            else R.drawable.ic_arrow_back
        )
    }

    // [핵심 수정] UI 상태 업데이트 로직 (제목 및 버튼 아이콘)
    private fun updateUiAfterNavigation() {
        val count = supportFragmentManager.backStackEntryCount
        val currentFragment = supportFragmentManager.findFragmentById(R.id.main_container)
        val isSubPage = count > 0
        val isHome = currentFragment is HomeFragment

        // 1. 버튼 아이콘 설정
        // 홈 화면이고 백스택이 없을 때만 '햄버거' / 나머지는 모두 '뒤로가기'
        if (isHome && !isSubPage) {
            btnMenu.setImageResource(R.drawable.ic_menu)
        } else {
            btnMenu.setImageResource(R.drawable.ic_arrow_back)
        }

        // 2. 제목 설정
        if (isSubPage) {
            // 상세 페이지라면 백스택에 저장된 이름을 가져옴 (예: 건물명, 호실명)
            val currentEntry = supportFragmentManager.getBackStackEntryAt(count - 1)
            tvHeaderTitle.text = currentEntry.name
        } else {
            // 탭의 메인 화면이라면 프래그먼트 타입에 따라 제목 설정
            tvHeaderTitle.text = when (currentFragment) {
                is BuildingListFragment -> "Classroom Reservation"
                is ReservationMapFragment -> "Map View"
                is MyPageFragment -> "My Page"
                is NotificationListFragment -> "Notifications"
                is ManageFavoritesFragment -> "Manage Favorites"
                else -> "LendMark" // HomeFragment
            }
        }

        // 3. 바텀 네비게이션 아이콘 동기화
        val selectedItem = when (currentFragment) {
            is HomeFragment -> R.id.nav_home
            is BuildingListFragment -> R.id.nav_book
            is ReservationMapFragment -> R.id.nav_map
            is MyPageFragment -> R.id.nav_my
            else -> bottomNav.selectedItemId
        }
        if (bottomNav.selectedItemId != selectedItem) {
            bottomNav.selectedItemId = selectedItem
        }
    }

    // 뒤로가기 로직 분리
    private fun handleBackPress() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
            return
        }

        val currentFragment = supportFragmentManager.findFragmentById(R.id.main_container)

        if (supportFragmentManager.backStackEntryCount > 0) {
            // 상세 페이지면 스택 하나 뺌 (제목도 자동으로 전 단계로 돌아감)
            supportFragmentManager.popBackStack()
        } else if (currentFragment !is HomeFragment) {
            // 홈이 아닌 다른 탭의 메인 화면이면 -> 홈 탭으로 이동
            bottomNav.selectedItemId = R.id.nav_home
        } else {
            // 홈 화면이면 앱 종료
            finish()
        }
    }

    // ... (initViews, loadUserData, openManageFavorites 등 나머지 함수 유지) ...
    private fun initViews() {
        drawerLayout = findViewById(R.id.drawerLayout)
        btnMenu = findViewById(R.id.btnMenu)
        btnNotification = findViewById(R.id.btnNotification)
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle)
        bottomNav = findViewById(R.id.bottomNav)

        val drawerContent = findViewById<View>(R.id.drawerContent)
        btnCloseDrawer = drawerContent.findViewById(R.id.btnCloseDrawer)
        menuMyReservation = drawerContent.findViewById(R.id.menuMyReservation)
        menuClassReservation = drawerContent.findViewById(R.id.menuClassReservation)
        menuFavorites = drawerContent.findViewById(R.id.menuFavorites)
        btnLogout = drawerContent.findViewById(R.id.btnLogout)
        tvUserName = drawerContent.findViewById(R.id.tvUserName)
        tvUserEmail = drawerContent.findViewById(R.id.tvUserEmail)
    }

    private fun loadUserData() {
        val currentUser = auth.currentUser
        if (currentUser == null) return
        val uid = currentUser.uid
        db.collection("users").document(uid).get().addOnSuccessListener { document ->
            if (document != null && document.exists()) {
                val name = document.getString("name") ?: "알 수 없음"
                val email = document.getString("email") ?: currentUser.email
                tvUserName.text = name
                tvUserEmail.text = email
            } else {
                tvUserName.text = "정보 없음"
            }
        }.addOnFailureListener { e ->
            Log.e("MainActivity", "Error fetching user data", e)
            tvUserName.text = "오류 발생"
        }
    }

    fun openManageFavorites() {
        replaceFragment(ManageFavoritesFragment(), "Manage Favorites")
    }
}