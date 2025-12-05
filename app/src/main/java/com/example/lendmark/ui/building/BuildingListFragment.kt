package com.example.lendmark.ui.building

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.lendmark.data.model.Building
import com.example.lendmark.databinding.FragmentBuildingListBinding
import com.example.lendmark.ui.main.MainActivity
import com.example.lendmark.ui.room.RoomListFragment
import com.google.firebase.firestore.FirebaseFirestore

class BuildingListFragment : Fragment() {

    private var _binding: FragmentBuildingListBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val buildingList = mutableListOf<Building>()
    private lateinit var adapter: BuildingListAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBuildingListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. 어댑터 설정 (리스트 클릭 시 이동 로직 포함)
        adapter = BuildingListAdapter(requireActivity() as AppCompatActivity, buildingList) { building ->
            navigateToRoomList(building.code.toString(), building.name)
        }

        binding.rvBuildingList.layoutManager = LinearLayoutManager(requireContext())
        binding.rvBuildingList.adapter = adapter

        // 2. 건물 목록 불러오기 (기본 기능)
        loadBuildings()

        // ⭐ 3. [NEW] HomeFragment에서 보낸 검색 결과 받기
        parentFragmentManager.setFragmentResultListener("search_request", viewLifecycleOwner) { _, bundle ->
            val buildingName = bundle.getString("selectedBuilding")

            if (!buildingName.isNullOrEmpty()) {
                // 검색된 건물 이름으로 ID(code)를 찾아서 바로 이동시킵니다.
                findBuildingAndNavigate(buildingName)
            }
        }
    }

    // ⭐ 검색된 이름으로 건물 정보를 찾고 이동하는 함수
    private fun findBuildingAndNavigate(targetName: String) {
        db.collection("buildings")
            .whereEqualTo("name", targetName) // 이름이 일치하는 건물을 찾음
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val doc = documents.documents[0]
                    // Building 객체 구조에 맞춰서 code를 가져옴
                    val code = doc.getLong("code")?.toString() ?: doc.id
                    val name = doc.getString("name") ?: targetName

                    // 강의실 목록 화면으로 이동
                    navigateToRoomList(code, name)
                } else {
                    Toast.makeText(requireContext(), "건물 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Log.e("BuildingList", "Search failed", it)
            }
    }

    // 화면 이동 로직을 함수로 분리 (중복 제거)
    private fun navigateToRoomList(buildingId: String, buildingName: String) {
        val bundle = Bundle().apply {
            putString("buildingId", buildingId)
            putString("buildingName", buildingName)
        }

        (requireActivity() as MainActivity).replaceFragment(
            RoomListFragment().apply { arguments = bundle },
            buildingName
        )
    }

    private fun loadBuildings() {
        db.collection("buildings")
            .orderBy("code")
            .get()
            .addOnSuccessListener { result ->
                buildingList.clear()

                for (doc in result) {
                    val building = doc.toObject(Building::class.java)
                    building.id = doc.id

                    if (building.name.isNotEmpty()) {
                        buildingList.add(building)
                    }
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "건물 목록 불러오기 실패", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}