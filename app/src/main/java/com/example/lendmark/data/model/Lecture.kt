package com.example.lendmark.data.model

data class Lecture(
    val room: String = "",          // 강의실 이름 (예: "405")
    val day: String = "",           // 요일 ("Mon", "Tue" ...)
    val periodStart: Int = 0,       // 시작 교시
    val periodEnd: Int = 0,         // 종료 교시
    val subject: String = ""        // 과목명
)



