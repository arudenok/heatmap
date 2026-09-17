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

    // Проверка уникальности "Ссылки на инструмент" (см. ToolService.create/update) - только
    // среди активных карточек (statuses = PENDING/PUBLISHED), отклонённые и архивные не
    // учитываются, чтобы ту же ссылку можно было завести заново в новой заявке.
    fun findFirstBySourceLabelAndStatusIn(sourceLabel: String, statuses: List<ToolStatus>): AiTool?

    // Тот же поиск, но исключая саму редактируемую карточку - иначе сохранение инструмента
    // без изменения ссылки считало бы её конфликтующей сама с собой.
    fun findFirstBySourceLabelAndStatusInAndIdNot(sourceLabel: String, statuses: List<ToolStatus>, excludeId: UUID): AiTool?
}
