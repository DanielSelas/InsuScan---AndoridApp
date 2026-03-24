package com.example.insuscan.chat.helpers

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import com.example.insuscan.chat.ActionButton
import com.example.insuscan.chat.ChatChipFactory
import com.example.insuscan.chat.ChatViewModel
import com.google.android.material.chip.ChipGroup

class StickyActionsHelper(
    private val context: Context,
    private val stickyContainer: LinearLayout,
    private val stickyDivider: View,
    private val viewModel: ChatViewModel
) {
    fun render(actions: List<ActionButton>?) {
        stickyContainer.removeAllViews()

        if (actions.isNullOrEmpty()) {
            stickyContainer.visibility = View.GONE
            stickyDivider.visibility = View.GONE
            return
        }

        stickyContainer.visibility = View.VISIBLE
        stickyDivider.visibility = View.VISIBLE

        val rows = actions.groupBy { it.row }.toSortedMap()

        rows.forEach { (_, rowButtons) ->
            val chipGroup = ChipGroup(context).apply {
                isSingleLine = false
                chipSpacingHorizontal = 8.dpToPx()
                chipSpacingVertical = 4.dpToPx()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.CENTER_HORIZONTAL
                }
            }

            rowButtons.forEach { button ->
                val chip = ChatChipFactory.create(context, button) { actionId ->
                    viewModel.onActionButton(actionId)
                }
                chipGroup.addView(chip)
            }

            stickyContainer.addView(chipGroup)
        }
    }

    private fun Int.dpToPx(): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }
}
