package ru.heatmap.registry.repository

import ru.heatmap.registry.domain.AiTool
import ru.heatmap.registry.domain.ToolStage
import ru.heatmap.registry.domain.ToolStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.util.UUID

interface AiToolRepository : JpaRepository<AiTool, UUID>, JpaSpecificationExecutor<AiTool> {
    fun countByStageAndStatus(stage: ToolStage, status: ToolStatus): Long
    fun countByStatus(status: ToolStatus): Long
}
