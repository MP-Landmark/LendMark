package com.example.lendmark.ui.home.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.lendmark.databinding.ItemFrequentlyUsedRoomBinding

data class Room(val name: String, val imageUrl: String)

class FrequentlyUsedRoomsAdapter(
    private val rooms: List<Room>
) : RecyclerView.Adapter<FrequentlyUsedRoomsAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemFrequentlyUsedRoomBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFrequentlyUsedRoomBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val room = rooms[position]

        holder.binding.tvRoomName.text = room.name

        Glide.with(holder.binding.ivRoomImage.context)
            .load(room.imageUrl)
            .centerCrop()
            .into(holder.binding.ivRoomImage)
    }

    override fun getItemCount() = rooms.size
}
