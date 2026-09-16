package ru.heatmap.registry.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/** Оценка инструмента пользователем по 5-балльной шкале - предлагается после скачивания. */
@Entity
@Table(name = "tool_rating", uniqueConstraints = [UniqueConstraint(columnNames = ["tool_id", "user_id"])])
class ToolRating(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tool_id", nullable = false)
    var tool: AiTool,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: AppUser,

    // "VALUE" - зарезервированное слово в H2, поэтому колонка называется rating_value.
    @Column(name = "rating_value", nullable = false)
    var value: Int,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
