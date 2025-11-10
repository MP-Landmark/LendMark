package com.example.lendmark.ui.reservation.building

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.lendmark.databinding.ActivityBuildingListBinding
import com.example.lendmark.ui.reservation.building.BuildingViewModel
import com.example.lendmark.ui.reservation.RoomListFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BuildingListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBuildingListBinding
    private val viewModel: BuildingViewModel by viewModels()
    private lateinit var adapter: BuildingAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBuildingListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // adapter 초기화
        adapter = BuildingAdapter { building ->

            val intent = Intent(this, RoomListFragment::class.java)
            intent.putExtra("buildingId", building.code)
            intent.putExtra("buildingName", building.name)
            startActivity(intent)
        }

        // RecyclerView 설정
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        // lifecycleScope에서 collect는 launchWhenStarted 대신 launch로 감싸기
        lifecycleScope.launch {
            viewModel.buildings.collectLatest { list ->
                adapter.submitList(list)
            }
        }

        viewModel.loadBuildings()
    }
}