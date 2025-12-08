package com.example.lendmark.ui.main

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.bumptech.glide.Glide
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
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import androidx.activity.viewModels
import com.example.lendmark.ui.notification.NotificationViewModel

class MainActivity : AppCompatActivity() {

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
    private lateinit var imgProfile: ImageView

    //  앱 켜지자마자 알림 비서(ViewModel) 고용! (자동으로 init 실행됨)
    private val notificationViewModel: NotificationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d("MainActivity", "알림 시스템 시작 여부: ${notificationViewModel.isInAppEnabled}")

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        initViews()
        loadUserData()
        setupListeners() // 리스너 설정
        fetchFcmToken() // fcm 토큰 가져오기

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        })

        if (savedInstanceState == null) {
            replaceFragment(HomeFragment(), "LendMark", addToBackStack = false)
        }
    }

    private fun fetchFcmToken() {
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w("FCM", "Fetching FCM token failed", task.exception)
                    return@addOnCompleteListener
                }

                val token = task.result
                Log.d("FCM", " Current FCM Token: $token")

                val uid = FirebaseAuth.getInstance().currentUser?.uid
                if (uid != null) {
                    saveTokenToFirestore(uid, token)
                } else {
                    Log.w("FCM", "⚠ 사용자 로그인 상태가 아님 → 토큰 저장 생략")
                }
            }
    }

    private fun saveTokenToFirestore(userId: String, token: String) {
        val db = FirebaseFirestore.getInstance()

        val data = mapOf(
            "fcmToken" to token,
            "updatedAt" to System.currentTimeMillis()
        )

        db.collection("users").document(userId)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                Log.d("FCM", "Token saved successfully")
            }
            .addOnFailureListener { e ->
                Log.e("FCM", "Failed to save token", e)
            }
    }

    private fun setupListeners() {
        btnMenu.setOnClickListener {
            val currentFragment = supportFragmentManager.findFragmentById(R.id.main_container)
            val isHome = currentFragment is HomeFragment
            val isSubPage = supportFragmentManager.backStackEntryCount > 0

            if (!isHome || isSubPage) {
                handleBackPress()
            } else {
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }

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

        supportFragmentManager.addOnBackStackChangedListener {
            updateUiAfterNavigation()
        }

        bottomNav.setOnItemSelectedListener { item ->
            val currentFragment = supportFragmentManager.findFragmentById(R.id.main_container)

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

        transaction.commitAllowingStateLoss()

        tvHeaderTitle.text = title

        val isHome = fragment is HomeFragment
        btnMenu.setImageResource(
            if (isHome && !addToBackStack) R.drawable.ic_menu
            else R.drawable.ic_arrow_back
        )
    }

    private fun updateUiAfterNavigation() {
        val count = supportFragmentManager.backStackEntryCount
        val currentFragment = supportFragmentManager.findFragmentById(R.id.main_container)
        val isSubPage = count > 0
        val isHome = currentFragment is HomeFragment

        if (isHome && !isSubPage) {
            btnMenu.setImageResource(R.drawable.ic_menu)
        } else {
            btnMenu.setImageResource(R.drawable.ic_arrow_back)
        }

        if (isSubPage) {
            val currentEntry = supportFragmentManager.getBackStackEntryAt(count - 1)
            tvHeaderTitle.text = currentEntry.name
        } else {
            tvHeaderTitle.text = when (currentFragment) {
                is BuildingListFragment -> "Classroom Reservation"
                is ReservationMapFragment -> "Map View"
                is MyPageFragment -> "My Page"
                is NotificationListFragment -> "Notifications"
                is ManageFavoritesFragment -> "Manage Favorites"
                else -> "LendMark"
            }
        }

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

    private fun handleBackPress() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
            return
        }

        val currentFragment = supportFragmentManager.findFragmentById(R.id.main_container)

        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else if (currentFragment !is HomeFragment) {
            bottomNav.selectedItemId = R.id.nav_home
        } else {
            finish()
        }
    }

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
        imgProfile = drawerContent.findViewById(R.id.imgProfile)
    }

    private fun loadUserData() {
        val currentUser = auth.currentUser
        if (currentUser == null) return
        val uid = currentUser.uid
        db.collection("users").document(uid).get().addOnSuccessListener { document ->
            if (document != null && document.exists()) {
                val name = document.getString("name") ?: "알 수 없음"
                val email = document.getString("email") ?: currentUser.email
                val profileImageUrl = document.getString("profileImageUrl")

                tvUserName.text = name
                tvUserEmail.text = email

                if (!profileImageUrl.isNullOrEmpty()) {
                    imgProfile.imageTintList = null
                    imgProfile.setPadding(0, 0, 0, 0)
                    Glide.with(this@MainActivity)
                        .load(profileImageUrl)
                        .circleCrop()
                        .into(imgProfile)
                } else {
                    imgProfile.setImageResource(R.drawable.ic_default_profile)
                }

            } else {
                tvUserName.text = "정보 없음"
                imgProfile.setImageResource(R.drawable.ic_default_profile)
            }
        }.addOnFailureListener { e ->
            Log.e("MainActivity", "Error fetching user data", e)
            tvUserName.text = "오류 발생"
            imgProfile.setImageResource(R.drawable.ic_default_profile)
        }
    }

    fun openManageFavorites() {
        replaceFragment(ManageFavoritesFragment(), "Manage Favorites")
    }
}