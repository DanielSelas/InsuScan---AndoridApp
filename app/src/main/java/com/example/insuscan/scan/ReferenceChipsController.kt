package com.example.insuscan.scan

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.insuscan.R
import com.example.insuscan.utils.ReferenceObjectHelper.ReferenceObjectType

/**
 * Manages the reference-object chip bar on the scan screen.
 *
 * Renders three chips (syringe, card, none), tracks the current selection,
 * resizes the on-screen target zone to match the chosen object, and notifies
 * [onSelectionChanged] whenever the user taps a different chip.
 */
class ReferenceChipsController(
    private val context: Context,
    private val chipGroup: LinearLayout,
    private val targetZone: View? = null
) {

    /** The server-value string of the currently selected reference type. */
    var selectedServerValue: String? = null
        private set

    /** Invoked whenever the user selects a different chip. */
    var onSelectionChanged: ((ReferenceObjectType) -> Unit)? = null

    private val chipSyringe: LinearLayout = chipGroup.findViewById(R.id.chip_ref_syringe)
    private val chipCard: LinearLayout = chipGroup.findViewById(R.id.chip_ref_card)
    private val chipNone: LinearLayout = chipGroup.findViewById(R.id.chip_ref_none)

    private val allChips: List<LinearLayout>
        get() = listOf(chipSyringe, chipCard, chipNone)

    /** Initialises chip click listeners and applies the default selection. */
    fun setup() {
        val defaultType = resolveDefaultType()
        applySelection(defaultType)
        setSelectedChip(chipForType(defaultType))

        chipGroup.visibility = View.VISIBLE

        chipSyringe.setOnClickListener { onChipSelected(ReferenceObjectType.INSULIN_SYRINGE) }
        chipCard.setOnClickListener { onChipSelected(ReferenceObjectType.CARD) }
        chipNone.setOnClickListener { onChipSelected(ReferenceObjectType.NONE) }
    }

    /** Programmatically selects a chip without user interaction. */
    fun setType(type: ReferenceObjectType) {
        setSelectedChip(chipForType(type))
        applySelection(type)
    }

    private fun resolveDefaultType(): ReferenceObjectType = ReferenceObjectType.INSULIN_SYRINGE

    private fun chipForType(type: ReferenceObjectType): LinearLayout = when (type) {
        ReferenceObjectType.INSULIN_SYRINGE -> chipSyringe
        ReferenceObjectType.CARD -> chipCard
        ReferenceObjectType.NONE -> chipNone
    }

    private fun onChipSelected(type: ReferenceObjectType) {
        setSelectedChip(chipForType(type))
        applySelection(type)
    }

    private fun applySelection(type: ReferenceObjectType) {
        selectedServerValue = type.serverValue
        updateTargetZone(type)
        onSelectionChanged?.invoke(type)
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
                    if (isSelected) R.color.text_on_primary else R.color.text_primary
                )
            )
        }
    }
}