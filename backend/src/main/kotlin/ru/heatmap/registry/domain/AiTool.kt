package ru.heatmap.registry.domain

import jakarta.persistence.*
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

    // Необязательное поле (раньше было обязательным) - не у каждого инструмента есть
    // выраженный агентский фреймворк.
    @Column(length = 64)
    var framework: String? = null,

    // Ограничения - свободный текст с автодополнением на фронтенде (см. FilterOptionsResponse.constraints),
    // необязательное поле. Пришло на смену полю "Сегмент" (см. историю - убрано полностью по просьбе).
    // Колонка называется tool_constraints, а не constraints - это слово зарезервировано в SQL
    // (используется в конструкции SET CONSTRAINTS), безопаснее не рисковать с движком БД.
    @Column(name = "tool_constraints", length = 500)
    var constraints: String? = null,

    @Column(name = "source_label", nullable = false, length = 128)
    var sourceLabel: String,

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

enum class ToolStatus {
    PENDING, PUBLISHED, REJECTED, ARCHIVED
}
