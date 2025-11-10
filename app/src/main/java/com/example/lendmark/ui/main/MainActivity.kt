package com.example.lendmark.ui.main

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.lendmark.R
import com.example.lendmark.ui.home.HomeFragment
import com.example.lendmark.ui.my.MyPageFragment
import com.example.lendmark.ui.reservation.building.BuildingListFragment
import com.example.lendmark.ui.reservation.ReservationMapFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // initial DB//uploadInitialData()
        //uploadTimetableData()
        setContentView(R.layout.activity_main)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        // 첫 화면: HOME
        if (savedInstanceState == null) {
            replaceFragment(HomeFragment())
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    replaceFragment(HomeFragment())
                    true
                }
                R.id.nav_book -> {
                    // BOOK 탭에서 어떤 화면 보여줄지: 일단 BuildingListFragment 사용
                    replaceFragment(BuildingListFragment())
                    true
                }
                R.id.nav_map -> {
                    replaceFragment(ReservationMapFragment())
                    true
                }
                R.id.nav_my -> {
                    replaceFragment(MyPageFragment())
                    true
                }
                else -> false
            }
        }
        // initial DB // val db = FirebaseFirestore.getInstance()
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_container, fragment)
            .commit()
    }




    // initial DB


//    fun uploadInitialData() {
//        val db = FirebaseFirestore.getInstance()
//
//        // 1. Buildings & Rooms
//        val dasan = hashMapOf(
//            "name" to "Dasan Hall",
//            "code" to -1,
//            "naverMapLat" to 37.631,
//            "naverMapLng" to 127.078
//        )
//        db.collection("buildings").document("dasan").set(dasan)
//
//        val rooms = listOf(
//            mapOf("name" to "101", "capacity" to 40,
//                "facilities" to listOf("projector", "whiteboard", "power-outlets")),
//            mapOf("name" to "107", "capacity" to 120,
//                "facilities" to listOf("projector", "whiteboard", "mic", "hdmi", "aircon"))
//        )
//        for (room in rooms) {
//            db.collection("buildings").document("dasan")
//                .collection("rooms").document("dasan-${room["name"]}")
//                .set(room)
//        }
//
//        // 2. Tag catalog
//        val tags = listOf(
//            mapOf("id" to "projector", "group" to "facility", "labelEn" to "Projector", "icon" to "projector"),
//            mapOf("id" to "whiteboard", "group" to "facility", "labelEn" to "Whiteboard", "icon" to "whiteboard"),
//            mapOf("id" to "power-outlets", "group" to "facility", "labelEn" to "Power outlets", "icon" to "power"),
//            mapOf("id" to "hdmi", "group" to "facility","labelEn" to "HDMI", "icon" to "hdmi"),
//            mapOf("id" to "mic", "group" to "facility", "labelEn" to "Microphone", "icon" to "mic"),
//            mapOf("id" to "aircon", "group" to "facility", "labelEn" to "Air conditioning", "icon" to "aircon"),
//            mapOf("id" to "large-hall", "group" to "roomType", "labelEn" to "Large lecture hall", "icon" to "hall")
//        )
//        for (tag in tags) {
//            db.collection("tag_catalog").document(tag["id"] as String).set(tag)
//        }
//

//        // 3. Users (sample)
//        val users = listOf(
//            mapOf(
//                "id" to "phy9558",
//                "email" to "phy9558@gmail.com",
//                "nickname" to "유영",
//                "createdAt" to FieldValue.serverTimestamp()
//            ),
//            mapOf(
//                "id" to "testuser",
//                "email" to "testuser@example.com",
//                "nickname" to "테스터",
//                "createdAt" to FieldValue.serverTimestamp()
//            )
//        )
//
//        for (user in users) {
//            val userId = user["id"] as String
//            db.collection("users").document(userId).set(user)
//
//            // 기본 즐겨찾기 샘플
//            val favorite = mapOf(
//                "buildingId" to "dasan",
//                "roomId" to "dasan-107",
//                "createdAt" to FieldValue.serverTimestamp()
//            )
//            db.collection("users").document(userId)
//                .collection("favorites").add(favorite)
//        }
//
//        Log.d("FirebaseSeed", "Initial Data Upload successfully")
//
//
//
//
//    }

    // timetable DB
//    fun uploadTimetableData() {
//        val db = FirebaseFirestore.getInstance()
//
//        // 1 예시 학과 데이터
//        val timetableData = listOf(
//            mapOf(
//                "college" to "College of Engineering",
//                "department" to "Department of Mechanical System Design Engineering",
//                "lectures" to listOf(
//                    mapOf(
//                        "subject" to "General Physics(1)",
//                        "day" to "Thu",
//                        "periods" to listOf(3,4),
//                        "room" to "Davinci Hall(039)-104"
//                    ),
//                    mapOf(
//                        "subject" to "General Physics(1)",
//                        "day" to "Tue",
//                        "periods" to listOf(6,7),
//                        "room" to "Davinci Hall(039)-104"
//                    )
//                )
//            ),
//            mapOf(
//                "college" to "College of Natural Sciences",
//                "department" to "Department of Physics",
//                "lectures" to listOf(
//                    mapOf(
//                        "subject" to "Thermodynamics",
//                        "day" to "Mon",
//                        "periods" to listOf(2,3,4),
//                        "room" to "Darwin Hall(031)-204"
//                    ),
//                    mapOf(
//                        "subject" to "Quantum Mechanics",
//                        "day" to "Fri",
//                        "periods" to listOf(5,6,7),
//                        "room" to "Darwin Hall(031)-201"
//                    )
//                )
//            )
//        )
//
//        // 2 firestore에 업로드
//        for (dept in timetableData) {
//            val deptName = (dept["department"] as String)
//                .lowercase()
//                .replace(" ", "-")
//                .replace("department-of-", "")
//
//            db.collection("timetables")
//                .document("2025-fall")
//                .collection("departments")
//                .document(deptName)
//                .set(dept)
//                .addOnSuccessListener {
//                    Log.d("FirebaseSeed", "Uploaded timetable for $deptName")
//                }
//                .addOnFailureListener {
//                    Log.e("FirebaseSeed", "Failed to upload $deptName", it)
//                }
//        }
//    }




}
