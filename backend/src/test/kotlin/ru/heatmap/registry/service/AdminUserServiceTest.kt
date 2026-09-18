package ru.heatmap.registry.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import ru.heatmap.registry.domain.AppUser
import ru.heatmap.registry.domain.Role
import ru.heatmap.registry.dto.UserResponse
import ru.heatmap.registry.mapper.UserMapper
import ru.heatmap.registry.repository.AppUserRepository
import ru.heatmap.registry.repository.NotificationRepository
import ru.heatmap.registry.repository.ToolDownloadRepository
import ru.heatmap.registry.repository.ToolNoteRepository
import ru.heatmap.registry.repository.ToolRatingRepository
import ru.heatmap.registry.web.BadRequestException
import ru.heatmap.registry.web.ConflictException
import ru.heatmap.registry.web.NotFoundException
import java.time.Instant
import java.util.Optional
import java.util.UUID

class AdminUserServiceTest {

    private val appUserRepository = mock<AppUserRepository>()
    private val toolRatingRepository = mock<ToolRatingRepository>()
    private val toolDownloadRepository = mock<ToolDownloadRepository>()
    private val notificationRepository = mock<NotificationRepository>()
    private val toolNoteRepository = mock<ToolNoteRepository>()
    private val userMapper = mock<UserMapper>()

    private val service = AdminUserService(
        appUserRepository,
        toolRatingRepository,
        toolDownloadRepository,
        notificationRepository,
        toolNoteRepository,
        userMapper
    )

    private fun user(id: UUID = UUID.randomUUID(), role: Role = Role.USER) =
        AppUser(id = id, username = "u-$id", passwordHash = "h", fullName = "Full Name", role = role)

    private fun stubMapper() {
        whenever(userMapper.toUserResponse(any())).thenAnswer {
            val u = it.arguments[0] as AppUser
            UserResponse(u.id!!, u.username, u.fullName, u.role.name, u.enabled, Instant.now())
        }
    }

    @Nested
    inner class FindAll {
        @Test
        fun `blank search lists everyone`() {
            stubMapper()
            whenever(appUserRepository.findAll()).thenReturn(listOf(user()))

            val result = service.findAll(null)

            assertThat(result).hasSize(1)
            verify(appUserRepository).findAll()
        }

        @Test
        fun `non-blank search delegates to the search query`() {
            stubMapper()
            whenever(appUserRepository.findByUsernameContainingIgnoreCaseOrFullNameContainingIgnoreCase("ivan", "ivan"))
                .thenReturn(listOf(user()))

            val result = service.findAll("  ivan  ")

            assertThat(result).hasSize(1)
            verify(appUserRepository)
                .findByUsernameContainingIgnoreCaseOrFullNameContainingIgnoreCase("ivan", "ivan")
        }
    }

    @Nested
    inner class UpdateRole {
        @Test
        fun `user not found throws NotFound`() {
            val id = UUID.randomUUID()
            whenever(appUserRepository.findById(id)).thenReturn(Optional.empty())

            assertThatThrownBy { service.updateRole(id, "ADMIN", UUID.randomUUID()) }
                .isInstanceOf(NotFoundException::class.java)
        }

        @Test
        fun `invalid role string throws BadRequest`() {
            val u = user()
            whenever(appUserRepository.findById(u.id!!)).thenReturn(Optional.of(u))

            assertThatThrownBy { service.updateRole(u.id!!, "SUPERUSER", UUID.randomUUID()) }
                .isInstanceOf(BadRequestException::class.java)
        }

        @Test
        fun `self demotion throws Conflict`() {
            val u = user(role = Role.ADMIN)
            whenever(appUserRepository.findById(u.id!!)).thenReturn(Optional.of(u))

            assertThatThrownBy { service.updateRole(u.id!!, "USER", u.id!!) }
                .isInstanceOf(ConflictException::class.java)
        }

        @Test
        fun `self reassignment to ADMIN is allowed`() {
            val u = user(role = Role.ADMIN)
            whenever(appUserRepository.findById(u.id!!)).thenReturn(Optional.of(u))
            whenever(appUserRepository.save(any())).thenAnswer { it.arguments[0] }
            stubMapper()

            service.updateRole(u.id!!, "ADMIN", u.id!!)

            assertThat(u.role).isEqualTo(Role.ADMIN)
        }

        @Test
        fun `updates another user's role`() {
            val u = user(role = Role.USER)
            whenever(appUserRepository.findById(u.id!!)).thenReturn(Optional.of(u))
            whenever(appUserRepository.save(any())).thenAnswer { it.arguments[0] }
            stubMapper()

            service.updateRole(u.id!!, "admin", UUID.randomUUID())

            assertThat(u.role).isEqualTo(Role.ADMIN)
        }
    }

    @Nested
    inner class UpdateEnabled {
        @Test
        fun `user not found throws NotFound`() {
            val id = UUID.randomUUID()
            whenever(appUserRepository.findById(id)).thenReturn(Optional.empty())

            assertThatThrownBy { service.updateEnabled(id, false, UUID.randomUUID()) }
                .isInstanceOf(NotFoundException::class.java)
        }

        @Test
        fun `self disable throws Conflict`() {
            val u = user()
            whenever(appUserRepository.findById(u.id!!)).thenReturn(Optional.of(u))

            assertThatThrownBy { service.updateEnabled(u.id!!, false, u.id!!) }
                .isInstanceOf(ConflictException::class.java)
        }

        @Test
        fun `self enable is allowed`() {
            val u = user()
            whenever(appUserRepository.findById(u.id!!)).thenReturn(Optional.of(u))
            whenever(appUserRepository.save(any())).thenAnswer { it.arguments[0] }
            stubMapper()

            service.updateEnabled(u.id!!, true, u.id!!)

            assertThat(u.enabled).isTrue()
        }

        @Test
        fun `disabling another user is allowed`() {
            val u = user()
            whenever(appUserRepository.findById(u.id!!)).thenReturn(Optional.of(u))
            whenever(appUserRepository.save(any())).thenAnswer { it.arguments[0] }
            stubMapper()

            service.updateEnabled(u.id!!, false, UUID.randomUUID())

            assertThat(u.enabled).isFalse()
        }
    }

    @Nested
    inner class Delete {
        @Test
        fun `user not found throws NotFound`() {
            val id = UUID.randomUUID()
            whenever(appUserRepository.findById(id)).thenReturn(Optional.empty())

            assertThatThrownBy { service.delete(id, UUID.randomUUID()) }
                .isInstanceOf(NotFoundException::class.java)
        }

        @Test
        fun `self delete throws Conflict`() {
            val u = user()
            whenever(appUserRepository.findById(u.id!!)).thenReturn(Optional.of(u))

            assertThatThrownBy { service.delete(u.id!!, u.id!!) }.isInstanceOf(ConflictException::class.java)
        }

        @Test
        fun `deleting another user cleans up dependent rows in order first`() {
            val u = user()
            whenever(appUserRepository.findById(u.id!!)).thenReturn(Optional.of(u))

            service.delete(u.id!!, UUID.randomUUID())

            val order = inOrder(toolRatingRepository, toolDownloadRepository, notificationRepository, toolNoteRepository, appUserRepository)
            order.verify(toolRatingRepository).deleteAllByUserId(u.id!!)
            order.verify(toolDownloadRepository).deleteAllByUserId(u.id!!)
            order.verify(notificationRepository).deleteAllByUserId(u.id!!)
            order.verify(toolNoteRepository).deleteAllByAuthorId(u.id!!)
            order.verify(appUserRepository).delete(u)
        }
    }
}
