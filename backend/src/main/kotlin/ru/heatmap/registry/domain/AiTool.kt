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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var stage: ToolStage = ToolStage.ACCESS,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: ToolStatus = ToolStatus.PENDING,

    // Роль и сегмент - множественные (инструмент может относиться сразу к нескольким).
    // ElementCollection - потому что это просто набор строк, отдельная сущность не нужна.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tool_role", joinColumns = [JoinColumn(name = "tool_id")])
    @Column(name = "role", nullable = false, length = 64)
    var roles: MutableSet<String> = mutableSetOf(),

    @Column(nullable = false, length = 64)
    var framework: String,

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tool_segment", joinColumns = [JoinColumn(name = "tool_id")])
    @Column(name = "segment", nullable = false, length = 64)
    var segments: MutableSet<String> = mutableSetOf(),

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
