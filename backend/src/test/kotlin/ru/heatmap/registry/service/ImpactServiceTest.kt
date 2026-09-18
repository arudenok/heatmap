package ru.heatmap.registry.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import ru.heatmap.registry.domain.ImpactBadgeStatus
import ru.heatmap.registry.domain.ImpactBlock
import ru.heatmap.registry.domain.ImpactColorVariant
import ru.heatmap.registry.domain.ImpactRow
import ru.heatmap.registry.dto.ImpactBlockResponse
import ru.heatmap.registry.dto.ImpactRowResponse
import ru.heatmap.registry.dto.UpdateImpactRowRequest
import ru.heatmap.registry.mapper.ImpactMapper
import ru.heatmap.registry.repository.ImpactBlockRepository
import ru.heatmap.registry.repository.ImpactRowRepository
import ru.heatmap.registry.web.NotFoundException
import java.util.Optional
import java.util.UUID

class ImpactServiceTest {

    private val impactBlockRepository = mock<ImpactBlockRepository>()
    private val impactRowRepository = mock<ImpactRowRepository>()
    private val impactMapper = mock<ImpactMapper>()

    private val service = ImpactService(impactBlockRepository, impactRowRepository, impactMapper)

    @Nested
    inner class FindAll {
        @Test
        fun `maps every ordered block through the mapper`() {
            val block = ImpactBlock(
                id = UUID.randomUUID(),
                code = "HABIT",
                icon = "star",
                title = "Habit",
                badgeText = "Достигнуто",
                badgeStatus = ImpactBadgeStatus.ACHIEVED
            )
            whenever(impactBlockRepository.findAllByOrderBySortOrderAsc()).thenReturn(listOf(block))
            whenever(impactMapper.toResponse(block)).thenReturn(
                ImpactBlockResponse("HABIT", "star", "Habit", "Достигнуто", "ACHIEVED", emptyList())
            )

            val result = service.findAll()

            assertThat(result).hasSize(1)
            assertThat(result[0].code).isEqualTo("HABIT")
        }
    }

    @Nested
    inner class UpdateRowValue {
        @Test
        fun `row not found throws NotFound`() {
            val id = UUID.randomUUID()
            whenever(impactRowRepository.findById(id)).thenReturn(Optional.empty())

            assertThatThrownBy { service.updateRowValue(id, UpdateImpactRowRequest("42%")) }
                .isInstanceOf(NotFoundException::class.java)
        }

        @Test
        fun `updates and trims the row value`() {
            val block = ImpactBlock(id = UUID.randomUUID(), code = "HABIT", icon = "star", title = "Habit", badgeText = "x")
            val row = ImpactRow(
                id = UUID.randomUUID(),
                block = block,
                label = "Метрика",
                value = "10%",
                colorVariant = ImpactColorVariant.DEFAULT
            )
            whenever(impactRowRepository.findById(row.id!!)).thenReturn(Optional.of(row))
            whenever(impactRowRepository.save(any())).thenAnswer { it.arguments[0] }
            // ImpactMapper.toResponse перегружен (ImpactBlock -> ImpactBlockResponse и
            // ImpactRow -> ImpactRowResponse) - any() без явного типа не даёт Kotlin понять,
            // какую перегрузку мокать.
            whenever(impactMapper.toResponse(any<ImpactRow>())).thenAnswer {
                val r = it.arguments[0] as ImpactRow
                ImpactRowResponse(r.id!!, r.label, r.value, r.colorVariant.name, r.isFooter)
            }

            val response = service.updateRowValue(row.id!!, UpdateImpactRowRequest("  99%  "))

            assertThat(row.value).isEqualTo("99%")
            assertThat(response.value).isEqualTo("99%")
        }
    }
}
