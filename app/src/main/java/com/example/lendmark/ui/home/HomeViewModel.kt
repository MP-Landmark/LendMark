package com.example.lendmark.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.lendmark.ui.home.adapter.Announcement
import com.example.lendmark.ui.home.adapter.Room

// Data class specifically for the upcoming reservation info
data class UpcomingReservationInfo(val roomName: String, val time: String)

class HomeViewModel : ViewModel() {

    private val _announcements = MutableLiveData<List<Announcement>>()
    val announcements: LiveData<List<Announcement>> = _announcements

    private val _frequentlyUsedRooms = MutableLiveData<List<Room>>()
    val frequentlyUsedRooms: LiveData<List<Room>> = _frequentlyUsedRooms

    private val _upcomingReservation = MutableLiveData<UpcomingReservationInfo?>()
    val upcomingReservation: LiveData<UpcomingReservationInfo?> = _upcomingReservation

    fun loadHomeData() {
        // In a real app, this data would be fetched from a repository or database
        _announcements.value = listOf(
            Announcement("Announcement", "Mon - Fri 09:00 - 18:00\nHolidays and vacations are closed"),
            Announcement("Review Event", "If you leave a review for your classroom, we will give you a voucher.")
        )

        _frequentlyUsedRooms.value = listOf(
            Room("Frontier Hall 107", ""),
            Room("Dasan Hall 301", ""),
            Room("Mirae Hall 205", "")
        )

        // Use the new, specific data class
        _upcomingReservation.value = UpcomingReservationInfo("Frontier Hall 107", "13:00 - 15:00")
    }
}
