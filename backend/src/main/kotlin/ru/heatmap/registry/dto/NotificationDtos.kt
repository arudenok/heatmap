package ru.heatmap.registry.dto

import java.time.Instant

data class NotificationResponse(
    val id: Long,
    val type: String,
    val toolId: Long?,
    val toolName: String,
    // Готовый текст на русском - фронту не нужно самому расшифровывать type/toolName.
    val message: String,
    val read: Boolean,
    val createdAt: Instant
)

data class UnreadCountResponse(
    val count: Long
)
