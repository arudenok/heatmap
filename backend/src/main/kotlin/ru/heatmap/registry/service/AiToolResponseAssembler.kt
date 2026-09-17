package ru.heatmap.registry.service

import ru.heatmap.registry.domain.AiTool
import ru.heatmap.registry.domain.ToolStatus
import ru.heatmap.registry.dto.ToolResponse
import ru.heatmap.registry.mapper.ToolMapper
import ru.heatmap.registry.repository.AiToolRepository
import ru.heatmap.registry.repository.ToolNoteRepository
import ru.heatmap.registry.repository.ToolRatingRepository
import ru.heatmap.registry.security.UserPrincipal
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Сборка ToolResponse и расчёт плашки "Топ" - общие и для ToolService (каталог/CRUD),
 * и для ToolModerationService (модерация), поэтому вынесены из ToolService, где раньше
 * был единственный потребитель.
 */
@Component
class AiToolResponseAssembler(
    private val aiToolRepository: AiToolRepository,
    private val toolRatingRepository: ToolRatingRepository,
    private val toolNoteRepository: ToolNoteRepository,
    private val toolMapper: ToolMapper
) {

    /**
     * Плашка "Топ" присваивается автоматически не более чем 1% опубликованных инструментов
     * с лучшим сочетанием оценки пользователей, просмотров и скачиваний (при равенстве прочего
     * оценка весит больше всего).
     */
    fun computeTopIds(): Set<UUID> {
        // Только числовые поля нужны для скора - без загрузки eager-коллекций
        // roles/framework/constraints, которые findAll(Specification) тянул бы за каждым
        // инструментом (см. AiToolScoreProjection).
        val published = aiToolRepository.findScoresByStatus(ToolStatus.PUBLISHED)
        if (published.isEmpty()) return emptySet()

        fun normalize(value: Double, min: Double, max: Double): Double =
            if (max > min) (value - min) / (max - min) else 0.0

        val ratings = published.map { if (it.ratingsCount > 0) it.ratingSum.toDouble() / it.ratingsCount else 0.0 }
        val views = published.map { it.views.toDouble() }
        val downloads = published.map { it.downloads.toDouble() }
        val ratingRange = (ratings.minOrNull() ?: 0.0) to (ratings.maxOrNull() ?: 0.0)
        val viewsRange = (views.minOrNull() ?: 0.0) to (views.maxOrNull() ?: 0.0)
        val downloadsRange = (downloads.minOrNull() ?: 0.0) to (downloads.maxOrNull() ?: 0.0)

        val scored = published.map { tool ->
            val avgRating = if (tool.ratingsCount > 0) tool.ratingSum.toDouble() / tool.ratingsCount else 0.0
            val score = 0.5 * normalize(avgRating, ratingRange.first, ratingRange.second) +
                0.25 * normalize(tool.views.toDouble(), viewsRange.first, viewsRange.second) +
                0.25 * normalize(tool.downloads.toDouble(), downloadsRange.first, downloadsRange.second)
            tool.id to score
        }

        val topCount = maxOf(1, kotlin.math.round(published.size * 0.01).toInt())
        return scored.sortedByDescending { it.second }.take(topCount).map { it.first }.toSet()
    }

    fun toResponse(tool: AiTool, principal: UserPrincipal?, topIds: Set<UUID>): ToolResponse {
        val canManage = principal != null && (principal.role == "ADMIN" || tool.createdBy?.id == principal.id)
        val myRating = principal?.let {
            toolRatingRepository.findByToolIdAndUserId(tool.id!!, it.id)?.value
        }
        // Заметки - внутренняя переписка администраторов, обычным пользователям даже количество
        // заметок видно не должно быть - поэтому считаем только для ADMIN.
        val notesCount = if (principal?.role == "ADMIN") toolNoteRepository.countByToolId(tool.id!!) else 0L
        return toolMapper.toBaseResponse(tool).copy(
            isTop = topIds.contains(tool.id),
            myRating = myRating,
            canManage = canManage,
            notesCount = notesCount,
            // Как и заметки - видно только администратору (см. AiTool.segment).
            segment = if (principal?.role == "ADMIN") tool.segment else null
        )
    }
}
