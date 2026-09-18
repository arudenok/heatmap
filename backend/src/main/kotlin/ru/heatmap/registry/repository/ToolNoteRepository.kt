package ru.heatmap.registry.repository

import ru.heatmap.registry.domain.ToolNote
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ToolNoteRepository : JpaRepository<ToolNote, UUID> {
    fun findByToolIdOrderByCreatedAtAsc(toolId: UUID): List<ToolNote>

    // Для бейджа с количеством заметок на кнопке "Заметки" (список инструментов, модерация).
    fun countByToolId(toolId: UUID): Long

    // Нужно при удалении пользователя администратором - иначе FK author_id в tool_note
    // помешает удалить саму запись app_user (см. AdminUserService.delete).
    fun deleteAllByAuthorId(authorId: UUID)

    // Нужно при удалении инструмента - иначе FK tool_id в tool_note помешает удалить
    // саму запись ai_tool (см. ToolService.delete).
    fun deleteAllByToolId(toolId: UUID)
}
