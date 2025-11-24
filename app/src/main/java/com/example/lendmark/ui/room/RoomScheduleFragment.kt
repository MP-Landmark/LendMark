package com.example.lendmark.ui.room

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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

    private val cellState = mutableMapOf<Pair<Int, Int>, SlotState>()
    private val cellViews = mutableMapOf<Pair<Int, Int>, TextView>()

    private data class SelectedRange(var day: Int, var start: Int, var end: Int)
    private var selectedRange: SelectedRange? = null


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
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
        updateSelectionInfo()
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
        val rowSpec = GridLayout.spec(row, rowSpan)
        val colSpec = GridLayout.spec(col, 1)
        val params = GridLayout.LayoutParams(rowSpec, colSpec)
        params.width = 0
        params.height = dp(60)
        params.columnSpec = colSpec
        params.rowSpec = rowSpec
        params.setGravity(Gravity.FILL)
        return params
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

        // 기존 칸 삭제
        for (p in start..end) {
            val v = cellViews[day to p] ?: continue
            v.visibility = View.GONE
            cellState[day to p] = SlotState.CLASS
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
        if (cellState[day to p] == SlotState.CLASS) return

        val range = selectedRange

        if (range == null || range.day != day) {
            clearSelection()
            selectedRange = SelectedRange(day, p, p)
        } else {
            if (p < range.start || p > range.end) {

                val newStart = min(range.start, p)
                val newEnd = max(range.end, p)

                for (i in newStart..newEnd) {
                    if (cellState[day to i] == SlotState.CLASS) return
                }

                range.start = newStart
                range.end = newEnd

            } else {
                clearSelection()
                selectedRange = SelectedRange(day, p, p)
            }
        }

        applySelection()
        updateSelectionInfo()
    }


    private fun clearSelection() {
        selectedRange = null
        cellViews.forEach { (_, v) ->
            v.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_cell_empty)
        }
    }

    private fun applySelection() {
        val range = selectedRange ?: return

        cellViews.forEach { (_, v) ->
            v.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_cell_empty)
        }

        for (p in range.start..range.end) {
            val v = cellViews[range.day to p] ?: continue
            v.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_cell_selected)
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

}
