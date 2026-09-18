package ru.heatmap.registry.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import ru.heatmap.registry.domain.AiTool
import ru.heatmap.registry.domain.ToolStage
import ru.heatmap.registry.domain.ToolStatus
import ru.heatmap.registry.specification.collectionContainsAnySpec
import ru.heatmap.registry.specification.searchSpec
import ru.heatmap.registry.specification.statusSpec
import ru.heatmap.registry.specification.tabSpec
import ru.heatmap.registry.specification.toolTypeSpec
import java.time.Instant
import java.time.temporal.ChronoUnit

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AiToolRepositoryIntegrationTest {

    @Autowired
    private lateinit var aiToolRepository: AiToolRepository

    private fun tool(
        name: String = "Tool",
        description: String = "Описание",
        ownerName: String = "Владелец",
        stage: ToolStage = ToolStage.ACCESS,
        status: ToolStatus = ToolStatus.PUBLISHED,
        roles: MutableSet<String> = mutableSetOf(),
        framework: MutableSet<String> = mutableSetOf(),
        constraints: MutableSet<String> = mutableSetOf(),
        toolType: String? = null,
        sourceLabel: String? = null
    ) = aiToolRepository.save(
        AiTool(
            name = name,
            description = description,
            stage = stage,
            status = status,
            roles = roles,
            framework = framework,
            constraints = constraints,
            toolType = toolType,
            sourceLabel = sourceLabel,
            ownerName = ownerName
        )
    )

    @Nested
    inner class Specifications {

        @Test
        fun `statusSpec filters by status`() {
            tool(status = ToolStatus.PUBLISHED)
            tool(status = ToolStatus.PENDING)

            val published = aiToolRepository.findAll(statusSpec(ToolStatus.PUBLISHED))
            assertThat(published).allMatch { it.status == ToolStatus.PUBLISHED }
        }

        @Test
        fun `the duplicate statusSpec in the repository package behaves identically`() {
            tool(status = ToolStatus.PUBLISHED)
            val published = aiToolRepository.findAll(ru.heatmap.registry.repository.statusSpec(ToolStatus.PUBLISHED))
            assertThat(published).isNotEmpty.allMatch { it.status == ToolStatus.PUBLISHED }
        }

        @Test
        fun `tabSpec TOP filters by the given id set, tabSpec for a stage filters by stage, unknown tab is null`() {
            val access = tool(stage = ToolStage.ACCESS)
            tool(stage = ToolStage.USAGE)

            val topOnly = aiToolRepository.findAll(tabSpec("TOP", setOf(access.id!!))!!)
            assertThat(topOnly).extracting<Any> { it.id }.containsExactly(access.id)

            val accessStage = aiToolRepository.findAll(tabSpec("ACCESS", emptySet())!!)
            assertThat(accessStage).allMatch { it.stage == ToolStage.ACCESS }

            assertThat(tabSpec("bogus", emptySet())).isNull()
        }

        @Test
        fun `collectionContainsAnySpec matches tools containing at least one of the given values`() {
            tool(roles = mutableSetOf("Разработка"))
            tool(roles = mutableSetOf("Аналитика"))

            val matches = aiToolRepository.findAll(collectionContainsAnySpec("roles", listOf("Разработка"))!!)
            assertThat(matches).allMatch { it.roles.contains("Разработка") }
            assertThat(matches).noneMatch { it.roles.contains("Аналитика") }

            assertThat(collectionContainsAnySpec("roles", emptyList())).isNull()
            assertThat(collectionContainsAnySpec("roles", null)).isNull()
        }

        @Test
        fun `toolTypeSpec matches tools whose toolType is in the given list`() {
            tool(toolType = "MCP")
            tool(toolType = "Skill")
            tool(toolType = "Agent")

            val matches = aiToolRepository.findAll(toolTypeSpec(listOf("MCP", "Skill"))!!)
            assertThat(matches).extracting<String> { it.toolType }.containsExactlyInAnyOrder("MCP", "Skill")
        }

        @Test
        fun `searchSpec does a case-insensitive substring match on name, description and ownerName`() {
            tool(name = "TestTool", description = "обычное описание", ownerName = "Иванов")
            tool(name = "Другой", description = "обычное описание", ownerName = "Петров")

            val byName = aiToolRepository.findAll(searchSpec("test")!!)
            assertThat(byName).extracting<String> { it.name }.containsExactly("TestTool")

            assertThat(searchSpec(null)).isNull()
            assertThat(searchSpec("  ")).isNull()
        }
    }

    @Nested
    inner class CustomQueries {

        @Test
        fun `count and distinct-value queries reflect persisted tools`() {
            tool(stage = ToolStage.ACCESS, status = ToolStatus.PUBLISHED, roles = mutableSetOf("Разработка", "Тестирование"))
            tool(stage = ToolStage.ACCESS, status = ToolStatus.PUBLISHED, framework = mutableSetOf("Openspec"))
            tool(stage = ToolStage.USAGE, status = ToolStatus.PENDING, constraints = mutableSetOf("ПКАП"), toolType = "MCP")

            assertThat(aiToolRepository.countByStageAndStatus(ToolStage.ACCESS, ToolStatus.PUBLISHED)).isEqualTo(2)
            assertThat(aiToolRepository.countByStatus(ToolStatus.PUBLISHED)).isEqualTo(2)
            assertThat(aiToolRepository.countByStatusAndCreatedAtAfter(ToolStatus.PUBLISHED, Instant.now().minus(1, ChronoUnit.DAYS)))
                .isEqualTo(2)

            assertThat(aiToolRepository.findDistinctRoles()).contains("Разработка", "Тестирование")
            assertThat(aiToolRepository.findDistinctFrameworks()).contains("Openspec")
            assertThat(aiToolRepository.findDistinctConstraints()).contains("ПКАП")
            assertThat(aiToolRepository.findDistinctToolTypes()).contains("MCP")
        }

        @Test
        fun `findFirstBySourceLabelAndStatusIn finds an active match and excludes the given id variant excludes self`() {
            val active = ACTIVE_STATUSES
            val existing = tool(status = ToolStatus.PENDING, sourceLabel = "https://sc-ci.example.local/x")

            val found = aiToolRepository.findFirstBySourceLabelAndStatusIn("https://sc-ci.example.local/x", active)
            assertThat(found?.id).isEqualTo(existing.id)

            val excludingSelf = aiToolRepository.findFirstBySourceLabelAndStatusInAndIdNot(
                "https://sc-ci.example.local/x", active, existing.id!!
            )
            assertThat(excludingSelf).isNull()
        }

        @Test
        fun `findScoresByStatus exposes the projection fields used for the top score calculation`() {
            val saved = tool(status = ToolStatus.PUBLISHED)
            saved.ratingSum = 12
            saved.ratingsCount = 3
            saved.views = 40
            saved.downloads = 5
            aiToolRepository.save(saved)

            val scores = aiToolRepository.findScoresByStatus(ToolStatus.PUBLISHED)
            val projection = scores.first { it.id == saved.id }
            assertThat(projection.ratingSum).isEqualTo(12)
            assertThat(projection.ratingsCount).isEqualTo(3)
            assertThat(projection.views).isEqualTo(40)
            assertThat(projection.downloads).isEqualTo(5)
        }

        private val ACTIVE_STATUSES = listOf(ToolStatus.PENDING, ToolStatus.PUBLISHED)
    }
}
