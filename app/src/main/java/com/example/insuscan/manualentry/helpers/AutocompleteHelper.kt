package com.example.insuscan.manualentry.helpers

import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import androidx.recyclerview.widget.RecyclerView
import com.example.insuscan.manualentry.AutocompleteAdapter
import com.example.insuscan.network.InsuScanApi
import com.example.insuscan.network.dto.AiSearchRequestDto
import com.example.insuscan.network.dto.ScoredFoodResultDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AutocompleteHelper(
    private val scope: CoroutineScope,
    private val etFoodName: EditText,
    private val etWeight: EditText,
    private val rvAutocomplete: RecyclerView,
    private val autocompleteAdapter: AutocompleteAdapter,
    private val api: InsuScanApi,
    private val onItemSelected: (ScoredFoodResultDto, Float) -> Unit,
    private val onHideKeyboard: () -> Unit
) {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingRunnable: Runnable? = null

    private companion object {
        const val DEBOUNCE_DELAY = 300L
        const val MIN_QUERY_LENGTH = 2
    }

    fun setup() {
        etFoodName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""

                pendingRunnable?.let { handler.removeCallbacks(it) }

                if (query.length < MIN_QUERY_LENGTH) {
                    hide()
                    return
                }

                pendingRunnable = Runnable { performSearch(query) }
                handler.postDelayed(pendingRunnable!!, DEBOUNCE_DELAY)
            }
        })

        etFoodName.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) hide()
        }
    }

    fun cleanup() {
        pendingRunnable?.let { handler.removeCallbacks(it) }
    }

    fun selectItem(item: ScoredFoodResultDto) {
        hide()
        onHideKeyboard()

        val weightGrams = etWeight.text.toString().toFloatOrNull() ?: 100f
        onItemSelected(item, weightGrams)

        etFoodName.text?.clear()
        etWeight.setText("100")
    }

    private fun performSearch(query: String) {
        scope.launch {
            try {
                val request = AiSearchRequestDto(
                    query = query,
                    userLanguage = "en",
                    limit = 5
                )

                val response = api.aiSearchFood(request)

                if (response.isSuccessful) {
                    val results = response.body()?.results ?: emptyList()
                    if (results.isNotEmpty()) show(results) else hide()
                }
            } catch (e: Exception) {
                hide()
            }
        }
    }

    private fun show(results: List<ScoredFoodResultDto>) {
        autocompleteAdapter.setItems(results)
        rvAutocomplete.visibility = View.VISIBLE
    }

    private fun hide() {
        autocompleteAdapter.clear()
        rvAutocomplete.visibility = View.GONE
    }
}
