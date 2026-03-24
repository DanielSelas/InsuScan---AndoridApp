package com.example.insuscan.manualentry.helpers

import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import com.example.insuscan.network.InsuScanApi
import com.example.insuscan.network.dto.AiSearchRequestDto
import com.example.insuscan.network.dto.ScoredFoodResultDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed class SearchOutcome {
    data class HighConfidence(val result: ScoredFoodResultDto, val weight: Float) : SearchOutcome()
    data class MediumConfidence(val query: String, val weight: Float, val options: List<ScoredFoodResultDto>) : SearchOutcome()
    data class LowConfidence(val query: String, val weight: Float, val message: String) : SearchOutcome()
    data class Error(val query: String, val weight: Float, val message: String) : SearchOutcome()
}

class FoodSearchHelper(
    private val scope: CoroutineScope,
    private val api: InsuScanApi,
    private val layoutSearching: View,
    private val searchingIndicator: ProgressBar,
    private val searchingText: TextView,
    private val btnAddFood: Button,
    private val etFoodName: EditText,
    private val etWeight: EditText,
    private val onResult: (SearchOutcome) -> Unit
) {

    private companion object {
        const val HIGH_CONFIDENCE_THRESHOLD = 85
        const val MEDIUM_CONFIDENCE_THRESHOLD = 60
    }

    fun searchFood(foodName: String, weightGrams: Float) {
        showSearchingState(true)

        scope.launch {
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
                    val code = response.code()
                    val msg = when (code) {
                        in 500..599 -> "Server error ($code). Make sure the InsuScan server is running."
                        408 -> "Request timed out. Try again."
                        else -> "Search failed (error $code). Try a different name or enter manually."
                    }
                    onResult(SearchOutcome.Error(foodName, weightGrams, msg))
                }
            } catch (e: java.net.ConnectException) {
                onResult(SearchOutcome.Error(foodName, weightGrams,
                    "Cannot connect to server.\nMake sure the InsuScan server is running."))
            } catch (e: java.net.SocketTimeoutException) {
                onResult(SearchOutcome.Error(foodName, weightGrams,
                    "Server took too long to respond.\nCheck your connection and try again."))
            } catch (e: java.net.UnknownHostException) {
                onResult(SearchOutcome.Error(foodName, weightGrams,
                    "No internet connection.\nPlease check your network settings."))
            } catch (e: Exception) {
                onResult(SearchOutcome.Error(foodName, weightGrams,
                    "Lookup failed: ${e.message ?: "Unknown error"}"))
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
                onResult(SearchOutcome.LowConfidence(originalQuery, weightGrams,
                    "No USDA match found for \"$originalQuery\".\nTry a different spelling or enter carbs manually."))
            }

            (results.first().relevanceScore ?: 0) >= HIGH_CONFIDENCE_THRESHOLD -> {
                onResult(SearchOutcome.HighConfidence(results.first(), weightGrams))
            }

            (results.first().relevanceScore ?: 0) >= MEDIUM_CONFIDENCE_THRESHOLD -> {
                onResult(SearchOutcome.MediumConfidence(originalQuery, weightGrams, results.take(3)))
            }

            else -> {
                onResult(SearchOutcome.LowConfidence(originalQuery, weightGrams,
                    "No accurate match for \"$originalQuery\".\nEnter the nutrition info manually below."))
            }
        }
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
}
