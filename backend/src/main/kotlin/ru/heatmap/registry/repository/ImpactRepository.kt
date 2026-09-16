package ru.heatmap.registry.repository

import ru.heatmap.registry.domain.ImpactBlock
import ru.heatmap.registry.domain.ImpactRow
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ImpactBlockRepository : JpaRepository<ImpactBlock, UUID> {
    fun findAllByOrderBySortOrderAsc(): List<ImpactBlock>
}

interface ImpactRowRepository : JpaRepository<ImpactRow, UUID>
