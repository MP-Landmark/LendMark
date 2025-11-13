package com.example.lendmark.ui.home.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.lendmark.databinding.ItemAnnouncementBinding

// Sample data class
data class Announcement(val title: String, val body: String)

class AnnouncementAdapter(
    private val announcements: List<Announcement>
) : RecyclerView.Adapter<AnnouncementAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemAnnouncementBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAnnouncementBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val announcement = announcements[position]
        holder.binding.tvAnnouncementTitle.text = announcement.title
        holder.binding.tvAnnouncementBody.text = announcement.body
    }

    override fun getItemCount() = announcements.size
}
