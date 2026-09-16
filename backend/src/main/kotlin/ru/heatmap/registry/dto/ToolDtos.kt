package ru.heatmap.registry.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

private const val URL_PATTERN = "^https?://.+"
private const val URL_MESSAGE = "Введите ссылку на инструмент (начинается с http:// или https://)"

data class ToolResponse(
    val id: UUID,
    val name: String,
    val description: String,
    val stage: String,
    val status: String,
    val roles: List<String>,
    val framework: String,
    val segments: List<String>,
    val sourceLabel: String,
    val ownerName: String,
    val downloads: Int,
    val dau: Int?,
    val efficiencyPct: Int,
    val isTop: Boolean,
    val views: Long,
    val avgRating: Double,
    val ratingsCount: Int,
    val myRating: Int?,
    val canManage: Boolean,
    val rejectionReason: String?,
    val notesCount: Long,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class RejectToolRequest(
    @field:NotBlank(message = "Укажите причину отклонения")
    @field:Size(max = 1000)
    val reason: String
)

// Комментарий необязателен - в отличие от отклонения заявки, где причина обязательна,
// администратор может архивировать инструмент и без пояснения.
data class ArchiveToolRequest(
    @field:Size(max = 1000)
    val reason: String? = null
)

data class RateToolRequest(
    @field:NotNull(message = "Укажите оценку")
    @field:Min(1, message = "Оценка - от 1 до 5")
    @field:Max(5, message = "Оценка - от 1 до 5")
    val rating: Int
)

data class CreateToolRequest(
    @field:NotBlank(message = "Введите название инструмента")
    @field:Size(max = 255)
    val name: String,

    // Ограничения по длине нет - полный текст всегда доступен в модалке "Подробнее".
    @field:NotBlank(message = "Введите описание")
    val description: String,

    @field:NotEmpty(message = "Выберите хотя бы одну роль")
    val roles: List<String>,

    @field:NotBlank
    val framework: String,

    @field:NotEmpty(message = "Выберите хотя бы один сегмент")
    val segments: List<String>,

    @field:NotBlank(message = "Введите ссылку на инструмент")
    @field:Pattern(regexp = URL_PATTERN, message = URL_MESSAGE)
    val sourceLabel: String
)

data class UpdateToolRequest(
    @field:Size(max = 255)
    val name: String? = null,

    val description: String? = null,

    val roles: List<String>? = null,
    val framework: String? = null,
    val segments: List<String>? = null,

    @field:Pattern(regexp = URL_PATTERN, message = URL_MESSAGE)
    val sourceLabel: String? = null,

    val stage: String? = null,
    val status: String? = null,

    @field:Min(0)
    val downloads: Int? = null,

    val dau: Int? = null,

    @field:Min(0) @field:Max(100)
    val efficiencyPct: Int? = null
    // "Топ" больше нельзя выставить вручную - плашка присваивается автоматически по оценкам,
    // просмотрам и скачиваниям (не более 3% опубликованных инструментов).
)

data class ToolCountsResponse(
    val top: Long,
    val access: Long,
    val usage: Long,
    val habit: Long,
    val standard: Long,
    val total: Long
)

data class FilterOptionsResponse(
    val roles: List<String>,
    val frameworks: List<String>,
    val segments: List<String>
)

data class StatsResponse(
    val totalTools: Long,
    val newThisWeek: Long,
    val accessCount: Long,
    val usageCount: Long,
    val standardCount: Long
)
