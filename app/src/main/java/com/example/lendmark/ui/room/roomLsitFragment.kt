package com.example.lendmark.ui.room

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.lendmark.databinding.FragmentRoomListBinding
import com.google.firebase.firestore.FirebaseFirestore

class RoomListFragment : Fragment() {

    private var _binding: FragmentRoomListBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private val roomList = mutableListOf<String>()
    private lateinit var adapter: RoomListAdapter

    private var buildingCode: Int = 0
    private var buildingName: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRoomListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // BuildingListFragment에서 전달된 데이터 받기
        buildingCode = arguments?.getInt("buildingCode") ?: 0
        buildingName = arguments?.getString("buildingName") ?: "알 수 없음"

        binding.tvBuildingTitle.text = buildingName

        adapter = RoomListAdapter(roomList) { roomName ->
            Toast.makeText(requireContext(), "$roomName 선택됨", Toast.LENGTH_SHORT).show()
            // 나중에 예약 상세로 이동 가능
        }

        binding.rvRoomList.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRoomList.adapter = adapter

        loadRooms()
    }

    private fun loadRooms() {
        db.collection("timetable")
            .document("2025-fall")
            .collection("building_info")
            .document(buildingCode.toString())
            .get()
            .addOnSuccessListener { doc ->
                val rooms = doc.get("rooms") as? List<*>
                if (rooms != null) {
                    roomList.clear()
                    roomList.addAll(rooms.filterIsInstance<String>())
                    adapter.notifyDataSetChanged()
                } else {
                    Toast.makeText(requireContext(), "강의실 정보를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "데이터 불러오기 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
