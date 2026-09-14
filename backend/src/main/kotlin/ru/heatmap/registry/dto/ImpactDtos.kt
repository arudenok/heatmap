package ru.heatmap.registry.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ImpactRowResponse(
    val id: Long,
    val label: String,
    val value: String,
    val colorVariant: String,
    val isFooter: Boolean
)

data class ImpactBlockResponse(
    val code: String,
    val icon: String,
    val title: String,
    val badgeText: String,
    val badgeStatus: String,
    val rows: List<ImpactRowResponse>
)

data class UpdateImpactRowRequest(
    @field:NotBlank
    @field:Size(max = 64)
    val value: String
)
