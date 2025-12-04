package com.example.lendmark.ui.chatbot

import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.util.Log
import android.view.View
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lendmark.R
import com.example.lendmark.data.model.ChatMessage
import com.example.lendmark.ui.room.RoomScheduleFragment
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import java.time.*

class ChatBotActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var spinnerTime: Spinner
    private lateinit var spinnerBuilding: Spinner
    private lateinit var btnAskAI: Button
    private lateinit var recyclerChat: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvDateSelector: TextView

    private lateinit var selectorContainer: View
    private lateinit var fragmentContainer: View

    private lateinit var adapter: ChatBotAdapter
    private val messages = mutableListOf<ChatMessage>()

    private val db = FirebaseFirestore.getInstance()
    private val buildingOptions = mutableListOf<BuildingOption>()
    private val timeList = mutableListOf<String>()
    private var selectedBuildingId = ""
    private var selectedBuildingName = ""

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chatbot)

        initViews()
        setupToolbar()
        setupRecyclerView()
        setupDatePicker()
        loadBuildings()
        setupListeners()

        selectedBuildingId = intent.getStringExtra("buildingId") ?: ""
        selectedBuildingName = intent.getStringExtra("buildingName") ?: ""

        tvDateSelector.text = LocalDate.now().toString()
        updateTimeSpinner(tvDateSelector.text.toString())
    }

    private fun initViews() {
        toolbar = findViewById(R.id.chatbotToolbar)
        tvDateSelector = findViewById(R.id.tvDateSelector)
        spinnerTime = findViewById(R.id.spinnerTime)
        spinnerBuilding = findViewById(R.id.spinnerBuilding)
        btnAskAI = findViewById(R.id.btnAskAI)
        recyclerChat = findViewById(R.id.recyclerChat)
        progressBar = findViewById(R.id.progressBarAI)

        selectorContainer = findViewById(R.id.selectorContainer)
        fragmentContainer = findViewById(R.id.chatbotFragmentContainer)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "AI Assistant"

        toolbar.setNavigationOnClickListener { handleBackPress() }
    }

    private fun handleBackPress() {
        if (fragmentContainer.visibility == View.VISIBLE) {
            // 예약 화면에서 챗봇 화면으로 복귀
            fragmentContainer.visibility = View.GONE
            selectorContainer.visibility = View.VISIBLE
            recyclerChat.visibility = View.VISIBLE
            supportFragmentManager.popBackStack()
            supportActionBar?.title = "AI Assistant"
        } else {
            finish()
        }
    }

    private fun openRoomSchedule(buildingId: String, buildingName: String, roomId: String) {
        Log.e("OPEN_ROOM", "Open $buildingName $roomId")

        selectorContainer.visibility = View.GONE
        recyclerChat.visibility = View.GONE
        fragmentContainer.visibility = View.VISIBLE

        val fragment = RoomScheduleFragment().apply {
            arguments = Bundle().apply {
                putString("buildingId", buildingId)
                putString("buildingName", buildingName)
                putString("roomId", roomId)
            }
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.chatbotFragmentContainer, fragment)
            .addToBackStack("chatbotRoom")
            .commit()

        supportActionBar?.title = "$buildingName ${roomId}호"
    }

    private fun setupRecyclerView() {
        adapter = ChatBotAdapter(
            messages,
            onRoomClick = { roomId ->
                openRoomSchedule(selectedBuildingId, selectedBuildingName, roomId)
            }
        )

        recyclerChat.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        recyclerChat.adapter = adapter
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupDatePicker() {
        tvDateSelector.setOnClickListener { showDatePicker() }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showDatePicker() {
        val today = LocalDate.now()
        val maxDate = today.plusDays(28)

        val validator = object : CalendarConstraints.DateValidator {
            override fun isValid(date: Long): Boolean {
                val d = Instant.ofEpochMilli(date).atZone(ZoneId.systemDefault()).toLocalDate()
                return !(d.isBefore(today) || d.isAfter(maxDate) || d.dayOfWeek.value >= 6)
            }
            override fun describeContents() = 0
            override fun writeToParcel(dest: Parcel, flags: Int) {}
        }

        MaterialDatePicker.Builder.datePicker()
            .setTitleText("날짜 선택")
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .setCalendarConstraints(
                CalendarConstraints.Builder().setValidator(validator).build()
            )
            .build()
            .apply {
                addOnPositiveButtonClickListener {
                    val selectedDate = Instant.ofEpochMilli(it)
                        .atZone(ZoneId.systemDefault()).toLocalDate()
                    tvDateSelector.text = selectedDate.toString()
                    updateTimeSpinner(selectedDate.toString())
                }
                show(supportFragmentManager, "datePicker")
            }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateTimeSpinner(date: String) {
        timeList.clear()
        val today = LocalDate.now().toString()
        val nowHour = LocalTime.now().hour

        if (date == today) {
            if (nowHour < 18) timeList.add("지금 바로")
            for (h in 8..17) if (h > nowHour) timeList.add("${h}시")
        } else {
            for (h in 8..17) timeList.add("${h}시")
        }

        spinnerTime.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, timeList
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun loadBuildings() {
        db.collection("buildings")
            .orderBy("code")
            .get()
            .addOnSuccessListener { snap ->
                buildingOptions.clear()
                for (doc in snap) {
                    val id = doc.id
                    val name = doc.getString("name") ?: id
                    buildingOptions.add(BuildingOption(id, name))
                }

                spinnerBuilding.adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_spinner_item,
                    buildingOptions.map { it.name }
                ).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
            }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupListeners() {
        btnAskAI.setOnClickListener { askAI() }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun askAI() {
        val date = tvDateSelector.text.toString()
        val timeText = spinnerTime.selectedItem?.toString() ?: return

        val pos = spinnerBuilding.selectedItemPosition
        require(pos != Spinner.INVALID_POSITION) {
            Toast.makeText(this, "건물을 선택하세요.", Toast.LENGTH_SHORT).show()
        }

        val building = buildingOptions[pos]
        selectedBuildingId = building.id
        selectedBuildingName = building.name

        addUserMessage("$date $timeText 예약 가능한 ${building.name} 강의실 알려줘")

        val hour =
            if (timeText == "지금 바로") LocalTime.now().hour
            else timeText.replace("시", "").trim().toInt()

        requestAI(building.id, building.name, date, hour)
    }

    private fun addUserMessage(text: String) {
        adapter.addMessage(ChatMessage(text, isUser = true))
        recyclerChat.scrollToPosition(adapter.itemCount - 1)
    }

    private fun addAiMessage(text: String, rooms: List<String>) {
        adapter.addMessage(ChatMessage(text, rooms, isUser = false))
        recyclerChat.scrollToPosition(adapter.itemCount - 1)
    }

    private fun requestAI(buildingId: String, buildingName: String, date: String, hour: Int) {
        progressBar.visibility = View.VISIBLE

        FirebaseFunctions.getInstance("asia-northeast3")
            .getHttpsCallable("chatbotAvailableRoomsV2")
            .call(
                hashMapOf(
                    "buildingId" to buildingId,
                    "buildingName" to buildingName,
                    "date" to date,
                    "hour" to hour
                )
            )
            .addOnSuccessListener {
                progressBar.visibility = View.GONE

                val map = it.data as? Map<*, *> ?: return@addOnSuccessListener
                val answer = map["answer"] as? String ?: ""
                val rawRooms = map["rooms"]

                val rooms: List<String> =
                    if (rawRooms is List<*>) rawRooms.map { s -> s.toString() }
                    else if (rawRooms is Map<*, *>) rawRooms.keys.map { key -> key.toString() }
                    else emptyList()

                addAiMessage(answer, rooms)
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                addAiMessage("AI 요청 실패: ${e.message}", emptyList())
            }
    }

    data class BuildingOption(val id: String, val name: String)
}
