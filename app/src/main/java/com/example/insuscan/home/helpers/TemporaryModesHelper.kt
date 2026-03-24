package com.example.insuscan.home.helpers

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.cardview.widget.CardView
import com.example.insuscan.profile.UserProfileManager

class TemporaryModesHelper(
    private val context: Context,
    private val sickModeSwitch: SwitchCompat,
    private val stressModeSwitch: SwitchCompat,
    private val exerciseModeSwitch: SwitchCompat,
    private val sickWarningCard: CardView,
    private val sickWarningText: TextView,
    private val activeModesText: TextView
) {

    private val pm = UserProfileManager

    fun loadModes() {
        sickModeSwitch.isChecked = pm.isSickModeEnabled(context)
        stressModeSwitch.isChecked = pm.isStressModeEnabled(context)
        exerciseModeSwitch.isChecked = pm.isExerciseModeEnabled(context)

        updateSickWarning()
        updateActiveModesText()
    }

    fun setupListeners() {
        sickModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            pm.setSickModeEnabled(context, isChecked)
            updateSickWarning()
            updateActiveModesText()
        }

        stressModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            pm.setStressModeEnabled(context, isChecked)
            updateActiveModesText()
        }

        exerciseModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            pm.setExerciseModeEnabled(context, isChecked)
            updateActiveModesText()
        }
    }

    private fun updateSickWarning() {
        val isSick = pm.isSickModeEnabled(context)
        val days = pm.getSickModeDays(context)

        if (isSick && days >= 3) {
            sickWarningCard.visibility = View.VISIBLE
            sickWarningText.text =
                "Sick mode has been ON for $days days. Consider consulting your doctor."
        } else {
            sickWarningCard.visibility = View.GONE
        }
    }

    private fun updateActiveModesText() {
        val activeModes = mutableListOf<String>()

        if (pm.isSickModeEnabled(context)) {
            activeModes.add("Sick +${pm.getSickDayAdjustment(context)}%")
        }
        if (pm.isStressModeEnabled(context)) {
            activeModes.add("Stress +${pm.getStressAdjustment(context)}%")
        }
        if (pm.isExerciseModeEnabled(context)) {
            activeModes.add("Exercise -${pm.getLightExerciseAdjustment(context)}%")
        }

        if (activeModes.isNotEmpty()) {
            activeModesText.visibility = View.VISIBLE
            activeModesText.text = "Active: ${activeModes.joinToString(" • ")}"
        } else {
            activeModesText.visibility = View.GONE
        }
    }
}
