package ru.heatmap.registry.service

import ru.heatmap.registry.domain.ToolNote
import ru.heatmap.registry.dto.CreateToolNoteRequest
import ru.heatmap.registry.dto.ToolNoteResponse
import ru.heatmap.registry.dto.UpdateToolNoteRequest
import ru.heatmap.registry.repository.AiToolRepository
import ru.heatmap.registry.repository.AppUserRepository
import ru.heatmap.registry.repository.ToolNoteRepository
import ru.heatmap.registry.security.UserPrincipal
import ru.heatmap.registry.web.ForbiddenException
import ru.heatmap.registry.web.NotFoundException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Заметки администраторов к инструменту - внутренняя переписка, не видна обычным пользователям
 * (весь контроллер смонтирован под /api/admin, доступ только ADMIN - см. SecurityConfig).
 * Каждая заметка - отдельная запись своего автора: один администратор добавляет заметки,
 * не затирая чужие, а редактировать/удалить может только собственную.
 */
@Service
class ToolNoteService(
    private val toolNoteRepository: ToolNoteRepository,
    private val aiToolRepository: AiToolRepository,
    private val appUserRepository: AppUserRepository,
    private val notificationService: NotificationService
) {

    fun listByTool(toolId: UUID, principal: UserPrincipal): List<ToolNoteResponse> {
        if (!aiToolRepository.existsById(toolId)) throw NotFoundException("Инструмент не найден")
        return toolNoteRepository.findByToolIdOrderByCreatedAtAsc(toolId).map { it.toResponse(principal) }
    }

    @Transactional
    fun create(toolId: UUID, request: CreateToolNoteRequest, principal: UserPrincipal): ToolNoteResponse {
        val tool = aiToolRepository.findByIdOrNull(toolId) ?: throw NotFoundException("Инструмент не найден")
        val author = appUserRepository.findByIdOrNull(principal.id) ?: throw NotFoundException("Пользователь не найден")
        // createdAt/updatedAt отдельно задают Instant.now() по умолчанию - две пары вызовов почти
        // никогда не совпадут до наносекунды, из-за чего свежая заметка на фронте выглядела бы
        // "изменённой". Явно фиксируем оба поля одним и тем же моментом времени.
        val now = Instant.now()
        val note = ToolNote(tool = tool, author = author, text = request.text.trim(), createdAt = now, updatedAt = now)
        val saved = toolNoteRepository.save(note)
        notificationService.notifyAdminsOfNewNote(tool, principal.id)
        return saved.toResponse(principal)
    }

    @Transactional
    fun update(noteId: UUID, request: UpdateToolNoteRequest, principal: UserPrincipal): ToolNoteResponse {
        val note = toolNoteRepository.findByIdOrNull(noteId) ?: throw NotFoundException("Заметка не найдена")
        if (note.author.id != principal.id) {
            throw ForbiddenException("Редактировать можно только собственную заметку")
        }
        note.text = request.text.trim()
        note.updatedAt = Instant.now()
        return toolNoteRepository.save(note).toResponse(principal)
    }

    @Transactional
    fun delete(noteId: UUID, principal: UserPrincipal) {
        val note = toolNoteRepository.findByIdOrNull(noteId) ?: throw NotFoundException("Заметка не найдена")
        if (note.author.id != principal.id) {
            throw ForbiddenException("Удалить можно только собственную заметку")
        }
        toolNoteRepository.delete(note)
    }

    private fun ToolNote.toResponse(principal: UserPrincipal) = ToolNoteResponse(
        id = this.id!!,
        toolId = this.tool.id!!,
        authorId = this.author.id!!,
        authorName = this.author.fullName,
        text = this.text,
        canManage = this.author.id == principal.id,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt
    )
}
