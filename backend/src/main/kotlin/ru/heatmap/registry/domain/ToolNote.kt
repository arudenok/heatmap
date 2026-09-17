package ru.heatmap.registry.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Заметка администратора к инструменту - внутренняя переписка между администраторами
 * (не видна обычным пользователям). Каждая заметка - отдельная запись со своим автором,
 * поэтому один администратор не может случайно затереть заметку другого: редактировать
 * или удалить можно только собственную запись (см. ToolNoteService).
 */
@Entity
@Table(name = "tool_note")
class ToolNote(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tool_id", nullable = false)
    var tool: AiTool,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    var author: AppUser,

    @Column(nullable = false, length = 2000)
    var text: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
