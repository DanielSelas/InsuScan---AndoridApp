package com.example.insuscan.meal

// Represents a scanned meal with nutritional and analysis data
data class Meal(
    val title: String,
    val carbs: Float,
    val insulinDose: Float? = null,  // this will become actualDose
    val timestamp: Long = System.currentTimeMillis(),

    // portion analysis data (save but don't display)
    val portionWeightGrams: Float? = null,
    val portionVolumeCm3: Float? = null,
    val plateDiameterCm: Float? = null,
    val plateDepthCm: Float? = null,
    val analysisConfidence: Float? = null,
    val referenceObjectDetected: Boolean? = null,
    val referenceObjectType: String? = null,

    // food items from server
    val foodItems: List<FoodItem>? = null,

    // server IDs for sync
    val serverId: String? = null,

    // local image path
    val imagePath: String? = null,

    // context flags
    val wasSickMode: Boolean = false,
    val wasStressMode: Boolean = false,

    // glucose and activity data
    val glucoseLevel: Int? = null,
    val glucoseUnits: String? = null,  // already exists - keep it
    val activityLevel: String? = null,  // "normal", "light", "intense"

    // calculation breakdown
    val carbDose: Float? = null,
    val correctionDose: Float? = null,
    val exerciseAdjustment: Float? = null,
    val sickAdjustment: Float? = null,
    val stressAdjustment: Float? = null,
    val activeInsulin: Float? = null, // IOB

    // added: separate recommended from actual
    val recommendedDose: Float? = null,

    // medical settings used at calculation time (for history display)
    val savedIcr: Float? = null,
    val savedIsf: Float? = null,
    val savedTargetGlucose: Int? = null,

    // adjustment percentages at calculation time (for history display)
    val savedSickPct: Int = 0,
    val savedStressPct: Int = 0,
    val savedExercisePct: Int = 0,

    val profileComplete: Boolean = false,
    val missingProfileFields: List<String> = emptyList(),
    val insulinMessage: String? = null,
)

data class FoodItem(
    val name: String,
    val nameHebrew: String? = null,
    val carbsGrams: Float? = null,
    val weightGrams: Float? = null,
    val confidence: Float? = null,
    val quantity: Float? = null,
    val quantityUnit: String? = null
)