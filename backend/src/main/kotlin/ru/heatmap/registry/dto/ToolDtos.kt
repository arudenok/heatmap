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

// Ссылка на инструмент должна вести на один из внутренних корпоративных сервисов -
// открытые ссылки на произвольные внешние сайты не принимаются. Токен должен начинать
// доменную метку (после точки или сразу после схемы), а не просто где-то встречаться
// в строке - иначе "https://evil.com/jira" тоже прошёл бы проверку.
private const val URL_PATTERN =
    "^(?i)https?://(?:[\\w-]+\\.)*(sc-ci|sbrf-bitbucket|stash|confluence|jira|mapp|sbertrack|onework)[\\w.-]*(?::\\d+)?(/.*)?$"
private const val URL_MESSAGE =
    "Ссылка должна вести на корпоративный сервис (sc-ci, sbrf-bitbucket, stash, confluence, jira, mapp, sbertrack, onework)"

data class ToolResponse(
    val id: UUID,
    val name: String,
    val description: String,
    val shortDescription: String?,
    val stage: String,
    val status: String,
    val roles: List<String>,
    val framework: String?,
    val constraints: String?,
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
    // Виден и редактируется только администратором (см. AiTool.segment) - для остальных
    // ролей ToolService всегда отдаёт null, независимо от значения в базе.
    val segment: String?,
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

    // Краткое описание - необязательное, показывается на карточках вместо обрезанного
    // полного описания (см. AiTool.shortDescription).
    @field:Size(max = 300, message = "Не более 300 символов")
    val shortDescription: String? = null,

    // Ограничения по длине нет - полный текст всегда доступен в модалке "Подробнее".
    @field:NotBlank(message = "Введите описание")
    val description: String,

    @field:NotEmpty(message = "Выберите хотя бы одну роль")
    val roles: List<String>,

    // Необязательное поле (раньше было обязательным) - не у каждого инструмента есть
    // выраженный агентский фреймворк.
    val framework: String? = null,

    // Свободный текст с подсказками (см. FilterOptionsResponse.constraints) - необязателен.
    val constraints: String? = null,

    @field:NotBlank(message = "Введите ссылку на инструмент")
    @field:Pattern(regexp = URL_PATTERN, message = URL_MESSAGE)
    val sourceLabel: String,

    // Доступно только администратору при создании (см. ToolService.create) - обычный
    // пользователь всегда попадает на модерацию с этапом Access, что бы сюда ни передал.
    val stage: String? = null,
    val status: String? = null
)

data class UpdateToolRequest(
    @field:Size(max = 255)
    val name: String? = null,

    val description: String? = null,

    @field:Size(max = 300, message = "Не более 300 символов")
    val shortDescription: String? = null,

    val roles: List<String>? = null,
    val framework: String? = null,
    val constraints: String? = null,

    @field:Pattern(regexp = URL_PATTERN, message = URL_MESSAGE)
    val sourceLabel: String? = null,

    val stage: String? = null,
    val status: String? = null,

    @field:Min(0)
    val downloads: Int? = null,

    val dau: Int? = null,

    @field:Min(0) @field:Max(100)
    val efficiencyPct: Int? = null,
    // "Топ" больше нельзя выставить вручную - плашка присваивается автоматически по оценкам,
    // просмотрам и скачиваниям (не более 3% опубликованных инструментов).

    // Новое поле "Сегмент" - видно и редактируется только администратором (обычно на
    // экране модерации), см. AiTool.segment. Игнорируется, если запрос шлёт не админ
    // (см. ToolService.update - применяется только внутри блока isAdmin).
    @field:Size(max = 128, message = "Не более 128 символов")
    val segment: String? = null
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
    // Подсказки для автодополнения поля "Ограничения" (свободный текст, не мультиселект,
    // поэтому это не фильтр, а просто список ранее введённых значений).
    val constraints: List<String>
)

data class StatsResponse(
    val totalTools: Long,
    val newThisWeek: Long,
    val accessCount: Long,
    val usageCount: Long,
    val standardCount: Long
)
