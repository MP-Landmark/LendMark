package com.example.lendmark.ui.notification

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class NotificationViewModel : ViewModel() {

    // Notification list data (read-only LiveData)
    private val _notifications = MutableLiveData<List<NotificationItem>>()
    val notifications: LiveData<List<NotificationItem>> get() = _notifications

    // Selected notification (e.g., for display in a dialog)
    private val _selectedNotification = MutableLiveData<NotificationItem?>()
    val selectedNotification: LiveData<NotificationItem?> get() = _selectedNotification

    init {
        // Temporary data for testing
        _notifications.value = listOf(
            NotificationItem(
                id = 1,
                title = "Reservation starts in 30 minutes",
                location = "Frontier Hall #107",
                date = "2025-10-23",
                startTime = "19:30",
                endTime = "21:30",
                remainingTime = "in 30 mins",
                type = "start"
            ),
            NotificationItem(
                id = 2,
                title = "Reservation ends in 10 minutes",
                location = "Mirae Hall #205",
                date = "2025-10-23",
                startTime = "18:30",
                endTime = "20:30",
                remainingTime = "10 mins left",
                type = "end"
            )
        )
    }

    // Called when a notification item is clicked
    fun selectNotification(item: NotificationItem) {
        _selectedNotification.value = item
        markAsRead(item.id)
    }

    // Mark a notification as read
    private fun markAsRead(notificationId: Int) {
        _notifications.value = _notifications.value?.map {
            if (it.id == notificationId) it.copy(isRead = true) else it
        }
    }

    // (Optional) Add a new notification
    fun addNotification(newItem: NotificationItem) {
        val current = _notifications.value ?: emptyList()
        _notifications.value = listOf(newItem) + current
    }

    // (Optional) Mark all as read
    fun markAllAsRead() {
        _notifications.value = _notifications.value?.map { it.copy(isRead = true) }
    }
}
