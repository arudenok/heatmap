package ru.heatmap.registry.service

import ru.heatmap.registry.dto.ImpactBlockResponse
import ru.heatmap.registry.dto.ImpactRowResponse
import ru.heatmap.registry.dto.UpdateImpactRowRequest
import ru.heatmap.registry.repository.ImpactBlockRepository
import ru.heatmap.registry.repository.ImpactRowRepository
import ru.heatmap.registry.web.NotFoundException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ImpactService(
    private val impactBlockRepository: ImpactBlockRepository,
    private val impactRowRepository: ImpactRowRepository
) {

    @Transactional(readOnly = true)
    fun findAll(): List<ImpactBlockResponse> =
        impactBlockRepository.findAllByOrderBySortOrderAsc().map { block ->
            ImpactBlockResponse(
                code = block.code,
                icon = block.icon,
                title = block.title,
                badgeText = block.badgeText,
                badgeStatus = block.badgeStatus.name,
                rows = block.rows.sortedBy { it.sortOrder }.map {
                    ImpactRowResponse(it.id!!, it.label, it.value, it.colorVariant.name, it.isFooter)
                }
            )
        }

    @Transactional
    fun updateRowValue(rowId: Long, request: UpdateImpactRowRequest): ImpactRowResponse {
        val row = impactRowRepository.findByIdOrNull(rowId) ?: throw NotFoundException("Строка метрики не найдена")
        row.value = request.value.trim()
        val saved = impactRowRepository.save(row)
        return ImpactRowResponse(saved.id!!, saved.label, saved.value, saved.colorVariant.name, saved.isFooter)
    }
}
