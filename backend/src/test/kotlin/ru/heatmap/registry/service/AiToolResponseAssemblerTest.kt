package ru.heatmap.registry.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import ru.heatmap.registry.domain.AiTool
import ru.heatmap.registry.domain.AppUser
import ru.heatmap.registry.domain.Role
import ru.heatmap.registry.domain.ToolRating
import ru.heatmap.registry.domain.ToolStage
import ru.heatmap.registry.domain.ToolStatus
import ru.heatmap.registry.dto.ToolResponse
import ru.heatmap.registry.mapper.ToolMapper
import ru.heatmap.registry.repository.AiToolRepository
import ru.heatmap.registry.repository.AiToolScoreProjection
import ru.heatmap.registry.repository.ToolNoteRepository
import ru.heatmap.registry.repository.ToolRatingRepository
import ru.heatmap.registry.security.UserPrincipal
import java.time.Instant
import java.util.UUID

private data class FakeScore(
    override val id: UUID,
    override val ratingSum: Long,
    override val ratingsCount: Int,
    override val views: Long,
    override val downloads: Int
) : AiToolScoreProjection

class AiToolResponseAssemblerTest {

    private val aiToolRepository = mock<AiToolRepository>()
    private val toolRatingRepository = mock<ToolRatingRepository>()
    private val toolNoteRepository = mock<ToolNoteRepository>()
    private val toolMapper = mock<ToolMapper>()

    private val assembler = AiToolResponseAssembler(aiToolRepository, toolRatingRepository, toolNoteRepository, toolMapper)

    private fun baseResponse(id: UUID) = ToolResponse(
        id = id,
        name = "Tool",
        description = "Desc",
        stage = "ACCESS",
        status = "PUBLISHED",
        roles = listOf("Разработка"),
        framework = emptyList(),
        constraints = emptyList(),
        ownerName = "Owner",
        downloads = 0,
        efficiencyPct = 0,
        isTop = false,
        views = 0,
        avgRating = 0.0,
        ratingsCount = 0,
        canManage = false,
        notesCount = 0L,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    private fun tool(id: UUID = UUID.randomUUID(), createdBy: AppUser? = null) = AiTool(
        id = id,
        name = "Tool",
        description = "Desc",
        ownerName = "Owner",
        createdBy = createdBy
    )

    private fun user(id: UUID = UUID.randomUUID(), role: Role = Role.USER) = AppUser(
        id = id,
        username = "u",
        passwordHash = "h",
        fullName = "Full Name",
        role = role
    )

    @Nested
    inner class ComputeTopIds {

        @Test
        fun `returns empty set when nothing published`() {
            whenever(aiToolRepository.findScoresByStatus(ToolStatus.PUBLISHED)).thenReturn(emptyList())

            assertThat(assembler.computeTopIds()).isEmpty()
        }

        @Test
        fun `single published tool is always top`() {
            val id = UUID.randomUUID()
            whenever(aiToolRepository.findScoresByStatus(ToolStatus.PUBLISHED))
                .thenReturn(listOf(FakeScore(id, ratingSum = 10, ratingsCount = 2, views = 100, downloads = 50)))

            assertThat(assembler.computeTopIds()).containsExactly(id)
        }

        @Test
        fun `top count is 1 percent rounded, minimum 1, and picks highest weighted score`() {
            val winnerId = UUID.randomUUID()
            val scores = mutableListOf(
                FakeScore(winnerId, ratingSum = 50, ratingsCount = 10, views = 1000, downloads = 500)
            )
            // 9 more tools at zero everywhere - clearly worse on every metric than the winner.
            repeat(9) {
                scores += FakeScore(UUID.randomUUID(), ratingSum = 0, ratingsCount = 0, views = 0, downloads = 0)
            }
            whenever(aiToolRepository.findScoresByStatus(ToolStatus.PUBLISHED)).thenReturn(scores)

            val top = assembler.computeTopIds()

            // max(1, round(10 * 0.01)) == 1
            assertThat(top).hasSize(1)
            assertThat(top).containsExactly(winnerId)
        }
    }

    @Nested
    inner class ToResponse {

        @Test
        fun `isTop reflects membership in topIds`() {
            val id = UUID.randomUUID()
            val t = tool(id)
            whenever(toolMapper.toBaseResponse(t)).thenReturn(baseResponse(id))

            val inTop = assembler.toResponse(t, null, setOf(id))
            val notInTop = assembler.toResponse(t, null, emptySet())

            assertThat(inTop.isTop).isTrue()
            assertThat(notInTop.isTop).isFalse()
        }

        @Test
        fun `canManage is true for admin regardless of ownership`() {
            val owner = user()
            val t = tool(createdBy = owner)
            whenever(toolMapper.toBaseResponse(t)).thenReturn(baseResponse(t.id!!))
            val admin = UserPrincipal(user(role = Role.ADMIN))

            val response = assembler.toResponse(t, admin, emptySet())

            assertThat(response.canManage).isTrue()
        }

        @Test
        fun `canManage is true for the owner even if not admin`() {
            val owner = user()
            val t = tool(createdBy = owner)
            whenever(toolMapper.toBaseResponse(t)).thenReturn(baseResponse(t.id!!))
            val ownerPrincipal = UserPrincipal(owner)

            val response = assembler.toResponse(t, ownerPrincipal, emptySet())

            assertThat(response.canManage).isTrue()
        }

        @Test
        fun `canManage is false for a non-owner non-admin`() {
            val owner = user()
            val t = tool(createdBy = owner)
            whenever(toolMapper.toBaseResponse(t)).thenReturn(baseResponse(t.id!!))
            val stranger = UserPrincipal(user(role = Role.USER))

            val response = assembler.toResponse(t, stranger, emptySet())

            assertThat(response.canManage).isFalse()
        }

        @Test
        fun `myRating is populated for an authenticated principal and null otherwise`() {
            val t = tool()
            whenever(toolMapper.toBaseResponse(t)).thenReturn(baseResponse(t.id!!))
            val principal = UserPrincipal(user())
            val rating = ToolRating(tool = t, user = user(), value = 4)
            whenever(toolRatingRepository.findByToolIdAndUserId(t.id!!, principal.id)).thenReturn(rating)

            val withPrincipal = assembler.toResponse(t, principal, emptySet())
            val anonymous = assembler.toResponse(t, null, emptySet())

            assertThat(withPrincipal.myRating).isEqualTo(4)
            assertThat(anonymous.myRating).isNull()
        }

        @Test
        fun `notesCount and segment are only exposed to admins`() {
            val t = tool()
            t.segment = "Секретный сегмент"
            whenever(toolMapper.toBaseResponse(t)).thenReturn(baseResponse(t.id!!))
            val admin = UserPrincipal(user(role = Role.ADMIN))
            val regularUser = UserPrincipal(user(role = Role.USER))
            whenever(toolNoteRepository.countByToolId(t.id!!)).thenReturn(3L)

            val adminResponse = assembler.toResponse(t, admin, emptySet())
            val userResponse = assembler.toResponse(t, regularUser, emptySet())
            val anonymousResponse = assembler.toResponse(t, null, emptySet())

            assertThat(adminResponse.notesCount).isEqualTo(3L)
            assertThat(adminResponse.segment).isEqualTo("Секретный сегмент")

            assertThat(userResponse.notesCount).isEqualTo(0L)
            assertThat(userResponse.segment).isNull()

            assertThat(anonymousResponse.notesCount).isEqualTo(0L)
            assertThat(anonymousResponse.segment).isNull()

            // Only the admin lookup above should have hit the repository - user/anonymous never triggered it.
            verify(toolNoteRepository, times(1)).countByToolId(t.id!!)
        }
    }
}
