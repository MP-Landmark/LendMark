package com.example.lendmark.ui.main

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.example.lendmark.R
import com.example.lendmark.ui.building.BuildingListFragment
import com.example.lendmark.ui.home.HomeFragment
import com.example.lendmark.ui.auth.AuthActivity
import com.example.lendmark.ui.my.ManageFavoritesFragment
import com.example.lendmark.ui.my.MyPageFragment
import com.example.lendmark.ui.notification.NotificationListFragment
import com.example.lendmark.ui.reservation.ReservationMapFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    // Firebase
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    // Main UI
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var btnMenu: ImageButton
    private lateinit var btnNotification: ImageButton
    private lateinit var tvHeaderTitle: TextView

    // Drawer UI
    private lateinit var btnCloseDrawer: ImageButton
    private lateinit var menuMyReservation: TextView
    private lateinit var menuClassReservation: TextView
    private lateinit var menuFavorites: TextView
    private lateinit var btnLogout: AppCompatButton

    // Profile UI in Drawer
    private lateinit var tvUserName: TextView
    private lateinit var tvUserEmail: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Firebase 인스턴스 초기화
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // 2. 뷰 바인딩 및 초기화
        initViews()

        // 3. 사용자 정보 불러오기 (DB 연동)
        loadUserData()

        // 4. 리스너 설정
        setupListeners()

        if (savedInstanceState == null) {
            replaceFragment(HomeFragment(), "LendMark", addToBackStack = false)
        }
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawerLayout)
        btnMenu = findViewById(R.id.btnMenu)
        btnNotification = findViewById(R.id.btnNotification)
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle)
        bottomNav = findViewById(R.id.bottomNav)

        // Drawer 레이아웃 내부 뷰 찾기
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

        if (currentUser == null) {
            // 로그인이 되어있지 않다면 처리 (예: 로그인 화면으로 이동)
            return
        }

        val uid = currentUser.uid

        // Firestore: users -> uid 문서 가져오기
        db.collection("users").document(uid)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    // DB 필드명: name, email (스크린샷 기준)
                    val name = document.getString("name") ?: "알 수 없음"
                    val email = document.getString("email") ?: currentUser.email

                    tvUserName.text = name
                    tvUserEmail.text = email
                } else {
                    tvUserName.text = "정보 없음"
                }
            }
            .addOnFailureListener { e ->
                Log.e("MainActivity", "Error fetching user data", e)
                tvUserName.text = "오류 발생"
            }
    }

    private fun setupListeners() {
        // [메인] 햄버거 버튼 -> 드로어 열기
        btnMenu.setOnClickListener {
            // 메인 탭 화면일 때만 드로어 열기, 상세 화면이면 뒤로가기
            if (supportFragmentManager.backStackEntryCount > 0) {
                onBackPressedDispatcher.onBackPressed()
            } else {
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        // [드로어] 닫기 버튼
        btnCloseDrawer.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        // [드로어] 내 예약 클릭
        menuMyReservation.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            // 마이페이지로 이동 (또는 내 예약 프래그먼트)
            bottomNav.selectedItemId = R.id.nav_my
        }

        // [드로어] 강의실 예약 클릭
        menuClassReservation.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            bottomNav.selectedItemId = R.id.nav_book
        }

        // [드로어] 즐겨찾기 관리 클릭
        menuFavorites.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            openManageFavorites()
        }

        // [드로어] 로그아웃
        btnLogout.setOnClickListener {
            auth.signOut()
            Toast.makeText(this, "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show()

            // 로그인 화면으로 이동 후 스택 비우기
            val intent = Intent(this, AuthActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        // [메인] 알림 버튼
        btnNotification.setOnClickListener {
            replaceFragment(NotificationListFragment(), "Notifications")
        }

        // 백스택 변경 감지 (헤더 타이틀 및 아이콘 변경)
        supportFragmentManager.addOnBackStackChangedListener {
            updateUiAfterNavigation()
        }

        // 바텀 네비게이션
        bottomNav.setOnItemSelectedListener { item ->
            val currentFragment = supportFragmentManager.findFragmentById(R.id.main_container)

            // 현재 탭 재클릭 방지 (메인 화면일 때만)
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

            // 탭 이동 시 백스택 비우기
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
        transaction.commit()

        tvHeaderTitle.text = title
        updateUiAfterNavigation()
    }

    private fun updateUiAfterNavigation() {
        val count = supportFragmentManager.backStackEntryCount
        val isSubPage = count > 0

        if (isSubPage) {
            // 상세 페이지 -> 뒤로가기 화살표
            btnMenu.setImageResource(R.drawable.ic_arrow_back)

            val currentEntry = supportFragmentManager.getBackStackEntryAt(count - 1)
            tvHeaderTitle.text = currentEntry.name
        } else {
            // 메인 페이지 -> 햄버거 메뉴
            btnMenu.setImageResource(R.drawable.ic_menu)

            // 현재 프래그먼트에 따라 타이틀 복구
            val currentFragment = supportFragmentManager.findFragmentById(R.id.main_container)
            tvHeaderTitle.text = when (currentFragment) {
                is BuildingListFragment -> "Classroom Reservation"
                is ReservationMapFragment -> "Map View"
                is MyPageFragment -> "My Page"
                is NotificationListFragment -> "Notifications"
                else -> "LendMark"
            }

            // 바텀 네비게이션 아이콘 동기화
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
    }

    fun openManageFavorites() {
        replaceFragment(ManageFavoritesFragment(), "Manage Favorites")
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
            return
        }

        val currentFragment = supportFragmentManager.findFragmentById(R.id.main_container)
        if (supportFragmentManager.backStackEntryCount > 0) {
            super.onBackPressedDispatcher.onBackPressed()
        } else if (currentFragment !is HomeFragment) {
            bottomNav.selectedItemId = R.id.nav_home
        } else {
            super.onBackPressedDispatcher.onBackPressed()
        }
    }
}