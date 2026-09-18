package ru.heatmap.registry.mapper

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.heatmap.registry.domain.ImpactBlock
import ru.heatmap.registry.domain.ImpactRow
import java.util.UUID

class ImpactMapperTest {

    private val mapper: ImpactMapper = ImpactMapperImpl()

    @Test
    fun `rows are sorted by sortOrder ascending regardless of insertion order`() {
        val block = ImpactBlock(
            id = UUID.randomUUID(),
            code = "x",
            icon = "🔥",
            title = "Title",
            badgeText = "Badge"
        )
        val row1 = ImpactRow(id = UUID.randomUUID(), block = block, label = "l1", value = "v1", sortOrder = 2)
        val row2 = ImpactRow(
            id = UUID.randomUUID(),
            block = block,
            label = "l2",
            value = "v2",
            sortOrder = 1,
            isFooter = true
        )
        block.rows = mutableListOf(row1, row2)

        val response = mapper.toResponse(block)

        assertThat(response.rows).extracting("label").containsExactly("l2", "l1")
    }

    @Test
    fun `isFooter maps from the row's isFooter flag`() {
        val block = ImpactBlock(id = UUID.randomUUID(), code = "y", icon = "📈", title = "T", badgeText = "B")
        val footerRow = ImpactRow(
            id = UUID.randomUUID(),
            block = block,
            label = "footer",
            value = "v",
            sortOrder = 0,
            isFooter = true
        )
        val normalRow = ImpactRow(
            id = UUID.randomUUID(),
            block = block,
            label = "normal",
            value = "v",
            sortOrder = 1,
            isFooter = false
        )

        assertThat(mapper.toResponse(footerRow).isFooter).isTrue()
        assertThat(mapper.toResponse(normalRow).isFooter).isFalse()
    }

    @Test
    fun `block scalar fields map through unchanged`() {
        val block = ImpactBlock(
            id = UUID.randomUUID(),
            code = "code1",
            icon = "🔥",
            title = "My Title",
            badgeText = "My Badge"
        )
        val response = mapper.toResponse(block)

        assertThat(response.code).isEqualTo("code1")
        assertThat(response.icon).isEqualTo("🔥")
        assertThat(response.title).isEqualTo("My Title")
        assertThat(response.badgeText).isEqualTo("My Badge")
        assertThat(response.rows).isEmpty()
    }
}
