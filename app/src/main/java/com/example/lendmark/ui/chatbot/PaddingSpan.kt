package com.example.lendmark.ui.chatbot

import android.graphics.Paint
import android.text.style.ReplacementSpan

class PaddingSpan(
    private val paddingX: Int,
    private val paddingY: Int
) : ReplacementSpan() {

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        return (paddingX * 2 + paint.measureText(text.subSequence(start, end).toString())).toInt()
    }

    override fun draw(
        canvas: android.graphics.Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val textStr = text.subSequence(start, end).toString()
        val rectPaint = Paint(paint)
        rectPaint.color = 0xFFE3F2FD.toInt() // 연한 파란색 배경

        // 배경 그리기
        canvas.drawRoundRect(
            x,
            (y + paint.ascent() - paddingY),
            x + paint.measureText(textStr) + paddingX * 2,
            (y + paint.descent() + paddingY),
            20f,
            20f,
            rectPaint
        )

        // 텍스트 그리기
        paint.color = 0xFF1E88E5.toInt() // 파란 글씨
        canvas.drawText(textStr, x + paddingX, y.toFloat(), paint)
    }
}
