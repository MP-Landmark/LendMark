package com.example.lendmark.ui.notification

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class NotificationViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // 화면에 보여줄 알림 리스트
    private val _notifications = MutableLiveData<List<NotificationItem>>()
    val notifications: LiveData<List<NotificationItem>> get() = _notifications

    // 선택된 알림 (다이얼로그용)
    private val _selectedNotification = MutableLiveData<NotificationItem?>()
    val selectedNotification: LiveData<NotificationItem?> get() = _selectedNotification

    // 인앱 알림 활성화 여부 (기본값 true)
    var isInAppEnabled: Boolean = true

    // 건물 ID와 이름을 매칭할 저장소 (예: "14" -> "Ceramics Hall")
    private var buildingNameMap = mapOf<String, String>()

    init {
        // 앱이 켜지면 '건물 이름'을 먼저 불러오고 -> 그 다음 예약을 확인합니다.
        loadBuildingNames()
    }

    // [1단계] 건물 이름 데이터 미리 가져오기
    private fun loadBuildingNames() {
        db.collection("buildings").get()
            .addOnSuccessListener { result ->
                // Firestore 문서 ID(예: "5")를 Key로, name 필드를 Value로 저장
                buildingNameMap = result.documents.associate { doc ->
                    val id = doc.id
                    val name = doc.getString("name") ?: "Building $id"
                    id to name
                }

                // 건물 이름 로딩이 끝나면 예약 체크 시작!
                checkReservationsAndCreateNotifications()
            }
            .addOnFailureListener {
                Log.e("NotificationVM", "건물 데이터 로딩 실패", it)
                // 실패하더라도 예약 체크는 진행 (건물 번호로 표시됨)
                checkReservationsAndCreateNotifications()
            }
    }

    // [2단계] Firestore 데이터를 가져와서 알림 생성
    fun checkReservationsAndCreateNotifications() {
        // 인앱 알림이 꺼져있으면 리스트를 비우고 종료
        if (!isInAppEnabled) {
            _notifications.value = emptyList()
            return
        }

        val currentUser = auth.currentUser
        if (currentUser == null) {
            _notifications.value = emptyList()
            return
        }

        val userId = currentUser.uid

        // 내 예약 가져오기
        db.collection("reservations")
            .whereEqualTo("userId", userId)
            // .whereEqualTo("status", "approved") // 필요 시 주석 해제 (승인된 것만 알림)
            .get()
            .addOnSuccessListener { documents ->
                val newNotifications = mutableListOf<NotificationItem>()
                val currentTime = System.currentTimeMillis()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

                for (doc in documents) {
                    try {
                        // 데이터 읽기
                        val dateStr = doc.getString("date") ?: ""
                        val periodStart = doc.getLong("periodStart")?.toInt() ?: 0
                        val periodEnd = doc.getLong("periodEnd")?.toInt() ?: 0

                        // 건물 ID로 이름 찾기 (없으면 기본값)
                        val buildingId = doc.getString("buildingId") ?: ""
                        val buildingName = buildingNameMap[buildingId] ?: "Building $buildingId"

                        val roomId = doc.getString("roomId") ?: ""

                        // 데이터가 불완전하면 패스
                        if (dateStr.isEmpty() || periodStart == 0 || periodEnd == 0) continue

                        val startTimeStr = convertPeriodToStartTime(periodStart)
                        val endTimeStr = convertPeriodToEndTime(periodEnd)

                        val startDateTime = dateFormat.parse("$dateStr $startTimeStr")?.time ?: 0L
                        val endDateTime = dateFormat.parse("$dateStr $endTimeStr")?.time ?: 0L

                        val diffStart = startDateTime - currentTime
                        val diffEnd = endDateTime - currentTime

                        // 조건 1: 시작 30분 전
                        if (diffStart > 0 && diffStart <= TimeUnit.MINUTES.toMillis(30)) {
                            val minsLeft = TimeUnit.MILLISECONDS.toMinutes(diffStart) + 1
                            newNotifications.add(
                                NotificationItem(
                                    id = doc.id.hashCode(),
                                    reservationId = doc.id,
                                    title = "Reservation starts in ${minsLeft} mins!",
                                    location = "$buildingName - Room $roomId", // 이름 적용됨
                                    date = dateStr,
                                    startTime = startTimeStr,
                                    endTime = endTimeStr,
                                    remainingTime = "Starts in ${minsLeft} mins",
                                    type = "start",
                                    isRead = false
                                )
                            )
                        }

                        // 조건 2: 종료 10분 전
                        if (diffEnd > 0 && diffEnd <= TimeUnit.MINUTES.toMillis(10)) {
                            val minsLeft = TimeUnit.MILLISECONDS.toMinutes(diffEnd) + 1
                            newNotifications.add(
                                NotificationItem(
                                    id = doc.id.hashCode() + 1,
                                    reservationId = doc.id,
                                    title = "Reservation ends in ${minsLeft} mins. Please clean up!",
                                    location = "$buildingName - Room $roomId", // 이름 적용됨
                                    date = dateStr,
                                    startTime = startTimeStr,
                                    endTime = endTimeStr,
                                    remainingTime = "Ends in ${minsLeft} mins",
                                    type = "end",
                                    isRead = false
                                )
                            )
                        }

                    } catch (e: Exception) {
                        Log.e("NotificationVM", "Error parsing reservation: ${e.message}")
                    }
                }

                // 최신 알림이 위로 오게 정렬
                newNotifications.sortBy { it.remainingTime }

                _notifications.value = newNotifications
            }
            .addOnFailureListener { e ->
                Log.e("NotificationVM", "Firestore error", e)
            }
    }

    // 아이템 클릭 시
    fun selectNotification(item: NotificationItem) {
        _selectedNotification.value = item
        // 클릭하면 읽음 처리 (UI 갱신용)
        _notifications.value = _notifications.value?.map {
            if (it.id == item.id) it.copy(isRead = true) else it
        }
    }

    // =================================================================
    // 수업시작시간 변환 (1교시 = 08:00)
    // =================================================================

    private fun convertPeriodToStartTime(period: Int): String {
        return when (period) {
            1 -> "08:00"
            2 -> "09:00"
            3 -> "10:00"
            4 -> "11:00"
            5 -> "12:00"
            6 -> "13:00"
            7 -> "14:00"
            8 -> "15:00"
            9 -> "16:00"
            10 -> "17:00"
            else -> "08:00" // 기본값
        }
    }

    private fun convertPeriodToEndTime(period: Int): String {
        return when (period) {
            1 -> "09:00"
            2 -> "10:00"
            3 -> "11:00"
            4 -> "12:00"
            5 -> "13:00"
            6 -> "14:00"
            7 -> "15:00"
            8 -> "16:00"
            9 -> "17:00"
            10 -> "18:00"
            else -> "18:00" // 기본값
        }
    }
}