package com.example.lendmark.ui.chatbot

import android.graphics.Color
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.lendmark.R
import com.example.lendmark.data.model.ChatMessage

class ChatBotAdapter(
    private val messages: MutableList<ChatMessage>,
    private val onRoomClick: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val TYPE_USER = 1
    private val TYPE_AI = 2

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) TYPE_USER else TYPE_AI
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_USER) {
            UserViewHolder(inflater.inflate(R.layout.item_chat_user, parent, false))
        } else {
            AiViewHolder(inflater.inflate(R.layout.item_chat_ai, parent, false))
        }
    }

    override fun getItemCount(): Int = messages.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position]

        if (holder is UserViewHolder) {
            holder.userMsg.text = msg.message
        } else if (holder is AiViewHolder) {
            bindAiText(holder, msg)
        }
    }

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    /** 🔥 bullet (- 105) 라인 제거 */
    private fun removeAiBulletItems(answer: String): String {
        return answer
            .lines()
            .filterNot { it.trim().matches(Regex("^[-•]\\s*\\d+.*$")) }
            .joinToString("\n")
    }

    /** 🔥 숫자만 남기기 — Firestore key와 동일하게 맞추기 위해 */
    private fun normalizeRoomId(raw: String): String {
        return raw.trim().replace(Regex("[^0-9]"), "")
    }

    /** 🔥 예약하기 클릭 가능하게 구성 */
    private fun bindAiText(holder: AiViewHolder, msg: ChatMessage) {

        val rooms = msg.roomList?.map { normalizeRoomId(it) } ?: emptyList()
        val cleanText = removeAiBulletItems(msg.message)

        val builder = SpannableStringBuilder()
        builder.append(cleanText.trim())
        builder.append("\n\n")

        rooms.forEach { roomId ->
            if (roomId.isBlank()) return@forEach

            builder.append("${roomId}호 ")

            val start = builder.length
            builder.append("예약하기")
            val end = builder.length

            val span = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    onRoomClick(roomId)  // 🔥 이미 sanitize 완료된 값 전달
                }

                override fun updateDrawState(ds: android.text.TextPaint) {
                    ds.color = Color.parseColor("#1E88E5")
                    ds.isUnderlineText = false
                }
            }

            builder.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            builder.append("\n")
        }

        holder.aiMsg.text = builder
        holder.aiMsg.movementMethod = LinkMovementMethod.getInstance()
        holder.aiMsg.highlightColor = Color.TRANSPARENT
    }

    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userMsg: TextView = itemView.findViewById(R.id.tvUserMessage)
    }

    class AiViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val aiMsg: TextView = itemView.findViewById(R.id.tvAiMessage)
    }
}
