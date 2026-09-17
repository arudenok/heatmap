package ru.heatmap.registry.repository

import ru.heatmap.registry.domain.AiTool
import ru.heatmap.registry.domain.ToolStage
import ru.heatmap.registry.domain.ToolStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface AiToolRepository : JpaRepository<AiTool, UUID>, JpaSpecificationExecutor<AiTool> {
    fun countByStageAndStatus(stage: ToolStage, status: ToolStatus): Long
    fun countByStatus(status: ToolStatus): Long
    fun countByStatusAndCreatedAtAfter(status: ToolStatus, after: Instant): Long

    // Проверка уникальности "Ссылки на инструмент" (см. ToolService.create/update) - только
    // среди активных карточек (statuses = PENDING/PUBLISHED), отклонённые и архивные не
    // учитываются, чтобы ту же ссылку можно было завести заново в новой заявке.
    fun findFirstBySourceLabelAndStatusIn(sourceLabel: String, statuses: List<ToolStatus>): AiTool?

    // Тот же поиск, но исключая саму редактируемую карточку - иначе сохранение инструмента
    // без изменения ссылки считало бы её конфликтующей сама с собой.
    fun findFirstBySourceLabelAndStatusInAndIdNot(sourceLabel: String, statuses: List<ToolStatus>, excludeId: UUID): AiTool?

    // Варианты для фильтра/формы (см. ToolService.filterOptions) - только реально сохранённые
    // значения, без загрузки самих инструментов целиком; пустые/бланковые строки отсеиваются
    // уже в Kotlin-коде (см. ToolService.filterOptions), здесь - только выборка distinct.
    @Query("select distinct r from AiTool t join t.roles r")
    fun findDistinctRoles(): List<String>

    @Query("select distinct f from AiTool t join t.framework f")
    fun findDistinctFrameworks(): List<String>

    @Query("select distinct c from AiTool t join t.constraints c")
    fun findDistinctConstraints(): List<String>

    @Query("select distinct t.toolType from AiTool t where t.toolType is not null")
    fun findDistinctToolTypes(): List<String>

    // Проекция для AiToolResponseAssembler.computeTopIds (см. AiToolScoreProjection) - только
    // числовые поля, нужные для расчёта скора "Топ", без eager-коллекций roles/framework/constraints.
    @Query(
        "select t.id as id, t.ratingSum as ratingSum, t.ratingsCount as ratingsCount, " +
            "t.views as views, t.downloads as downloads from AiTool t where t.status = :status"
    )
    fun findScoresByStatus(status: ToolStatus): List<AiToolScoreProjection>
}
