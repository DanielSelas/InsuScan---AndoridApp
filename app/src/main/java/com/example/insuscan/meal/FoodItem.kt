package com.example.insuscan.meal

data class FoodItem(
    val name: String,
    val nameHebrew: String? = null,
    val carbsGrams: Float? = null,
    val weightGrams: Float? = null,
    val confidence: Float? = null,
    val quantity: Float? = null,
    val quantityUnit: String? = null,
    // bbox from GPT (% of image) for GrabCut segmentation
    val bboxXPct: Float? = null,
    val bboxYPct: Float? = null,
    val bboxWPct: Float? = null,
    val bboxHPct: Float? = null
)
