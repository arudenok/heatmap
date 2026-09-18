package ru.heatmap.registry.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import ru.heatmap.registry.domain.AiTool
import ru.heatmap.registry.domain.AppUser
import ru.heatmap.registry.domain.Notification
import ru.heatmap.registry.domain.NotificationType
import ru.heatmap.registry.domain.Role
import ru.heatmap.registry.dto.NotificationResponse
import ru.heatmap.registry.mapper.NotificationMapper
import ru.heatmap.registry.repository.AppUserRepository
import ru.heatmap.registry.repository.NotificationRepository
import ru.heatmap.registry.security.UserPrincipal
import ru.heatmap.registry.web.ForbiddenException
import ru.heatmap.registry.web.NotFoundException
import java.time.Instant
import java.util.Optional
import java.util.UUID

class NotificationServiceTest {

    private val notificationRepository = mock<NotificationRepository>()
    private val appUserRepository = mock<AppUserRepository>()
    private val notificationMapper = mock<NotificationMapper>()

    private val service = NotificationService(notificationRepository, appUserRepository, notificationMapper)

    private fun user(id: UUID = UUID.randomUUID(), role: Role = Role.ADMIN) =
        AppUser(id = id, username = "u-$id", passwordHash = "h", fullName = "Full Name", role = role)

    private fun tool(id: UUID = UUID.randomUUID()) = AiTool(id = id, name = "Tool", description = "Desc", ownerName = "Owner")

    @Nested
    inner class NotifyAdminsOfNewSubmission {
        @Test
        fun `saves one notification per admin`() {
            val admins = listOf(user(), user(), user())
            whenever(appUserRepository.findByRole(Role.ADMIN)).thenReturn(admins)
            val t = tool()

            service.notifyAdminsOfNewSubmission(t)

            val captor = argumentCaptor<Notification>()
            verify(notificationRepository, times(3)).save(captor.capture())
            assertThat(captor.allValues).allSatisfy {
                assertThat(it.type).isEqualTo(NotificationType.NEW_SUBMISSION)
                assertThat(it.toolId).isEqualTo(t.id)
                assertThat(it.toolName).isEqualTo(t.name)
            }
        }
    }

    @Nested
    inner class NotifyAdminsOfNewNote {
        @Test
        fun `excludes the note's own author from notified admins`() {
            val author = user()
            val otherAdmin = user()
            whenever(appUserRepository.findByRole(Role.ADMIN)).thenReturn(listOf(author, otherAdmin))
            val t = tool()

            service.notifyAdminsOfNewNote(t, author.id!!)

            val captor = argumentCaptor<Notification>()
            verify(notificationRepository, times(1)).save(captor.capture())
            assertThat(captor.firstValue.user).isEqualTo(otherAdmin)
            assertThat(captor.firstValue.type).isEqualTo(NotificationType.NEW_TOOL_NOTE)
        }
    }

    @Nested
    inner class NotifyOwnerOfArchive {
        @Test
        fun `no owner means no notification`() {
            val t = tool()

            service.notifyOwnerOfArchive(t, "reason")

            verify(notificationRepository, never()).save(any())
        }

        @Test
        fun `owner present saves TOOL_ARCHIVED with the reason`() {
            val owner = user()
            val t = AiTool(id = UUID.randomUUID(), name = "T", description = "D", ownerName = "O", createdBy = owner)

            service.notifyOwnerOfArchive(t, "Устарел")

            val captor = argumentCaptor<Notification>()
            verify(notificationRepository).save(captor.capture())
            assertThat(captor.firstValue.type).isEqualTo(NotificationType.TOOL_ARCHIVED)
            assertThat(captor.firstValue.reason).isEqualTo("Устарел")
        }
    }

    @Nested
    inner class NotifyOwnerOfModerationResult {
        @Test
        fun `no owner means no notification`() {
            val t = tool()

            service.notifyOwnerOfModerationResult(t, approved = true, reason = "whatever")

            verify(notificationRepository, never()).save(any())
        }

        @Test
        fun `approved forces reason to null`() {
            val owner = user()
            val t = AiTool(id = UUID.randomUUID(), name = "T", description = "D", ownerName = "O", createdBy = owner)

            service.notifyOwnerOfModerationResult(t, approved = true, reason = "ignored")

            val captor = argumentCaptor<Notification>()
            verify(notificationRepository).save(captor.capture())
            assertThat(captor.firstValue.type).isEqualTo(NotificationType.SUBMISSION_APPROVED)
            assertThat(captor.firstValue.reason).isNull()
        }

        @Test
        fun `rejected preserves the reason`() {
            val owner = user()
            val t = AiTool(id = UUID.randomUUID(), name = "T", description = "D", ownerName = "O", createdBy = owner)

            service.notifyOwnerOfModerationResult(t, approved = false, reason = "Плохое качество")

            val captor = argumentCaptor<Notification>()
            verify(notificationRepository).save(captor.capture())
            assertThat(captor.firstValue.type).isEqualTo(NotificationType.SUBMISSION_REJECTED)
            assertThat(captor.firstValue.reason).isEqualTo("Плохое качество")
        }
    }

    @Nested
    inner class ListAndUnreadCount {
        @Test
        fun `list maps notifications through the mapper`() {
            val u = user()
            val n = Notification(user = u, type = NotificationType.NEW_SUBMISSION, toolId = UUID.randomUUID(), toolName = "T")
            whenever(notificationRepository.findByUserIdOrderByCreatedAtDesc(u.id!!)).thenReturn(listOf(n))
            whenever(notificationMapper.toResponse(n)).thenReturn(
                NotificationResponse(n.id ?: UUID.randomUUID(), "NEW_SUBMISSION", "T", "msg", false, Instant.now())
            )

            val result = service.list(UserPrincipal(u))

            assertThat(result).hasSize(1)
        }

        @Test
        fun `unreadCount delegates to repository`() {
            val u = user()
            whenever(notificationRepository.countByUserIdAndReadFalse(u.id!!)).thenReturn(4L)

            assertThat(service.unreadCount(UserPrincipal(u))).isEqualTo(4L)
        }
    }

    @Nested
    inner class MarkRead {
        @Test
        fun `not found throws NotFound`() {
            val id = UUID.randomUUID()
            whenever(notificationRepository.findById(id)).thenReturn(Optional.empty())

            assertThatThrownBy { service.markRead(id, UserPrincipal(user())) }.isInstanceOf(NotFoundException::class.java)
        }

        @Test
        fun `wrong owner throws Forbidden`() {
            val owner = user()
            val n = Notification(id = UUID.randomUUID(), user = owner, type = NotificationType.NEW_SUBMISSION, toolId = null, toolName = "T")
            whenever(notificationRepository.findById(n.id!!)).thenReturn(Optional.of(n))

            assertThatThrownBy { service.markRead(n.id!!, UserPrincipal(user())) }.isInstanceOf(ForbiddenException::class.java)
        }

        @Test
        fun `already read is a no-op`() {
            val owner = user()
            val n = Notification(id = UUID.randomUUID(), user = owner, type = NotificationType.NEW_SUBMISSION, toolId = null, toolName = "T", read = true)
            whenever(notificationRepository.findById(n.id!!)).thenReturn(Optional.of(n))

            service.markRead(n.id!!, UserPrincipal(owner))

            verify(notificationRepository, never()).save(any())
        }

        @Test
        fun `unread notification is marked read and saved`() {
            val owner = user()
            val n = Notification(id = UUID.randomUUID(), user = owner, type = NotificationType.NEW_SUBMISSION, toolId = null, toolName = "T", read = false)
            whenever(notificationRepository.findById(n.id!!)).thenReturn(Optional.of(n))

            service.markRead(n.id!!, UserPrincipal(owner))

            assertThat(n.read).isTrue()
            verify(notificationRepository).save(n)
        }
    }

    @Nested
    inner class MarkAllRead {
        @Test
        fun `marks every unread notification as read and saves them all`() {
            val owner = user()
            val n1 = Notification(id = UUID.randomUUID(), user = owner, type = NotificationType.NEW_SUBMISSION, toolId = null, toolName = "A")
            val n2 = Notification(id = UUID.randomUUID(), user = owner, type = NotificationType.NEW_TOOL_NOTE, toolId = null, toolName = "B")
            whenever(notificationRepository.findAllByUserIdAndReadFalse(owner.id!!)).thenReturn(listOf(n1, n2))

            service.markAllRead(UserPrincipal(owner))

            assertThat(n1.read).isTrue()
            assertThat(n2.read).isTrue()
            verify(notificationRepository).saveAll(listOf(n1, n2))
        }
    }
}
