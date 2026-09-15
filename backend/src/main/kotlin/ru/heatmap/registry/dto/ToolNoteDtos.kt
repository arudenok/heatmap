package ru.heatmap.registry.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class ToolNoteResponse(
    val id: Long,
    val toolId: Long,
    val authorId: Long,
    val authorName: String,
    val text: String,
    // true, если заметку оставил текущий пользователь - только он может её редактировать/удалить.
    val canManage: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class CreateToolNoteRequest(
    @field:NotBlank(message = "Введите текст заметки")
    @field:Size(max = 2000)
    val text: String
)

data class UpdateToolNoteRequest(
    @field:NotBlank(message = "Введите текст заметки")
    @field:Size(max = 2000)
    val text: String
)
