package ru.heatmap.registry.web

import ru.heatmap.registry.dto.ImpactBlockResponse
import ru.heatmap.registry.service.ImpactService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/impact")
class ImpactController(private val impactService: ImpactService) {

    @GetMapping
    fun list(): List<ImpactBlockResponse> = impactService.findAll()
}
