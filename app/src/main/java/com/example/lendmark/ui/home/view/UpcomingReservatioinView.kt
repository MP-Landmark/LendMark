package com.example.lendmark.ui.home.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import com.example.lendmark.databinding.ViewUpcomingReservationBinding
import com.google.android.material.card.MaterialCardView

class UpcomingReservationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : MaterialCardView(context, attrs, defStyleAttr) {

    private val binding: ViewUpcomingReservationBinding

    init {
        // XML 붙이기
        val inflater = LayoutInflater.from(context)
        binding = ViewUpcomingReservationBinding.inflate(inflater, this, true)

        // 기본 카드 스타일
        radius = 16f
        cardElevation = 8f
        isClickable = true
        foreground = context.getDrawable(android.R.drawable.list_selector_background)
    }

    /** 예약 정보 설정 함수 */
    fun setReservationDetails(roomName: String, time: String) {
        binding.tvUpcomingReservationDetails.text = "$roomName • $time"
    }

    /** "See >" 클릭 리스너 */
    fun setSeeDetailsClickListener(listener: () -> Unit) {
        binding.tvSeeDetails.setOnClickListener { listener() }
    }
}
