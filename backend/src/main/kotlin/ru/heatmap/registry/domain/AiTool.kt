package ru.heatmap.registry.domain

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "ai_tool")
class AiTool(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(nullable = false)
    var name: String,

    // Без ограничения по длине (VARCHAR без указания размера в H2) - полное описание всегда
    // доступно через модалку "Подробнее", на карточках оно лишь визуально обрезается на фронтенде.
    // CLOB здесь не подходит - Hibernate не даёт использовать lower() (нужен для поиска) на CLOB-поле.
    @Column(nullable = false)
    var description: String,

    // Краткое описание - необязательное, 1-2 предложения. Показывается на карточке инструмента
    // вместо (обрезанного) полного описания; полное всегда доступно по кнопке "Подробное описание"
    // в модалке (см. ToolDetailModal). Если не заполнено - на карточке используется description.
    @Column(name = "short_description", length = 300)
    var shortDescription: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var stage: ToolStage = ToolStage.ACCESS,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: ToolStatus = ToolStatus.PENDING,

    // Роль - множественная (инструмент может относиться сразу к нескольким).
    // ElementCollection - потому что это просто набор строк, отдельная сущность не нужна.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tool_role", joinColumns = [JoinColumn(name = "tool_id")])
    @Column(name = "role", nullable = false, length = 64)
    var roles: MutableSet<String> = mutableSetOf(),

    // Агентский фреймворк - множественный (как roles/constraints выше - инструмент может
    // использовать сразу несколько), поэтому тоже отдельная таблица "многие-ко-многим"
    // tool_framework, а не колонка в ai_tool. Необязательное поле - список может быть пустым
    // (раньше было одно обязательное значение).
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tool_framework", joinColumns = [JoinColumn(name = "tool_id")])
    @Column(name = "framework", nullable = false, length = 64)
    var framework: MutableSet<String> = mutableSetOf(),

    // Тип инструмента - категория из фиксированного набора (см. ToolService.TOOL_TYPE_OPTIONS:
    // Skill/MCP/Agent/Harness/Tool/Framework/Другое). Необязательное поле, свободный VARCHAR,
    // а не enum - список категорий может меняться, отдельная миграция под каждое изменение не нужна.
    @Column(name = "tool_type", length = 32)
    var toolType: String? = null,

    // Ограничения - множественные (как и roles выше - инструмент может подпадать сразу под
    // несколько), поэтому тоже вынесены в отдельную таблицу "многие-ко-многим" tool_constraint
    // (см. 003-create-tool-role-segment.sql), а не колонка в ai_tool. Пришло на смену полю
    // "Сегмент" (см. историю - убрано полностью по просьбе). Поле в этой таблице называется
    // constraint_value, а не constraint - это слово зарезервировано в SQL.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tool_constraint", joinColumns = [JoinColumn(name = "tool_id")])
    @Column(name = "constraint_value", nullable = false, length = 128)
    var constraints: MutableSet<String> = mutableSetOf(),

    // Ссылка необязательна для администратора - он может завести карточку до появления
    // публичной ссылки (см. ToolService.create/update и ToolDetailModal - кнопка "Скачать"
    // тогда показывает, что ссылки нет). Для обычного пользователя обязательность
    // проверяется на уровне сервиса, а не аннотацией, т.к. правило зависит от роли.
    @Column(name = "source_label", length = 128)
    var sourceLabel: String? = null,

    @Column(name = "owner_name", nullable = false)
    var ownerName: String,

    @Column(nullable = false)
    var downloads: Int = 0,

    @Column
    var dau: Int? = null,

    @Column(name = "efficiency_pct", nullable = false)
    var efficiencyPct: Int = 0,

    @Column(nullable = false)
    var views: Long = 0,

    @Column(name = "rating_sum", nullable = false)
    var ratingSum: Long = 0,

    @Column(name = "ratings_count", nullable = false)
    var ratingsCount: Int = 0,

    // Новое поле "Сегмент" - не то же самое, что историческое поле "Сегмент" (см. комментарий
    // у constraints выше - то было убрано полностью и заменено "Ограничениями"). Это отдельное,
    // видно и редактируется только администратором (обычно при рассмотрении заявки на модерации),
    // поэтому колонка называется admin_segment.
    @Column(name = "admin_segment", length = 128)
    var segment: String? = null,

    // Причина отклонения - заполняется администратором при отклонении заявки,
    // показывается автору, сбрасывается при одобрении и при новой отправке на модерацию.
    @Column(name = "rejection_reason", length = 1000)
    var rejectionReason: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    var createdBy: AppUser? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)

enum class ToolStage {
    ACCESS, USAGE, HABIT, STANDARD
}

// DRAFT - автор отозвал свою заявку с модерации (см. ToolService.withdraw); инструмент в этом
// статусе не виден никому, кроме самого автора (вкладка "Черновики" в "Мои инструменты"), и не
// участвует ни в одной выборке реестра. Из черновика можно либо удалить инструмент насовсем,
// либо отредактировать и отправить повторно - тогда статус меняется на PENDING (см.
// ToolService.update, shouldResubmitOnEdit).
enum class ToolStatus {
    PENDING, PUBLISHED, REJECTED, ARCHIVED, DRAFT
}
