package com.example.lendmark.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lendmark.data.local.RecentRoomEntity
import com.example.lendmark.ui.home.adapter.Announcement
import com.example.lendmark.ui.home.adapter.Room
import com.example.lendmark.ui.main.MyApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

data class UpcomingReservationInfo(
    val reservationId: String,
    val roomName: String,
    val time: String
)

class HomeViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val uid = FirebaseAuth.getInstance().currentUser?.uid

    private val _announcements = MutableLiveData<List<Announcement>>()
    val announcements: LiveData<List<Announcement>> = _announcements

    private val _frequentlyUsedRooms = MutableLiveData<List<Room>>()
    val frequentlyUsedRooms: LiveData<List<Room>> = _frequentlyUsedRooms

    private val _upcomingReservation = MutableLiveData<UpcomingReservationInfo?>()
    val upcomingReservation: LiveData<UpcomingReservationInfo?> = _upcomingReservation

    private val _recentViewedRooms = MutableLiveData<List<RecentRoomEntity>>()
    val recentViewedRooms: LiveData<List<RecentRoomEntity>> = _recentViewedRooms


    fun loadHomeData() {
        // 공지
        _announcements.value = listOf(
            Announcement("Announcement", "Mon - Fri 09:00 - 18:00\nHolidays and vacations are closed"),
            Announcement("Review Event", "If you leave a review for your classroom, we will give you a voucher.")
        )

        loadFrequentlyUsedRooms()
        loadUpcomingReservation()
        loadRecentViewedRooms()
    }


    // ---------------------------------------------------------
    // ⭐ 1. 최근 본 강의실(LOCAL DB / ROOM)
    // ---------------------------------------------------------

    fun loadRecentViewedRooms() {
        viewModelScope.launch {
            val dao = MyApp.database.recentRoomDao()
            val rooms = dao.getRecentRooms()
            _recentViewedRooms.postValue(rooms)
        }
    }

    fun addRecentViewedRoom(roomId: String, buildingId: String, roomName: String) {
        viewModelScope.launch {
            val dao = MyApp.database.recentRoomDao()

            val entry = RecentRoomEntity(
                roomId = roomId,
                buildingId = buildingId,
                roomName = roomName,
                viewedAt = System.currentTimeMillis()
            )

            dao.insertRecentRoom(entry)
            dao.trimRecentRooms()
            loadRecentViewedRooms()
        }
    }


    // ---------------------------------------------------------
    // ⭐ 2. 자주 사용하는 강의실 (FIRESTORE)
    // ---------------------------------------------------------

    private fun loadFrequentlyUsedRooms() {
        if (uid == null) {
            _frequentlyUsedRooms.value = emptyList()
            return
        }

        db.collection("reservations")
            .whereEqualTo("userId", uid)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    _frequentlyUsedRooms.value = emptyList()
                    return@addOnSuccessListener
                }

                val roomCounts = documents.mapNotNull { doc ->
                    val building = doc.getString("buildingId")
                    val room = doc.getString("roomId")
                    if (building != null && room != null) "$building $room" else null
                }
                    .groupingBy { it }
                    .eachCount()

                val topRooms = roomCounts.entries
                    .sortedByDescending { it.value }
                    .take(3)
                    .map { Room(it.key, "") }

                _frequentlyUsedRooms.value = topRooms
            }
            .addOnFailureListener {
                _frequentlyUsedRooms.value = emptyList()
            }
    }


    // ---------------------------------------------------------
    // ⭐ 3. 곧 있을 예약 (FIRESTORE)
    // ---------------------------------------------------------

    private fun loadUpcomingReservation() {
        if (uid == null) {
            _upcomingReservation.value = null
            return
        }

        db.collection("reservations")
            .whereEqualTo("userId", uid)
            .whereEqualTo("status", "approved")
            .whereGreaterThanOrEqualTo("timestamp", Timestamp.now())
            .orderBy("timestamp")
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                if (snap.isEmpty) {
                    _upcomingReservation.value = null
                    return@addOnSuccessListener
                }

                val doc = snap.documents.first()

                val buildingId = doc.getString("buildingId") ?: ""
                val roomId = doc.getString("roomId") ?: ""
                val start = doc.getLong("periodStart")?.toInt() ?: 0
                val end = doc.getLong("periodEnd")?.toInt() ?: 0
                val date = doc.getString("date") ?: ""

                val timeText = "${periodToTime(start)} - ${periodToTime(end)}"

                _upcomingReservation.value = UpcomingReservationInfo(
                    reservationId = doc.id,
                    roomName = "$buildingId $roomId",
                    time = "$date • $timeText"
                )
            }
            .addOnFailureListener {
                _upcomingReservation.value = null
            }
    }


    private fun periodToTime(period: Int): String {
        val hour = 8 + period
        return String.format("%02d:00", hour)
    }

    // 검색 결과를 담을 LiveData 추가
    private val _searchResults = MutableLiveData<List<String>>()
    val searchResults: LiveData<List<String>> = _searchResults


    // ---------------------------------------------------------
    // 4. 건물 검색 기능 (SEARCH)
    // ---------------------------------------------------------
    fun searchBuilding(query: String) {
        val cleanQuery = query.trim()

        if (cleanQuery.isEmpty()) {
            _searchResults.value = emptyList()
            return
        }


        // 'buildings' 컬렉션의 모든 문서를 가져옴
        db.collection("buildings")
            .get()
            .addOnSuccessListener { result ->
                val matchedList = mutableListOf<String>()

                for (document in result.documents) {
                    // 1. 문서 안의 "name" 필드 값을 가져옵니다 (예: "Frontier Hall")
                    val buildingName = document.getString("name")

                    // 2. name 값이 존재하고, 검색어(query)를 포함하는지 확인 (대소문자 무시)
                    // 예: "Frontier Hall" 안에 "frontier"가 포함되어 있으면 통과
                    if (buildingName != null && buildingName.contains(cleanQuery, ignoreCase = true)) {
                        matchedList.add(buildingName)
                    }
                }

                // 결과 리스트 업데이트
                _searchResults.value = matchedList
            }
            .addOnFailureListener {
                _searchResults.value = emptyList()
            }
    }
    fun clearSearchResults() {
        _searchResults.value = emptyList()
    }
}
