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

// Базовый набор категорий "Тип инструмента" (см. AiTool.toolType) - всегда виден в форме/фильтре
// (см. ToolService.filterOptions), но больше не единственно допустимый: администратор может
// завести свой вариант (см. SelectDropdown.allowCustom в AddToolModal), как и с ролями/
// фреймворком/ограничениями, поэтому строгой валидации по regexp для этого поля больше нет -
// только ограничение длины (см. TOOL_TYPE_MAX_LENGTH ниже).
val TOOL_TYPE_OPTIONS = listOf("Skill", "MCP", "Agent", "Harness", "Tool", "Framework", "Другое")
private const val TOOL_TYPE_MAX_LENGTH = 60

data class ToolResponse(
    val id: UUID,
    val name: String,
    val description: String,
    val shortDescription: String?,
    val stage: String,
    val status: String,
    val roles: List<String>,
    // Множественное, как и roles выше (см. AiTool.framework) - список может быть пустым.
    val framework: List<String>,
    // Категория из фиксированного набора (см. TOOL_TYPE_OPTIONS) - необязательна.
    val toolType: String?,
    // Множественное, как и roles выше (см. AiTool.constraints) - список может быть пустым.
    val constraints: List<String>,
    val sourceLabel: String?,
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

    // Множественное, как и roles выше - в отличие от ролей, не обязательно ни одного значения
    // (раньше было одно обязательное значение).
    val framework: List<String> = emptyList(),

    // Тип инструмента - необязательная категория, обычно из базового набора (см. TOOL_TYPE_OPTIONS),
    // но администратор может ввести свой вариант (см. AddToolModal/SelectDropdown.allowCustom).
    @field:Size(max = TOOL_TYPE_MAX_LENGTH, message = "Не более $TOOL_TYPE_MAX_LENGTH символов")
    val toolType: String? = null,

    // Множественное, как и roles выше (см. FilterOptionsResponse.constraints для подсказок на
    // фронте) - в отличие от ролей, не обязательно ни одного значения.
    val constraints: List<String> = emptyList(),

    // Обязательна для обычного пользователя, но необязательна для администратора -
    // это правило зависит от роли, поэтому проверяется в ToolService.create, а не аннотацией.
    @field:Pattern(regexp = URL_PATTERN, message = URL_MESSAGE)
    val sourceLabel: String? = null,

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
    val framework: List<String>? = null,

    @field:Size(max = TOOL_TYPE_MAX_LENGTH, message = "Не более $TOOL_TYPE_MAX_LENGTH символов")
    val toolType: String? = null,

    val constraints: List<String>? = null,

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
    // Варианты для поля "Ограничения" - и в форме добавления инструмента (AddToolModal), и в
    // фильтре на главной (FilterBar.vue), это один и тот же MultiSelectDropdown - базовый
    // набор (см. ToolService.PRESET_CONSTRAINTS) плюс реально сохранённые значения.
    val constraints: List<String>,
    // "Тип инструмента" - базовый набор (см. TOOL_TYPE_OPTIONS) плюс реально сохранённые
    // значения, включая "свой вариант" администратора (см. ToolService.filterOptions).
    // В форме добавления - один выбор (SelectDropdown), в фильтре на главной - несколько
    // (MultiSelectDropdown), но список вариантов один и тот же.
    val toolTypes: List<String>
)

data class StatsResponse(
    val totalTools: Long,
    val newThisWeek: Long,
    val accessCount: Long,
    val usageCount: Long,
    val standardCount: Long
)
