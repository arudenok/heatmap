package ru.heatmap.registry.dto

import java.time.Instant
import java.util.UUID

data class NotificationResponse(
    val id: UUID,
    val type: String,
    val toolId: UUID?,
    val toolName: String,
    // Готовый текст на русском - фронту не нужно самому расшифровывать type/toolName.
    val message: String,
    val read: Boolean,
    val createdAt: Instant
)

data class UnreadCountResponse(
    val count: Long
)
