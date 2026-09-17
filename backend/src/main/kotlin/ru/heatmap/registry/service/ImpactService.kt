package ru.heatmap.registry.service

import ru.heatmap.registry.dto.ImpactBlockResponse
import ru.heatmap.registry.dto.ImpactRowResponse
import ru.heatmap.registry.dto.UpdateImpactRowRequest
import ru.heatmap.registry.mapper.ImpactMapper
import ru.heatmap.registry.repository.ImpactBlockRepository
import ru.heatmap.registry.repository.ImpactRowRepository
import ru.heatmap.registry.web.NotFoundException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ImpactService(
    private val impactBlockRepository: ImpactBlockRepository,
    private val impactRowRepository: ImpactRowRepository,
    private val impactMapper: ImpactMapper
) {

    @Transactional(readOnly = true)
    fun findAll(): List<ImpactBlockResponse> =
        impactBlockRepository.findAllByOrderBySortOrderAsc().map { impactMapper.toResponse(it) }

    @Transactional
    fun updateRowValue(rowId: UUID, request: UpdateImpactRowRequest): ImpactRowResponse {
        val row = impactRowRepository.findByIdOrNull(rowId) ?: throw NotFoundException("Строка метрики не найдена")
        row.value = request.value.trim()
        return impactMapper.toResponse(impactRowRepository.save(row))
    }
}
