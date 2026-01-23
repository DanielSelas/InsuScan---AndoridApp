package com.example.insuscan.mapping

import com.example.insuscan.meal.FoodItem
import com.example.insuscan.network.dto.FoodItemDto

object FoodItemDtoMapper : Mapper<FoodItemDto, FoodItem> {

    override fun map(from: FoodItemDto): FoodItem {
        return FoodItem(
            name = from.name,
            nameHebrew = from.nameHebrew,
            carbsGrams = from.carbsGrams,
            weightGrams = from.estimatedWeightGrams,
            confidence = from.confidence
        )
    }
}