package ru.heatmap.registry.repository

import ru.heatmap.registry.domain.ToolRating
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ToolRatingRepository : JpaRepository<ToolRating, UUID> {
    fun findByToolIdAndUserId(toolId: UUID, userId: UUID): ToolRating?

    // Нужно при удалении пользователя администратором - иначе FK user_id в tool_rating
    // помешает удалить саму запись app_user.
    fun deleteAllByUserId(userId: UUID)

    // Нужно при удалении инструмента - иначе FK tool_id в tool_rating помешает удалить
    // саму запись ai_tool (см. ToolService.delete).
    fun deleteAllByToolId(toolId: UUID)
}
