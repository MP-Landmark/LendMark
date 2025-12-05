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
import com.example.lendmark.R // ⭐ 패키지명이 맞는지 꼭 확인하세요! (빨간줄 뜨면 Alt+Enter로 import)
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

// ⭐ ViewModel -> AndroidViewModel로 변경 (Application Context 사용 위함)
class NotificationViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Application Context 가져오기
    private val context = getApplication<Application>().applicationContext

    // 화면에 보여줄 알림 리스트 (기존 기능 유지)
    private val _notifications = MutableLiveData<List<NotificationItem>>()
    val notifications: LiveData<List<NotificationItem>> get() = _notifications

    private val _selectedNotification = MutableLiveData<NotificationItem?>()
    val selectedNotification: LiveData<NotificationItem?> get() = _selectedNotification

    var isInAppEnabled: Boolean = true
    private var buildingNameMap = mapOf<String, String>()

    // ⭐ 중복 알림 방지용 (이미 보낸 알림 ID 저장)
    private val notifiedSet = mutableSetOf<String>()

    init {
        createNotificationChannel() // 앱 시작 시 알림 채널 생성
        loadBuildingNames()
    }

    // -----------------------------------------------------------
    // ⭐ [NEW] 시스템 알림 채널 만들기 (안드로이드 8.0 이상 필수)
    // -----------------------------------------------------------
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "LendMark 알림"
            val descriptionText = "예약 시작 및 종료 알림"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("lendmark_channel_id", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // -----------------------------------------------------------
    // ⭐ [NEW] 상단바 알림 보내기 함수
    // -----------------------------------------------------------
    private fun sendLocalNotification(id: Int, title: String, content: String) {
        val notificationId = "noti_$id"

        // 이미 보낸 알림이면 또 보내지 않음 (중복 방지)
        if (notifiedSet.contains(notificationId)) return

        val builder = NotificationCompat.Builder(context, "lendmark_channel_id")
            .setSmallIcon(R.drawable.ic_notification_clock) // ⭐ 아이콘이 없으면 R.drawable.ic_launcher_foreground 로 변경
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        try {
            notificationManager.notify(id, builder.build())
            notifiedSet.add(notificationId) // 보냄 처리
            Log.d("LocalNoti", "알림 발송 성공: $title")
        } catch (e: SecurityException) {
            Log.e("LocalNoti", "알림 권한 없음: ${e.message}")
        }
    }

    private fun loadBuildingNames() {
        db.collection("buildings").get()
            .addOnSuccessListener { result ->
                buildingNameMap = result.documents.associate { doc ->
                    doc.id to (doc.getString("name") ?: "Building ${doc.id}")
                }
                checkReservationsAndCreateNotifications()
            }
            .addOnFailureListener {
                checkReservationsAndCreateNotifications()
            }
    }

    fun checkReservationsAndCreateNotifications() {
        if (!isInAppEnabled) {
            _notifications.value = emptyList()
            return
        }

        val currentUser = auth.currentUser
        if (currentUser == null) {
            _notifications.value = emptyList()
            return
        }

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

                        // 시간 변환 (0 -> 08:00)
                        val startTimeStr = convertPeriodToStartTime(periodStart)
                        val endTimeStr = convertPeriodToEndTime(periodEnd)

                        val startDateTime = dateFormat.parse("$dateStr $startTimeStr")?.time ?: 0L
                        val endDateTime = dateFormat.parse("$dateStr $endTimeStr")?.time ?: 0L

                        val diffStart = startDateTime - currentTime
                        val diffEnd = endDateTime - currentTime

                        // 🔔 조건 1: 시작 30분 전
                        if (diffStart > 0 && diffStart <= TimeUnit.MINUTES.toMillis(30)) {
                            val minsLeft = TimeUnit.MILLISECONDS.toMinutes(diffStart) + 1
                            val title = "Reservation Starting Soon!"
                            val body = "$buildingName $roomId - Starts in $minsLeft mins"

                            // 1) 리스트에 추가 (기존 기능)
                            newNotifications.add(
                                NotificationItem(
                                    id = doc.id.hashCode(),
                                    reservationId = doc.id,
                                    title = title,
                                    location = "$buildingName - Room $roomId",
                                    date = dateStr,
                                    startTime = startTimeStr,
                                    endTime = endTimeStr,
                                    remainingTime = "Starts in $minsLeft mins",
                                    type = "start"
                                )
                            )

                            // 2) ⭐ 시스템 상단바 알림 발송 (추가된 기능)
                            sendLocalNotification(doc.id.hashCode(), title, body)
                        }

                        // 🔔 조건 2: 종료 10분 전
                        if (diffEnd > 0 && diffEnd <= TimeUnit.MINUTES.toMillis(10)) {
                            val minsLeft = TimeUnit.MILLISECONDS.toMinutes(diffEnd) + 1
                            val title = "Reservation Ending Soon"
                            val body = "Please clean up! Ends in $minsLeft mins"

                            // 1) 리스트에 추가
                            newNotifications.add(
                                NotificationItem(
                                    id = doc.id.hashCode() + 1,
                                    reservationId = doc.id,
                                    title = title,
                                    location = "$buildingName - Room $roomId",
                                    date = dateStr,
                                    startTime = startTimeStr,
                                    endTime = endTimeStr,
                                    remainingTime = "Ends in $minsLeft mins",
                                    type = "end"
                                )
                            )

                            // 2) ⭐ 시스템 상단바 알림 발송
                            sendLocalNotification(doc.id.hashCode() + 1, title, body)
                        }

                    } catch (e: Exception) {
                        Log.e("NotificationVM", "Error: ${e.message}")
                    }
                }

                newNotifications.sortBy { it.remainingTime }
                _notifications.value = newNotifications
            }
    }

    fun selectNotification(item: NotificationItem) {
        _selectedNotification.value = item
        _notifications.value = _notifications.value?.map {
            if (it.id == item.id) it.copy(isRead = true) else it
        }
    }

    // 시간 변환 수정됨 (0 -> 08:00)
    private fun convertPeriodToStartTime(period: Int): String {
        val hour = 8 + period
        return String.format(Locale.getDefault(), "%02d:00", hour)
    }

    private fun convertPeriodToEndTime(period: Int): String {
        val hour = 8 + period + 1
        return String.format(Locale.getDefault(), "%02d:00", hour)
    }
}