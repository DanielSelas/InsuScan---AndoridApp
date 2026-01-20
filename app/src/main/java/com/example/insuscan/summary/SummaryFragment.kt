package com.example.insuscan.summary

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.insuscan.R
import com.example.insuscan.meal.Meal
import com.example.insuscan.meal.MealSessionManager
import com.example.insuscan.profile.UserProfileManager
import com.example.insuscan.utils.ToastHelper
import com.example.insuscan.utils.TopBarHelper
import com.google.android.material.bottomnavigation.BottomNavigationView

class SummaryFragment : Fragment(R.layout.fragment_summary) {

    // Food detection views
    private lateinit var mealItemsText: TextView
    private lateinit var totalCarbsText: TextView
    private lateinit var editButton: Button

    // Glucose input
    private lateinit var glucoseEditText: EditText
    private lateinit var glucoseUnitText: TextView
    private lateinit var glucoseStatusText: TextView

    // Activity level
    private lateinit var activityRadioGroup: RadioGroup
    private lateinit var rbNormal: RadioButton
    private lateinit var rbLight: RadioButton
    private lateinit var rbIntense: RadioButton

    // Dose calculation views
    private lateinit var carbDoseText: TextView
    private lateinit var correctionLayout: LinearLayout
    private lateinit var correctionDoseText: TextView
    private lateinit var sickLayout: LinearLayout
    private lateinit var sickAdjustmentText: TextView
    private lateinit var stressLayout: LinearLayout
    private lateinit var stressAdjustmentText: TextView
    private lateinit var exerciseLayout: LinearLayout
    private lateinit var exerciseAdjustmentText: TextView
    private lateinit var finalDoseText: TextView
    private lateinit var calculateButton: Button

    // Analysis views
    private lateinit var analysisCard: CardView
    private lateinit var analysisLayout: LinearLayout
    private lateinit var portionWeightText: TextView
    private lateinit var plateDimensionsText: TextView
    private lateinit var confidenceText: TextView
    private lateinit var referenceStatusText: TextView

    // Bottom button
    private lateinit var logButton: Button

    private val ctx get() = requireContext()

    // Calculation results (stored for saving)
    private var lastCalculatedDose: Float = 0f

    companion object {
        private const val MSG_SET_RATIO = "Please set insulin to carb ratio in Profile first"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        findViews(view)
        initializeTopBar(view)
        setupGlucoseUnit()
        updateFoodDisplay()
        updateAnalysisResults()
        initializeListeners()
        calculateDose() // initial calculation
    }

    private fun initializeTopBar(rootView: View) {
        TopBarHelper.setupTopBarBackToScan(
            rootView = rootView,
            title = "Meal Summary"
        ) {
            val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav)
            bottomNav.selectedItemId = R.id.scanFragment
        }
    }

    private fun findViews(view: View) {
        // Food detection
        mealItemsText = view.findViewById(R.id.tv_meal_items)
        totalCarbsText = view.findViewById(R.id.tv_total_carbs)
        editButton = view.findViewById(R.id.btn_edit_meal)

        // Glucose input
        glucoseEditText = view.findViewById(R.id.et_current_glucose)
        glucoseUnitText = view.findViewById(R.id.tv_glucose_unit)
        glucoseStatusText = view.findViewById(R.id.tv_glucose_status)

        // Activity level
        activityRadioGroup = view.findViewById(R.id.rg_activity_level)
        rbNormal = view.findViewById(R.id.rb_activity_normal)
        rbLight = view.findViewById(R.id.rb_activity_light)
        rbIntense = view.findViewById(R.id.rb_activity_intense)

        // Dose calculation
        carbDoseText = view.findViewById(R.id.tv_carb_dose)
        correctionLayout = view.findViewById(R.id.layout_correction_dose)
        correctionDoseText = view.findViewById(R.id.tv_correction_dose)
        sickLayout = view.findViewById(R.id.layout_sick_adjustment)
        sickAdjustmentText = view.findViewById(R.id.tv_sick_adjustment)
        stressLayout = view.findViewById(R.id.layout_stress_adjustment)
        stressAdjustmentText = view.findViewById(R.id.tv_stress_adjustment)
        exerciseLayout = view.findViewById(R.id.layout_exercise_adjustment)
        exerciseAdjustmentText = view.findViewById(R.id.tv_exercise_adjustment)
        finalDoseText = view.findViewById(R.id.tv_final_dose)
        calculateButton = view.findViewById(R.id.btn_calculate_insulin)

        // Analysis
        analysisCard = view.findViewById(R.id.card_analysis_results)
        analysisLayout = view.findViewById(R.id.layout_analysis_results)
        portionWeightText = view.findViewById(R.id.tv_portion_weight)
        plateDimensionsText = view.findViewById(R.id.tv_plate_dimensions)
        confidenceText = view.findViewById(R.id.tv_analysis_confidence)
        referenceStatusText = view.findViewById(R.id.tv_reference_status)

        // Bottom
        logButton = view.findViewById(R.id.btn_log_meal)
    }

    private fun setupGlucoseUnit() {
        val unit = UserProfileManager.getGlucoseUnits(ctx)
        glucoseUnitText.text = unit
    }

    private fun initializeListeners() {
        editButton.setOnClickListener {
            findNavController().navigate(R.id.action_summaryFragment_to_manualEntryFragment)
        }

        calculateButton.setOnClickListener {
            calculateDose()
        }

        logButton.setOnClickListener {
            saveMeal()
        }

        // Glucose input listener - update status and recalculate
        glucoseEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateGlucoseStatus()
                calculateDose()
            }
        })

        // Activity level listener
        activityRadioGroup.setOnCheckedChangeListener { _, _ ->
            calculateDose()
        }

        // Update activity labels with percentages
        updateActivityLabels()
    }

    private fun updateActivityLabels() {
        val pm = UserProfileManager
        val lightAdj = pm.getLightExerciseAdjustment(ctx)
        val intenseAdj = pm.getIntenseExerciseAdjustment(ctx)

        rbNormal.text = "Normal (no adjustment)"
        rbLight.text = "After light exercise (-$lightAdj%)"
        rbIntense.text = "After intense exercise (-$intenseAdj%)"
    }

    private fun updateGlucoseStatus() {
        val glucoseStr = glucoseEditText.text.toString()
        val glucose = glucoseStr.toIntOrNull()

        if (glucose == null) {
            glucoseStatusText.text = ""
            return
        }

        val target = UserProfileManager.getTargetGlucose(ctx) ?: 100
        val unit = UserProfileManager.getGlucoseUnits(ctx)

        // Convert if needed
        val glucoseInMgDl = if (unit == "mmol/L") (glucose * 18) else glucose
        val targetInMgDl = target

        when {
            glucoseInMgDl < 70 -> {
                glucoseStatusText.text = "⚠️ Low!"
                glucoseStatusText.setTextColor(0xFFD32F2F.toInt())
            }
            glucoseInMgDl < targetInMgDl - 20 -> {
                glucoseStatusText.text = "Below target"
                glucoseStatusText.setTextColor(0xFFF57C00.toInt())
            }
            glucoseInMgDl <= targetInMgDl + 30 -> {
                glucoseStatusText.text = "✓ In range"
                glucoseStatusText.setTextColor(0xFF388E3C.toInt())
            }
            glucoseInMgDl <= 180 -> {
                glucoseStatusText.text = "Above target"
                glucoseStatusText.setTextColor(0xFFF57C00.toInt())
            }
            else -> {
                glucoseStatusText.text = "⚠️ High!"
                glucoseStatusText.setTextColor(0xFFD32F2F.toInt())
            }
        }
    }

    private fun updateFoodDisplay() {
        val meal = MealSessionManager.currentMeal

        if (meal == null) {
            mealItemsText.text = "No food detected"
            totalCarbsText.text = "Total carbs: -- g"
            return
        }

        // Display food items
        val items = meal.foodItems
        if (!items.isNullOrEmpty()) {
            val itemsText = items.joinToString("\n") { item ->
                val name = item.nameHebrew ?: item.name
                val carbs = item.carbsGrams?.toInt() ?: 0
                "• $name (${carbs}g carbs)"
            }
            mealItemsText.text = itemsText
        } else {
            mealItemsText.text = meal.title
        }

        totalCarbsText.text = "Total carbs: ${meal.carbs.toInt()} g"
    }

    private fun calculateDose() {
        val meal = MealSessionManager.currentMeal
        val pm = UserProfileManager

        // Get ICR
        val unitsPerGram = pm.getUnitsPerGram(ctx)
        if (unitsPerGram == null) {
            finalDoseText.text = "Set ICR in profile"
            return
        }

        val carbs = meal?.carbs ?: 0f

        // 1. Carb dose
        val carbDose = carbs * unitsPerGram
        carbDoseText.text = String.format("%.1f u", carbDose)

        // 2. Correction dose (if glucose entered)
        var correctionDose = 0f
        val glucoseStr = glucoseEditText.text.toString()
        val currentGlucose = glucoseStr.toIntOrNull()

        if (currentGlucose != null) {
            val target = pm.getTargetGlucose(ctx) ?: 100
            val isf = pm.getCorrectionFactor(ctx) ?: 50f
            val unit = pm.getGlucoseUnits(ctx)

            // Convert to mg/dL if needed
            val glucoseInMgDl = if (unit == "mmol/L") (currentGlucose * 18) else currentGlucose

            if (glucoseInMgDl > target && isf > 0) {
                correctionDose = (glucoseInMgDl - target) / isf
                correctionLayout.visibility = View.VISIBLE
                correctionDoseText.text = String.format("+%.1f u", correctionDose)
            } else if (glucoseInMgDl < target) {
                // Negative correction (reduce dose)
                correctionDose = (glucoseInMgDl - target) / isf
                correctionLayout.visibility = View.VISIBLE
                correctionDoseText.text = String.format("%.1f u", correctionDose)
            } else {
                correctionLayout.visibility = View.GONE
            }
        } else {
            correctionLayout.visibility = View.GONE
        }

        // Base dose before adjustments
        val baseDose = carbDose + correctionDose

        // 3. Sick day adjustment
        var sickAdjustment = 0f
        if (pm.isSickModeEnabled(ctx)) {
            val sickPercent = pm.getSickDayAdjustment(ctx)
            sickAdjustment = baseDose * (sickPercent / 100f)
            sickLayout.visibility = View.VISIBLE
            sickAdjustmentText.text = String.format("+%.1f u", sickAdjustment)
        } else {
            sickLayout.visibility = View.GONE
        }

        // 4. Stress adjustment
        var stressAdjustment = 0f
        if (pm.isStressModeEnabled(ctx)) {
            val stressPercent = pm.getStressAdjustment(ctx)
            stressAdjustment = baseDose * (stressPercent / 100f)
            stressLayout.visibility = View.VISIBLE
            stressAdjustmentText.text = String.format("+%.1f u", stressAdjustment)
        } else {
            stressLayout.visibility = View.GONE
        }

        // 5. Exercise adjustment (from radio buttons OR home toggle)
        var exerciseAdjustment = 0f
        val selectedActivity = activityRadioGroup.checkedRadioButtonId

        when (selectedActivity) {
            R.id.rb_activity_light -> {
                val lightPercent = pm.getLightExerciseAdjustment(ctx)
                exerciseAdjustment = -(baseDose * (lightPercent / 100f))
                exerciseLayout.visibility = View.VISIBLE
                exerciseAdjustmentText.text = String.format("%.1f u", exerciseAdjustment)
            }
            R.id.rb_activity_intense -> {
                val intensePercent = pm.getIntenseExerciseAdjustment(ctx)
                exerciseAdjustment = -(baseDose * (intensePercent / 100f))
                exerciseLayout.visibility = View.VISIBLE
                exerciseAdjustmentText.text = String.format("%.1f u", exerciseAdjustment)
            }
            else -> {
                // Check home screen exercise mode
                if (pm.isExerciseModeEnabled(ctx)) {
                    val lightPercent = pm.getLightExerciseAdjustment(ctx)
                    exerciseAdjustment = -(baseDose * (lightPercent / 100f))
                    exerciseLayout.visibility = View.VISIBLE
                    exerciseAdjustmentText.text = String.format("%.1f u", exerciseAdjustment)
                } else {
                    exerciseLayout.visibility = View.GONE
                }
            }
        }

        // 6. Final dose
        var finalDose = baseDose + sickAdjustment + stressAdjustment + exerciseAdjustment

        // Apply rounding
        val rounding = pm.getDoseRounding(ctx)
        finalDose = if (rounding == 0.5f) {
            (Math.round(finalDose * 2) / 2f)
        } else {
            Math.round(finalDose).toFloat()
        }

        // Don't allow negative dose
        if (finalDose < 0) finalDose = 0f

        lastCalculatedDose = finalDose
        finalDoseText.text = String.format("%.1f u", finalDose)
    }

    private fun updateAnalysisResults() {
        val meal = MealSessionManager.currentMeal

        if (meal == null || !hasAnalysisData(meal)) {
            analysisCard.visibility = View.GONE
            return
        }

        analysisCard.visibility = View.VISIBLE

        meal.portionWeightGrams?.let { weight ->
            portionWeightText.text = "Estimated weight: ${weight.toInt()} g"
        }

        val diameter = meal.plateDiameterCm
        val depth = meal.plateDepthCm
        if (diameter != null && depth != null) {
            plateDimensionsText.text = String.format("Plate: %.1f cm × %.1f cm", diameter, depth)
        }

        meal.analysisConfidence?.let { confidence ->
            val percentage = (confidence * 100).toInt()
            confidenceText.text = "Confidence: $percentage%"
        }

        val refStatus = when (meal.referenceObjectDetected) {
            true -> "Reference: Detected ✓"
            false -> "Reference: Not detected"
            null -> "Reference: N/A"
        }
        referenceStatusText.text = refStatus
    }

    private fun hasAnalysisData(meal: Meal): Boolean {
        return meal.portionWeightGrams != null ||
                meal.plateDiameterCm != null ||
                meal.analysisConfidence != null
    }

    private fun saveMeal() {
        val meal = MealSessionManager.currentMeal
        if (meal == null) {
            ToastHelper.showShort(ctx, "No meal data to save")
            return
        }

        if (meal.carbs <= 0f) {
            ToastHelper.showShort(ctx, "No carbs detected. Please edit the meal first.")
            return
        }

        // Get glucose value if entered
        val glucoseValue = glucoseEditText.text.toString().toIntOrNull()
        val glucoseUnits = UserProfileManager.getGlucoseUnits(ctx)

        // Get activity level
        val activityLevel = when (activityRadioGroup.checkedRadioButtonId) {
            R.id.rb_activity_light -> "light"
            R.id.rb_activity_intense -> "intense"
            else -> "normal"
        }

        // Get calculation breakdown
        val pm = UserProfileManager
        val unitsPerGram = pm.getUnitsPerGram(ctx)
        val carbDose = if (unitsPerGram != null) meal.carbs * unitsPerGram else null

        var correctionDose: Float? = null
        if (glucoseValue != null && unitsPerGram != null) {
            val target = pm.getTargetGlucose(ctx) ?: 100
            val isf = pm.getCorrectionFactor(ctx) ?: 50f
            val glucoseInMgDl = if (glucoseUnits == "mmol/L") glucoseValue * 18 else glucoseValue
            if (glucoseInMgDl > target) {
                correctionDose = (glucoseInMgDl - target) / isf
            }
        }

        // Calculate exercise adjustment
        var exerciseAdj: Float? = null
        if (activityLevel != "normal" && carbDose != null) {
            val adjPercent = when (activityLevel) {
                "light" -> pm.getLightExerciseAdjustment(ctx)
                "intense" -> pm.getIntenseExerciseAdjustment(ctx)
                else -> 0
            }
            exerciseAdj = -(carbDose * adjPercent / 100f)
        }

        // Create updated meal with all details
        val updatedMeal = meal.copy(
            insulinDose = lastCalculatedDose,
            glucoseLevel = glucoseValue,
            glucoseUnits = glucoseUnits,
            activityLevel = activityLevel,
            carbDose = carbDose,
            correctionDose = correctionDose,
            exerciseAdjustment = exerciseAdj,
            wasSickMode = pm.isSickModeEnabled(ctx),
            wasStressMode = pm.isStressModeEnabled(ctx)
        )

        MealSessionManager.saveCurrentMealWithDose(updatedMeal)
        ToastHelper.showShort(ctx, "Meal saved to history")
        selectHistoryTab()
    }
    private fun selectHistoryTab() {
        val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.selectedItemId = R.id.historyFragment
    }

    override fun onResume() {
        super.onResume()
        setupGlucoseUnit()
        updateFoodDisplay()
        updateAnalysisResults()
        updateActivityLabels()
        calculateDose()
    }
}