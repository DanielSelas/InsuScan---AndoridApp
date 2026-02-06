package com.example.insuscan.manualentry

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.insuscan.R
import com.example.insuscan.meal.FoodItem
import com.example.insuscan.meal.Meal
import com.example.insuscan.meal.MealSessionManager
import com.example.insuscan.network.repository.FoodSearchRepositoryImpl
import com.example.insuscan.utils.ToastHelper
import com.example.insuscan.utils.TopBarHelper
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch
import android.app.AlertDialog
import com.google.android.material.textfield.TextInputEditText
class ManualEntryFragment : Fragment(R.layout.fragment_manual_entry) {

    // Views
    private lateinit var etFoodName: EditText
    private lateinit var btnAddFood: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: TextView
    private lateinit var totalCarbsText: TextView
    private lateinit var btnSave: Button
   private lateinit var btnRescan: Button
    private lateinit var btnAddCustom: Button

    // Adapters
    private lateinit var foodItemAdapter: FoodItemEditorAdapter

    // Data
    private val searchRepository = FoodSearchRepositoryImpl()
    private val editableItems = mutableListOf<EditableFoodItem>()

    private val ctx get() = requireContext()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTopBar(view)
        findViews(view)
        setupAdapters()
        setupSearch()
        setupListeners()
        loadExistingMeal()
        updateTotalCarbs()
    }

    private fun setupTopBar(view: View) {
        TopBarHelper.setupTopBar(
            rootView = view,
            title = "Edit Meal",
            onBack = { findNavController().popBackStack() }
        )
    }

    private fun findViews(view: View) {
        etFoodName = view.findViewById(R.id.et_food_name)
        btnAddFood = view.findViewById(R.id.btn_add_food)
        recyclerView = view.findViewById(R.id.rv_food_items)
        emptyState = view.findViewById(R.id.tv_empty_state)
        totalCarbsText = view.findViewById(R.id.tv_total_carbs)
        btnSave = view.findViewById(R.id.btn_save)
        btnRescan = view.findViewById(R.id.btn_rescan)
        btnAddCustom = view.findViewById(R.id.btn_add_custom)
    }

    private fun setupAdapters() {
        // Food items editor adapter
        foodItemAdapter = FoodItemEditorAdapter(
            onItemChanged = { updateTotalCarbs() },
            onItemRemoved = { item ->
                editableItems.remove(item)
                foodItemAdapter.removeItem(item)
                updateEmptyState()
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(ctx)
        recyclerView.adapter = foodItemAdapter
    }

    private fun setupSearch() {
        // Direct add flow: user clicks button to add food
        btnAddFood.setOnClickListener {
            val foodName = etFoodName.text.toString().trim()
            if (foodName.isNotEmpty()) {
                addFoodItemWithLookup(foodName)
                etFoodName.text?.clear()
            } else {
                ToastHelper.showShort(ctx, "אנא הזן שם מזון")
            }
        }

        // Also add on Enter key
        etFoodName.setOnEditorActionListener { _, _, _ ->
            btnAddFood.performClick()
            true
        }
    }

    private fun addFoodItemWithLookup(foodName: String) {
        // 1. Create item with loading state
        val newItem = EditableFoodItem(
            name = foodName,
            weightGrams = 100f,
            carbsPer100g = null,  // Will be filled after USDA response
            usdaFdcId = null,
            isLoading = true
        )

        // 2. Add to list immediately
        editableItems.add(newItem)
        foodItemAdapter.addItem(newItem)
        updateEmptyState()

        // 3. Fetch USDA data in background
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = searchRepository.searchFood(foodName)

                result.onSuccess { results ->
                    if (results.isNotEmpty()) {
                        // Use first (best) result
                        val bestMatch = results.first()

                        // Update item with USDA data
                        newItem.carbsPer100g = bestMatch.carbsPer100g
                        newItem.usdaFdcId = bestMatch.fdcId
                        newItem.isLoading = false

                        // Use serving size if available
                        if (bestMatch.servingSize != null && bestMatch.servingSize > 0) {
                            newItem.weightGrams = bestMatch.servingSize
                        }

                        foodItemAdapter.notifyItemChanged(
                            foodItemAdapter.items.indexOf(newItem)
                        )
                        updateTotalCarbs()
                    } else {
                        // No results found
                        newItem.isLoading = false
                        newItem.carbsPer100g = 0f
                        foodItemAdapter.notifyItemChanged(
                            foodItemAdapter.items.indexOf(newItem)
                        )
                        ToastHelper.showShort(ctx, "לא נמצא מידע עבור: $foodName")
                    }
                }

                result.onFailure {
                    // Network error
                    newItem.isLoading = false
                    newItem.carbsPer100g = 0f
                    foodItemAdapter.notifyItemChanged(
                        foodItemAdapter.items.indexOf(newItem)
                    )
                    ToastHelper.showShort(ctx, "שגיאה בחיפוש נתונים")
                }

            } catch (e: Exception) {
                newItem.isLoading = false
                newItem.carbsPer100g = 0f
                foodItemAdapter.notifyItemChanged(
                    foodItemAdapter.items.indexOf(newItem)
                )
            }
        }
    }

    private fun setupListeners() {
        btnSave.setOnClickListener { saveMeal() }

        btnRescan.setOnClickListener {
            // Go back to scan screen
            val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav)
            bottomNav.selectedItemId = R.id.scanFragment
        }

        btnAddCustom.setOnClickListener {
            showAddCustomFoodDialog()
        }
    }

    private fun loadExistingMeal() {
        val currentMeal = MealSessionManager.currentMeal ?: return

        // Convert existing food items to editable items
        currentMeal.foodItems?.forEach { item ->
            val editable = EditableFoodItem(
                name = item.name,
                weightGrams = item.weightGrams ?: 100f,
                carbsPer100g = calculateCarbsPer100g(item),
                usdaFdcId = null,
                isLoading = false
            )
            editableItems.add(editable)
        }

        // If no food items but has title/carbs, create single item
        if (editableItems.isEmpty() && currentMeal.carbs > 0) {
            val editable = EditableFoodItem(
                name = currentMeal.title,
                weightGrams = 100f,
                carbsPer100g = currentMeal.carbs,
                usdaFdcId = null,
                isLoading = false
            )
            editableItems.add(editable)
        }

        foodItemAdapter.setItems(editableItems)
        updateEmptyState()
    }

    private fun calculateCarbsPer100g(item: FoodItem): Float {
        val weight = item.weightGrams ?: 100f
        val carbs = item.carbsGrams ?: 0f
        return if (weight > 0) (carbs * 100f) / weight else 0f
    }

    private fun updateTotalCarbs() {
        val total = foodItemAdapter.getTotalCarbs()
        totalCarbsText.text = "${total.toInt()}g"
    }

    private fun updateEmptyState() {
        if (editableItems.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun saveMeal() {
        if (editableItems.isEmpty()) {
            ToastHelper.showShort(ctx, "Add at least one food item")
            return
        }

        // Convert editable items to FoodItem list
        val foodItems = editableItems.map { editable ->
            FoodItem(
                name = editable.name,
                nameHebrew = null,
                carbsGrams = editable.totalCarbs,
                weightGrams = editable.weightGrams,
                confidence = 1.0f  // manual entry = 100% confidence
            )
        }

        val totalCarbs = foodItemAdapter.getTotalCarbs()

        // Build title from food names
        val title = if (foodItems.size == 1) {
            foodItems.first().name
        } else {
            "${foodItems.first().name} + ${foodItems.size - 1} more"
        }

        // Get existing meal data to preserve analysis info
        val existingMeal = MealSessionManager.currentMeal

        val updatedMeal = Meal(
            title = title,
            carbs = totalCarbs,
            foodItems = foodItems,
            // Preserve existing analysis data
            portionWeightGrams = existingMeal?.portionWeightGrams,
            plateDiameterCm = existingMeal?.plateDiameterCm,
            plateDepthCm = existingMeal?.plateDepthCm,
            analysisConfidence = existingMeal?.analysisConfidence,
            referenceObjectDetected = existingMeal?.referenceObjectDetected,
            // Keep the image path when editing
            imagePath = existingMeal?.imagePath
        )

        MealSessionManager.setCurrentMeal(updatedMeal)

        ToastHelper.showShort(ctx, "Meal updated: ${totalCarbs.toInt()}g carbs")
        findNavController().popBackStack()
    }

    private fun showAddCustomFoodDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_custom_food, null)

        val etName = dialogView.findViewById<TextInputEditText>(R.id.et_food_name)
        val etWeight = dialogView.findViewById<TextInputEditText>(R.id.et_weight)
        val etCarbsPer100g = dialogView.findViewById<TextInputEditText>(R.id.et_carbs_per_100g)
        val tvCarbsPreview = dialogView.findViewById<TextView>(R.id.tv_carbs_preview)

        // Update carbs preview when values change
        val updatePreview = {
            val weight = etWeight.text.toString().toFloatOrNull() ?: 0f
            val carbsPer100g = etCarbsPer100g.text.toString().toFloatOrNull() ?: 0f
            val totalCarbs = (weight * carbsPer100g) / 100f
            tvCarbsPreview.text = "Total carbs: ${totalCarbs.toInt()}g"
        }

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { updatePreview() }
        }

        etWeight.addTextChangedListener(textWatcher)
        etCarbsPer100g.addTextChangedListener(textWatcher)

        AlertDialog.Builder(ctx)
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = etName.text.toString().trim()
                val weight = etWeight.text.toString().toFloatOrNull()
                val carbsPer100g = etCarbsPer100g.text.toString().toFloatOrNull()

                // Validate input
                if (name.isEmpty()) {
                    ToastHelper.showShort(ctx, "Please enter food name")
                    return@setPositiveButton
                }
                if (weight == null || weight <= 0) {
                    ToastHelper.showShort(ctx, "Please enter valid weight")
                    return@setPositiveButton
                }
                if (carbsPer100g == null || carbsPer100g < 0) {
                    ToastHelper.showShort(ctx, "Please enter carbs per 100g")
                    return@setPositiveButton
                }

                // Add custom item
                addCustomFoodItem(name, weight, carbsPer100g)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addCustomFoodItem(name: String, weight: Float, carbsPer100g: Float) {
        val newItem = EditableFoodItem(
            name = name,
            weightGrams = weight,
            carbsPer100g = carbsPer100g,
            usdaFdcId = null,  // no USDA ID for custom items
            isLoading = false  // Custom items don't need loading
        )

        editableItems.add(newItem)
        foodItemAdapter.addItem(newItem)
        updateEmptyState()

        ToastHelper.showShort(ctx, "Added: $name")
    }
}