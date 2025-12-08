package com.example.lendmark.ui.home.adapter

import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SearchResultsAdapter(
    private val buildings: List<String>,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<SearchResultsAdapter.SearchViewHolder>() {

    inner class SearchViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView) {
        fun bind(buildingName: String) {
            textView.text = buildingName
            textView.setOnClickListener { onItemClick(buildingName) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchViewHolder {
        val textView = TextView(parent.context).apply {
            // ⭐ [디자인 수정] 항목 간격(Margin) 추가
            val params = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, // 가로 꽉 차게
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(10, 10, 10, 20) // 상하좌우 여백 (아래쪽 20을 줘서 띄움)
            }
            layoutParams = params

            setPadding(40, 30, 40, 30) // 내부 여백을 늘려서 버튼처럼 통통하게

            // 배경색 (연한 회색) + 약간의 둥근 모서리 느낌(XML drawable이 좋지만 코드로 간단히 처리)
            setBackgroundColor(Color.parseColor("#F5F5F5"))

            textSize = 16f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER_VERTICAL // 텍스트 세로 중앙 정렬
        }
        return SearchViewHolder(textView)
    }

    override fun onBindViewHolder(holder: SearchViewHolder, position: Int) {
        holder.bind(buildings[position])
    }

    override fun getItemCount(): Int = buildings.size
}