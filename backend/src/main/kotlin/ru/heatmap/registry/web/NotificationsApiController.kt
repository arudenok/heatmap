package ru.heatmap.registry.web

import ru.heatmap.registry.dto.NotificationResponse
import ru.heatmap.registry.dto.UnreadCountResponse
import ru.heatmap.registry.security.currentPrincipal
import ru.heatmap.registry.service.NotificationService
import ru.heatmap.registry.web.api.NotificationsApi
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class NotificationsApiController(private val notificationService: NotificationService) : NotificationsApi {

    override fun listNotifications(): ResponseEntity<List<NotificationResponse>> =
        ResponseEntity.ok(notificationService.list(currentPrincipal()))

    override fun getUnreadNotificationCount(): ResponseEntity<UnreadCountResponse> =
        ResponseEntity.ok(UnreadCountResponse(notificationService.unreadCount(currentPrincipal())))

    override fun markNotificationRead(id: UUID): ResponseEntity<Unit> {
        notificationService.markRead(id, currentPrincipal())
        return ResponseEntity.noContent().build()
    }

    override fun markAllNotificationsRead(): ResponseEntity<Unit> {
        notificationService.markAllRead(currentPrincipal())
        return ResponseEntity.noContent().build()
    }
}
