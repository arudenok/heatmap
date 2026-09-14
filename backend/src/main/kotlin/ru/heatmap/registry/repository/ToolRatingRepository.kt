package ru.heatmap.registry.repository

import ru.heatmap.registry.domain.ToolRating
import org.springframework.data.jpa.repository.JpaRepository

interface ToolRatingRepository : JpaRepository<ToolRating, Long> {
    fun findByToolIdAndUserId(toolId: Long, userId: Long): ToolRating?

    // Нужно при удалении пользователя администратором - иначе FK user_id в tool_rating
    // помешает удалить саму запись app_user.
    fun deleteAllByUserId(userId: Long)
}
