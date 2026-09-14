package ru.heatmap.registry.repository

import ru.heatmap.registry.domain.ToolDownload
import org.springframework.data.jpa.repository.JpaRepository

interface ToolDownloadRepository : JpaRepository<ToolDownload, Long> {
    fun existsByToolIdAndUserId(toolId: Long, userId: Long): Boolean
    fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<ToolDownload>

    // Нужно при удалении пользователя администратором - иначе FK user_id в tool_download
    // помешает удалить саму запись app_user.
    fun deleteAllByUserId(userId: Long)
}
