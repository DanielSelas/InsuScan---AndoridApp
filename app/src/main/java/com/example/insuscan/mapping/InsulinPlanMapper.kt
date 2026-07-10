package com.example.insuscan.mapping

import com.example.insuscan.network.dto.InsulinPlanDto
import com.example.insuscan.profile.InsulinPlan

/**
 * Converts insulin plans between the network DTO and the app model.
 */
object InsulinPlanMapper {

    private const val DEFAULT_PLAN_NAME = "Custom"

    fun toModel(dto: InsulinPlanDto): InsulinPlan = InsulinPlan(
        id = dto.id ?: "",
        name = dto.name ?: DEFAULT_PLAN_NAME,
        isDefault = dto.isDefault,
        icr = dto.icr,
        isf = dto.isf,
        targetGlucose = dto.targetGlucose
    )

    fun toDto(plan: InsulinPlan): InsulinPlanDto = InsulinPlanDto(
        id = plan.id,
        name = plan.name,
        isDefault = plan.isDefault,
        icr = plan.icr,
        isf = plan.isf,
        targetGlucose = plan.targetGlucose
    )

    fun toModelList(dtos: List<InsulinPlanDto>?): List<InsulinPlan> =
        dtos?.map { toModel(it) } ?: emptyList()

    fun toDtoList(plans: List<InsulinPlan>): List<InsulinPlanDto> =
        plans.map { toDto(it) }
}