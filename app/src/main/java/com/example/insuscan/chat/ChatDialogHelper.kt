package com.example.insuscan.chat

import android.app.AlertDialog
import android.widget.EditText
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import com.example.insuscan.meal.MealSessionManager
import com.example.insuscan.profile.UserProfileManager

/**
 * Handles all dialogs and bottom sheets shown from ChatFragment.
 * Extracted to keep ChatFragment focused on setup and observation.
 */
class ChatDialogHelper(
    private val fragment: Fragment,
    private val viewModel: ChatViewModel
) {
    var openEditMealSheet: EditMealBottomSheet? = null

    private val ctx get() = fragment.requireContext()
    private val fm get() = fragment.parentFragmentManager

    fun showEditMedicalBottomSheet() {
        EditMedicalBottomSheet { icr, isf, target ->
            viewModel.updateMedicalSettings(icr, isf, target)
        }.show(fm, "EditMedicalBottomSheet")
    }

    fun showEditMealDialog() {
        val items = MealSessionManager.currentMeal?.foodItems ?: return
        val sheet = EditMealBottomSheet(items) { updatedItems ->
            viewModel.updateMealItems(updatedItems)
            openEditMealSheet = null
        }
        openEditMealSheet = sheet
        sheet.show(fm, "EditMealBottomSheet")
    }

    fun showEditActivityDialog() {
        val pm = UserProfileManager
        val layout = buildLayout()

        val lightInput = addLabeledInput(layout, "🏃 Light Exercise Reduction %", pm.getLightExerciseAdjustment(ctx).toString())
        val intenseInput = addLabeledInput(layout, "🏋️ Intense Exercise Reduction %", pm.getIntenseExerciseAdjustment(ctx).toString())

        AlertDialog.Builder(ctx)
            .setTitle("⚙️ Edit Activity Adjustments")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                viewModel.updateAdjustmentPercentages(
                    light = lightInput.text.toString().toIntOrNull(),
                    intense = intenseInput.text.toString().toIntOrNull()
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun showEditSickStressDialog() {
        val pm = UserProfileManager
        val layout = buildLayout()

        val sickInput = addLabeledInput(layout, "🤒 Sick Day Increase %", pm.getSickDayAdjustment(ctx).toString())
        val stressInput = addLabeledInput(layout, "😫 Stress Increase %", pm.getStressAdjustment(ctx).toString())

        AlertDialog.Builder(ctx)
            .setTitle("⚙️ Edit Health Adjustments")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                viewModel.updateAdjustmentPercentages(
                    sick = sickInput.text.toString().toIntOrNull(),
                    stress = stressInput.text.toString().toIntOrNull()
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun buildLayout() = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(48, 32, 48, 0)
    }

    private fun addLabeledInput(layout: LinearLayout, label: String, value: String): EditText {
        layout.addView(android.widget.TextView(ctx).apply {
            text = label
            textSize = 14f
            setPadding(0, 8, 0, 4)
        })
        val input = EditText(ctx).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(value)
        }
        layout.addView(input)
        return input
    }
}
