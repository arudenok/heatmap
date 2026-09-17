package ru.heatmap.registry.service

import ru.heatmap.registry.domain.ToolStatus
import ru.heatmap.registry.dto.ToolResponse
import ru.heatmap.registry.repository.AiToolRepository
import ru.heatmap.registry.security.UserPrincipal
import ru.heatmap.registry.specification.statusSpec
import ru.heatmap.registry.web.BadRequestException
import ru.heatmap.registry.web.NotFoundException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Админ-модерация заявок и архив инструментов (см. AdminController) - выделено из ToolService,
 * которая отвечает за пользовательский каталог/CRUD, чтобы не смешивать эти два сценария в одном классе.
 */
@Service
class ToolModerationService(
    private val aiToolRepository: AiToolRepository,
    private val notificationService: NotificationService,
    private val toolResponseAssembler: AiToolResponseAssembler
) {

    // Инструменты на модерации всегда PENDING - в число опубликованных топ-инструментов попасть не могут.
    // principal передаётся (хотя эндпоинт и так доступен только ADMIN), чтобы в ответе корректно
    // считался notesCount - он виден только администратору (см. AiToolResponseAssembler.toResponse).
    fun pendingModeration(principal: UserPrincipal): List<ToolResponse> =
        aiToolRepository.findAll(statusSpec(ToolStatus.PENDING))
            .map { toolResponseAssembler.toResponse(it, principal, emptySet()) }

    @Transactional
    fun approve(id: UUID): ToolResponse {
        val tool = aiToolRepository.findByIdOrNull(id) ?: throw NotFoundException("Инструмент не найден")
        tool.status = ToolStatus.PUBLISHED
        tool.rejectionReason = null
        tool.updatedAt = Instant.now()
        val saved = aiToolRepository.save(tool)
        notificationService.notifyOwnerOfModerationResult(saved, approved = true, reason = null)
        return toolResponseAssembler.toResponse(saved, null, toolResponseAssembler.computeTopIds())
    }

    @Transactional
    fun reject(id: UUID, reason: String): ToolResponse {
        val tool = aiToolRepository.findByIdOrNull(id) ?: throw NotFoundException("Инструмент не найден")
        tool.status = ToolStatus.REJECTED
        tool.rejectionReason = reason.trim()
        tool.updatedAt = Instant.now()
        // Отклонённый инструмент не может быть "Топ".
        val saved = aiToolRepository.save(tool)
        notificationService.notifyOwnerOfModerationResult(saved, approved = false, reason = tool.rejectionReason)
        return toolResponseAssembler.toResponse(saved, null, emptySet())
    }

    // Инструменты в архиве всегда PUBLISHED-in-the-past, но самим статусом ARCHIVED уже
    // не попадают в обычный реестр (см. statusSpec(PUBLISHED) в ToolService.findByTab) -
    // отдельная выборка нужна только для вкладки "Архив" в администрировании.
    fun archived(principal: UserPrincipal): List<ToolResponse> =
        aiToolRepository.findAll(statusSpec(ToolStatus.ARCHIVED))
            .map { toolResponseAssembler.toResponse(it, principal, emptySet()) }

    /** Комментарий необязателен (см. ArchiveToolRequest) - уведомление автору уходит в любом случае. */
    @Transactional
    fun archive(id: UUID, reason: String?): ToolResponse {
        val tool = aiToolRepository.findByIdOrNull(id) ?: throw NotFoundException("Инструмент не найден")
        if (tool.status != ToolStatus.PUBLISHED) {
            throw BadRequestException("Архивировать можно только опубликованный инструмент")
        }
        tool.status = ToolStatus.ARCHIVED
        tool.updatedAt = Instant.now()
        val saved = aiToolRepository.save(tool)
        notificationService.notifyOwnerOfArchive(saved, reason?.trim()?.takeIf { it.isNotBlank() })
        return toolResponseAssembler.toResponse(saved, null, emptySet())
    }

    // Восстановление всегда возвращает в PUBLISHED - архивировать можно только опубликованный
    // инструмент (см. archive выше), поэтому "восстановить" однозначно значит "опубликовать снова".
    @Transactional
    fun restore(id: UUID): ToolResponse {
        val tool = aiToolRepository.findByIdOrNull(id) ?: throw NotFoundException("Инструмент не найден")
        if (tool.status != ToolStatus.ARCHIVED) {
            throw BadRequestException("Восстановить можно только архивированный инструмент")
        }
        tool.status = ToolStatus.PUBLISHED
        tool.updatedAt = Instant.now()
        return toolResponseAssembler.toResponse(aiToolRepository.save(tool), null, emptySet())
    }
}
