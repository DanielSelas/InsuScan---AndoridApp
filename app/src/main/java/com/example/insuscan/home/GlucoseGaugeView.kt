package com.example.insuscan.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.insuscan.R
import android.graphics.Path

/**
 * A circular gauge that displays a glucose value as an arc, colored by range
 * (critical / warning / in-range), with a small drop icon in the center.
 */
class GlucoseGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ARC_STROKE_WIDTH
        strokeCap = Paint.Cap.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ARC_STROKE_WIDTH
        strokeCap = Paint.Cap.ROUND
    }

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        strokeWidth = ICON_STROKE_WIDTH
        strokeCap = Paint.Cap.ROUND
    }

    private val oval = RectF()

    var glucoseValue: Int = DEFAULT_GLUCOSE
        set(value) { field = value; invalidate() }

    var minGlucose: Int = DEFAULT_MIN_GLUCOSE
        set(value) { field = value; invalidate() }

    var maxGlucose: Int = DEFAULT_MAX_GLUCOSE
        set(value) { field = value; invalidate() }

    var rangeMin: Int = DEFAULT_RANGE_MIN
        set(value) { field = value; invalidate() }

    var rangeMax: Int = DEFAULT_RANGE_MAX
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
        val radius = (minOf(width, height) / 2f) - RADIUS_PADDING

        oval.set(cx - radius, cy - radius, cx + radius, cy + radius)

        val startAngle = START_ANGLE
        val sweepMax = SWEEP_MAX_DEGREES

        trackPaint.color = ContextCompat.getColor(context, R.color.divider)
        canvas.drawArc(oval, startAngle, sweepMax, false, trackPaint)

        val progress = ((glucoseValue - minGlucose).toFloat() / (maxGlucose - minGlucose)).coerceIn(0f, 1f)
        val sweepAngle = sweepMax * progress

        fillPaint.color = resolveColor(glucoseValue)
        canvas.drawArc(oval, startAngle, sweepAngle, false, fillPaint)

        iconPaint.color = resolveColor(glucoseValue)
        val dropRadius = DROP_RADIUS
        canvas.drawCircle(cx, cy + 6f, dropRadius, iconPaint)
        val path = Path().apply {
            moveTo(cx, cy - 16f)
            lineTo(cx - dropRadius, cy + 6f)
            lineTo(cx + dropRadius, cy + 6f)
            close()
        }
        canvas.drawPath(path, iconPaint)
    }

    companion object {
        private const val ARC_STROKE_WIDTH = 10f
        private const val ICON_STROKE_WIDTH = 2f
        private const val START_ANGLE = 135f
        private const val SWEEP_MAX_DEGREES = 270f
        private const val RADIUS_PADDING = 16f
        private const val DROP_RADIUS = 10f
        private const val DEFAULT_GLUCOSE = 108
        private const val DEFAULT_MIN_GLUCOSE = 40
        private const val DEFAULT_MAX_GLUCOSE = 300
        private const val DEFAULT_RANGE_MIN = 70
        private const val DEFAULT_RANGE_MAX = 180
    }
}