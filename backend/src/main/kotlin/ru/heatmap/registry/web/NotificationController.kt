package ru.heatmap.registry.web

import ru.heatmap.registry.dto.NotificationResponse
import ru.heatmap.registry.dto.UnreadCountResponse
import ru.heatmap.registry.security.UserPrincipal
import ru.heatmap.registry.service.NotificationService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/notifications")
class NotificationController(private val notificationService: NotificationService) {

    @GetMapping
    fun list(@AuthenticationPrincipal principal: UserPrincipal): List<NotificationResponse> =
        notificationService.list(principal)

    @GetMapping("/unread-count")
    fun unreadCount(@AuthenticationPrincipal principal: UserPrincipal): UnreadCountResponse =
        UnreadCountResponse(notificationService.unreadCount(principal))

    @PostMapping("/{id}/read")
    fun markRead(@PathVariable id: Long, @AuthenticationPrincipal principal: UserPrincipal) =
        notificationService.markRead(id, principal)

    @PostMapping("/read-all")
    fun markAllRead(@AuthenticationPrincipal principal: UserPrincipal) =
        notificationService.markAllRead(principal)
}
