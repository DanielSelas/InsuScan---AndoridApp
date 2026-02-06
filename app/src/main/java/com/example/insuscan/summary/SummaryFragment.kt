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
    private var lastCalculatedDose: Float = 0f

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
        // Update activity labels with percentages
        updateActivityLabels()

        scanNowButton.setOnClickListener {
            (requireActivity() as? MainActivity)?.selectScanTab()
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

                addFoodItemRow(name, weight, carbs, index == items.lastIndex)
            }
            
            // Update total text with the calculated sum
            totalCarbsText.text = "Total carbs: ${calculatedTotalCarbs.toInt()} g"
        } else {
            // No food detected - show clear message
            addSingleMessageRow("No food detected in image")
            totalCarbsText.text = "Total carbs: 0 g"
        }
    }

    private fun addFoodItemRow(name: String, weight: Int?, carbs: Int, isLast: Boolean) {
        val row = LinearLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 16)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        // 1. Food Name
        val nameText = TextView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = name
            textSize = 16f
            setTextColor(0xFF424242.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        row.addView(nameText)

        // 2. Weight (if available)
        if (weight != null && weight > 0) {
            val weightText = TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                text = "Weight: ${weight}g"
                textSize = 13f
                setTextColor(0xFF757575.toInt())
                setPadding(16, 0, 16, 0)
            }
            row.addView(weightText)
        }

        // 3. Carbs
        val carbsText = TextView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            text = "Carbs: ${carbs}g"
            textSize = 14f
            setTextColor(0xFF1976D2.toInt()) // Blue distinct color
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        row.addView(carbsText)

        mealItemsContainer.addView(row)

        // Divider
        if (!isLast) {
             val divider = View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2) // slightly thicker divider
                setBackgroundColor(0xFFEEEEEE.toInt())
             }
             mealItemsContainer.addView(divider)
        }
    }

    private fun addSingleMessageRow(message: String) {
        val textView = TextView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = message
            textSize = 15f
            setTextColor(0xFF616161.toInt())
            setPadding(0, 8, 0, 8)
        }
        mealItemsContainer.addView(textView)
    }

    private fun calculateDose() {
        val meal = MealSessionManager.currentMeal?: return
        val pm = UserProfileManager

        // --- 1. Server Status Check (Hybrid Check) ---
        // If server indicates incomplete profile -> stop and show warning
        if (!meal.profileComplete) {
            finalDoseText.text = "Setup Required"
            finalDoseText.setTextColor(resources.getColor(R.color.red_warning, null))
            carbDoseText.text = "Tap here to complete profile"
            carbDoseText.setTextColor(resources.getColor(R.color.light_blue, null))
            carbDoseText.setOnClickListener {
                findNavController().navigate(R.id.action_summaryFragment_to_profileFragment)
            }
            correctionLayout.visibility = View.GONE
            return
        }

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
        val unitsPerGram = pm.getUnitsPerGram(ctx)

        // ADD LOGS HERE
        Log.d("DEBUG_SAVE", "=== Building Updated Meal ===")
        Log.d("DEBUG_SAVE", "ICR Raw: ${pm.getInsulinCarbRatioRaw(ctx)}")
        Log.d("DEBUG_SAVE", "Units per gram: $unitsPerGram")
        Log.d("DEBUG_SAVE", "Meal carbs (input): ${meal.carbs}")
        Log.d("DEBUG_SAVE", "Meal carbDose (input): ${meal.carbDose}")
        Log.d("DEBUG_SAVE", "Glucose: $glucoseValue $glucoseUnits")
        Log.d("DEBUG_SAVE", "Activity: $activityLevel")
        Log.d("DEBUG_SAVE", "Last calculated dose: $lastCalculatedDose")
        
        val calculatedCarbDose = unitsPerGram?.let { meal.carbs * it }
        Log.d("DEBUG_SAVE", "Calculated Carb dose: $calculatedCarbDose")
        Log.d("DEBUG_SAVE", "Exercise adj: ${calculateExerciseAdjustment(activityLevel, unitsPerGram, meal.carbs)}")

        return meal.copy(
            insulinDose = lastCalculatedDose,
            recommendedDose = lastCalculatedDose,
            glucoseLevel = glucoseValue,
            glucoseUnits = glucoseUnits,
            activityLevel = activityLevel,
            carbDose = calculatedCarbDose,
            correctionDose = calculateCorrectionDose(glucoseValue, glucoseUnits, unitsPerGram),
            exerciseAdjustment = calculateExerciseAdjustment(activityLevel, unitsPerGram, meal.carbs),
            sickAdjustment = calculateSickAdjustment(meal.carbs, unitsPerGram, correctionDose = calculateCorrectionDose(glucoseValue, glucoseUnits, unitsPerGram)),
            stressAdjustment = calculateStressAdjustment(meal.carbs, unitsPerGram, correctionDose = calculateCorrectionDose(glucoseValue, glucoseUnits, unitsPerGram)),
            wasSickMode = pm.isSickModeEnabled(ctx),
            wasStressMode = pm.isStressModeEnabled(ctx)
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