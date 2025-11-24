package com.example.lendmark.data.model

data class Schedule(
    val date: String = "",
    val start: String = "",
    val end: String = "",
    val type: String = "",
    val courseName: String? = null,
    val reservedBy: String? = null
)

