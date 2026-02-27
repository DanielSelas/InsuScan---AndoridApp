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
            confidence = from.confidence,
            bboxXPct = from.bboxXPct,
            bboxYPct = from.bboxYPct,
            bboxWPct = from.bboxWPct,
            bboxHPct = from.bboxHPct
        )
    }


    fun mapToDto(from: FoodItem): FoodItemDto {
        return FoodItemDto(
            name = from.name,
            nameHebrew = from.nameHebrew,
            estimatedWeightGrams = from.weightGrams,
            carbsGrams = from.carbsGrams,
            confidence = from.confidence,
            bboxXPct = from.bboxXPct,
            bboxYPct = from.bboxYPct,
            bboxWPct = from.bboxWPct,
            bboxHPct = from.bboxHPct
        )
    }
}