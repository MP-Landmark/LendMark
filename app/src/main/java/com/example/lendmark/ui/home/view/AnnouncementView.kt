package com.example.lendmark.ui.home.view

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.viewpager2.widget.ViewPager2
import com.example.lendmark.ui.home.adapter.Announcement
import com.example.lendmark.ui.home.adapter.AnnouncementAdapter

class AnnouncementView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val viewPager: ViewPager2

    init {
        // Create a ViewPager2 instance programmatically
        viewPager = ViewPager2(context)
        viewPager.layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )
        
        // Add the ViewPager to this FrameLayout
        addView(viewPager)
    }

    fun setAnnouncements(announcements: List<Announcement>) {
        viewPager.adapter = AnnouncementAdapter(announcements)
    }
}
