package com.example.lendmark.ui.home

import android.util.Log
import androidx.lifecycle.*
import com.example.lendmark.data.local.RecentRoomEntity
import com.example.lendmark.data.model.Building
import com.example.lendmark.data.sources.announcement.*
import com.example.lendmark.ui.home.adapter.Room
import com.example.lendmark.ui.main.MyApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class UpcomingReservationInfo(
    val reservationId: String,
    val roomName: String,
    val time: String
)

class HomeViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val uid = FirebaseAuth.getInstance().currentUser?.uid

    // ============= ANNOUNCEMENT =============
    private val announcementRepo = AnnouncementRepository(
        weatherRepo = WeatherRepository(),
        academicCrawler = AcademicCrawler()
    )

    private val _announcementSlides = MutableLiveData<List<AnnouncementItem>>()
    val announcementSlides: LiveData<List<AnnouncementItem>> = _announcementSlides


    // ============= FREQUENTLY USED ROOMS =============
    private val _frequentlyUsedRooms = MutableLiveData<List<Room>>()
    val frequentlyUsedRooms: LiveData<List<Room>> = _frequentlyUsedRooms

    // ============= UPCOMING RESERVATION =============
    private val _upcomingReservation = MutableLiveData<UpcomingReservationInfo?>()
    val upcomingReservation: LiveData<UpcomingReservationInfo?> = _upcomingReservation

    // ============= RECENT VIEWED ROOMS =============
    private val _recentViewedRooms = MutableLiveData<List<RecentRoomEntity>>()
    val recentViewedRooms: LiveData<List<RecentRoomEntity>> = _recentViewedRooms

    // ============= SEARCH RESULT =============
    private val _searchResults = MutableLiveData<List<String>>()
    val searchResults: LiveData<List<String>> = _searchResults

    // ============= HOME BUILDING LIST (NEW) =============
    private val _homeBuildings = MutableLiveData<List<Building>>()  // 랜덤 3개
    val homeBuildings: LiveData<List<Building>> = _homeBuildings


    // =============================================
    // LOAD ALL HOME DATA
    // =============================================
    fun loadHomeData() {
        loadAnnouncementSlides()
        loadFrequentlyUsedRooms()
        loadUpcomingReservation()
        loadRecentViewedRooms()
        loadHomeBuildings()
    }


    // =============================================
    // ANNOUNCEMENT
    // =============================================
    private fun loadAnnouncementSlides() {
        viewModelScope.launch {
            val slides = announcementRepo.loadAnnouncements()
            _announcementSlides.postValue(slides)
        }
    }


    // =============================================
    // RECENT VIEWED ROOMS
    // =============================================
    fun loadRecentViewedRooms() {
        viewModelScope.launch {
            val dao = MyApp.database.recentRoomDao()
            _recentViewedRooms.postValue(dao.getRecentRooms())
        }
    }


    fun addRecentViewedRoom(roomId: String, buildingId: String, roomName: String) {
        viewModelScope.launch {
            val dao = MyApp.database.recentRoomDao()
            dao.insertRecentRoom(
                RecentRoomEntity(
                    roomId = roomId,
                    buildingId = buildingId,
                    roomName = roomName,
                    viewedAt = System.currentTimeMillis()
                )
            )
            dao.trimRecentRooms()
            loadRecentViewedRooms()
        }
    }


    // =============================================
    // FREQUENTLY USED ROOMS
    // =============================================
    private fun loadFrequentlyUsedRooms() {
        if (uid == null) {
            _frequentlyUsedRooms.value = emptyList()
            return
        }

        db.collection("reservations")
            .whereEqualTo("userId", uid)
            .get()
            .addOnSuccessListener { docs ->
                if (docs.isEmpty) {
                    _frequentlyUsedRooms.value = emptyList()
                    return@addOnSuccessListener
                }

                val roomCounts = docs.mapNotNull { doc ->
                    val b = doc.getString("buildingId")
                    val r = doc.getString("roomId")
                    if (b != null && r != null) "$b $r" else null
                }.groupingBy { it }.eachCount()

                val top = roomCounts.entries.sortedByDescending { it.value }.take(3)
                val result = mutableListOf<Room>()

                if (top.isEmpty()) {
                    _frequentlyUsedRooms.value = emptyList()
                } else {
                    top.forEach { entry ->
                        val (buildingId, roomId) = entry.key.split(" ")

                        db.collection("buildings").document(buildingId)
                            .get()
                            .addOnSuccessListener { doc ->
                                val img = doc.getString("imageUrl") ?: ""
                                val buildingName = doc.getString("name") ?: buildingId

                                result.add(Room("$buildingName $roomId", img))

                                if (result.size == top.size) {
                                    _frequentlyUsedRooms.value = result
                                }
                            }
                    }
                }
            }
    }


    // =============================================
    // UPCOMING RESERVATION
    // =============================================
    private fun loadUpcomingReservation() {
        if (uid == null) {
            _upcomingReservation.value = null
            return
        }

        db.collection("reservations")
            .whereEqualTo("userId", uid)
            .whereEqualTo("status", "approved")
            .get()
            .addOnSuccessListener { snap ->
                if (snap.isEmpty) {
                    _upcomingReservation.value = null
                    return@addOnSuccessListener
                }

                val now = System.currentTimeMillis()
                val oneHourLater = now + (60 * 60 * 1000)

                val targetDoc = snap.documents.firstOrNull { doc ->
                    val dateStr = doc.getString("date") ?: return@firstOrNull false
                    val start = doc.getLong("periodStart")?.toInt() ?: return@firstOrNull false
                    val startMillis = convertToMillis(dateStr, start)
                    startMillis in now..oneHourLater
                }

                if (targetDoc == null) {
                    _upcomingReservation.value = null
                    return@addOnSuccessListener
                }

                val buildingId = targetDoc.getString("buildingId") ?: ""
                val roomId = targetDoc.getString("roomId") ?: ""
                val start = targetDoc.getLong("periodStart")!!.toInt()
                val end = targetDoc.getLong("periodEnd")!!.toInt()
                val date = targetDoc.getString("date") ?: ""

                db.collection("buildings").document(buildingId).get()
                    .addOnSuccessListener { buildingDoc ->
                        val buildingName = buildingDoc.getString("name") ?: buildingId
                        val time = "$date • ${periodToTime(start)} - ${periodToTime(end + 1)}"

                        _upcomingReservation.value =
                            UpcomingReservationInfo(targetDoc.id, "$buildingName $roomId", time)
                    }
            }
    }


    private fun convertToMillis(dateStr: String, periodStart: Int): Long {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = sdf.parse(dateStr) ?: return 0L

            val cal = Calendar.getInstance()
            cal.time = date
            cal.set(Calendar.HOUR_OF_DAY, 8 + periodStart)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.timeInMillis
        } catch (e: Exception) {
            0L
        }
    }

    private fun periodToTime(period: Int): String {
        val hour = 8 + period
        return String.format("%02d:00", hour)
    }


    // =============================================
    // 🔎 BUILDING SEARCH
    // =============================================
    fun searchBuilding(query: String) {
        val clean = query.trim()
        if (clean.isEmpty()) {
            _searchResults.value = emptyList()
            return
        }

        db.collection("buildings")
            .get()
            .addOnSuccessListener { result ->
                val matched = result.documents.mapNotNull { doc ->
                    val name = doc.getString("name")
                    if (name != null && name.contains(clean, ignoreCase = true)) name else null
                }
                _searchResults.value = matched
            }
            .addOnFailureListener {
                _searchResults.value = emptyList()
            }
    }

    fun clearSearchResults() {
        _searchResults.value = emptyList()
    }


    // =============================================
    // ⭐ HOME — BUILDING LIST (RANDOM 3개)
    // =============================================
    private fun loadHomeBuildings() {
        db.collection("buildings")
            .get()
            .addOnSuccessListener { docs ->
                val all = docs.toObjects(Building::class.java)

                // 🔥 랜덤 3개 선택
                _homeBuildings.value = all.shuffled().take(3)
            }
            .addOnFailureListener {
                _homeBuildings.value = emptyList()
            }
    }
}
