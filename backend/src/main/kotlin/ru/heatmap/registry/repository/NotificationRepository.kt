package ru.heatmap.registry.repository

import ru.heatmap.registry.domain.Notification
import org.springframework.data.jpa.repository.JpaRepository

interface NotificationRepository : JpaRepository<Notification, Long> {
    fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<Notification>
    fun countByUserIdAndReadFalse(userId: Long): Long
    fun findAllByUserIdAndReadFalse(userId: Long): List<Notification>

    // Нужно при удалении пользователя администратором - иначе FK user_id в notification
    // помешает удалить саму запись app_user.
    fun deleteAllByUserId(userId: Long)
}
