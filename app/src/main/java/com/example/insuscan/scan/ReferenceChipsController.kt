package com.example.insuscan.scan

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.insuscan.R
import com.example.insuscan.profile.UserProfileManager
import com.example.insuscan.utils.ReferenceObjectHelper
import com.example.insuscan.utils.ReferenceObjectHelper.ReferenceObjectType

class ReferenceChipsController(
    private val context: Context,
    private val chipGroup: LinearLayout,
    private val toggleButton: TextView? = null,
    private val targetZone: View? = null,
    private val dismissAnchor: View? = null
) {

    var selectedServerValue: String? = null
        private set

    var onSelectionChanged: ((ReferenceObjectType) -> Unit)? = null

    private val chipSyringe: LinearLayout = chipGroup.findViewById(R.id.chip_ref_syringe)
    private val chipFork: LinearLayout = chipGroup.findViewById(R.id.chip_ref_fork)
    private val chipCard: LinearLayout = chipGroup.findViewById(R.id.chip_ref_card)
    private val chipNone: LinearLayout = chipGroup.findViewById(R.id.chip_ref_none)

    private val allChips: List<LinearLayout>
        get() = listOf(chipSyringe, chipFork, chipCard, chipNone)

    fun setup() {
        val defaultType = resolveDefaultType()
        applySelection(defaultType)
        setSelectedChip(chipForType(defaultType))

        chipSyringe.setOnClickListener { onChipSelected(ReferenceObjectType.INSULIN_SYRINGE) }
        chipFork.setOnClickListener { onChipSelected(ReferenceObjectType.SYRINGE_KNIFE) }
        chipCard.setOnClickListener { onChipSelected(ReferenceObjectType.CARD) }
        chipNone.setOnClickListener { onChipSelected(ReferenceObjectType.NONE) }

        toggleButton?.setOnClickListener {
            chipGroup.visibility =
                if (chipGroup.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        dismissAnchor?.setOnClickListener {
            if (chipGroup.visibility == View.VISIBLE) {
                chipGroup.visibility = View.GONE
            }
        }
    }

    fun setType(type: ReferenceObjectType) {
        setSelectedChip(chipForType(type))
        applySelection(type)
    }

    private fun resolveDefaultType(): ReferenceObjectType {
        val profileType = UserProfileManager.getReferenceObjectType(context)
        return when {
            profileType.contains("Card", ignoreCase = true) -> ReferenceObjectType.CARD
            profileType.contains("Pen", ignoreCase = true) -> ReferenceObjectType.INSULIN_SYRINGE
            else -> ReferenceObjectType.INSULIN_SYRINGE
        }
    }

    private fun chipForType(type: ReferenceObjectType): LinearLayout = when (type) {
        ReferenceObjectType.INSULIN_SYRINGE -> chipSyringe
        ReferenceObjectType.SYRINGE_KNIFE -> chipFork
        ReferenceObjectType.CARD -> chipCard
        ReferenceObjectType.NONE -> chipNone
    }

    private fun onChipSelected(type: ReferenceObjectType) {
        setSelectedChip(chipForType(type))
        applySelection(type)
        if (toggleButton != null) {
            chipGroup.postDelayed({ chipGroup.visibility = View.GONE }, 300)
        }
    }

    private fun applySelection(type: ReferenceObjectType) {
        selectedServerValue = type.serverValue
        updateTargetZone(type)
        updateToggleIcon(type)
        onSelectionChanged?.invoke(type)
    }

    private fun updateToggleIcon(type: ReferenceObjectType) {
        toggleButton?.text = when (type) {
            ReferenceObjectType.INSULIN_SYRINGE -> "💉"
            ReferenceObjectType.SYRINGE_KNIFE -> "🍴"
            ReferenceObjectType.CARD -> "💳"
            ReferenceObjectType.NONE -> "❌"
        }
    }

    private fun updateTargetZone(type: ReferenceObjectType) {
        val zone = targetZone ?: return
        val density = context.resources.displayMetrics.density

        when (type) {
            ReferenceObjectType.INSULIN_SYRINGE -> {
                zone.visibility = View.VISIBLE
                zone.layoutParams = zone.layoutParams.apply {
                    width = (30 * density).toInt()
                    height = (180 * density).toInt()
                }
            }
            ReferenceObjectType.SYRINGE_KNIFE -> {
                zone.visibility = View.VISIBLE
                zone.layoutParams = zone.layoutParams.apply {
                    width = (35 * density).toInt()
                    height = (180 * density).toInt()
                }
            }
            ReferenceObjectType.CARD -> {
                zone.visibility = View.VISIBLE
                zone.layoutParams = zone.layoutParams.apply {
                    width = (65 * density).toInt()
                    height = (100 * density).toInt()
                }
            }
            ReferenceObjectType.NONE -> {
                zone.visibility = View.GONE
            }
        }
        zone.requestLayout()
    }

    private fun setSelectedChip(selected: LinearLayout) {
        allChips.forEach { chip ->
            val isSelected = chip == selected
            chip.setBackgroundResource(
                if (isSelected) R.drawable.ref_chip_selected_bg
                else R.drawable.ref_chip_unselected_bg
            )
            val label = chip.getChildAt(1) as? TextView
            label?.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (isSelected) R.color.text_on_primary else R.color.primary
                )
            )
        }
    }
}