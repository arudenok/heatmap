package ru.heatmap.registry.repository

import ru.heatmap.registry.domain.Notification
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface NotificationRepository : JpaRepository<Notification, UUID> {
    fun findByUserIdOrderByCreatedAtDesc(userId: UUID): List<Notification>
    fun countByUserIdAndReadFalse(userId: UUID): Long
    fun findAllByUserIdAndReadFalse(userId: UUID): List<Notification>

    // Нужно при удалении пользователя администратором - иначе FK user_id в notification
    // помешает удалить саму запись app_user.
    fun deleteAllByUserId(userId: UUID)
}
