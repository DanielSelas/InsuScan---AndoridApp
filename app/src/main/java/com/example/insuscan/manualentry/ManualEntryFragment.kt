package com.example.insuscan.manualentry

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
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
import com.example.insuscan.network.RetrofitClient
import com.example.insuscan.network.dto.AiSearchRequestDto
import com.example.insuscan.network.dto.ScoredFoodResultDto
import com.example.insuscan.utils.ToastHelper
import com.example.insuscan.utils.TopBarHelper
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch
import android.app.AlertDialog
import com.google.android.material.textfield.TextInputEditText

class ManualEntryFragment : Fragment(R.layout.fragment_manual_entry) {

    // Views
    private lateinit var etFoodName: EditText
    private lateinit var etWeight: EditText
    private lateinit var btnAddFood: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: TextView
    private lateinit var totalCarbsText: TextView
    private lateinit var btnSave: Button
    private lateinit var btnRescan: Button
    private lateinit var layoutSearching: View
    private lateinit var searchingIndicator: ProgressBar
    private lateinit var searchingText: TextView
    private lateinit var rvAutocomplete: RecyclerView

    // Adapters
    private lateinit var foodItemAdapter: FoodItemEditorAdapter
    private lateinit var autocompleteAdapter: AutocompleteAdapter

    // Data
    private val api = RetrofitClient.api
    private val editableItems = mutableListOf<EditableFoodItem>()

    // Autocomplete debounce
    private val autocompleteHandler = Handler(Looper.getMainLooper())
    private var autocompleteRunnable: Runnable? = null
    private val AUTOCOMPLETE_DELAY = 300L
    private val MIN_QUERY_LENGTH = 2

    // Confidence thresholds
    private val HIGH_CONFIDENCE_THRESHOLD = 85
    private val MEDIUM_CONFIDENCE_THRESHOLD = 60

    private val ctx get() = requireContext()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTopBar(view)
        findViews(view)
        setupAdapters()
        setupAutocomplete()
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
        etWeight = view.findViewById(R.id.et_weight)
        btnAddFood = view.findViewById(R.id.btn_add_food)
        recyclerView = view.findViewById(R.id.rv_food_items)
        emptyState = view.findViewById(R.id.tv_empty_state)
        totalCarbsText = view.findViewById(R.id.tv_total_carbs)
        btnSave = view.findViewById(R.id.btn_save)
        btnRescan = view.findViewById(R.id.btn_rescan)
        layoutSearching = view.findViewById(R.id.layout_searching)
        searchingIndicator = view.findViewById(R.id.searching_indicator)
        searchingText = view.findViewById(R.id.tv_searching)
        rvAutocomplete = view.findViewById(R.id.rv_autocomplete)
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
                showEditItemDialog(item)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(ctx)
        recyclerView.adapter = foodItemAdapter

        // Autocomplete adapter
        autocompleteAdapter = AutocompleteAdapter { selectedItem ->
            selectAutocompleteItem(selectedItem)
        }
        rvAutocomplete.layoutManager = LinearLayoutManager(ctx)
        rvAutocomplete.adapter = autocompleteAdapter
    }

    private fun setupAutocomplete() {
        etFoodName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                
                // Cancel previous search
                autocompleteRunnable?.let { autocompleteHandler.removeCallbacks(it) }
                
                if (query.length < MIN_QUERY_LENGTH) {
                    hideAutocomplete()
                    return
                }
                
                // Debounced search
                autocompleteRunnable = Runnable {
                    performAutocompleteSearch(query)
                }
                autocompleteHandler.postDelayed(autocompleteRunnable!!, AUTOCOMPLETE_DELAY)
            }
        })

        // Hide autocomplete when focus lost
        etFoodName.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                hideAutocomplete()
            }
        }
    }

    private fun performAutocompleteSearch(query: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val request = AiSearchRequestDto(
                    query = query,
                    userLanguage = "en",
                    limit = 5
                )

                val response = api.aiSearchFood(request)
                
                if (response.isSuccessful) {
                    val results = response.body()?.results ?: emptyList()
                    if (results.isNotEmpty()) {
                        showAutocomplete(results)
                    } else {
                        hideAutocomplete()
                    }
                }
            } catch (e: Exception) {
                // Silently fail for autocomplete
                hideAutocomplete()
            }
        }
    }

    private fun showAutocomplete(results: List<ScoredFoodResultDto>) {
        autocompleteAdapter.setItems(results)
        rvAutocomplete.visibility = View.VISIBLE
    }

    private fun hideAutocomplete() {
        autocompleteAdapter.clear()
        rvAutocomplete.visibility = View.GONE
    }

    private fun selectAutocompleteItem(item: ScoredFoodResultDto) {
        hideAutocomplete()
        hideKeyboard()
        
        val weightGrams = etWeight.text.toString().toFloatOrNull() ?: 100f
        addItemFromResult(item, weightGrams)
        
        // Use displayName for toast
        val displayName = item.displayName ?: item.foodName ?: "Unknown"
        ToastHelper.showShort(ctx, "✅ Added: $displayName")
        
        etFoodName.text?.clear()
        etWeight.setText("100")
    }

    private fun setupSearch() {
        btnAddFood.setOnClickListener {
            val foodName = etFoodName.text.toString().trim()
            val weightGrams = etWeight.text.toString().toFloatOrNull() ?: 100f

            if (foodName.isNotEmpty()) {
                hideAutocomplete()
                addFoodWithSmartLookup(foodName, weightGrams)
            } else {
                ToastHelper.showShort(ctx, "Please enter food name")
            }
        }

        etFoodName.setOnEditorActionListener { _, _, _ ->
            btnAddFood.performClick()
            true
        }
    }

    private fun hideKeyboard() {
        val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etFoodName.windowToken, 0)
    }

    private fun showSearchingState(show: Boolean) {
        if (show) {
            layoutSearching.visibility = View.VISIBLE
            searchingIndicator.visibility = View.VISIBLE
            searchingText.visibility = View.VISIBLE
            btnAddFood.isEnabled = false
            btnAddFood.text = "..."
            etFoodName.isEnabled = false
            etWeight.isEnabled = false
        } else {
            layoutSearching.visibility = View.GONE
            searchingIndicator.visibility = View.GONE
            searchingText.visibility = View.GONE
            btnAddFood.isEnabled = true
            btnAddFood.text = "Add"
            etFoodName.isEnabled = true
            etWeight.isEnabled = true
        }
    }

    private fun addFoodWithSmartLookup(foodName: String, weightGrams: Float) {
        hideKeyboard()
        showSearchingState(true)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val request = AiSearchRequestDto(
                    query = foodName,
                    userLanguage = "en",
                    limit = 5
                )

                val response = api.aiSearchFood(request)
                
                if (response.isSuccessful) {
                    val results = response.body()?.results ?: emptyList()
                    handleSearchResults(foodName, weightGrams, results)
                } else {
                    showManualEntryDialog(foodName, weightGrams, "Search error")
                }

            } catch (e: Exception) {
                showManualEntryDialog(foodName, weightGrams, "Network error")
            } finally {
                showSearchingState(false)
                etFoodName.text?.clear()
                etWeight.setText("100")
            }
        }
    }

    private fun handleSearchResults(
        originalQuery: String,
        weightGrams: Float,
        results: List<ScoredFoodResultDto>
    ) {
        when {
            results.isEmpty() -> {
                showManualEntryDialog(originalQuery, weightGrams, "No results for: $originalQuery")
            }
            
            (results.first().relevanceScore ?: 0) >= HIGH_CONFIDENCE_THRESHOLD -> {
                val bestMatch = results.first()
                addItemFromResult(bestMatch, weightGrams)
                val displayName = bestMatch.displayName ?: bestMatch.foodName ?: "Unknown"
                ToastHelper.showShort(ctx, "✅ Added: $displayName")
            }
            
            (results.first().relevanceScore ?: 0) >= MEDIUM_CONFIDENCE_THRESHOLD -> {
                showSelectFoodDialog(originalQuery, weightGrams, results.take(3))
            }
            
            else -> {
                showManualEntryDialog(originalQuery, weightGrams, "No good match for: $originalQuery")
            }
        }
    }

    private fun showSelectFoodDialog(
        originalQuery: String,
        weightGrams: Float,
        options: List<ScoredFoodResultDto>
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_select_food, null)
        
        val tvSearchQuery = dialogView.findViewById<TextView>(R.id.tv_search_query)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radio_group_options)
        val option1 = dialogView.findViewById<RadioButton>(R.id.option_1)
        val option2 = dialogView.findViewById<RadioButton>(R.id.option_2)
        val option3 = dialogView.findViewById<RadioButton>(R.id.option_3)
        val btnManual = dialogView.findViewById<Button>(R.id.btn_manual_entry)

        tvSearchQuery.text = "Results for: $originalQuery"

        val radioButtons = listOf(option1, option2, option3)
        options.forEachIndexed { index, result ->
            if (index < radioButtons.size) {
                radioButtons[index].visibility = View.VISIBLE
                // Use displayName in dialog
                val displayName = result.displayName ?: result.foodName ?: "Unknown"
                radioButtons[index].text = "$displayName (${result.carbsPer100g?.toInt() ?: 0}g/100g)"
                radioButtons[index].tag = result
            }
        }
        
        for (i in options.size until radioButtons.size) {
            radioButtons[i].visibility = View.GONE
        }

        val dialog = AlertDialog.Builder(ctx)
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val selectedId = radioGroup.checkedRadioButtonId
                if (selectedId != -1) {
                    val selectedButton = dialogView.findViewById<RadioButton>(selectedId)
                    val selectedResult = selectedButton.tag as? ScoredFoodResultDto
                    if (selectedResult != null) {
                        addItemFromResult(selectedResult, weightGrams)
                        val displayName = selectedResult.displayName ?: selectedResult.foodName ?: "Unknown"
                        ToastHelper.showShort(ctx, "✅ Added: $displayName")
                    }
                } else {
                    ToastHelper.showShort(ctx, "Please select an item")
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        btnManual.setOnClickListener {
            dialog.dismiss()
            showManualEntryDialog(originalQuery, weightGrams, null)
        }

        dialog.show()
    }

    private fun showManualEntryDialog(foodName: String, weightGrams: Float, message: String?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_custom_food, null)

        val etName = dialogView.findViewById<TextInputEditText>(R.id.et_food_name)
        val etDialogWeight = dialogView.findViewById<TextInputEditText>(R.id.et_weight)
        val etCarbsPer100g = dialogView.findViewById<TextInputEditText>(R.id.et_carbs_per_100g)
        val tvCarbsPreview = dialogView.findViewById<TextView>(R.id.tv_carbs_preview)

        etName.setText(foodName)
        etDialogWeight.setText(weightGrams.toInt().toString())

        val updatePreview = {
            val weight = etDialogWeight.text.toString().toFloatOrNull() ?: 0f
            val carbsPer100g = etCarbsPer100g.text.toString().toFloatOrNull() ?: 0f
            val totalCarbs = (weight * carbsPer100g) / 100f
            tvCarbsPreview.text = "Total carbs: ${totalCarbs.toInt()}g"
        }

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { updatePreview() }
        }

        etDialogWeight.addTextChangedListener(textWatcher)
        etCarbsPer100g.addTextChangedListener(textWatcher)

        val builder = AlertDialog.Builder(ctx)
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = etName.text.toString().trim()
                val weight = etDialogWeight.text.toString().toFloatOrNull()
                val carbsPer100gVal = etCarbsPer100g.text.toString().toFloatOrNull()

                if (name.isEmpty()) {
                    ToastHelper.showShort(ctx, "Enter food name")
                    return@setPositiveButton
                }
                if (weight == null || weight <= 0) {
                    ToastHelper.showShort(ctx, "Enter valid weight")
                    return@setPositiveButton
                }
                if (carbsPer100gVal == null || carbsPer100gVal < 0) {
                    ToastHelper.showShort(ctx, "Enter carbs per 100g")
                    return@setPositiveButton
                }

                addCustomFoodItem(name, weight, carbsPer100gVal)
            }
            .setNegativeButton("Cancel", null)

        if (message != null) {
            builder.setTitle(message)
        }

        builder.show()
    }

    private fun addItemFromResult(result: ScoredFoodResultDto, weightGrams: Float) {
        // Use displayName for the item name
        val name = result.displayName ?: result.foodName ?: "Unknown"
        
        val newItem = EditableFoodItem(
            name = name,
            weightGrams = weightGrams,
            carbsPer100g = result.carbsPer100g,
            usdaFdcId = result.fdcId,
            isLoading = false
        )

        editableItems.add(0, newItem)
        foodItemAdapter.insertItemAt(0, newItem)
        recyclerView.scrollToPosition(0)
        updateEmptyState()
        updateTotalCarbs()
    }

    private fun addCustomFoodItem(name: String, weight: Float, carbsPer100g: Float) {
        val newItem = EditableFoodItem(
            name = name,
            weightGrams = weight,
            carbsPer100g = carbsPer100g,
            usdaFdcId = null,
            isLoading = false
        )

        editableItems.add(0, newItem)
        foodItemAdapter.insertItemAt(0, newItem)
        recyclerView.scrollToPosition(0)
        updateEmptyState()
        updateTotalCarbs()

        ToastHelper.showShort(ctx, "✅ Added: $name")
        ToastHelper.showShort(ctx, "✅ Added: $name")
    }

    private fun showEditItemDialog(item: EditableFoodItem) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_custom_food, null)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.et_food_name)
        val etDialogWeight = dialogView.findViewById<TextInputEditText>(R.id.et_weight)
        val etCarbsPer100g = dialogView.findViewById<TextInputEditText>(R.id.et_carbs_per_100g)
        val tvCarbsPreview = dialogView.findViewById<TextView>(R.id.tv_carbs_preview)

        etName.setText(item.name)
        etDialogWeight.setText(item.weightGrams.toInt().toString())
        etCarbsPer100g.setText(item.carbsPer100g?.toInt()?.toString() ?: "0")

        val updatePreview = {
            val weight = etDialogWeight.text.toString().toFloatOrNull() ?: 0f
            val carbsPer100g = etCarbsPer100g.text.toString().toFloatOrNull() ?: 0f
            val totalCarbs = (weight * carbsPer100g) / 100f
            tvCarbsPreview.text = "Total carbs: ${totalCarbs.toInt()}g"
        }

        // Initial preview
        updatePreview()

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { updatePreview() }
        }

        etDialogWeight.addTextChangedListener(textWatcher)
        etCarbsPer100g.addTextChangedListener(textWatcher)

        AlertDialog.Builder(ctx)
            .setTitle("Edit Item")
            .setView(dialogView)
            .setPositiveButton("Update") { _, _ ->
                val name = etName.text.toString().trim()
                val weight = etDialogWeight.text.toString().toFloatOrNull()
                val carbsPer100gVal = etCarbsPer100g.text.toString().toFloatOrNull()

                if (name.isNotEmpty() && weight != null && carbsPer100gVal != null) {
                    // Update item
                    item.name = name
                    item.weightGrams = weight
                    item.carbsPer100g = carbsPer100gVal
                    
                    // Notify adapter
                    val index = foodItemAdapter.items.indexOf(item)
                    if (index >= 0) {
                        foodItemAdapter.notifyItemChanged(index)
                    }
                    updateTotalCarbs()
                    ToastHelper.showShort(ctx, "Updated")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupListeners() {
        btnSave.setOnClickListener { saveMeal() }

        btnRescan.setOnClickListener {
            val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav)
            bottomNav.selectedItemId = R.id.scanFragment
        }
    }

    private fun loadExistingMeal() {
        val currentMeal = MealSessionManager.currentMeal ?: return

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

        val foodItems = editableItems.map { editable ->
            FoodItem(
                name = editable.name,
                nameHebrew = null,
                carbsGrams = editable.totalCarbs,
                weightGrams = editable.weightGrams,
                confidence = 1.0f
            )
        }

        val totalCarbs = foodItemAdapter.getTotalCarbs()

        val title = if (foodItems.size == 1) {
            foodItems.first().name
        } else {
            "${foodItems.first().name} + ${foodItems.size - 1} more"
        }

        val existingMeal = MealSessionManager.currentMeal

        val updatedMeal = Meal(
            title = title,
            carbs = totalCarbs,
            foodItems = foodItems,
            portionWeightGrams = existingMeal?.portionWeightGrams,
            plateDiameterCm = existingMeal?.plateDiameterCm,
            plateDepthCm = existingMeal?.plateDepthCm,
            analysisConfidence = existingMeal?.analysisConfidence,
            referenceObjectDetected = existingMeal?.referenceObjectDetected,
            imagePath = existingMeal?.imagePath
        )

        MealSessionManager.setCurrentMeal(updatedMeal)

        ToastHelper.showShort(ctx, "Meal updated: ${totalCarbs.toInt()}g carbs")
        findNavController().popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up handler callbacks
        autocompleteRunnable?.let { autocompleteHandler.removeCallbacks(it) }
    }
}