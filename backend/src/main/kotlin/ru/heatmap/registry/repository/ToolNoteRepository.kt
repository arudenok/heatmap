package ru.heatmap.registry.repository

import ru.heatmap.registry.domain.ToolNote
import org.springframework.data.jpa.repository.JpaRepository

interface ToolNoteRepository : JpaRepository<ToolNote, Long> {
    fun findByToolIdOrderByCreatedAtAsc(toolId: Long): List<ToolNote>

    // Для бейджа с количеством заметок на кнопке "Заметки" (список инструментов, модерация).
    fun countByToolId(toolId: Long): Long

    // Нужно при удалении пользователя администратором - иначе FK author_id в tool_note
    // помешает удалить саму запись app_user (см. AdminUserService.delete).
    fun deleteAllByAuthorId(authorId: Long)
}
