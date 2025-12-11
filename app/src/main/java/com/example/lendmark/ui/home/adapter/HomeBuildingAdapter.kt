package com.example.lendmark.ui.home.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.lendmark.data.model.Building
import com.example.lendmark.databinding.ItemHomeBuildingBinding

class HomeBuildingAdapter(
    private val buildings: List<Building>,
    private val onClick: (Building) -> Unit
) : RecyclerView.Adapter<HomeBuildingAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemHomeBuildingBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHomeBuildingBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val building = buildings[position]

        holder.binding.apply {

            tvBuildingName.text = building.name

            // ⭐ Glide 로 Firestore imageUrl 로드
            Glide.with(imgBuilding.context)
                .load(building.imageUrl)
                .placeholder(android.R.color.darker_gray)
                .centerCrop()
                .into(imgBuilding)

            root.setOnClickListener { onClick(building) }
        }
    }

    override fun getItemCount(): Int = buildings.size
}
