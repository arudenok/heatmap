package ru.heatmap.registry.service

import ru.heatmap.registry.domain.AiTool
import ru.heatmap.registry.domain.AppUser
import ru.heatmap.registry.domain.Notification
import ru.heatmap.registry.domain.NotificationType
import ru.heatmap.registry.domain.Role
import ru.heatmap.registry.dto.NotificationResponse
import ru.heatmap.registry.repository.AppUserRepository
import ru.heatmap.registry.repository.NotificationRepository
import ru.heatmap.registry.security.UserPrincipal
import ru.heatmap.registry.web.ForbiddenException
import ru.heatmap.registry.web.NotFoundException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val appUserRepository: AppUserRepository
) {

    /** Заявка на модерацию создана - уведомляем всех администраторов. */
    @Transactional
    fun notifyAdminsOfNewSubmission(tool: AiTool) {
        val admins = appUserRepository.findByRole(Role.ADMIN)
        admins.forEach { admin ->
            notificationRepository.save(
                Notification(
                    user = admin,
                    type = NotificationType.NEW_SUBMISSION,
                    toolId = tool.id,
                    toolName = tool.name
                )
            )
        }
    }

    /**
     * Добавлена новая заметка к инструменту - уведомляем всех остальных администраторов
     * (кроме автора самой заметки, ему уведомление о собственной записи не нужно).
     */
    @Transactional
    fun notifyAdminsOfNewNote(tool: AiTool, authorId: Long) {
        val admins = appUserRepository.findByRole(Role.ADMIN).filter { it.id != authorId }
        admins.forEach { admin ->
            notificationRepository.save(
                Notification(
                    user = admin,
                    type = NotificationType.NEW_TOOL_NOTE,
                    toolId = tool.id,
                    toolName = tool.name
                )
            )
        }
    }

    /** Заявку одобрили или отклонили - уведомляем автора, если он известен (не удалён). */
    @Transactional
    fun notifyOwnerOfModerationResult(tool: AiTool, approved: Boolean, reason: String?) {
        val owner = tool.createdBy ?: return
        notificationRepository.save(
            Notification(
                user = owner,
                type = if (approved) NotificationType.SUBMISSION_APPROVED else NotificationType.SUBMISSION_REJECTED,
                toolId = tool.id,
                toolName = tool.name,
                reason = if (approved) null else reason
            )
        )
    }

    @Transactional(readOnly = true)
    fun list(principal: UserPrincipal): List<NotificationResponse> =
        notificationRepository.findByUserIdOrderByCreatedAtDesc(principal.id).map { it.toResponse() }

    @Transactional(readOnly = true)
    fun unreadCount(principal: UserPrincipal): Long =
        notificationRepository.countByUserIdAndReadFalse(principal.id)

    @Transactional
    fun markRead(id: Long, principal: UserPrincipal) {
        val notification = notificationRepository.findByIdOrNull(id) ?: throw NotFoundException("Уведомление не найдено")
        if (notification.user.id != principal.id) {
            throw ForbiddenException("Недостаточно прав для этого уведомления")
        }
        if (!notification.read) {
            notification.read = true
            notificationRepository.save(notification)
        }
    }

    @Transactional
    fun markAllRead(principal: UserPrincipal) {
        val unread = notificationRepository.findAllByUserIdAndReadFalse(principal.id)
        unread.forEach { it.read = true }
        notificationRepository.saveAll(unread)
    }

    private fun Notification.toResponse() = NotificationResponse(
        id = this.id!!,
        type = this.type.name,
        toolId = this.toolId,
        toolName = this.toolName,
        message = buildMessage(this.type, this.toolName, this.reason),
        read = this.read,
        createdAt = this.createdAt
    )

    private fun buildMessage(type: NotificationType, toolName: String, reason: String?): String = when (type) {
        NotificationType.NEW_SUBMISSION -> "Новая заявка на модерацию: «$toolName»"
        NotificationType.SUBMISSION_APPROVED -> "Ваша заявка «$toolName» одобрена и опубликована"
        NotificationType.SUBMISSION_REJECTED ->
            if (!reason.isNullOrBlank()) "Ваша заявка «$toolName» отклонена модератором: $reason"
            else "Ваша заявка «$toolName» отклонена модератором"
        NotificationType.NEW_TOOL_NOTE -> "Новая заметка к инструменту «$toolName»"
    }
}
