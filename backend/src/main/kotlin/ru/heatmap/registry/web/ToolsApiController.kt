package ru.heatmap.registry.web

import ru.heatmap.registry.dto.CreateToolRequest
import ru.heatmap.registry.dto.FilterOptionsResponse
import ru.heatmap.registry.dto.RateToolRequest
import ru.heatmap.registry.dto.StatsResponse
import ru.heatmap.registry.dto.ToolCountsResponse
import ru.heatmap.registry.dto.ToolResponse
import ru.heatmap.registry.dto.UpdateToolRequest
import ru.heatmap.registry.security.currentPrincipal
import ru.heatmap.registry.security.currentPrincipalOrNull
import ru.heatmap.registry.service.ToolService
import ru.heatmap.registry.web.api.ToolsApi
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class ToolsApiController(private val toolService: ToolService) : ToolsApi {

    override fun listTools(
        tab: String,
        role: List<String>?,
        framework: List<String>?,
        constraints: List<String>?,
        toolType: List<String>?,
        search: String?,
        sort: String?
    ): ResponseEntity<List<ToolResponse>> = ResponseEntity.ok(
        toolService.findByTab(tab, role, framework, constraints, toolType, search, sort, currentPrincipalOrNull())
    )

    override fun getToolCounts(): ResponseEntity<ToolCountsResponse> = ResponseEntity.ok(toolService.counts())

    override fun getToolStats(): ResponseEntity<StatsResponse> = ResponseEntity.ok(toolService.stats())

    override fun getFilterOptions(): ResponseEntity<FilterOptionsResponse> =
        ResponseEntity.ok(toolService.filterOptions())

    override fun listMyTools(): ResponseEntity<List<ToolResponse>> =
        ResponseEntity.ok(toolService.findMine(currentPrincipal()))

    override fun listDownloadedTools(): ResponseEntity<List<ToolResponse>> =
        ResponseEntity.ok(toolService.findDownloaded(currentPrincipal()))

    override fun getTool(id: UUID): ResponseEntity<ToolResponse> =
        ResponseEntity.ok(toolService.findById(id, currentPrincipalOrNull()))

    override fun registerToolView(id: UUID): ResponseEntity<ToolResponse> =
        ResponseEntity.ok(toolService.incrementView(id, currentPrincipalOrNull()))

    override fun registerToolDownload(id: UUID): ResponseEntity<ToolResponse> =
        ResponseEntity.ok(toolService.incrementDownload(id, currentPrincipalOrNull()))

    override fun rateTool(id: UUID, rateToolRequest: RateToolRequest): ResponseEntity<ToolResponse> =
        ResponseEntity.ok(toolService.rate(id, rateToolRequest.rating, currentPrincipal()))

    override fun createTool(createToolRequest: CreateToolRequest): ResponseEntity<ToolResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(toolService.create(createToolRequest, currentPrincipal()))

    override fun updateTool(id: UUID, updateToolRequest: UpdateToolRequest): ResponseEntity<ToolResponse> =
        ResponseEntity.ok(toolService.update(id, updateToolRequest, currentPrincipal()))

    override fun withdrawTool(id: UUID): ResponseEntity<ToolResponse> =
        ResponseEntity.ok(toolService.withdraw(id, currentPrincipal()))

    override fun deleteTool(id: UUID): ResponseEntity<Unit> {
        toolService.delete(id, currentPrincipal())
        return ResponseEntity.noContent().build()
    }
}
