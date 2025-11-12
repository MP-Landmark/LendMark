package com.example.lendmark.data.model

data class Building(
    val name: String = "",               // 건물 이름
    val code: Int = 0,                   // 건물 고유 번호
    val roomCount: Int = 0,              // 이용 가능한 강의실 수
    val imageUrl: String = "",           // 건물 이미지 URL
    val naverMapLat: Double = 0.0,       // 네이버 지도 위도
    val naverMapLng: Double = 0.0,       // 네이버 지도 경도
    val timetable: Map<String, List<Lecture>> = emptyMap() // 강의실별 시간표
)

