package com.example.lendmark.ui.notification

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.lendmark.R
import com.example.lendmark.databinding.ItemNotificationBinding

// 알림 데이터 모델
data class NotificationItem(
    val id: Int,
    val title: String,              // 예: "강의실 예약 시작 30분 전입니다"
    val location: String,           // 예: "프론티어관 107호"
    val date: String,               // 예: "2025-10-23"
    val startTime: String,          // 예: "18:54"
    val endTime: String,            // 예: "20:24"
    val remainingTime: String,      // 예: "30분 후"
    val type: String,               // "start" or "end" — 아이콘 구분용
    var isRead: Boolean = false
)

class NotificationAdapter(
    private var items: List<NotificationItem>,
    private val onClick: (NotificationItem) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

    // ViewHolder 정의
    inner class ViewHolder(private val binding: ItemNotificationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: NotificationItem) {
            binding.tvTitle.text = item.title
            binding.tvDetail.text = "예약 내역: ${item.location}"
            binding.tvDate.text = "${item.date} · ${item.startTime} - ${item.endTime}"
            binding.tvTimeLeft.text = "예정: ${item.remainingTime}"

            //  시작/종료에 따라 아이콘 다르게 표시
            val iconRes = if (item.type == "end") {
                R.drawable.ic_notification_calender
            } else {
                R.drawable.ic_notification_clock
            }
            binding.ivIcon.setImageResource(iconRes)

            // 클릭 이벤트 연결
            binding.root.setOnClickListener { onClick(item) }

            // 🔹 읽음 처리 색상 (회색으로 표시)
            if (item.isRead) {
                val gray = Color.parseColor("#D1D5DB")
                binding.tvTitle.setTextColor(gray)
                binding.tvDetail.setTextColor(gray)
                binding.tvDate.setTextColor(gray)
                binding.tvTimeLeft.setTextColor(gray)
                binding.ivIcon.imageAlpha = 128
            } else {
                binding.tvTitle.setTextColor(Color.parseColor("#1F1F1F"))
                binding.tvDetail.setTextColor(Color.parseColor("#4B5563"))
                binding.tvDate.setTextColor(Color.parseColor("#6B7280"))
                binding.tvTimeLeft.setTextColor(Color.parseColor("#6B7280"))
                binding.ivIcon.imageAlpha = 255
            }
        }
    }

    // 여기서 ViewHolder 생성 (밖으로 빼야 함)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNotificationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    // 데이터 바인딩
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    // 리스트 갱신 함수
    fun updateList(newItems: List<NotificationItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
