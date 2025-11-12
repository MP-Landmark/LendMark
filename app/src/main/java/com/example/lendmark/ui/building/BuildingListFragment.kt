package com.example.lendmark.ui.building

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.lendmark.data.model.Building
import com.example.lendmark.databinding.FragmentBuildingListBinding
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

        adapter = BuildingListAdapter(buildingList) { building ->
            // 🔹 클릭 시 강의실 리스트 페이지로 이동 (다음 단계에서 구현)
            val bundle = Bundle().apply {
                putString("buildingName", building.name)
                putInt("buildingCode", building.code)
                putDouble("lat", building.naverMapLat)
                putDouble("lng", building.naverMapLng)
            }
            findNavController().navigate(
                com.example.lendmark.R.id.action_buildingList_to_roomList,
                bundle
            )
        }

        binding.rvBuildingList.layoutManager = LinearLayoutManager(requireContext())
        binding.rvBuildingList.adapter = adapter

        loadBuildings()
    }

    private fun loadBuildings() {
        db.collection("buildings").get()
            .addOnSuccessListener { result ->
                buildingList.clear()
                for (doc in result) {
                    val building = doc.toObject(Building::class.java)
                    buildingList.add(building)
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "건물 목록을 불러오지 못했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
