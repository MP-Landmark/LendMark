package com.example.lendmark.ui.room

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.TextView
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.gridlayout.widget.GridLayout
import com.example.lendmark.R
import com.example.lendmark.databinding.FragmentRoomScheduleBinding
import com.example.lendmark.utils.SlotState
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.math.min
import kotlin.math.max
import android.widget.Button
import android.widget.Spinner
import android.widget.ArrayAdapter
import com.google.firebase.auth.FirebaseAuth

class RoomScheduleFragment : Fragment() {

    private var _binding: FragmentRoomScheduleBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()

    private lateinit var buildingId: String
    private lateinit var roomId: String
    private lateinit var buildingName: String

    // 시간표 구조 (08:00~17:00 → 총 10칸)
    private val periodLabels = listOf(
        "08:00", "09:00", "10:00", "11:00",
        "12:00", "13:00", "14:00", "15:00",
        "16:00", "17:00"
    )
    private val periods = periodLabels.indices

    private val dayKeys = listOf("Mon", "Tue", "Wed", "Thu", "Fri")
    private val dayLabels = listOf("월", "화", "수", "목", "금")

    // 각 칸에 대응되는 TextView
    private val cellViews = mutableMapOf<Pair<Int, Int>, TextView>()
    private val cellState = mutableMapOf<Pair<Int, Int>, SlotState>()

    private data class SelectedRange(var day: Int, var start: Int, var end: Int)
    private var selectedRange: SelectedRange? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRoomScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        buildingId = arguments?.getString("buildingId") ?: ""
        roomId = arguments?.getString("roomId") ?: ""
        buildingName = arguments?.getString("buildingName") ?: "건물"

        binding.tvRoomTitle.text = "$buildingName ${roomId}호"

        createGridTable()
        loadTimetable()

        // 그리드 생성 후 예약 불러오기
        Handler(Looper.getMainLooper()).post {
            loadExistingReservations()
        }

        updateSelectionInfo()

        binding.btnReserve.setOnClickListener {
            openReservationDialog()
        }
    }

    // ============================================================
    // 1) GridLayout 표 구성
    // ============================================================

    private fun createGridTable() {
        val grid = binding.gridSchedule
        grid.removeAllViews()
        grid.columnCount = 6    // 시간 + 월~금

        // ---------- 헤더 ----------
        addHeaderCell(0, 0, "시간")
        for (i in dayLabels.indices) {
            addHeaderCell(0, i + 1, dayLabels[i])
        }

        // ---------- 내용 ----------
        for (p in periods) {

            // 왼쪽 시간
            addTimeCell(p + 1, 0, periodLabels[p])

            // 월~금
            for (d in 0 until 5) {
                val tv = addEmptyCell(p + 1, d + 1)

                val key = d to p
                cellState[key] = SlotState.EMPTY
                cellViews[key] = tv

                tv.setOnClickListener { onCellClicked(d, p) }
            }
        }
    }

    private fun addHeaderCell(row: Int, col: Int, text: String) {
        val grid = binding.gridSchedule

        val tv = TextView(requireContext()).apply {
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = dp(45)
                rowSpec = GridLayout.spec(row)
                columnSpec = GridLayout.spec(col, 1f)
                setMargins(1, 1, 1, 1)
            }
            layoutParams = params
            gravity = Gravity.CENTER
            this.text = text
            textSize = 12f
            setPadding(4)
            setBackgroundResource(R.drawable.bg_time_cell)
        }

        grid.addView(tv)
    }

    private fun addTimeCell(row: Int, col: Int, text: String) {
        val tv = TextView(requireContext()).apply {
            this.text = text
            gravity = Gravity.CENTER
            textSize = 12f
            setTextColor(Color.BLACK)
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_time_cell)
        }
        binding.gridSchedule.addView(tv, gridParam(row, col, 1))
    }

    private fun addEmptyCell(row: Int, col: Int): TextView {
        val tv = TextView(requireContext()).apply {
            gravity = Gravity.CENTER
            textSize = 11f
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_cell_empty)
        }
        binding.gridSchedule.addView(tv, gridParam(row, col, 1))
        return tv
    }

    // rowSpan 적용 가능
    private fun gridParam(row: Int, col: Int, rowSpan: Int): GridLayout.LayoutParams {
        var rowSpec = GridLayout.spec(row, rowSpan)
        val colSpec = GridLayout.spec(col, 1)
        return GridLayout.LayoutParams(rowSpec, colSpec).apply {
            width = 0
            height = dp(60)
            columnSpec = colSpec
            rowSpec = rowSpec
            setGravity(Gravity.FILL)
        }
    }

    // ============================================================
    // 2) 수업(강의) 블록 배치 (rowSpan으로 병합)
    // ============================================================

    private fun loadTimetable() {
        db.collection("buildings").document(buildingId)
            .get()
            .addOnSuccessListener { doc ->

                val roomData =
                    (doc.get("timetable") as? Map<String, Any>)
                        ?.get(roomId) as? Map<String, Any> ?: return@addOnSuccessListener

                val schedule = roomData["schedule"] as? List<Map<String, Any>>
                    ?: return@addOnSuccessListener

                schedule.forEach { item ->
                    val dayKey = item["day"] as? String ?: return@forEach
                    val dayIndex = dayKeys.indexOf(dayKey)
                    if (dayIndex == -1) return@forEach

                    val start = (item["periodStart"] as Long).toInt()
                    val end = (item["periodEnd"] as Long).toInt()
                    val subject = item["subject"] as? String ?: ""

                    // 기존 칸 제거 후 병합 셀 추가
                    mergeClassBlock(dayIndex, start, end, subject)
                }
            }
    }

    private fun mergeClassBlock(day: Int, start: Int, end: Int, title: String) {
        val grid = binding.gridSchedule

        // 기존 칸 숨기고 상태 CLASS로
        for (p in start..end) {
            val key = day to p
            val v = cellViews[key] ?: continue
            v.visibility = View.GONE
            cellState[key] = SlotState.CLASS
        }

        val rowStart = start + 1
        val span = end - start + 1

        val tv = TextView(requireContext()).apply {
            text = title
            gravity = Gravity.CENTER
            textSize = 11f
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_cell_class)
        }

        grid.addView(tv, gridParam(rowStart, day + 1, span))
    }

    // ============================================================
    // 3) 셀 선택 로직
    // ============================================================

    private fun onCellClicked(day: Int, p: Int) {

        // 이미 예약된 칸은 클릭 불가
        if (cellState[day to p] == SlotState.CLASS ||
            cellState[day to p] == SlotState.RESERVED) return

        val range = selectedRange

        // 기존에 선택된 것이 없으면 새로 선택
        if (range == null) {
            selectedRange = SelectedRange(day, p, p)
            applySelection()
            updateSelectionInfo()
            return
        }

        // 같은 칸을 다시 눌렀을 때 -> 선택 취소
        if (range.day == day && range.start == p && range.end == p) {
            clearSelection()
            updateSelectionInfo()
            return
        }

        // 다른 날이면 리셋 후 새로 선택
        if (range.day != day) {
            clearSelection()
            selectedRange = SelectedRange(day, p, p)
            applySelection()
            updateSelectionInfo()
            return
        }

        // 연속 범위 확장
        if (p < range.start || p > range.end) {

            // 중간에 수업이 껴있는지 검사
            for (i in min(range.start, p)..max(range.end, p)) {
                if (cellState[day to i] == SlotState.CLASS ||
                    cellState[day to i] == SlotState.RESERVED) return
            }

            range.start = min(range.start, p)
            range.end = max(range.end, p)
        } else {
            // 눌렀는데 범위 안에 있음 → 단일 셀 취급하고 다시 선택
            clearSelection()
            selectedRange = SelectedRange(day, p, p)
        }

        applySelection()
        updateSelectionInfo()
    }


    private fun clearSelection() {
        selectedRange = null
        cellViews.forEach { (key, v) ->
            val st = cellState[key]
            // 수업/예약 칸은 건드리지 않음
            if (st != SlotState.CLASS && st != SlotState.RESERVED) {
                v.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_cell_empty)
            }
        }
    }

    private fun applySelection() {
        val range = selectedRange ?: return

        // 먼저 선택 가능 칸들만 비우기
        cellViews.forEach { (key, v) ->
            val st = cellState[key]
            if (st != SlotState.CLASS && st != SlotState.RESERVED) {
                v.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_cell_empty)
            }
        }

        // 선택 구간만 보라색으로
        for (p in range.start..range.end) {
            val key = range.day to p
            val st = cellState[key]
            if (st == SlotState.EMPTY || st == SlotState.SELECTED) {
                val v = cellViews[key] ?: continue
                v.background = ContextCompat.getDrawable(
                    requireContext(),
                    R.drawable.bg_cell_selected
                )
            }
        }
    }

    // ============================================================
    // 4) 선택 정보 표시
    // ============================================================

    private fun updateSelectionInfo() {
        val range = selectedRange
        if (range == null) {
            binding.tvSelectedInfo.text = "선택된 시간이 없습니다."
            binding.tvSelectedDuration.text = ""
            binding.btnReserve.isEnabled = false
            return
        }

        val dayKor = dayLabels[range.day] + "요일"
        val startT = periodLabels[range.start]
        val endT = periodLabels[min(periodLabels.size - 1, range.end + 1)]

        val hours = range.end - range.start + 1

        binding.tvSelectedInfo.text = "$dayKor $startT - $endT"
        binding.tvSelectedDuration.text = "${hours}시간 선택됨"
        binding.btnReserve.isEnabled = true
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    // ============================================================
    // 5) 예약 다이얼로그
    // ============================================================

    private fun openReservationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.reservation_dialog, null)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val etUserName = dialogView.findViewById<EditText>(R.id.etUserName)
        val etMajor = dialogView.findViewById<EditText>(R.id.etMajor)
        val spPurpose = dialogView.findViewById<Spinner>(R.id.spPurpose)
        val etPeople = dialogView.findViewById<EditText>(R.id.etPeople)
        val etPurposeCustom = dialogView.findViewById<EditText>(R.id.etPurposeCustom)
        val btnSubmit = dialogView.findViewById<Button>(R.id.btnSubmit)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)

        // 현재 로그인한 사용자 정보 불러오기
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            db.collection("users").document(uid).get()
                .addOnSuccessListener { document ->
                    if (document != null) {
                        val name = document.getString("name") ?: ""
                        val major = document.getString("department") ?: ""
                        etUserName.setText(name)
                        etMajor.setText(major)
                    }
                }
        }

        // ----------- 목적 드롭다운 -----------
        val items = listOf("스터디", "발표 준비", "미팅", "기타 (직접 입력)")
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            items
        )
        spPurpose.adapter = adapter

        spPurpose.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                val selected = items[position]
                if (selected.contains("기타")) {
                    etPurposeCustom.visibility = View.VISIBLE
                } else {
                    etPurposeCustom.visibility = View.GONE
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // ----------- 다음 버튼 -----------
        btnSubmit.setOnClickListener {
            val name = etUserName.text.toString()
            val major = etMajor.text.toString()
            val peopleStr = etPeople.text.toString()

            if (name.isBlank() || major.isBlank() || peopleStr.isBlank()) {
                Toast.makeText(requireContext(), "모든 정보를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val people = peopleStr.toIntOrNull()
            if (people == null || people <= 0) {
                Toast.makeText(requireContext(), "인원 수를 올바르게 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val purpose = if (etPurposeCustom.visibility == View.VISIBLE) {
                etPurposeCustom.text.toString()
            } else {
                spPurpose.selectedItem.toString()
            }

            saveReservation(name, major, people, purpose)
            dialog.dismiss()
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    // ============================================================
    // 6) Firestore 저장
    // ============================================================

    private fun saveReservation(
        userName: String,
        major: String,
        people: Int,
        purpose: String
    ) {
        val range = selectedRange ?: return
        val dayKey = dayKeys[range.day]   // Mon, Tue ...

        val reservation = hashMapOf(
            "userName" to userName,
            "major" to major,
            "people" to people,
            "purpose" to purpose,
            "roomId" to roomId,
            "buildingId" to buildingId,
            "day" to dayKey,
            "periodStart" to range.start,
            "periodEnd" to range.end,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("reservations")
            .add(reservation)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "예약 완료!", Toast.LENGTH_SHORT).show()
                // 예약된 것을 시간표에 반영
                applyReservationToTable(range.day, range.start, range.end)
            }.addOnFailureListener {
                Toast.makeText(requireContext(), "오류 발생", Toast.LENGTH_SHORT).show()
            }
    }

    // 예약 완료 후 시간표에 ‘예약됨’ 표시
    private fun applyReservationToTable(day: Int, start: Int, end: Int) {
        for (p in start..end) {
            val key = day to p
            val tv = cellViews[key] ?: continue

            tv.background = ContextCompat.getDrawable(
                requireContext(),
                R.drawable.bg_cell_reserved
            )
            tv.text = "예약됨"
            tv.setTextColor(Color.BLACK)

            cellState[key] = SlotState.RESERVED
            tv.setOnClickListener(null)
        }
    }

    // ============================================================
    // 7) Firestore에서 기존 예약 불러오기
    // ============================================================

    private fun loadExistingReservations() {
        db.collection("reservations")
            .whereEqualTo("buildingId", buildingId)
            .whereEqualTo("roomId", roomId)
            .get()
            .addOnSuccessListener { result ->
                for (doc in result) {
                    val dayKey = doc.getString("day") ?: continue
                    val dayIndex = dayKeys.indexOf(dayKey)
                    if (dayIndex == -1) continue

                    val start = doc.getLong("periodStart")?.toInt() ?: continue
                    val end = doc.getLong("periodEnd")?.toInt() ?: continue

                    // 시간표에 표시
                    markReservationOnTable(dayIndex, start, end)
                }
            }
    }

    private fun markReservationOnTable(day: Int, start: Int, end: Int) {
        for (p in start..end) {
            val key = day to p
            val tv = cellViews[key] ?: continue

            tv.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_cell_reserved)

            tv.text = "예약됨"
            tv.setTextColor(Color.BLACK)

            // 이미 예약된 칸은 클릭 불가
            tv.setOnClickListener(null)
            cellState[key] = SlotState.RESERVED
        }
    }
}
