package ru.heatmap.registry.repository

import ru.heatmap.registry.domain.AiTool
import ru.heatmap.registry.domain.ToolStage
import ru.heatmap.registry.domain.ToolStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

interface AiToolRepository : JpaRepository<AiTool, Long>, JpaSpecificationExecutor<AiTool> {
    fun countByStageAndStatus(stage: ToolStage, status: ToolStatus): Long
    fun countByStatus(status: ToolStatus): Long
}
