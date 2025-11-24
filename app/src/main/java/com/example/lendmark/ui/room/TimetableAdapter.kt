package com.example.lendmark.ui.room

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.lendmark.R
import com.example.lendmark.utils.SlotState

class TimetableAdapter(
    private var items: List<SlotState>
) : RecyclerView.Adapter<TimetableAdapter.TimetableViewHolder>() {

    inner class TimetableViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvSlot: TextView = itemView.findViewById(R.id.tvSlot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimetableViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_timetable, parent, false)
        return TimetableViewHolder(view)
    }

    override fun onBindViewHolder(holder: TimetableViewHolder, position: Int) {
        val state = items[position]

        when (state) {
            SlotState.EMPTY -> {
                holder.tvSlot.text = ""
                holder.tvSlot.setBackgroundColor(Color.parseColor("#FFFFFF"))
            }

            SlotState.CLASS -> {
                holder.tvSlot.text = "CLASS"
                holder.tvSlot.setBackgroundColor(Color.parseColor("#BDBDBD"))
            }

            SlotState.RESERVED -> {
                holder.tvSlot.text = "RES"
                holder.tvSlot.setBackgroundColor(Color.parseColor("#64B5F6"))
            }

            SlotState.SELECTED -> {
                holder.tvSlot.text = "SELECT"
                holder.tvSlot.setBackgroundColor(Color.parseColor("#D1C4E9"))
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<SlotState>) {
        items = newItems
        notifyDataSetChanged()
    }
}

