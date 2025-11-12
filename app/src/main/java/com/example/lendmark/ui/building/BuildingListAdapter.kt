package com.example.lendmark.ui.building

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.lendmark.data.model.Building
import com.example.lendmark.databinding.ItemBuildingBinding

class BuildingListAdapter(
    private val buildings: List<Building>,
    private val onClick: (Building) -> Unit
) : RecyclerView.Adapter<BuildingListAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemBuildingBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding =
            ItemBuildingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val building = buildings[position]
        with(holder.binding) {
            tvBuildingName.text = building.name
            tvBuildingCode.text = "코드: ${building.code}"
            tvBuildingRooms.text = "예약 가능한 강의실 ${building.rooms}개"

            Glide.with(imgBuilding.context)
                .load(building.imageUrl)
                .centerCrop()
                .into(imgBuilding)

            root.setOnClickListener { onClick(building) }
        }
    }

    override fun getItemCount() = buildings.size
}
