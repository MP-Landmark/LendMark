package com.example.lendmark.ui.room

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.lendmark.databinding.ItemRoomBinding

class RoomListAdapter(
    private val rooms: List<String>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<RoomListAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemRoomBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRoomBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val roomName = rooms[position]
        with(holder.binding) {
            tvRoomName.text = roomName
            tvRoomStatus.text = "예약 가능" // 나중에 예약 여부 체크 시 갱신 가능
            root.setOnClickListener { onClick(roomName) }
        }
    }

    override fun getItemCount() = rooms.size
}
