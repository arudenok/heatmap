package ru.heatmap.registry.web

import ru.heatmap.registry.dto.*
import ru.heatmap.registry.security.UserPrincipal
import ru.heatmap.registry.service.ToolService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/tools")
class ToolController(private val toolService: ToolService) {

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "TOP") tab: String,
        @RequestParam(required = false) role: List<String>?,
        @RequestParam(required = false) framework: String?,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) sort: String?,
        @AuthenticationPrincipal principal: UserPrincipal?
    ): List<ToolResponse> = toolService.findByTab(tab, role, framework, search, sort, principal)

    @GetMapping("/counts")
    fun counts(): ToolCountsResponse = toolService.counts()

    @GetMapping("/stats")
    fun stats(): StatsResponse = toolService.stats()

    @GetMapping("/filter-options")
    fun filterOptions(): FilterOptionsResponse = toolService.filterOptions()

    @GetMapping("/mine")
    fun mine(@AuthenticationPrincipal principal: UserPrincipal): List<ToolResponse> = toolService.findMine(principal)

    // "Мои инструменты" - то, что пользователь скачивал; тут же можно поставить/изменить оценку.
    @GetMapping("/downloaded")
    fun downloaded(@AuthenticationPrincipal principal: UserPrincipal): List<ToolResponse> =
        toolService.findDownloaded(principal)

    @GetMapping("/{id}")
    fun getOne(@PathVariable id: UUID, @AuthenticationPrincipal principal: UserPrincipal?): ToolResponse =
        toolService.findById(id, principal)

    // Просмотр засчитывается только по явному клику "Подробнее" на карточке, не за листинг.
    @PostMapping("/{id}/view")
    fun registerView(@PathVariable id: UUID, @AuthenticationPrincipal principal: UserPrincipal?): ToolResponse =
        toolService.incrementView(id, principal)

    // Скачивание засчитывается по клику "Скачать" в карточке "Подробнее".
    @PostMapping("/{id}/download")
    fun registerDownload(@PathVariable id: UUID, @AuthenticationPrincipal principal: UserPrincipal?): ToolResponse =
        toolService.incrementDownload(id, principal)

    // Оценка предлагается пользователю после скачивания инструмента; требует авторизации.
    @PostMapping("/{id}/rating")
    fun rate(
        @PathVariable id: UUID,
        @Valid @RequestBody request: RateToolRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ToolResponse = toolService.rate(id, request.rating, principal)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: CreateToolRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ToolResponse = toolService.create(request, principal)

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateToolRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ToolResponse = toolService.update(id, request, principal)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID, @AuthenticationPrincipal principal: UserPrincipal) =
        toolService.delete(id, principal)
}
