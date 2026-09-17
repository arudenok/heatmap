package ru.heatmap.registry.web

import ru.heatmap.registry.dto.ArchiveToolRequest
import ru.heatmap.registry.dto.CreateToolNoteRequest
import ru.heatmap.registry.dto.ImpactRowResponse
import ru.heatmap.registry.dto.RejectToolRequest
import ru.heatmap.registry.dto.ToolNoteResponse
import ru.heatmap.registry.dto.ToolResponse
import ru.heatmap.registry.dto.UpdateImpactRowRequest
import ru.heatmap.registry.dto.UpdateToolNoteRequest
import ru.heatmap.registry.dto.UpdateUserEnabledRequest
import ru.heatmap.registry.dto.UpdateUserRoleRequest
import ru.heatmap.registry.dto.UserResponse
import ru.heatmap.registry.security.currentPrincipal
import ru.heatmap.registry.service.AdminUserService
import ru.heatmap.registry.service.ImpactService
import ru.heatmap.registry.service.ToolModerationService
import ru.heatmap.registry.service.ToolNoteService
import ru.heatmap.registry.web.api.AdminApi
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class AdminApiController(
    private val toolModerationService: ToolModerationService,
    private val adminUserService: AdminUserService,
    private val impactService: ImpactService,
    private val toolNoteService: ToolNoteService
) : AdminApi {

    // ===== Модерация заявок (этап Access) =====

    override fun listPendingTools(): ResponseEntity<List<ToolResponse>> =
        ResponseEntity.ok(toolModerationService.pendingModeration(currentPrincipal()))

    override fun approveTool(id: UUID): ResponseEntity<ToolResponse> =
        ResponseEntity.ok(toolModerationService.approve(id))

    override fun rejectTool(id: UUID, rejectToolRequest: RejectToolRequest): ResponseEntity<ToolResponse> =
        ResponseEntity.ok(toolModerationService.reject(id, rejectToolRequest.reason))

    // ===== Архив (скрыть опубликованный инструмент из реестра, не удаляя его) =====

    override fun listArchivedTools(): ResponseEntity<List<ToolResponse>> =
        ResponseEntity.ok(toolModerationService.archived(currentPrincipal()))

    override fun archiveTool(id: UUID, archiveToolRequest: ArchiveToolRequest): ResponseEntity<ToolResponse> =
        ResponseEntity.ok(toolModerationService.archive(id, archiveToolRequest.reason))

    override fun restoreTool(id: UUID): ResponseEntity<ToolResponse> =
        ResponseEntity.ok(toolModerationService.restore(id))

    // ===== Заметки администраторов к инструменту (внутренняя переписка) =====

    override fun listToolNotes(toolId: UUID): ResponseEntity<List<ToolNoteResponse>> =
        ResponseEntity.ok(toolNoteService.listByTool(toolId, currentPrincipal()))

    override fun createToolNote(
        toolId: UUID,
        createToolNoteRequest: CreateToolNoteRequest
    ): ResponseEntity<ToolNoteResponse> =
        ResponseEntity.ok(toolNoteService.create(toolId, createToolNoteRequest, currentPrincipal()))

    override fun updateToolNote(
        noteId: UUID,
        updateToolNoteRequest: UpdateToolNoteRequest
    ): ResponseEntity<ToolNoteResponse> =
        ResponseEntity.ok(toolNoteService.update(noteId, updateToolNoteRequest, currentPrincipal()))

    override fun deleteToolNote(noteId: UUID): ResponseEntity<Unit> {
        toolNoteService.delete(noteId, currentPrincipal())
        return ResponseEntity.noContent().build()
    }

    // ===== Пользователи =====

    override fun listUsers(search: String?): ResponseEntity<List<UserResponse>> =
        ResponseEntity.ok(adminUserService.findAll(search))

    override fun updateUserRole(id: UUID, updateUserRoleRequest: UpdateUserRoleRequest): ResponseEntity<UserResponse> =
        ResponseEntity.ok(adminUserService.updateRole(id, updateUserRoleRequest.role, currentPrincipal().id))

    override fun updateUserEnabled(
        id: UUID,
        updateUserEnabledRequest: UpdateUserEnabledRequest
    ): ResponseEntity<UserResponse> =
        ResponseEntity.ok(adminUserService.updateEnabled(id, updateUserEnabledRequest.enabled, currentPrincipal().id))

    override fun deleteUser(id: UUID): ResponseEntity<Unit> {
        adminUserService.delete(id, currentPrincipal().id)
        return ResponseEntity.noContent().build()
    }

    // ===== Метрики влияния =====

    override fun updateImpactRow(
        id: UUID,
        updateImpactRowRequest: UpdateImpactRowRequest
    ): ResponseEntity<ImpactRowResponse> =
        ResponseEntity.ok(impactService.updateRowValue(id, updateImpactRowRequest))
}
