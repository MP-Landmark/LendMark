package com.example.lendmark.ui.home

import android.util.Log
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
                    Log.d("FREQ", "No reservations found for user")
                    _frequentlyUsedRooms.value = emptyList()
                    return@addOnSuccessListener
                }

                Log.d("FREQ", "Total reservations = ${documents.size()}")

                val roomCounts = documents.mapNotNull { doc ->
                    val buildingId = doc.getString("buildingId")
                    val roomId = doc.getString("roomId")

                    Log.d(
                        "FREQ",
                        "Reservation -> buildingId=$buildingId, roomId=$roomId"
                    )

                    if (buildingId != null && roomId != null)
                        "$buildingId $roomId"
                    else null
                }
                    .groupingBy { it }
                    .eachCount()

                Log.d("FREQ", "Grouped Rooms = $roomCounts")

                val topRooms = roomCounts.entries
                    .sortedByDescending { it.value }
                    .take(3)

                Log.d("FREQ", "Top rooms = $topRooms")

                val result = mutableListOf<Room>()

                topRooms.forEach { entry ->
                    val parts = entry.key.split(" ")
                    val buildingId = parts.getOrNull(0) ?: ""
                    val roomId = parts.getOrNull(1) ?: ""

                    Log.d("FREQ", "Processing room: buildingId=$buildingId, roomId=$roomId")

                    db.collection("buildings").document(buildingId)
                        .get()
                        .addOnSuccessListener { doc ->
                            if (!doc.exists()) {
                                Log.e("FREQ", "Building document NOT FOUND for id=$buildingId")
                            }

                            val imageUrl = doc.getString("imageUrl") ?: ""

                            Log.d(
                                "FREQ",
                                "Building loaded: id=$buildingId, imageUrl=$imageUrl"
                            )

                            result.add(
                                Room(
                                    name = "$buildingId Hall $roomId",
                                    imageUrl = imageUrl
                                )
                            )

                            Log.d("FREQ", "Added Room -> name=${"$buildingId Hall $roomId"}, imageUrl=$imageUrl")

                            if (result.size == topRooms.size) {
                                Log.d("FREQ", "Final result list ready: $result")
                                _frequentlyUsedRooms.value = result
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("FREQ", "Error loading building $buildingId", e)
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("FREQ", "Failed to load reservations", e)
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


}
