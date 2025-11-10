package com.example.lendmark.ui.reservation.building

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.lendmark.R
import com.example.lendmark.data.model.Building
import com.example.lendmark.databinding.ItemBuildingBinding

class BuildingAdapter(
    private val onClick: (Building) -> Unit
) : ListAdapter<Building, BuildingAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(private val binding: ItemBuildingBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(building: Building) {
            binding.tvName.text = building.name
            binding.tvRoomCount.text = "예약 가능한 강의실 ${building.roomCount}개"

            // Glide로 이미지 불러오기
            Glide.with(binding.root)
                .load(building.imageUrl)
                .placeholder(R.drawable.ic_building) // 기본 이미지
                .error(R.drawable.ic_building)
                .centerCrop()
                .into(binding.imgBuilding)

            binding.root.setOnClickListener { onClick(building) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBuildingBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private class DiffCallback : DiffUtil.ItemCallback<Building>() {
        override fun areItemsTheSame(oldItem: Building, newItem: Building) = oldItem.code == newItem.code
        override fun areContentsTheSame(oldItem: Building, newItem: Building) = oldItem == newItem
    }
}