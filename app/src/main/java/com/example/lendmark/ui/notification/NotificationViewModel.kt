package com.example.lendmark.ui.notification

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.lendmark.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class NotificationViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val context = getApplication<Application>().applicationContext

    private val _notifications = MutableLiveData<List<NotificationItem>>()
    val notifications: LiveData<List<NotificationItem>> get() = _notifications

    private val _selectedNotification = MutableLiveData<NotificationItem?>()
    val selectedNotification: LiveData<NotificationItem?> get() = _selectedNotification

    var isInAppEnabled: Boolean = true
    private var buildingNameMap = mapOf<String, String>()

    // 중복 알림 방지 (이미 알림을 보낸 예약 ID 저장)
    private val notifiedSet = mutableSetOf<String>()

    init {
        createNotificationChannel()
        loadBuildingNamesAndStartLoop()
    }

    private fun startPeriodicCheck() {
        viewModelScope.launch {
            while (isActive) {
                // Log.d("NotificationVM", "🔄 1분 자동 체크 중...")
                checkReservationsAndCreateNotifications()
                delay(60 * 1000L) // 1분 대기
            }
        }
    }

    fun checkReservationsAndCreateNotifications() {
        if (!isInAppEnabled) return

        val currentUser = auth.currentUser ?: return

        db.collection("reservations")
            .whereEqualTo("userId", currentUser.uid)
            .whereEqualTo("status", "approved")
            .get()
            .addOnSuccessListener { documents ->
                val newNotifications = mutableListOf<NotificationItem>()
                val currentTime = System.currentTimeMillis()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

                for (doc in documents) {
                    try {
                        val dateStr = doc.getString("date") ?: ""
                        val periodStart = doc.getLong("periodStart")?.toInt() ?: 0
                        val periodEnd = doc.getLong("periodEnd")?.toInt() ?: 0

                        if (dateStr.isEmpty()) continue

                        val buildingId = doc.getString("buildingId") ?: ""
                        val buildingName = buildingNameMap[buildingId] ?: "Building $buildingId"
                        val roomId = doc.getString("roomId") ?: ""

                        val startTimeStr = convertPeriodToStartTime(periodStart)
                        val endTimeStr = convertPeriodToEndTime(periodEnd)

                        val startDateTime = dateFormat.parse("$dateStr $startTimeStr")?.time ?: 0L
                        val endDateTime = dateFormat.parse("$dateStr $endTimeStr")?.time ?: 0L

                        val diffStart = startDateTime - currentTime
                        val diffEnd = endDateTime - currentTime

                        // 시작 30분 전 (0 < 남은시간 <= 30분)
                        if (diffStart > 0 && diffStart <= TimeUnit.MINUTES.toMillis(30)) {
                            // 정확한 분 계산 (올림 처리)
                            // 30분 0초 -> 30분, 29분 59초 -> 30분
                            val minsLeft = (diffStart - 1) / (1000 * 60) + 1

                            val title = "Reservation Starting Soon!"
                            val body = "$buildingName $roomId - Starts in $minsLeft mins"

                            newNotifications.add(
                                createNotiItem(doc.id, title, body, dateStr, startTimeStr, endTimeStr, "start", minsLeft)
                            )

                            val uniqueNotiId = "${doc.id}_start"
                            if (!notifiedSet.contains(uniqueNotiId)) {
                                sendLocalNotification(doc.id.hashCode(), title, body)
                                notifiedSet.add(uniqueNotiId)
                            }
                        }

                        // 종료 10분 전
                        if (diffEnd > 0 && diffEnd <= TimeUnit.MINUTES.toMillis(10)) {
                            val minsLeft = (diffEnd + 59999) / 60000

                            val title = "Reservation Ending Soon"
                            val body = "$buildingName $roomId - Ends in $minsLeft mins. Please clean up!"

                            newNotifications.add(
                                createNotiItem(doc.id, title, body, dateStr, startTimeStr, endTimeStr, "end", minsLeft)
                            )

                            val uniqueNotiId = "${doc.id}_end"
                            if (!notifiedSet.contains(uniqueNotiId)) {
                                sendLocalNotification(doc.id.hashCode() + 1, title, body)
                                notifiedSet.add(uniqueNotiId)
                            }
                        }

                    } catch (e: Exception) {
                        Log.e("NotificationVM", "Error: ${e.message}")
                    }
                }

                newNotifications.sortBy { it.remainingTime }
                _notifications.postValue(newNotifications)
            }
    }

    private fun createNotiItem(id: String, title: String, location: String, date: String, start: String, end: String, type: String, mins: Long): NotificationItem {
        return NotificationItem(
            id = id.hashCode(),
            reservationId = id,
            title = title,
            location = location,
            date = date,
            startTime = start,
            endTime = end,
            remainingTime = "$mins mins left",
            type = type
        )
    }

    private fun sendLocalNotification(id: Int, title: String, content: String) {
        val builder = NotificationCompat.Builder(context, "lendmark_channel_id")
            .setSmallIcon(R.drawable.ic_notification_clock)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            notificationManager.notify(id, builder.build())
            Log.d("LocalNoti", "알림 발송: $title")
        } catch (e: SecurityException) {
            Log.e("LocalNoti", "권한 없음")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("lendmark_channel_id", "LendMark 알림", NotificationManager.IMPORTANCE_HIGH)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun loadBuildingNamesAndStartLoop() {
        db.collection("buildings").get()
            .addOnSuccessListener { result ->
                buildingNameMap = result.documents.associate { doc ->
                    doc.id to (doc.getString("name") ?: "Building ${doc.id}")
                }
                startPeriodicCheck()
            }
            .addOnFailureListener { startPeriodicCheck() }
    }

    fun selectNotification(item: NotificationItem) {
        _selectedNotification.value = item
        _notifications.value = _notifications.value?.map {
            if (it.id == item.id) it.copy(isRead = true) else it
        }
    }

    private fun convertPeriodToStartTime(period: Int): String {
        val hour = 8 + period
        return String.format(Locale.getDefault(), "%02d:00", hour)
    }

    private fun convertPeriodToEndTime(period: Int): String {
        val hour = 8 + period + 1
        return String.format(Locale.getDefault(), "%02d:00", hour)
    }
}