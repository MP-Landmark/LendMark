package com.example.lendmark.data.model

data class Lecture(
    val dow: Int = 0,          // 1=Mon ... 7=Sun
    val startMin: Int = 0,     // minutes from 00:00
    val endMin: Int = 0,
    val courseId: String = "",
    val title: String = "",
    val dept: String = ""
)


