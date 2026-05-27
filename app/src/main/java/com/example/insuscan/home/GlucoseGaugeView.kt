package com.example.insuscan.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.insuscan.R

class GlucoseGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
    }

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        strokeWidth = 2f
        strokeCap = Paint.Cap.ROUND
    }

    private val oval = RectF()

    var glucoseValue: Int = 108
        set(value) { field = value; invalidate() }

    var minGlucose: Int = 40
        set(value) { field = value; invalidate() }

    var maxGlucose: Int = 300
        set(value) { field = value; invalidate() }

    var rangeMin: Int = 70
        set(value) { field = value; invalidate() }

    var rangeMax: Int = 180
        set(value) { field = value; invalidate() }

    private fun resolveColor(glucoseVal: Int): Int {
        return when {
            glucoseVal < rangeMin -> ContextCompat.getColor(context, R.color.status_critical)
            glucoseVal > rangeMax -> ContextCompat.getColor(context, R.color.status_warning)
            else -> ContextCompat.getColor(context, R.color.secondary)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = (minOf(width, height) / 2f) - 16f

        oval.set(cx - radius, cy - radius, cx + radius, cy + radius)

        val startAngle = 135f
        val sweepMax = 270f

        trackPaint.color = ContextCompat.getColor(context, R.color.divider)
        canvas.drawArc(oval, startAngle, sweepMax, false, trackPaint)

        val progress = ((glucoseValue - minGlucose).toFloat() / (maxGlucose - minGlucose)).coerceIn(0f, 1f)
        val sweepAngle = sweepMax * progress

        fillPaint.color = resolveColor(glucoseValue)
        canvas.drawArc(oval, startAngle, sweepAngle, false, fillPaint)

        iconPaint.color = resolveColor(glucoseValue)
        val dropRadius = 10f
        canvas.drawCircle(cx, cy + 6f, dropRadius, iconPaint)
        val path = android.graphics.Path().apply {
            moveTo(cx, cy - 16f)
            lineTo(cx - dropRadius, cy + 6f)
            lineTo(cx + dropRadius, cy + 6f)
            close()
        }
        canvas.drawPath(path, iconPaint)
    }
}