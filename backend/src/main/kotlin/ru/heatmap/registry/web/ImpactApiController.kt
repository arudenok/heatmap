package ru.heatmap.registry.web

import ru.heatmap.registry.dto.ImpactBlockResponse
import ru.heatmap.registry.service.ImpactService
import ru.heatmap.registry.web.api.ImpactApi
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class ImpactApiController(private val impactService: ImpactService) : ImpactApi {

    override fun listImpactBlocks(): ResponseEntity<List<ImpactBlockResponse>> =
        ResponseEntity.ok(impactService.findAll())
}
