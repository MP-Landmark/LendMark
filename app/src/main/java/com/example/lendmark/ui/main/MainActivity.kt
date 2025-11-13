package com.example.lendmark.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import android.widget.ImageButton
import android.widget.TextView
import com.example.lendmark.R
import com.example.lendmark.ui.home.HomeFragment
import com.example.lendmark.ui.my.MyPageFragment
import com.example.lendmark.ui.notification.NotificationListFragment
import com.example.lendmark.ui.building.BuildingListFragment
import com.example.lendmark.ui.reservation.ReservationMapFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.example.lendmark.ui.my.ManageFavoritesFragment //✅


class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var btnMenu: ImageButton
    private lateinit var btnNotification: ImageButton
    private lateinit var tvHeaderTitle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 상단 헤더 요소 가져오기
        btnMenu = findViewById(R.id.btnMenu)
        btnNotification = findViewById(R.id.btnNotification)
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle)
        bottomNav = findViewById(R.id.bottomNav)

        // 첫 화면: HomeFragment
        if (savedInstanceState == null) {
            replaceFragment(HomeFragment())
            tvHeaderTitle.text = "LendMark"
        }

        // 하단 네비게이션 탭 선택 시 프래그먼트 교체
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    replaceFragment(HomeFragment())
                    tvHeaderTitle.text = "LendMark"
                    true
                }
                R.id.nav_book -> {
                    replaceFragment(BuildingListFragment())
                    tvHeaderTitle.text = "강의실 예약"
                    true
                }
                R.id.nav_map -> {
                    replaceFragment(ReservationMapFragment())
                    tvHeaderTitle.text = "지도 보기"
                    true
                }
                R.id.nav_my -> {
                    replaceFragment(MyPageFragment())
                    tvHeaderTitle.text = "My Page"
                    true
                }
                else -> false
            }
        }

        // 상단 오른쪽 알림 버튼 클릭 시 → 알림 화면으로 전환
        btnNotification.setOnClickListener {
            replaceFragment(NotificationListFragment())
            tvHeaderTitle.text = "Notifications"

            // ☰ → ← 로 변경
            btnMenu.setImageResource(R.drawable.ic_arrow_back)
            btnMenu.setOnClickListener {
                onBackPressedDispatcher.onBackPressed()
                btnMenu.setImageResource(R.drawable.ic_menu)
                tvHeaderTitle.text = "LendMark"
            }
        }

        // 왼쪽 햄버거 버튼 클릭 (지금은 임시)
        btnMenu.setOnClickListener {
            // TODO: Drawer 메뉴 연결 (추후)
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_container, fragment)
            .addToBackStack(null)
            .commit()
    }
    fun openManageFavorites() {
        replaceFragment(ManageFavoritesFragment())
        tvHeaderTitle.text = "즐겨찾기 관리"
    } //✅
}





