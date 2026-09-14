package ru.heatmap.registry.repository

import ru.heatmap.registry.domain.ImpactBlock
import ru.heatmap.registry.domain.ImpactRow
import org.springframework.data.jpa.repository.JpaRepository

interface ImpactBlockRepository : JpaRepository<ImpactBlock, Long> {
    fun findAllByOrderBySortOrderAsc(): List<ImpactBlock>
}

interface ImpactRowRepository : JpaRepository<ImpactRow, Long>
