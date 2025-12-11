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
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.gridlayout.widget.GridLayout
import com.example.lendmark.R
import com.example.lendmark.databinding.FragmentRoomScheduleBinding
import com.example.lendmark.utils.SlotState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import androidx.fragment.app.activityViewModels
import com.example.lendmark.ui.home.HomeViewModel
import android.util.Log


class RoomScheduleFragment : Fragment() {

    companion object {
        // -1주까지는 보기만, 0~4주까지 예약 가능
        private const val MIN_WEEK_OFFSET = -1
        private const val MAX_WEEK_OFFSET = 4
    }

    private var _binding: FragmentRoomScheduleBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()


    private val uid: String?
        get() = FirebaseAuth.getInstance().currentUser?.uid

    private lateinit var buildingId: String
    private lateinit var roomId: String
    private lateinit var buildingName: String

    // 0 = 이번주, -1 = 지난주, +1 = 다음주 ...
    private var weekOffset = 0
    private val weekDates = mutableListOf<Date>()  // 월~금 실제 날짜

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

    private val homeViewModel: HomeViewModel by activityViewModels()


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRoomScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        buildingId = arguments?.getString("buildingId") ?: ""
        roomId = arguments?.getString("roomId") ?: ""
        buildingName = arguments?.getString("buildingName") ?: "건물"

        // ⭐ 추가: 최근 본 강의실 저장
        saveRecentlyViewedRoom()

        binding.tvRoomTitle.text = "$buildingName ${roomId}호"

        // 주 정보 계산 + UI 세팅
        calculateWeekDates()
        updateWeekUI()
        updateWeekButtons()

        // 표 생성 + 수업/예약 로드
        createGridTable()
        loadTimetable()

        Handler(Looper.getMainLooper()).post {
            loadExistingReservations()
        }

        updateSelectionInfo()

        // 이전 주
        binding.btnPrevWeek.setOnClickListener {
            if (weekOffset > MIN_WEEK_OFFSET) {
                weekOffset--
                refreshWeek()
            }
        }

        // 다음 주
        binding.btnNextWeek.setOnClickListener {
            if (weekOffset < MAX_WEEK_OFFSET) {
                weekOffset++
                refreshWeek()
            }
        }

        // 예약 버튼
        binding.btnReserve.setOnClickListener {
            openReservationDialog()
        }
    }

    // ============================================================
    // 1) 주간 날짜 계산 & UI
    // ============================================================

    // 이번 주 기준, weekOffset 적용해서 월~금 실제 날짜 계산
    private fun calculateWeekDates() {
        weekDates.clear()

        val calendar = Calendar.getInstance()
        calendar.time = Date()

        // 이번 주 월요일 정확히 세팅
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)

        // weekOffset 적용
        calendar.add(Calendar.WEEK_OF_YEAR, weekOffset)

        for (i in 0..4) {
            weekDates.add(calendar.time)
            calendar.add(Calendar.DATE, 1)
        }
    }

    private fun updateWeekUI() {
        if (weekDates.isEmpty()) return

        val sdf = SimpleDateFormat("MM.dd", Locale.KOREA)
        val start = sdf.format(weekDates.first())
        val end = sdf.format(weekDates.last())
        binding.tvWeekRange.text = "$start - $end"
    }

    private fun refreshWeek() {
        selectedRange = null
        updateSelectionInfo()

        calculateWeekDates()
        updateWeekUI()
        updateWeekButtons()

        createGridTable()
        loadTimetable()
        loadExistingReservations()
    }

    private fun updateWeekButtons() {
        // 이번주(0)에는 이전주 버튼 숨김
        binding.btnPrevWeek.visibility =
            if (weekOffset == 0) View.INVISIBLE else View.VISIBLE

        // 다음주 버튼은 "오늘 + 28일" 안넘는 경우에만 보이게
        if (weekDates.isEmpty()) {
            binding.btnNextWeek.visibility = View.INVISIBLE
            return
        }

        val lastDayOfWeek = weekDates.last()
        val cal = Calendar.getInstance()
        cal.time = Date()
        cal.add(Calendar.DATE, 28)
        val limit = cal.time

        binding.btnNextWeek.visibility =
            if (lastDayOfWeek.before(limit)) View.VISIBLE else View.INVISIBLE
    }

    // ============================================================
    // 2) GridLayout 표 구성 (헤더 + 칸)
    // ============================================================

    private fun createGridTable() {
        val grid = binding.gridSchedule
        grid.removeAllViews()
        grid.columnCount = 6 // 시간 + 월~금

        cellViews.clear()
        cellState.clear()

        // ---------- 헤더 ----------
        addHeaderCell(0, 0, "시간")

        val headerDateFormat = SimpleDateFormat("MM/dd", Locale.KOREA)
        for (i in dayLabels.indices) {
            val dateText = if (weekDates.size > i) {
                headerDateFormat.format(weekDates[i])
            } else {
                ""
            }
            addHeaderCell(0, i + 1, dayLabels[i] + "\n" + dateText)
        }

        // ---------- 내용 ----------
        for (p in periods) {
            // 왼쪽 시간
            addTimeCell(p + 1, 0, periodLabels[p])

            // 월~금
            for (d in 0 until 5) {
                val date = weekDates[d]
                val reservableDate = isDateReservable(d)
                val reservableTime = !isPastTimeSlot(date, p)

                val isReservable = reservableDate && reservableTime

                val state = if (isReservable) {
                    SlotState.EMPTY
                } else {
                    SlotState.DISABLED
                }

                val tv = addEmptyCell(p + 1, d + 1, state)

                val key = d to p
                cellState[key] = state
                cellViews[key] = tv

                if (state == SlotState.EMPTY) {
                    tv.setOnClickListener { onCellClicked(d, p) }
                } else {
                    tv.isClickable = false
                }
            }
        }
    }

    private fun addHeaderCell(row: Int, col: Int, text: String) {
        val grid = binding.gridSchedule

        val tv = TextView(requireContext()).apply {
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = dp(60)  // 행 높이와 맞춤
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

    private fun addEmptyCell(row: Int, col: Int, state: SlotState): TextView {
        val tv = TextView(requireContext()).apply {
            gravity = Gravity.CENTER
            textSize = 11f

            when (state) {
                SlotState.EMPTY -> {
                    background = ContextCompat.getDrawable(
                        requireContext(),
                        R.drawable.bg_cell_empty
                    )
                    setTextColor(Color.BLACK)
                    isClickable = true
                }
                SlotState.DISABLED -> {
                    background = ContextCompat.getDrawable(
                        requireContext(),
                        R.drawable.bg_cell_disabled
                    )
                    setTextColor(Color.parseColor("#888888"))
                    isClickable = false
                }
                else -> {
                    // 기본은 EMPTY와 동일하게, 나중에 state로 다시 덮어씀
                    background = ContextCompat.getDrawable(
                        requireContext(),
                        R.drawable.bg_cell_empty
                    )
                    setTextColor(Color.BLACK)
                }
            }
        }

        binding.gridSchedule.addView(tv, gridParam(row, col, 1))
        return tv
    }

    // rowSpan 적용 가능
    private fun gridParam(row: Int, col: Int, rowSpan: Int): GridLayout.LayoutParams {
        val rowSpec = GridLayout.spec(row, rowSpan)
        val colSpec = GridLayout.spec(col, 1)
        return GridLayout.LayoutParams(rowSpec, colSpec).apply {
            width = 0
            height = dp(60)
            columnSpec = colSpec
            this.rowSpec = rowSpec
            setGravity(Gravity.FILL)
            setMargins(1, 1, 1, 1)   // 헤더와 동일한 테두리 간격
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    // ============================================================
    //  날짜 관련 유틸
    // ============================================================

    private fun getDateString(date: Date): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
        return sdf.format(date)
    }

    private fun isDateAfterLimit(date: Date): Boolean {
        val cal = Calendar.getInstance()
        cal.time = Date()
        cal.add(Calendar.DATE, 28)  // 4주 = 28일
        val limit = cal.time
        return date.after(limit)
    }

    // 오늘 날짜의 이미 지난 시간 칸인지
    private fun isPastTimeSlot(date: Date, periodIndex: Int): Boolean {
        val now = Calendar.getInstance()

        val target = Calendar.getInstance().apply {
            time = date
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // 날짜가 오늘이 아니면 "지나간 시간" 개념 없음
        if (target.get(Calendar.YEAR) != now.get(Calendar.YEAR) ||
            target.get(Calendar.DAY_OF_YEAR) != now.get(Calendar.DAY_OF_YEAR)
        ) {
            return false
        }

        // 08:00 + periodIndex 기준
        val hour = 8 + periodIndex
        val endHour = hour + 1

        val nowHour = now.get(Calendar.HOUR_OF_DAY)
        val nowMinute = now.get(Calendar.MINUTE)

        return nowHour > endHour || (nowHour == endHour && nowMinute > 0)
    }

    // 해당 dayIndex(0~4)가 예약 가능한 날짜인지
    private fun isDateReservable(dayIndex: Int): Boolean {
        if (dayIndex !in 0 until weekDates.size) return false

        val target = Calendar.getInstance().apply {
            time = weekDates[dayIndex]
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val todayCal = Calendar.getInstance().apply {
            time = Date()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // 과거 날짜는 예약 불가
        if (target.before(todayCal)) return false

        // 4주 이후 날짜는 예약 불가
        if (isDateAfterLimit(target.time)) return false

        // 주 단위 제한 (0~4주만)
        return weekOffset in 0..MAX_WEEK_OFFSET
    }

    // ============================================================
    // 3) 수업(강의) 블록 배치 (rowSpan으로 병합)
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

                    // 지난 날짜/4주 이후 날짜엔 수업 표시 안 함
                    if (!isDateReservable(dayIndex)) return@forEach

                    val start = (item["periodStart"] as Long).toInt()
                    val end = (item["periodEnd"] as Long).toInt()
                    val subject = item["subject"] as? String ?: ""

                    // 오늘 날짜라면 이미 지난 시간대 수업은 안보이게
                    val date = weekDates[dayIndex]
                    if (isPastTimeSlot(date, start)) return@forEach

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
    // 4) 셀 선택 로직
    // ============================================================

    private fun onCellClicked(day: Int, p: Int) {
        // 오늘 날짜의 이미 지난 시간 → 선택 불가
        if (isPastTimeSlot(weekDates[day], p)) return

        // 날짜가 예약 불가면 선택 불가
        if (!isDateReservable(day)) return

        // 수업/예약/비활성 칸은 선택 불가
        when (cellState[day to p]) {
            SlotState.CLASS, SlotState.RESERVED, SlotState.DISABLED -> return
            else -> {}
        }

        val range = selectedRange

        // 처음 선택
        if (range == null) {
            selectedRange = SelectedRange(day, p, p)
            applySelection()
            updateSelectionInfo()
            return
        }

        // 같은 칸 다시 클릭 → 선택 취소
        if (range.day == day && range.start == p && range.end == p) {
            clearSelection()
            updateSelectionInfo()
            return
        }

        // 다른 요일 클릭 → 새로 선택
        if (range.day != day) {
            clearSelection()
            selectedRange = SelectedRange(day, p, p)
            applySelection()
            updateSelectionInfo()
            return
        }

        // 같은 요일 내 연속 확장
        if (p < range.start || p > range.end) {
            // 중간에 수업/예약 껴있으면 확장 불가
            for (i in min(range.start, p)..max(range.end, p)) {
                if (cellState[day to i] == SlotState.CLASS ||
                    cellState[day to i] == SlotState.RESERVED ||
                    cellState[day to i] == SlotState.DISABLED
                ) return
            }

            range.start = min(range.start, p)
            range.end = max(range.end, p)
        } else {
            // 영역 안쪽 클릭 → 단일 셀로 재선택
            clearSelection()
            selectedRange = SelectedRange(day, p, p)
        }

        applySelection()
        updateSelectionInfo()
    }

    private fun clearSelection() {
        selectedRange = null

        // 상태 기반으로 다시 그리기
        cellViews.forEach { (key, v) ->
            when (cellState[key]) {
                SlotState.CLASS -> {
                    v.background = ContextCompat.getDrawable(
                        requireContext(),
                        R.drawable.bg_cell_class
                    )
                    v.setTextColor(Color.BLACK)
                }
                SlotState.RESERVED -> {
                    v.background = ContextCompat.getDrawable(
                        requireContext(),
                        R.drawable.bg_cell_reserved
                    )
                    v.setTextColor(Color.BLACK)
                }
                SlotState.DISABLED -> {
                    v.background = ContextCompat.getDrawable(
                        requireContext(),
                        R.drawable.bg_cell_disabled
                    )
                    v.setTextColor(Color.parseColor("#888888"))
                }
                else -> {
                    v.background = ContextCompat.getDrawable(
                        requireContext(),
                        R.drawable.bg_cell_empty
                    )
                    v.setTextColor(Color.BLACK)
                }
            }
        }
    }

    private fun applySelection() {
        val range = selectedRange ?: return

        // 먼저 전체 셀을 상태에 맞게 다시 칠함 (선택 흔적 제거)
        cellViews.forEach { (key, v) ->
            when (cellState[key]) {
                SlotState.CLASS -> {
                    v.background = ContextCompat.getDrawable(
                        requireContext(),
                        R.drawable.bg_cell_class
                    )
                    v.setTextColor(Color.BLACK)
                }
                SlotState.RESERVED -> {
                    v.background = ContextCompat.getDrawable(
                        requireContext(),
                        R.drawable.bg_cell_reserved
                    )
                    v.setTextColor(Color.BLACK)
                }
                SlotState.DISABLED -> {
                    v.background = ContextCompat.getDrawable(
                        requireContext(),
                        R.drawable.bg_cell_disabled
                    )
                    v.setTextColor(Color.parseColor("#888888"))
                }
                else -> {
                    v.background = ContextCompat.getDrawable(
                        requireContext(),
                        R.drawable.bg_cell_empty
                    )
                    v.setTextColor(Color.BLACK)
                }
            }
        }

        // 선택 영역만 보라색으로 표시 (예약 가능한 칸만)
        for (p in range.start..range.end) {
            val key = range.day to p
            if (cellState[key] == SlotState.EMPTY) {
                val v = cellViews[key] ?: continue
                v.background = ContextCompat.getDrawable(
                    requireContext(),
                    R.drawable.bg_cell_selected
                )
                v.setTextColor(Color.BLACK)
            }
        }
    }

    // ============================================================
    // 5) 선택 정보 표시
    // ============================================================

    // 날짜 포맷 → "수요일 10:00 - 12:00" 중 날짜 부분 만드는 함수
    private fun getFormattedDate(date: Date): String {
        val cal = Calendar.getInstance().apply { time = date }
        val dayOfWeek = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "월요일"
            Calendar.TUESDAY -> "화요일"
            Calendar.WEDNESDAY -> "수요일"
            Calendar.THURSDAY -> "목요일"
            Calendar.FRIDAY -> "금요일"
            Calendar.SATURDAY -> "토요일"
            else -> "일요일"
        }

        val sdf = SimpleDateFormat("MM월 dd일", Locale.KOREA)
        val dayStr = sdf.format(date)

        return "$dayStr ($dayOfWeek)"
    }


    private fun updateSelectionInfo() {
        val range = selectedRange
        if (range == null) {
            binding.tvSelectedInfo.text = "선택된 시간이 없습니다."
            binding.tvSelectedDuration.text = ""
            binding.btnReserve.isEnabled = false
            return
        }

        val date = weekDates.getOrNull(range.day)
        if (date == null || !isDateReservable(range.day)) {
            binding.tvSelectedInfo.text = "선택된 시간이 없습니다."
            binding.tvSelectedDuration.text = ""
            binding.btnReserve.isEnabled = false
            return
        }

        val dateText = SimpleDateFormat("MM월 dd일", Locale.KOREA).format(date)
        val dayKor = "${dayLabels[range.day]}요일"
        val startT = periodLabels[range.start]
        val endT = periodLabels[min(periodLabels.size - 1, range.end + 1)]

        val hours = range.end - range.start + 1

        binding.tvSelectedInfo.text = "$dateText ($dayKor)\n$startT ~ $endT"
        binding.tvSelectedDuration.text = "${hours}시간 선택됨"
        binding.btnReserve.isEnabled = true
    }

    // ============================================================
    // 6) 예약 다이얼로그
    // ============================================================

    private fun openReservationDialog() {
        val range = selectedRange
        if (range == null || !isDateReservable(range.day)) {
            Toast.makeText(requireContext(), "예약할 시간을 먼저 선택해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

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
        val btnNext = dialogView.findViewById<Button>(R.id.btnSubmit)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)

        // 로그인된 사용자 정보 자동 채우기
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            db.collection("users").document(uid).get()
                .addOnSuccessListener { doc ->
                    etUserName.setText(doc.getString("name") ?: "")
                    etMajor.setText(doc.getString("department") ?: "")
                }
        }

        // 목적 드롭다운
        val items = listOf("스터디", "발표 준비", "미팅", "기타 (직접 입력)")
        spPurpose.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, items)

        spPurpose.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                etPurposeCustom.visibility =
                    if (items[pos].contains("기타")) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnNext.setOnClickListener {
            val name = etUserName.text.toString().trim()
            val major = etMajor.text.toString().trim()
            val peopleStr = etPeople.text.toString().trim()
            val purpose =
                if (etPurposeCustom.visibility == View.VISIBLE)
                    etPurposeCustom.text.toString().trim()
                else spPurpose.selectedItem.toString()

            // 입력 검증
            when {
                name.isBlank() -> {
                    Toast.makeText(requireContext(), "이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                major.isBlank() -> {
                    Toast.makeText(requireContext(), "학과를 입력해주세요.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                peopleStr.isBlank() -> {
                    Toast.makeText(requireContext(), "인원을 입력해주세요.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                peopleStr.toIntOrNull() == null || peopleStr.toInt() <= 0 -> {
                    Toast.makeText(requireContext(), "인원 수를 올바르게 입력해주세요.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                purpose.isBlank() -> {
                    Toast.makeText(requireContext(), "목적을 입력해주세요.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            // 예약 내용 확인 모달로 이동
            val people = peopleStr.toInt()
            dialog.dismiss()

            openReservationConfirmDialog(name, major, people, purpose)
        }

        dialog.show()
    }


    private fun openReservationConfirmDialog(
        userName: String,
        major: String,
        people: Int,
        purpose: String
    ) {
        val range = selectedRange ?: return
        val dialogView = layoutInflater.inflate(R.layout.reservation_confirm_dialog, null)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvRoom = dialogView.findViewById<TextView>(R.id.tvConfirmRoom)
        val tvTime = dialogView.findViewById<TextView>(R.id.tvConfirmTime)
        val tvUser = dialogView.findViewById<TextView>(R.id.tvConfirmUser)
        val tvPurpose = dialogView.findViewById<TextView>(R.id.tvConfirmPurpose)
        val tvPeople = dialogView.findViewById<TextView>(R.id.tvConfirmPeople)

        val date = weekDates[range.day]
        val dateStr = getFormattedDate(date)     // "수요일 10:00 - 12:00" 형태 포맷 함수

        tvRoom.text = buildingName + " " + roomId
        tvTime.text = dateStr
        tvUser.text = "$userName ($major)"
        tvPurpose.text = purpose
        tvPeople.text = "${people}명"

        val btnConfirm = dialogView.findViewById<Button>(R.id.btnConfirmSubmit)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnConfirmCancel)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnConfirm.setOnClickListener {
            dialog.dismiss()
            saveReservation(userName, major, people, purpose)   // 최종 저장
        }

        dialog.show()
    }


    private fun showReservationSuccessDialog() {
        val dialogView = layoutInflater.inflate(R.layout.reservation_success_dialog, null)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnOk = dialogView.findViewById<Button>(R.id.btnSuccessOk)
        btnOk.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }




    // ============================================================
    // 7) Firestore 저장 (날짜 포함)
    // ============================================================


    private fun getWeekDateStrings(): List<String> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
        return weekDates.map { sdf.format(it) }
    }


    private fun saveReservation(
        userName: String,
        major: String,
        people: Int,
        purpose: String
    ) {
        val range = selectedRange ?: return
        if (!isDateReservable(range.day)) {
            Toast.makeText(requireContext(), "해당 날짜는 예약이 불가능합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val date = weekDates[range.day]
        val dateString = getDateString(date)
        val dayKey = dayKeys[range.day]

        // 1) Firestore에서 시간 겹치는 예약 있는지 1차 검사
        db.collection("reservations")
            .whereEqualTo("buildingId", buildingId)
            .whereEqualTo("roomId", roomId)
            .whereEqualTo("date", dateString)
            .whereEqualTo("status", "approved")
            .get()
            .addOnSuccessListener { result ->
                // 기존 예약들과 충돌 여부 검사
                for (doc in result) {
                    val existingStart = doc.getLong("periodStart")?.toInt() ?: continue
                    val existingEnd = doc.getLong("periodEnd")?.toInt() ?: continue

                    // 겹치는지 체크: (start ≤ end2 && start2 ≤ end)
                    val isOverlap =
                        existingStart <= range.end && range.start <= existingEnd

                    if (isOverlap) {
                        Toast.makeText(
                            requireContext(),
                            "⚠ 해당 시간대는 이미 예약되었습니다.",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@addOnSuccessListener
                    }
                }

                // 2) 충돌 없음 → 저장 가능
                val reservation = hashMapOf(
                    "userId" to uid,
                    "userName" to userName,
                    "major" to major,
                    "people" to people,
                    "purpose" to purpose,
                    "roomId" to roomId,
                    "buildingId" to buildingId,
                    "day" to dayKey,
                    "date" to dateString,
                    "periodStart" to range.start,
                    "periodEnd" to range.end,
                    "timestamp" to System.currentTimeMillis(),
                    "status" to "approved"
                )

                db.collection("reservations")
                    .add(reservation)
                    .addOnSuccessListener {
                        applyReservationToTable(range.day, range.start, range.end)
                        showReservationSuccessDialog()
                    }
                    .addOnFailureListener {
                        Toast.makeText(requireContext(), "예약 저장 실패", Toast.LENGTH_SHORT).show()
                    }
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
    // 8) Firestore에서 기존 예약 불러오기 (현재 주만)
    // ============================================================

    private fun loadExistingReservations() {

        val weekDateStrings = getWeekDateStrings()

        db.collection("reservations")
            .whereEqualTo("buildingId", buildingId)
            .whereEqualTo("roomId", roomId)
            .whereIn("date", weekDateStrings)
            .whereEqualTo("status", "approved")
            .get()
            .addOnSuccessListener { result ->
                for (doc in result) {
                    val dayKey = doc.getString("day") ?: continue
                    val dayIndex = dayKeys.indexOf(dayKey)
                    if (dayIndex == -1) continue

                    val start = doc.getLong("periodStart")?.toInt() ?: continue
                    val end = doc.getLong("periodEnd")?.toInt() ?: continue

                    markReservationOnTable(dayIndex, start, end)
                }
            }
    }


    private fun markReservationOnTable(day: Int, start: Int, end: Int) {
        for (p in start..end) {
            val key = day to p
            val tv = cellViews[key] ?: continue

            tv.background = ContextCompat.getDrawable(
                requireContext(),
                R.drawable.bg_cell_reserved
            )
            tv.text = "예약됨"
            tv.setTextColor(Color.BLACK)

            tv.setOnClickListener(null)
            cellState[key] = SlotState.RESERVED
        }
    }
    private fun saveRecentlyViewedRoom() {
        if (buildingId.isBlank() || roomId.isBlank()) return

        val roomName = "$buildingName $roomId"

        Log.d("RECENT", "Saving recent room: $roomName")

        homeViewModel.addRecentViewedRoom(
            roomId = roomId,
            buildingId = buildingId,
            roomName = roomName
        )
    }
}
