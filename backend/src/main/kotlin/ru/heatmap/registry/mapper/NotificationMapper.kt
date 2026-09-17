package ru.heatmap.registry.mapper

import ru.heatmap.registry.domain.Notification
import ru.heatmap.registry.domain.NotificationType
import ru.heatmap.registry.dto.NotificationResponse
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(componentModel = "spring")
abstract class NotificationMapper {

    @Mapping(
        target = "message",
        expression = "java(buildMessage(notification.getType(), notification.getToolName(), notification.getReason()))"
    )
    abstract fun toResponse(notification: Notification): NotificationResponse

    protected fun buildMessage(type: NotificationType, toolName: String, reason: String?): String = when (type) {
        NotificationType.NEW_SUBMISSION -> "Новая заявка на модерацию: «$toolName»"
        NotificationType.SUBMISSION_APPROVED -> "Ваша заявка «$toolName» одобрена и опубликована"
        NotificationType.SUBMISSION_REJECTED ->
            if (!reason.isNullOrBlank()) "Ваша заявка «$toolName» отклонена модератором: $reason"
            else "Ваша заявка «$toolName» отклонена модератором"
        NotificationType.NEW_TOOL_NOTE -> "Новая заметка к инструменту «$toolName»"
        NotificationType.TOOL_ARCHIVED ->
            if (!reason.isNullOrBlank()) "Ваш инструмент «$toolName» архивирован администратором: $reason"
            else "Ваш инструмент «$toolName» архивирован администратором"
    }
}
