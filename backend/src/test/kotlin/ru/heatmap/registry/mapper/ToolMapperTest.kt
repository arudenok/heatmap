package ru.heatmap.registry.mapper

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.heatmap.registry.domain.AiTool
import ru.heatmap.registry.domain.ToolStage
import ru.heatmap.registry.domain.ToolStatus
import java.util.UUID

class ToolMapperTest {

    private val mapper: ToolMapper = ToolMapperImpl()

    private fun tool(
        roles: MutableSet<String> = mutableSetOf("b", "a"),
        framework: MutableSet<String> = mutableSetOf("z", "y"),
        constraints: MutableSet<String> = mutableSetOf("c1"),
        ratingSum: Long = 9,
        ratingsCount: Int = 3
    ) = AiTool(
        id = UUID.randomUUID(),
        name = "Tool",
        description = "Desc",
        stage = ToolStage.ACCESS,
        status = ToolStatus.PUBLISHED,
        roles = roles,
        framework = framework,
        constraints = constraints,
        ownerName = "Owner",
        downloads = 5,
        efficiencyPct = 80,
        views = 42,
        ratingSum = ratingSum,
        ratingsCount = ratingsCount
    )

    @Test
    fun `sorts roles, framework and constraints alphabetically`() {
        val response = mapper.toBaseResponse(tool())

        assertThat(response.roles).containsExactly("a", "b")
        assertThat(response.framework).containsExactly("y", "z")
        assertThat(response.constraints).containsExactly("c1")
    }

    @Test
    fun `computes average rating when ratings exist`() {
        val response = mapper.toBaseResponse(tool(ratingSum = 9, ratingsCount = 3))
        assertThat(response.avgRating).isEqualTo(3.0)
    }

    @Test
    fun `average rating is zero with no ratings`() {
        val response = mapper.toBaseResponse(tool(ratingSum = 0, ratingsCount = 0))
        assertThat(response.avgRating).isEqualTo(0.0)
    }

    @Test
    fun `context-dependent fields default to false-zero-null`() {
        val response = mapper.toBaseResponse(tool())

        assertThat(response.isTop).isFalse()
        assertThat(response.canManage).isFalse()
        assertThat(response.notesCount).isEqualTo(0L)
        assertThat(response.myRating).isNull()
    }

    @Test
    fun `maps straightforward scalar fields through unchanged`() {
        val source = tool()
        val response = mapper.toBaseResponse(source)

        assertThat(response.id).isEqualTo(source.id)
        assertThat(response.name).isEqualTo("Tool")
        assertThat(response.description).isEqualTo("Desc")
        assertThat(response.stage).isEqualTo("ACCESS")
        assertThat(response.status).isEqualTo("PUBLISHED")
        assertThat(response.ownerName).isEqualTo("Owner")
        assertThat(response.downloads).isEqualTo(5)
        assertThat(response.efficiencyPct).isEqualTo(80)
        assertThat(response.views).isEqualTo(42)
        assertThat(response.ratingsCount).isEqualTo(3)
    }
}
