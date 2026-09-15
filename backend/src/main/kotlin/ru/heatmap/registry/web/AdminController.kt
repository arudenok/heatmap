package ru.heatmap.registry.web

import ru.heatmap.registry.dto.*
import ru.heatmap.registry.security.UserPrincipal
import ru.heatmap.registry.service.AdminUserService
import ru.heatmap.registry.service.ImpactService
import ru.heatmap.registry.service.ToolNoteService
import ru.heatmap.registry.service.ToolService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin")
class AdminController(
    private val toolService: ToolService,
    private val adminUserService: AdminUserService,
    private val impactService: ImpactService,
    private val toolNoteService: ToolNoteService
) {

    // ===== Модерация заявок (этап Access) =====

    @GetMapping("/tools/pending")
    fun pendingTools(@AuthenticationPrincipal principal: UserPrincipal): List<ToolResponse> =
        toolService.pendingModeration(principal)

    @PostMapping("/tools/{id}/approve")
    fun approve(@PathVariable id: Long): ToolResponse = toolService.approve(id)

    @PostMapping("/tools/{id}/reject")
    fun reject(
        @PathVariable id: Long,
        @Valid @RequestBody request: RejectToolRequest
    ): ToolResponse = toolService.reject(id, request.reason)

    // ===== Заметки администраторов к инструменту (внутренняя переписка) =====

    @GetMapping("/tools/{toolId}/notes")
    fun listNotes(
        @PathVariable toolId: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): List<ToolNoteResponse> = toolNoteService.listByTool(toolId, principal)

    @PostMapping("/tools/{toolId}/notes")
    fun createNote(
        @PathVariable toolId: Long,
        @Valid @RequestBody request: CreateToolNoteRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ToolNoteResponse = toolNoteService.create(toolId, request, principal)

    @PatchMapping("/tools/notes/{noteId}")
    fun updateNote(
        @PathVariable noteId: Long,
        @Valid @RequestBody request: UpdateToolNoteRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ToolNoteResponse = toolNoteService.update(noteId, request, principal)

    @DeleteMapping("/tools/notes/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteNote(
        @PathVariable noteId: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ) = toolNoteService.delete(noteId, principal)

    // ===== Пользователи =====

    @GetMapping("/users")
    fun users(@RequestParam(required = false) search: String?): List<UserResponse> = adminUserService.findAll(search)

    @PatchMapping("/users/{id}/role")
    fun updateRole(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateUserRoleRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): UserResponse = adminUserService.updateRole(id, request.role, principal.id)

    @PatchMapping("/users/{id}/enabled")
    fun updateEnabled(
        @PathVariable id: Long,
        @RequestBody request: UpdateUserEnabledRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): UserResponse = adminUserService.updateEnabled(id, request.enabled, principal.id)

    @DeleteMapping("/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteUser(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ) = adminUserService.delete(id, principal.id)

    // ===== Метрики влияния =====

    @PatchMapping("/impact/rows/{id}")
    fun updateImpactRow(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateImpactRowRequest
    ): ImpactRowResponse = impactService.updateRowValue(id, request)
}
