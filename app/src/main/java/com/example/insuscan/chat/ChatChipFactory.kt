package com.example.insuscan.chat

import android.content.Context
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import com.example.insuscan.R
import com.google.android.material.chip.Chip

object ChatChipFactory {

    private const val MIN_HEIGHT_PRIMARY = 56f
    private const val MIN_HEIGHT_DEFAULT = 50f
    private const val TEXT_SIZE_PRIMARY = 15f
    private const val TEXT_SIZE_DEFAULT = 14f
    private const val TEXT_SIZE_TERTIARY = 13f
    private const val PADDING_PRIMARY = 24f
    private const val PADDING_DEFAULT = 20f
    private const val PADDING_TERTIARY = 14f
    private const val CORNER_RADIUS = 20f
    private const val STROKE_WIDTH = 1.5f

    fun create(
        context: Context,
        button: ActionButton,
        onClick: (String) -> Unit
    ): Chip {
        return Chip(context).apply {
            text = button.label
            isClickable = true
            isCheckable = false
            chipCornerRadius = CORNER_RADIUS

            applyStyle(this, context, button.style)
            setOnClickListener { onClick(button.actionId) }
        }
    }

    private fun applyStyle(chip: Chip, ctx: Context, style: ChipStyle) {
        when (style) {
            ChipStyle.PRIMARY -> {
                chip.chipMinHeight = MIN_HEIGHT_PRIMARY
                chip.textSize = TEXT_SIZE_PRIMARY
                chip.chipStartPadding = PADDING_PRIMARY
                chip.chipEndPadding = PADDING_PRIMARY
                chip.chipBackgroundColor = ColorStateList.valueOf(
                    ContextCompat.getColor(ctx, R.color.primary)
                )
                chip.setTextColor(ContextCompat.getColor(ctx, R.color.text_on_primary))
                chip.chipStrokeWidth = 0f
            }

            ChipStyle.SECONDARY -> {
                chip.chipMinHeight = MIN_HEIGHT_DEFAULT
                chip.textSize = TEXT_SIZE_DEFAULT
                chip.chipStartPadding = PADDING_DEFAULT
                chip.chipEndPadding = PADDING_DEFAULT
                chip.chipBackgroundColor = ColorStateList.valueOf(
                    ContextCompat.getColor(ctx, R.color.surface)
                )
                chip.setTextColor(ContextCompat.getColor(ctx, R.color.primary))
                chip.chipStrokeWidth = STROKE_WIDTH
                chip.chipStrokeColor = ColorStateList.valueOf(
                    ContextCompat.getColor(ctx, R.color.primary)
                )
            }

            ChipStyle.TERTIARY -> {
                chip.chipMinHeight = MIN_HEIGHT_DEFAULT
                chip.textSize = TEXT_SIZE_TERTIARY
                chip.chipStartPadding = PADDING_TERTIARY
                chip.chipEndPadding = PADDING_TERTIARY
                chip.chipBackgroundColor = ColorStateList.valueOf(
                    ContextCompat.getColor(ctx, R.color.transparent)
                )
                chip.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                chip.chipStrokeWidth = 0f
            }
        }
    }
}