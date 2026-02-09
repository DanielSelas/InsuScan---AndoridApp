package com.example.insuscan.summary

import android.app.AlertDialog
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
import androidx.core.content.ContextCompat
import com.example.insuscan.utils.ToastHelper
import com.example.insuscan.utils.TopBarHelper
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.lifecycle.lifecycleScope
import androidx.constraintlayout.widget.Group
import com.example.insuscan.MainActivity
import kotlinx.coroutines.launch
import com.example.insuscan.network.repository.MealRepository
import com.example.insuscan.network.repository.MealRepositoryImpl
import android.app.Dialog
import android.graphics.BitmapFactory
import android.util.Log
import android.view.Window
import android.widget.ImageButton
import android.widget.ImageView
import com.bumptech.glide.Glide
import java.io.File
import com.example.insuscan.utils.FileLogger
class SummaryFragment : Fragment(R.layout.fragment_summary) {

    // Food detection views
    private lateinit var mealItemsContainer: LinearLayout
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

    // Image views
    private lateinit var imageCard: CardView
    private lateinit var mealImageView: ImageView

    // Active Insulin
    private lateinit var activeInsulinEditText: EditText

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
    private lateinit var activeInsulinLayout: LinearLayout
    private lateinit var activeInsulinText: TextView
    private lateinit var finalDoseText: TextView

    // Analysis views
    private lateinit var analysisCard: CardView
    private lateinit var analysisLayout: LinearLayout
    private lateinit var portionWeightText: TextView
    private lateinit var plateDimensionsText: TextView
    private lateinit var confidenceText: TextView
    private lateinit var referenceStatusText: TextView
    private val mealRepository = MealRepositoryImpl()

    // Bottom button
    private lateinit var logButton: Button

    private val ctx get() = requireContext()

    // Empty State
    private lateinit var contentGroup: Group
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var scanNowButton: Button

    // Calculation results (stored for saving)
    private var lastCalculatedResult: DoseResult? = null

    companion object {
        private const val MSG_SET_RATIO = "Please set insulin to carb ratio in Profile first"

        // Validation result for save operation
        private sealed class SaveValidation {
            object Valid : SaveValidation()
            object NoMealData : SaveValidation()
            object NoFoodDetected : SaveValidation()
            object ProfileIncomplete : SaveValidation()
        }
    }

    data class DoseResult(
        val carbDose: Float,
        val correctionDose: Float,
        val baseDose: Float,
        val sickAdj: Float,
        val stressAdj: Float,
        val exerciseAdj: Float,
        val iob: Float,
        val finalDose: Float,
        val roundedDose: Float
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        findViews(view)
        initializeTopBar(view)
        setupGlucoseUnit()
        updateFoodDisplay()
        updateAnalysisResults()
        displayMealImage()
        initializeListeners()
        updateEmptyStateVisibility()
        if (MealSessionManager.currentMeal != null) {
            calculateDose() // initial calculation
        }
    }

    private fun updateEmptyStateVisibility() {
        val hasMeal = MealSessionManager.currentMeal != null
        if (hasMeal) {
            contentGroup.visibility = View.VISIBLE
            emptyStateLayout.visibility = View.GONE
        } else {
            contentGroup.visibility = View.GONE
            emptyStateLayout.visibility = View.VISIBLE
        }
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
        mealItemsContainer = view.findViewById(R.id.layout_meal_items_container)
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

        // Active Insulin
        activeInsulinEditText = view.findViewById(R.id.et_active_insulin)

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
        
        // Note: You might need to add a layout for IOB display in the breakdown if desired, 
        // but for now we just subtract it from the final. 
        // Ideally we should add a row for it in layout_summary_breakdown if possible, 
        // but it's not strictly required by the prompt, just the input.
        
        finalDoseText = view.findViewById(R.id.tv_final_dose)

        // Analysis
        analysisCard = view.findViewById(R.id.card_analysis_results)
        analysisLayout = view.findViewById(R.id.layout_analysis_results)
        portionWeightText = view.findViewById(R.id.tv_portion_weight)
        plateDimensionsText = view.findViewById(R.id.tv_plate_dimensions)
        confidenceText = view.findViewById(R.id.tv_analysis_confidence)
        referenceStatusText = view.findViewById(R.id.tv_reference_status)

        // Bottom
        logButton = view.findViewById(R.id.btn_log_meal)

        // Image card
        imageCard = view.findViewById(R.id.card_image)
        mealImageView = view.findViewById(R.id.iv_meal_image)

        // Empty State
        contentGroup = view.findViewById(R.id.group_content)
        emptyStateLayout = view.findViewById(R.id.layout_empty_state)
        scanNowButton = view.findViewById(R.id.btn_scan_now)
    }

    private fun setupGlucoseUnit() {
        val unit = UserProfileManager.getGlucoseUnits(ctx)
        glucoseUnitText.text = unit
    }

    private fun initializeListeners() {
        editButton.setOnClickListener {
            findNavController().navigate(R.id.action_summaryFragment_to_manualEntryFragment)
        }

        // Initially disable the log button and make it gray
        setLogButtonEnabled(false)

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

        // Active Insulin input listener
        activeInsulinEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                calculateDose()
            }
        })

        // Activity level listener
        activityRadioGroup.setOnCheckedChangeListener { _, _ ->
            calculateDose()
        }

        // Update activity labels with percentages
        updateActivityLabels()

        scanNowButton.setOnClickListener {
            (requireActivity() as? MainActivity)?.selectScanTab()
        }

        setupScrollListener()
    }

    private fun setLogButtonEnabled(enabled: Boolean) {
        logButton.isEnabled = enabled
        if (enabled) {
             // Restore original color (assuming it was blue/primary)
             logButton.backgroundTintList = android.content.res.ColorStateList.valueOf(
                 ContextCompat.getColor(ctx, R.color.primary)
             )
             // If R.color.primary isn't available, we can hardcode the blue color used in XML or default
             // The XML used default or a specific tint. Let's try to set it to the blue used elsewhere.
             // XML had no backgroundTint, so it used default theme color.
             // We can just set alpha to 1f vs 0.5f or use a gray color.
             logButton.alpha = 1.0f
        } else {
            // Make it gray/looks disabled
             logButton.alpha = 0.5f
        }
    }

    private fun setupScrollListener() {
        val scrollView = view?.findViewById<ScrollView>(R.id.content_scroll_view) ?: return

        // 1. Check if scrolling is even needed (content fits on screen)
        scrollView.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                // Remove listener to avoid multiple calls
                scrollView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                
                checkScrollAndEnable(scrollView)
            }
        })

        // 2. Listen for scroll changes
        scrollView.viewTreeObserver.addOnScrollChangedListener {
            checkScrollAndEnable(scrollView)
        }
    }

    private fun checkScrollAndEnable(scrollView: ScrollView) {
        if (logButton.isEnabled) return // Already enabled

        val child = scrollView.getChildAt(0) ?: return
        val childHeight = child.height
        val scrollHeight = scrollView.height
        val scrollY = scrollView.scrollY

        // Check if content fits entirely or if we scrolled to bottom
        // We add a small buffer (e.g. 50px) to make it easier to trigger
        val diff = (childHeight - (scrollHeight + scrollY))

        if (childHeight <= scrollHeight || diff <= 50) {
            setLogButtonEnabled(true)
        }
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
                glucoseStatusText.setTextColor(ContextCompat.getColor(ctx, R.color.status_critical))
            }
            glucoseInMgDl < targetInMgDl - 20 -> {
                glucoseStatusText.text = "Below target"
                glucoseStatusText.setTextColor(ContextCompat.getColor(ctx, R.color.status_warning))
            }
            glucoseInMgDl <= targetInMgDl + 30 -> {
                glucoseStatusText.text = "✓ In range"
                glucoseStatusText.setTextColor(ContextCompat.getColor(ctx, R.color.status_normal))
            }
            glucoseInMgDl <= 180 -> {
                glucoseStatusText.text = "Above target"
                glucoseStatusText.setTextColor(ContextCompat.getColor(ctx, R.color.status_warning))
            }
            else -> {
                glucoseStatusText.text = "⚠️ High!"
                glucoseStatusText.setTextColor(ContextCompat.getColor(ctx, R.color.status_critical))
            }
        }
    }

    private fun updateFoodDisplay() {
        val meal = MealSessionManager.currentMeal

        mealItemsContainer.removeAllViews()

        if (meal == null) {
            addSingleMessageRow("No meal data")
            totalCarbsText.text = "Total carbs: -- g"
            return
        }

        // Check if we have actual food detection results
        val items = meal.foodItems
        if (!items.isNullOrEmpty()) {
            var calculatedTotalCarbs = 0f

            items.forEachIndexed { index, item ->
                val name = item.nameHebrew ?: item.name
                val carbs = item.carbsGrams?.toInt() ?: 0
                val weight = item.weightGrams?.toInt()
                
                // Accumulate total from items for consistency
                calculatedTotalCarbs += (item.carbsGrams ?: 0f)

                addFoodItemRow(item, index, index == items.lastIndex)
            }
            
            // Update total text with the calculated sum
            totalCarbsText.text = String.format("Total carbs: %.2f g", calculatedTotalCarbs)
        } else {
            // No food detected - show clear message
            addSingleMessageRow("No food detected in image")
            totalCarbsText.text = "Total carbs: 0 g"
        }
    }

    private fun addFoodItemRow(item: com.example.insuscan.meal.FoodItem, index: Int, isLast: Boolean) {
        val row = LinearLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 16)
            gravity = android.view.Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            // Add ripple effect
            val outValue = android.util.TypedValue()
            ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
        }

        val name = item.nameHebrew ?: item.name
        val carbs = item.carbsGrams ?: 0f
        val weight = item.weightGrams?.toInt()
        val hasMissingData = carbs == 0f

        // 1. Food Name
        val nameText = TextView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = if (hasMissingData) "⚠️ $name" else name
            textSize = 16f
            setTextColor(ContextCompat.getColor(ctx, if (hasMissingData) R.color.error else R.color.text_primary))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        row.addView(nameText)

        // 2. Weight (Editable)
        if (weight != null && weight > 0) {
            val weightText = TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                text = "Weight: ${weight}g ✎" // Add pencil icon to indicate editability
                textSize = 13f
                setTextColor(ContextCompat.getColor(ctx, R.color.primary)) // Blue to indicate interaction
                setPadding(16, 0, 16, 0)
                setOnClickListener {
                    showWeightEditDialog(index, item)
                }
            }
            row.addView(weightText)
        }

        // 3. Carbs or Missing Data Action
        val trailingText = TextView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            if (hasMissingData) {
                text = "Fix >"
                textSize = 14f
                setTextColor(ContextCompat.getColor(ctx, R.color.error)) // Red
                setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                text = String.format("Carbs: %.2f g", carbs)
                textSize = 14f
                setTextColor(ContextCompat.getColor(ctx, R.color.primary)) // Blue distinct color
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
        }
        row.addView(trailingText)

        // Row Click Listener
        if (hasMissingData) {
            row.setOnClickListener {
                findNavController().navigate(R.id.action_summaryFragment_to_manualEntryFragment)
            }
        }

        mealItemsContainer.addView(row)

        // Divider
        if (!isLast) {
             val divider = View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2)
                setBackgroundColor(0xFFEEEEEE.toInt())
             }
             mealItemsContainer.addView(divider)
        }
    }

    private fun showWeightEditDialog(index: Int, item: com.example.insuscan.meal.FoodItem) {
        val currentWeight = item.weightGrams ?: 0f
        val input = EditText(ctx).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(currentWeight.toInt().toString())
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }

        AlertDialog.Builder(ctx)
            .setTitle("Edit Weight (grams)")
            .setMessage("Enter new weight for ${item.nameHebrew ?: item.name}:")
            .setView(input)
            .setPositiveButton("Update") { _, _ ->
                val newWeightStr = input.text.toString()
                val newWeight = newWeightStr.toFloatOrNull()
                if (newWeight != null && newWeight > 0) {
                    updateMealItemWeight(index, item, newWeight)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateMealItemWeight(index: Int, item: com.example.insuscan.meal.FoodItem, newWeight: Float) {
        val currentMeal = MealSessionManager.currentMeal ?: return
        val currentItems = currentMeal.foodItems?.toMutableList() ?: return
        
        // Calculate factor based on old weight
        val oldWeight = item.weightGrams ?: 100f // prevent div by zero if missing
        val safeOldWeight = if (oldWeight == 0f) 100f else oldWeight
        
        val currentCarbs = item.carbsGrams ?: 0f
        val carbsPerGram = currentCarbs / safeOldWeight
        
        val newCarbs = newWeight * carbsPerGram
        
        // Update item
        val updatedItem = item.copy(
            weightGrams = newWeight,
            carbsGrams = newCarbs
        )
        
        currentItems[index] = updatedItem
        
        // Update Meal
        // Recalculate total meal carbs
        var newTotalCarbs = 0f
        currentItems.forEach { newTotalCarbs += (it.carbsGrams ?: 0f) }
        
        val updatedMeal = currentMeal.copy(
            foodItems = currentItems,
            carbs = newTotalCarbs
        )
        
        MealSessionManager.updateCurrentMeal(updatedMeal)
        
        // Refresh UI
        updateFoodDisplay()
        calculateDose()
        ToastHelper.showShort(ctx, "Weight updated: ${newWeight.toInt()}g")
    }

    private fun addSingleMessageRow(message: String) {
        val textView = TextView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = message
            textSize = 15f
            setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
            setPadding(0, 8, 0, 8)
        }
        mealItemsContainer.addView(textView)
    }

    private fun calculateDose() {
        val meal = MealSessionManager.currentMeal ?: return
        val pm = UserProfileManager
        
        // 1. Check Profile
        if (!meal.profileComplete) {
            showProfileIncompleteState()
            return
        }

        val gramsPerUnit = pm.getGramsPerUnit(ctx)
        if (gramsPerUnit == null) {
            finalDoseText.text = "Set ICR in profile"
            return
        }

        // 2. Perform centralized calculation
        val result = performCalculation(
            carbs = meal.carbs,
            glucose = glucoseEditText.text.toString().toIntOrNull(),
            activeInsulin = activeInsulinEditText.text.toString().toFloatOrNull(),
            activityLevel = getSelectedActivityLevel(),
            gramsPerUnit = gramsPerUnit
        )
        
        lastCalculatedResult = result

        // 3. Update UI
        carbDoseText.text = String.format("%.1f u", result.carbDose)

        // Correction
        if (result.correctionDose != 0f) {
            correctionLayout.visibility = View.VISIBLE
            val sign = if (result.correctionDose > 0) "+" else ""
            correctionDoseText.text = String.format("%s%.1f u", sign, result.correctionDose)
            // Color code
            correctionDoseText.setTextColor(ContextCompat.getColor(ctx, if(result.correctionDose > 0) R.color.status_warning else R.color.status_normal))
        } else {
            correctionLayout.visibility = View.GONE
        }

        // Adjustments
        if (result.sickAdj > 0) {
            sickLayout.visibility = View.VISIBLE
            sickAdjustmentText.text = String.format("+%.1f u", result.sickAdj)
        } else {
            sickLayout.visibility = View.GONE
        }

        if (result.stressAdj > 0) {
            stressLayout.visibility = View.VISIBLE
            stressAdjustmentText.text = String.format("+%.1f u", result.stressAdj)
        } else {
            stressLayout.visibility = View.GONE
        }

        if (result.exerciseAdj > 0) { // Exercise adjustment is a reduction
            exerciseLayout.visibility = View.VISIBLE
            exerciseAdjustmentText.text = String.format("-%.1f u", result.exerciseAdj)
        } else {
            exerciseLayout.visibility = View.GONE
        }

        // Final Dose
        finalDoseText.text = String.format("%.1f u", result.roundedDose)
    }

    /**
     * THE GOLDEN LOGIC (Client Implementation)
     * Matches Server-Side InsulinCalculator.java
     */
    private fun performCalculation(
        carbs: Float,
        glucose: Int?,
        activeInsulin: Float?,
        activityLevel: String,
        gramsPerUnit: Float
    ): DoseResult {
        val pm = UserProfileManager
        
        FileLogger.log("CALC", "--- New Calculation Started ---")
        FileLogger.log("CALC", "INPUTS:")
        FileLogger.log("CALC", "  Carbs: $carbs g")
        FileLogger.log("CALC", "  Glucose: $glucose mg/dL")
        FileLogger.log("CALC", "  Active Insulin: $activeInsulin u")
        FileLogger.log("CALC", "  Activity: $activityLevel")
        FileLogger.log("CALC", "PROFILE:")
        FileLogger.log("CALC", "  ICR: $gramsPerUnit g/u")
        
        // 1. Carb Dose = Carbs / Ratio
        val carbDose = if (gramsPerUnit > 0) carbs / gramsPerUnit else 0f
        FileLogger.log("CALC", "STEP 1: Carb Dose = $carbs / $gramsPerUnit = $carbDose u")

        // 2. Correction Dose
        var correctionDose = 0f
        if (glucose != null) {
            val target = pm.getTargetGlucose(ctx) ?: 100
            val isf = pm.getCorrectionFactor(ctx) ?: 50f
            val unit = pm.getGlucoseUnits(ctx)
            
            // Normalize to mg/dL
            val glucoseInMgDl = if (unit == "mmol/L") (glucose * 18) else glucose
            
            if (isf > 0) {
                correctionDose = (glucoseInMgDl - target) / isf
                FileLogger.log("CALC", "STEP 2: Correction = ($glucoseInMgDl - $target) / $isf = $correctionDose u")
            }
        } else {
             FileLogger.log("CALC", "STEP 2: Correction = 0 (No glucose)")
        }

        // 3. Base Dose
        val baseDose = carbDose + correctionDose
        FileLogger.log("CALC", "STEP 3: Base Dose = $carbDose + $correctionDose = $baseDose u")

        // 4. Adjustments (Applied to Base Dose)
        // Sick
        var sickAdj = 0f
        if (pm.isSickModeEnabled(ctx)) {
            val pct = pm.getSickDayAdjustment(ctx)
            sickAdj = baseDose * (pct / 100f)
            FileLogger.log("CALC", "STEP 4a: Sick Adj = $baseDose * $pct% = +$sickAdj u")
        }
        
        // Stress
        var stressAdj = 0f
        if (pm.isStressModeEnabled(ctx)) {
            val pct = pm.getStressAdjustment(ctx)
            stressAdj = baseDose * (pct / 100f)
            FileLogger.log("CALC", "STEP 4b: Stress Adj = $baseDose * $pct% = +$stressAdj u")
        }
        
        // Exercise
        var exerciseAdj = 0f 
        // Logic: if specific button selected -> use that. Else if home mode -> use light.
        if (activityLevel == "light") {
            val pct = pm.getLightExerciseAdjustment(ctx)
            exerciseAdj = baseDose * (pct / 100f)
            FileLogger.log("CALC", "STEP 4c: Exercise (Light) = $baseDose * $pct% = -$exerciseAdj u")
        } else if (activityLevel == "intense") {
            val pct = pm.getIntenseExerciseAdjustment(ctx)
            exerciseAdj = baseDose * (pct / 100f)
            FileLogger.log("CALC", "STEP 4c: Exercise (Intense) = $baseDose * $pct% = -$exerciseAdj u")
        } else if (pm.isExerciseModeEnabled(ctx) && activityLevel == "normal") {
            // Fallback to Home Screen Mode
            val pct = pm.getLightExerciseAdjustment(ctx)
            exerciseAdj = baseDose * (pct / 100f)
            FileLogger.log("CALC", "STEP 4c: Exercise (Home) = $baseDose * $pct% = -$exerciseAdj u")
        }

        // IOB
        val iob = activeInsulin ?: 0f
        FileLogger.log("CALC", "STEP 4d: Active Insulin (IOB) = -$iob u")

        // 5. Final
        var finalDose = baseDose + sickAdj + stressAdj - exerciseAdj - iob
        if (finalDose < 0) finalDose = 0f
        FileLogger.log("CALC", "STEP 5: Final Raw = $baseDose + $sickAdj + $stressAdj - $exerciseAdj - $iob = $finalDose u")

        // 6. Rounding (User requested precise result)
        // We bypass the profile's rounding preference and provide 2-decimal precision
        val roundedDose = (Math.round(finalDose * 100) / 100f)
        FileLogger.log("CALC", "STEP 6: Rounded (2 dec) = $roundedDose u")
        FileLogger.log("CALC", "--- Calculation Complete ---")

        return DoseResult(
            carbDose, correctionDose, baseDose, 
            sickAdj, stressAdj, exerciseAdj, iob, 
            finalDose, roundedDose
        )
    }

    private fun showProfileIncompleteState() {
        finalDoseText.text = "Setup Required"
        finalDoseText.setTextColor(ContextCompat.getColor(ctx, R.color.error))
        carbDoseText.text = "Tap here to complete profile"
        carbDoseText.setTextColor(ContextCompat.getColor(ctx, R.color.primary))
        carbDoseText.setOnClickListener {
            findNavController().navigate(R.id.action_summaryFragment_to_profileFragment)
        }
        correctionLayout.visibility = View.GONE
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

    private fun displayMealImage() {
        val meal = MealSessionManager.currentMeal
        val imagePath = meal?.imagePath

        if (imagePath.isNullOrEmpty()) {
            imageCard.visibility = View.GONE
            return
        }

        val imageFile = File(imagePath)
        if (!imageFile.exists()) {
            imageCard.visibility = View.GONE
            return
        }

        // Load and display the image
        Glide.with(this)
            .load(imageFile)
            .into(mealImageView)

        imageCard.visibility = View.VISIBLE

        mealImageView.setOnClickListener {
            showFullscreenImage(imagePath)
        }
    }

    private fun showFullscreenImage(imagePath: String) {
        val dialog = Dialog(ctx, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_image_fullscreen)

        val imageView = dialog.findViewById<ImageView>(R.id.iv_fullscreen_image)
        val closeBtn = dialog.findViewById<ImageButton>(R.id.btn_close)

        // Glide handles EXIF orientation automatically
        Glide.with(this)
            .load(File(imagePath))
            .into(imageView)

        closeBtn.setOnClickListener {
            dialog.dismiss()
        }

        imageView.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun hasAnalysisData(meal: Meal): Boolean {
        return meal.portionWeightGrams != null ||
                meal.plateDiameterCm != null ||
                meal.analysisConfidence != null
    }

    private fun saveMeal() {
        val meal = MealSessionManager.currentMeal

        when (val validation = validateBeforeSave(meal)) {
            is SaveValidation.Valid -> performSave(meal!!)
            is SaveValidation.NoMealData -> showError("No meal data to save")
            is SaveValidation.NoFoodDetected -> showError("No food detected. Use 'Edit' to add items manually.")
            is SaveValidation.ProfileIncomplete -> showIncompleteProfileDialog(meal!!)
        }
    }

    private fun validateBeforeSave(meal: Meal?): SaveValidation {
        return when {
            meal == null -> SaveValidation.NoMealData
            meal.carbs <= 0f && meal.foodItems.isNullOrEmpty() -> SaveValidation.NoFoodDetected
            !meal.profileComplete -> SaveValidation.ProfileIncomplete
            else -> SaveValidation.Valid
        }
    }

    private fun showError(message: String) {
        ToastHelper.showShort(ctx, message)
    }

    private fun showIncompleteProfileDialog(meal: Meal) {
        AlertDialog.Builder(ctx)
            .setTitle("Insulin Not Calculated")
            .setMessage("Your medical profile is incomplete, so no insulin dose was calculated.\n\nDo you want to complete your profile now, or save the meal without insulin data?")
            .setPositiveButton("Complete Profile") { _, _ ->
                findNavController().navigate(R.id.action_summaryFragment_to_profileFragment)
            }
            .setNegativeButton("Save Anyway") { _, _ ->
                performSave(meal)
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun performSave(meal: Meal) {
        val updatedMeal = buildUpdatedMeal(meal)
        MealSessionManager.updateCurrentMeal(updatedMeal)

        val mealId = updatedMeal.serverId
        if (mealId != null) {
            saveToServer(mealId, updatedMeal.insulinDose)
        } else {
            showError("Meal logged (Local Mode)")
            selectHistoryTab()
        }
    }

    private fun buildUpdatedMeal(meal: Meal): Meal {
        val pm = UserProfileManager
        val glucoseValue = glucoseEditText.text.toString().toIntOrNull()
        val glucoseUnits = pm.getGlucoseUnits(ctx)
        val activityLevel = getSelectedActivityLevel()
        val gramsPerUnit = pm.getGramsPerUnit(ctx)
        val activeInsulin = activeInsulinEditText.text.toString().toFloatOrNull() ?: 0f

        if (gramsPerUnit == null) return meal

        // Re-run calculation to be safe/sure
        val result = performCalculation(
            meal.carbs, 
            glucoseValue, 
            activeInsulin, 
            activityLevel, 
            gramsPerUnit
        )

        val exerciseAdjValue = if(result.exerciseAdj > 0) -result.exerciseAdj else 0f

        return meal.copy(
            insulinDose = result.roundedDose,
            recommendedDose = result.roundedDose,
            glucoseLevel = glucoseValue,
            glucoseUnits = glucoseUnits,
            activityLevel = activityLevel,
            carbDose = result.carbDose,
            correctionDose = result.correctionDose,
            exerciseAdjustment = exerciseAdjValue,
            sickAdjustment = result.sickAdj,
            stressAdjustment = result.stressAdj,
            wasSickMode = pm.isSickModeEnabled(ctx),
            wasStressMode = pm.isStressModeEnabled(ctx),
            activeInsulin = activeInsulin
        )
    }

    private fun getSelectedActivityLevel(): String {
        return when (activityRadioGroup.checkedRadioButtonId) {
            R.id.rb_activity_light -> "light"
            R.id.rb_activity_intense -> "intense"
            else -> "normal"
        }
    }

    private fun calculateCorrectionDose(glucose: Int?, units: String, unitsPerGram: Float?): Float? {
        if (glucose == null || unitsPerGram == null) return null

        val pm = UserProfileManager
        val target = pm.getTargetGlucose(ctx) ?: 100
        val isf = pm.getCorrectionFactor(ctx) ?: 50f
        val glucoseInMgDl = if (units == "mmol/L") glucose * 18 else glucose

        return if (glucoseInMgDl > target) (glucoseInMgDl - target) / isf else null
    }

    private fun calculateExerciseAdjustment(activityLevel: String, unitsPerGram: Float?, carbs: Float): Float? {
        if (activityLevel == "normal" || unitsPerGram == null) return null

        val pm = UserProfileManager
        val carbDose = carbs * unitsPerGram
        val adjPercent = when (activityLevel) {
            "light" -> pm.getLightExerciseAdjustment(ctx)
            "intense" -> pm.getIntenseExerciseAdjustment(ctx)
            else -> 0
        }

        return -(carbDose * adjPercent / 100f)
    }

    private fun calculateSickAdjustment(carbs: Float, unitsPerGram: Float?, correctionDose: Float?): Float? {
        if (!UserProfileManager.isSickModeEnabled(ctx) || unitsPerGram == null) return null
        
        val carbDose = carbs * unitsPerGram
        val baseDose = carbDose + (correctionDose ?: 0f)
        val sickPercent = UserProfileManager.getSickDayAdjustment(ctx)
        return baseDose * (sickPercent / 100f)
    }

    private fun calculateStressAdjustment(carbs: Float, unitsPerGram: Float?, correctionDose: Float?): Float? {
        if (!UserProfileManager.isStressModeEnabled(ctx) || unitsPerGram == null) return null

        val carbDose = carbs * unitsPerGram
        val baseDose = carbDose + (correctionDose ?: 0f)
        val stressPercent = UserProfileManager.getStressAdjustment(ctx)
        return baseDose * (stressPercent / 100f)
    }

    private fun saveToServer(mealId: String, dose: Float?) {
        logButton.isEnabled = false

        lifecycleScope.launch {
            try {
                // Use the current meal from session which has been updated with the latest UI values
                val currentMeal = MealSessionManager.currentMeal
                if (currentMeal == null) {
                     showError("Error: No meal data to save")
                     logButton.isEnabled = true
                     return@launch
                }

                // Map to DTO to send the full object (including carb dose, correction, etc.)
                val mealDto = com.example.insuscan.mapping.MealDtoMapper.mapToDto(currentMeal)
                val userEmail = com.example.insuscan.auth.AuthManager.getUserEmail() ?: ""

                // Use saveScannedMeal to save the detailed breakdown to the server
                val result = mealRepository.saveScannedMeal(userEmail, mealDto)

                if (result.isSuccess) {
                    ToastHelper.showShort(ctx, "Meal logged successfully")
                    selectHistoryTab()
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                    showError("Failed to save: $errorMsg")
                    logButton.isEnabled = true
                }
            } catch (e: Exception) {
                showError("Error: ${e.message}")
                logButton.isEnabled = true
            }
        }
    }

    private fun handleSaveResult(result: Result<*>) {
        if (result.isSuccess) {
            ToastHelper.showShort(ctx, "Meal saved to history")
            MealSessionManager.clearSession()
            selectHistoryTab()
        } else {
            showError("Failed to save: ${result.exceptionOrNull()?.message}")
            logButton.isEnabled = true
        }
    }

    private fun selectHistoryTab() {
        val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.selectedItemId = R.id.historyFragment
    }

    // Checks if the user has completed the profile and updates the meal object
    private fun checkAndRefreshProfileStatus() {
        val meal = MealSessionManager.currentMeal ?: return

        // If already complete, nothing to do
        if (meal.profileComplete) return

        // Check local profile data
        val pm = UserProfileManager
        val hasICR = pm.getInsulinCarbRatioRaw(ctx) != null
        val hasISF = pm.getCorrectionFactor(ctx) != null
        val hasTarget = pm.getTargetGlucose(ctx) != null

        // If profile is now full, update the object in memory
        if (hasICR && hasISF && hasTarget) {
            val updatedMeal = meal.copy(
                profileComplete = true,
                insulinMessage = null // Clear the warning message
            )
            MealSessionManager.setCurrentMeal(updatedMeal)
        }
    }

    override fun onResume() {
        super.onResume()

        updateEmptyStateVisibility()
        if (MealSessionManager.currentMeal == null) return

        checkAndRefreshProfileStatus()

        setupGlucoseUnit()
        updateFoodDisplay()
        updateAnalysisResults()
        displayMealImage()
        updateActivityLabels()
        calculateDose()
    }
}