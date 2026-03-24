package com.example.insuscan.manualentry

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.insuscan.R
import com.example.insuscan.manualentry.helpers.AutocompleteHelper
import com.example.insuscan.manualentry.helpers.FoodDialogHelper
import com.example.insuscan.manualentry.helpers.FoodSearchHelper
import com.example.insuscan.manualentry.helpers.MealPersistenceHelper
import com.example.insuscan.manualentry.helpers.SearchOutcome
import com.example.insuscan.meal.MealSessionManager
import com.example.insuscan.network.RetrofitClient
import com.example.insuscan.network.dto.ScoredFoodResultDto
import com.example.insuscan.utils.ToastHelper
import com.example.insuscan.utils.TopBarHelper
import com.google.android.material.bottomnavigation.BottomNavigationView

class ManualEntryFragment : Fragment(R.layout.fragment_manual_entry) {

    private lateinit var etFoodName: EditText
    private lateinit var etWeight: EditText
    private lateinit var btnAddFood: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: TextView
    private lateinit var totalCarbsText: TextView

    private lateinit var foodItemAdapter: FoodItemEditorAdapter
    private lateinit var autocompleteHelper: AutocompleteHelper
    private lateinit var foodSearchHelper: FoodSearchHelper
    private lateinit var foodDialogHelper: FoodDialogHelper
    private val mealPersistenceHelper = MealPersistenceHelper()

    private val editableItems = mutableListOf<EditableFoodItem>()
    private val ctx get() = requireContext()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        TopBarHelper.setupTopBar(view, "Edit Meal") { findNavController().popBackStack() }
        findViews(view)
        setupAdapters()
        setupHelpers(view)
        setupListeners()
        loadExistingMeal()
    }

    private fun findViews(view: View) {
        etFoodName = view.findViewById(R.id.et_food_name)
        etWeight = view.findViewById(R.id.et_weight)
        btnAddFood = view.findViewById(R.id.btn_add_food)
        recyclerView = view.findViewById(R.id.rv_food_items)
        emptyState = view.findViewById(R.id.tv_empty_state)
        totalCarbsText = view.findViewById(R.id.tv_total_carbs)
    }

    private fun setupAdapters() {
        foodItemAdapter = FoodItemEditorAdapter(
            onItemChanged = { updateTotalCarbs() },
            onItemRemoved = { item ->
                editableItems.remove(item)
                foodItemAdapter.removeItem(item)
                updateEmptyState()
            },
            onItemEdit = { item ->
                foodDialogHelper.showEditItemDialog(item) {
                    val index = foodItemAdapter.items.indexOf(item)
                    if (index >= 0) foodItemAdapter.notifyItemChanged(index)
                    updateTotalCarbs()
                }
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(ctx)
        recyclerView.adapter = foodItemAdapter
    }

    private fun setupHelpers(view: View) {
        val scope = viewLifecycleOwner.lifecycleScope
        val api = RetrofitClient.api

        val autocompleteAdapter = AutocompleteAdapter { autocompleteHelper.selectItem(it) }
        val rvAutocomplete = view.findViewById<RecyclerView>(R.id.rv_autocomplete)
        rvAutocomplete.layoutManager = LinearLayoutManager(ctx)
        rvAutocomplete.adapter = autocompleteAdapter

        autocompleteHelper = AutocompleteHelper(
            scope, etFoodName, etWeight, rvAutocomplete, autocompleteAdapter, api,
            onItemSelected = { result, weight -> addItemFromResult(result, weight) },
            onHideKeyboard = ::hideKeyboard
        )
        autocompleteHelper.setup()

        foodDialogHelper = FoodDialogHelper(
            context = ctx,
            layoutInflater = layoutInflater,
            onItemAdded = ::addCustomItem,
            onItemUpdated = { }
        )

        foodSearchHelper = FoodSearchHelper(
            scope, api,
            layoutSearching = view.findViewById(R.id.layout_searching),
            searchingIndicator = view.findViewById(R.id.searching_indicator),
            searchingText = view.findViewById(R.id.tv_searching),
            btnAddFood = btnAddFood, etFoodName = etFoodName, etWeight = etWeight,
            onResult = ::handleSearchOutcome
        )
    }

    private fun handleSearchOutcome(outcome: SearchOutcome) {
        when (outcome) {
            is SearchOutcome.HighConfidence -> addItemFromResult(outcome.result, outcome.weight)
            is SearchOutcome.MediumConfidence -> foodDialogHelper.showSelectFoodDialog(
                outcome.query, outcome.weight, outcome.options
            ) { result, weight -> addItemFromResult(result, weight) }
            is SearchOutcome.LowConfidence ->
                foodDialogHelper.showManualEntryDialog(outcome.query, outcome.weight, outcome.message)
            is SearchOutcome.Error ->
                foodDialogHelper.showManualEntryDialog(outcome.query, outcome.weight, outcome.message)
        }
    }

    private fun setupListeners() {
        btnAddFood.setOnClickListener {
            val foodName = etFoodName.text.toString().trim()
            val weightGrams = etWeight.text.toString().toFloatOrNull() ?: 100f
            if (foodName.isNotEmpty()) {
                hideKeyboard()
                foodSearchHelper.searchFood(foodName, weightGrams)
            } else {
                ToastHelper.showShort(ctx, "Please enter food name")
            }
        }

        etFoodName.setOnEditorActionListener { _, _, _ ->
            btnAddFood.performClick()
            true
        }

        view?.findViewById<Button>(R.id.btn_save)?.setOnClickListener { saveMeal() }
        view?.findViewById<Button>(R.id.btn_rescan)?.setOnClickListener {
            requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav)
                .selectedItemId = R.id.scanFragment
        }
    }

    private fun addItemFromResult(result: ScoredFoodResultDto, weightGrams: Float) {
        val name = result.displayName ?: result.foodName ?: "Unknown"
        addItem(EditableFoodItem(
            name = name, weightGrams = weightGrams,
            carbsPer100g = result.carbsPer100g, usdaFdcId = result.fdcId, isLoading = false
        ))
        ToastHelper.showShort(ctx, "✅ Added: $name")
    }

    private fun addCustomItem(name: String, weight: Float, carbsPer100g: Float) {
        addItem(EditableFoodItem(
            name = name, weightGrams = weight,
            carbsPer100g = carbsPer100g, usdaFdcId = null, isLoading = false
        ))
        ToastHelper.showShort(ctx, "✅ Added: $name")
    }

    private fun addItem(item: EditableFoodItem) {
        editableItems.add(0, item)
        foodItemAdapter.insertItemAt(0, item)
        recyclerView.scrollToPosition(0)
        updateEmptyState()
        updateTotalCarbs()
    }

    private fun loadExistingMeal() {
        editableItems.addAll(mealPersistenceHelper.loadExistingItems())
        foodItemAdapter.setItems(editableItems)
        updateEmptyState()
        updateTotalCarbs()
    }

    private fun saveMeal() {
        if (editableItems.isEmpty()) {
            ToastHelper.showShort(ctx, "Add at least one food item")
            return
        }
        val totalCarbs = foodItemAdapter.getTotalCarbs()
        val updatedMeal = mealPersistenceHelper.buildUpdatedMeal(editableItems, totalCarbs)
        MealSessionManager.setCurrentMeal(updatedMeal)
        ToastHelper.showShort(ctx, "Meal updated: ${totalCarbs.toInt()}g carbs")
        findNavController().popBackStack()
    }

    private fun updateTotalCarbs() {
        totalCarbsText.text = "${foodItemAdapter.getTotalCarbs().toInt()}g"
    }

    private fun updateEmptyState() {
        val empty = editableItems.isEmpty()
        emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun hideKeyboard() {
        val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etFoodName.windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        autocompleteHelper.cleanup()
    }
}
