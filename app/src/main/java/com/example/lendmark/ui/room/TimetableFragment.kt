package com.example.lendmark.ui.room

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.example.lendmark.databinding.FragmentTimetableBinding
import com.example.lendmark.ui.room.TimetableAdapter
import com.example.lendmark.utils.SlotState
import com.google.firebase.firestore.FirebaseFirestore

class TimetableFragment : Fragment() {

    private lateinit var binding: FragmentTimetableBinding
    private lateinit var adapter: TimetableAdapter
    private val db = FirebaseFirestore.getInstance()

    private val SLOT_MINUTES = 30
    private val START_MIN = 9 * 60
    private val END_MIN = 21 * 60
    private val TOTAL_SLOTS = (END_MIN - START_MIN) / SLOT_MINUTES

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTimetableBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = TimetableAdapter(List(TOTAL_SLOTS) { SlotState.EMPTY })

        binding.rvTimetable.layoutManager =
            GridLayoutManager(requireContext(), 1)
        binding.rvTimetable.adapter = adapter

        val buildingId = "1"   // TODO 전달받기
        val roomId = "101"     // TODO 전달받기

        loadTimetable(buildingId, roomId)
    }

    private fun loadTimetable(buildingId: String, roomId: String) {
        db.collection("buildings")
            .document(buildingId)
            .collection("rooms")
            .document(roomId)
            .get()
            .addOnSuccessListener { doc ->

                val rawList = doc.get("timetable") as? List<Map<String, Any>>
                val slots = MutableList(TOTAL_SLOTS) { SlotState.EMPTY }

                rawList?.forEach { item ->
                    val start = item["start"] as String
                    val end = item["end"] as String
                    val type = item["type"] as String

                    val sIdx = (toMinutes(start) - START_MIN) / SLOT_MINUTES
                    val eIdx = (toMinutes(end) - START_MIN) / SLOT_MINUTES

                    for (i in sIdx until eIdx) {
                        if (i in slots.indices) {
                            slots[i] = when (type) {
                                "class" -> SlotState.CLASS
                                "reservation" -> SlotState.RESERVED
                                else -> SlotState.EMPTY
                            }
                        }
                    }
                }

                adapter.submitList(slots)
            }
    }

    private fun toMinutes(time: String): Int {
        val (h, m) = time.split(":").map { it.toInt() }
        return h * 60 + m
    }
}
