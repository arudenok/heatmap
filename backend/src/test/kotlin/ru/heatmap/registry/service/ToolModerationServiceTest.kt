package ru.heatmap.registry.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import ru.heatmap.registry.domain.AiTool
import ru.heatmap.registry.domain.ToolStatus
import ru.heatmap.registry.dto.ToolResponse
import ru.heatmap.registry.repository.AiToolRepository
import ru.heatmap.registry.security.UserPrincipal
import ru.heatmap.registry.domain.AppUser
import ru.heatmap.registry.domain.Role
import ru.heatmap.registry.web.BadRequestException
import ru.heatmap.registry.web.NotFoundException
import java.time.Instant
import java.util.Optional
import java.util.UUID

class ToolModerationServiceTest {

    private val aiToolRepository = mock<AiToolRepository>()
    private val notificationService = mock<NotificationService>()
    private val toolResponseAssembler = mock<AiToolResponseAssembler>()

    private val service = ToolModerationService(aiToolRepository, notificationService, toolResponseAssembler)

    private fun tool(id: UUID = UUID.randomUUID(), status: ToolStatus = ToolStatus.PENDING) =
        AiTool(id = id, name = "Tool", description = "Desc", ownerName = "Owner", status = status)

    private fun fakeResponse(id: UUID) = ToolResponse(
        id = id, name = "Tool", description = "Desc", stage = "ACCESS", status = "PENDING",
        roles = emptyList(), framework = emptyList(), constraints = emptyList(), ownerName = "Owner",
        downloads = 0, efficiencyPct = 0, isTop = false, views = 0, avgRating = 0.0, ratingsCount = 0,
        canManage = false, notesCount = 0L, createdAt = Instant.now(), updatedAt = Instant.now()
    )

    private fun stubAssembler() {
        whenever(toolResponseAssembler.computeTopIds()).thenReturn(emptySet())
        whenever(toolResponseAssembler.toResponse(any(), any(), any())).thenAnswer {
            fakeResponse((it.arguments[0] as AiTool).id!!)
        }
    }

    private fun admin() = UserPrincipal(AppUser(UUID.randomUUID(), "1000", "h", "Admin", Role.ADMIN))

    @Nested
    inner class PendingModeration {
        @Test
        fun `lists pending tools`() {
            whenever(aiToolRepository.findAll(any<org.springframework.data.jpa.domain.Specification<AiTool>>()))
                .thenReturn(listOf(tool()))
            stubAssembler()

            assertThat(service.pendingModeration(admin())).hasSize(1)
        }
    }

    @Nested
    inner class Approve {
        @Test
        fun `not found throws NotFound`() {
            val id = UUID.randomUUID()
            whenever(aiToolRepository.findById(id)).thenReturn(Optional.empty())

            assertThatThrownBy { service.approve(id) }.isInstanceOf(NotFoundException::class.java)
        }

        @Test
        fun `publishes and notifies owner`() {
            val t = tool(status = ToolStatus.PENDING)
            t.rejectionReason = "old"
            whenever(aiToolRepository.findById(t.id!!)).thenReturn(Optional.of(t))
            whenever(aiToolRepository.save(any())).thenAnswer { it.arguments[0] }
            stubAssembler()

            service.approve(t.id!!)

            assertThat(t.status).isEqualTo(ToolStatus.PUBLISHED)
            assertThat(t.rejectionReason).isNull()
            verify(notificationService).notifyOwnerOfModerationResult(t, true, null)
        }
    }

    @Nested
    inner class Reject {
        @Test
        fun `not found throws NotFound`() {
            val id = UUID.randomUUID()
            whenever(aiToolRepository.findById(id)).thenReturn(Optional.empty())

            assertThatThrownBy { service.reject(id, "reason") }.isInstanceOf(NotFoundException::class.java)
        }

        @Test
        fun `rejects with trimmed reason and notifies owner`() {
            val t = tool()
            whenever(aiToolRepository.findById(t.id!!)).thenReturn(Optional.of(t))
            whenever(aiToolRepository.save(any())).thenAnswer { it.arguments[0] }
            stubAssembler()

            service.reject(t.id!!, "  Не соответствует требованиям  ")

            assertThat(t.status).isEqualTo(ToolStatus.REJECTED)
            assertThat(t.rejectionReason).isEqualTo("Не соответствует требованиям")
            verify(notificationService).notifyOwnerOfModerationResult(t, false, "Не соответствует требованиям")
        }
    }

    @Nested
    inner class Archived {
        @Test
        fun `lists archived tools`() {
            whenever(aiToolRepository.findAll(any<org.springframework.data.jpa.domain.Specification<AiTool>>()))
                .thenReturn(listOf(tool(status = ToolStatus.ARCHIVED)))
            stubAssembler()

            assertThat(service.archived(admin())).hasSize(1)
        }
    }

    @Nested
    inner class Archive {
        @Test
        fun `not found throws NotFound`() {
            val id = UUID.randomUUID()
            whenever(aiToolRepository.findById(id)).thenReturn(Optional.empty())

            assertThatThrownBy { service.archive(id, "reason") }.isInstanceOf(NotFoundException::class.java)
        }

        @Test
        fun `non-published tool throws BadRequest`() {
            val t = tool(status = ToolStatus.PENDING)
            whenever(aiToolRepository.findById(t.id!!)).thenReturn(Optional.of(t))

            assertThatThrownBy { service.archive(t.id!!, null) }.isInstanceOf(BadRequestException::class.java)
        }

        @Test
        fun `archives a published tool with a reason`() {
            val t = tool(status = ToolStatus.PUBLISHED)
            whenever(aiToolRepository.findById(t.id!!)).thenReturn(Optional.of(t))
            whenever(aiToolRepository.save(any())).thenAnswer { it.arguments[0] }
            stubAssembler()

            service.archive(t.id!!, "  Устарел  ")

            assertThat(t.status).isEqualTo(ToolStatus.ARCHIVED)
            verify(notificationService).notifyOwnerOfArchive(t, "Устарел")
        }

        @Test
        fun `archives a published tool with a blank reason mapped to null`() {
            val t = tool(status = ToolStatus.PUBLISHED)
            whenever(aiToolRepository.findById(t.id!!)).thenReturn(Optional.of(t))
            whenever(aiToolRepository.save(any())).thenAnswer { it.arguments[0] }
            stubAssembler()

            service.archive(t.id!!, "   ")

            verify(notificationService).notifyOwnerOfArchive(eq(t), isNull())
        }
    }

    @Nested
    inner class Restore {
        @Test
        fun `not found throws NotFound`() {
            val id = UUID.randomUUID()
            whenever(aiToolRepository.findById(id)).thenReturn(Optional.empty())

            assertThatThrownBy { service.restore(id) }.isInstanceOf(NotFoundException::class.java)
        }

        @Test
        fun `non-archived tool throws BadRequest`() {
            val t = tool(status = ToolStatus.PUBLISHED)
            whenever(aiToolRepository.findById(t.id!!)).thenReturn(Optional.of(t))

            assertThatThrownBy { service.restore(t.id!!) }.isInstanceOf(BadRequestException::class.java)
        }

        @Test
        fun `restores an archived tool back to published`() {
            val t = tool(status = ToolStatus.ARCHIVED)
            whenever(aiToolRepository.findById(t.id!!)).thenReturn(Optional.of(t))
            whenever(aiToolRepository.save(any())).thenAnswer { it.arguments[0] }
            stubAssembler()

            service.restore(t.id!!)

            assertThat(t.status).isEqualTo(ToolStatus.PUBLISHED)
        }
    }
}
