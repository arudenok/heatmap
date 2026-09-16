package ru.heatmap.registry.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * Уведомление пользователю: администратору - о новой заявке на модерацию,
 * автору инструмента - о результате модерации его заявки.
 * tool_id хранится без FK и дублируется toolName снимком на момент создания -
 * уведомление должно остаться читаемым, даже если инструмент потом удалят.
 */
@Entity
@Table(name = "notification")
class Notification(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: AppUser,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var type: NotificationType,

    @Column(name = "tool_id")
    var toolId: UUID?,

    @Column(name = "tool_name", nullable = false)
    var toolName: String,

    // Причина отклонения - заполняется только для SUBMISSION_REJECTED, снимок на момент отклонения
    // (независимо от того, что потом станет с самим инструментом - см. AiTool.rejectionReason).
    @Column(name = "reason", length = 1000)
    var reason: String? = null,

    @Column(name = "is_read", nullable = false)
    var read: Boolean = false,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
)

enum class NotificationType {
    NEW_SUBMISSION,
    SUBMISSION_APPROVED,
    SUBMISSION_REJECTED,
    NEW_TOOL_NOTE,
    TOOL_ARCHIVED
}
