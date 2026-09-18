package ru.heatmap.registry.repository

import ru.heatmap.registry.domain.ToolDownload
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ToolDownloadRepository : JpaRepository<ToolDownload, UUID> {
    fun existsByToolIdAndUserId(toolId: UUID, userId: UUID): Boolean
    fun findByUserIdOrderByCreatedAtDesc(userId: UUID): List<ToolDownload>

    // Нужно при удалении пользователя администратором - иначе FK user_id в tool_download
    // помешает удалить саму запись app_user.
    fun deleteAllByUserId(userId: UUID)

    // Нужно при удалении инструмента - иначе FK tool_id в tool_download помешает удалить
    // саму запись ai_tool (см. ToolService.delete).
    fun deleteAllByToolId(toolId: UUID)
}
