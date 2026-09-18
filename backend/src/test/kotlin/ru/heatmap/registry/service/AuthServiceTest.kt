package ru.heatmap.registry.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import ru.heatmap.registry.domain.AppUser
import ru.heatmap.registry.domain.Role
import ru.heatmap.registry.dto.LoginRequest
import ru.heatmap.registry.dto.MeResponse
import ru.heatmap.registry.dto.RegisterRequest
import ru.heatmap.registry.dto.UpdateProfileRequest
import ru.heatmap.registry.mapper.UserMapper
import ru.heatmap.registry.repository.AppUserRepository
import ru.heatmap.registry.security.JwtService
import ru.heatmap.registry.security.UserPrincipal
import ru.heatmap.registry.web.BadRequestException
import ru.heatmap.registry.web.ConflictException
import ru.heatmap.registry.web.NotFoundException
import ru.heatmap.registry.web.UnauthorizedException
import java.util.Optional
import java.util.UUID

class AuthServiceTest {

    private val appUserRepository = mock<AppUserRepository>()
    private val passwordEncoder = mock<PasswordEncoder>()
    private val jwtService = mock<JwtService>()
    private val authenticationManager = mock<AuthenticationManager>()
    private val userMapper = mock<UserMapper>()

    private val service = AuthService(appUserRepository, passwordEncoder, jwtService, authenticationManager, userMapper)

    private fun user(
        id: UUID = UUID.randomUUID(),
        username: String = "1000",
        role: Role = Role.USER,
        passwordHash: String = "hash"
    ) = AppUser(id = id, username = username, passwordHash = passwordHash, fullName = "Full Name", role = role)

    private fun meResponse(user: AppUser) = MeResponse(user.id!!, user.username, user.fullName, user.role.name)

    @Nested
    inner class Register {
        @Test
        fun `existing username throws Conflict`() {
            whenever(appUserRepository.existsByUsernameIgnoreCase("1000")).thenReturn(true)

            assertThatThrownBy {
                service.register(RegisterRequest(username = "1000", password = "secret1", fullName = "Ivan Ivanov"))
            }.isInstanceOf(ConflictException::class.java)
        }

        @Test
        fun `success encodes password, saves USER role, and returns a token`() {
            whenever(appUserRepository.existsByUsernameIgnoreCase("2000")).thenReturn(false)
            whenever(passwordEncoder.encode("secret1")).thenReturn("encoded")
            // Реальный JPA-репозиторий проставил бы id при сохранении (см. AppUser.id -
            // @GeneratedValue) - конструктор AuthService.register() создаёт AppUser без id,
            // поэтому мок должен сам его сгенерировать, иначе saved.id!! падает с NPE.
            whenever(appUserRepository.save(any())).thenAnswer {
                (it.arguments[0] as AppUser).also { u -> if (u.id == null) u.id = UUID.randomUUID() }
            }
            whenever(jwtService.generateToken(any(), any(), any())).thenReturn("jwt-token")
            whenever(userMapper.toMeResponse(any())).thenAnswer { meResponse(it.arguments[0] as AppUser) }

            val response = service.register(RegisterRequest(username = "2000", password = "secret1", fullName = "Ivan Ivanov"))

            assertThat(response.token).isEqualTo("jwt-token")
            assertThat(response.user.username).isEqualTo("2000")
            verify(appUserRepository).save(org.mockito.kotlin.check { assertThat(it.role).isEqualTo(Role.USER) })
        }
    }

    @Nested
    inner class Login {
        @Test
        fun `authentication failure throws Unauthorized`() {
            whenever(authenticationManager.authenticate(any())).thenThrow(BadCredentialsException("bad"))

            assertThatThrownBy {
                service.login(LoginRequest(username = "1000", password = "wrong"))
            }.isInstanceOf(UnauthorizedException::class.java)
        }

        @Test
        fun `success returns a fresh token`() {
            val u = user(username = "1000")
            val principal = UserPrincipal(u)
            val authToken = UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
            whenever(authenticationManager.authenticate(any())).thenReturn(authToken)
            whenever(appUserRepository.findByUsernameIgnoreCase("1000")).thenReturn(u)
            whenever(jwtService.generateToken(any(), any(), any())).thenReturn("jwt-token")
            whenever(userMapper.toMeResponse(u)).thenReturn(meResponse(u))

            val response = service.login(LoginRequest(username = "1000", password = "correct"))

            assertThat(response.token).isEqualTo("jwt-token")
        }

        @Test
        fun `user vanished after successful authentication throws Unauthorized`() {
            val u = user(username = "1000")
            val principal = UserPrincipal(u)
            val authToken = UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
            whenever(authenticationManager.authenticate(any())).thenReturn(authToken)
            whenever(appUserRepository.findByUsernameIgnoreCase("1000")).thenReturn(null)

            assertThatThrownBy {
                service.login(LoginRequest(username = "1000", password = "correct"))
            }.isInstanceOf(UnauthorizedException::class.java)
        }
    }

    @Nested
    inner class Me {
        @Test
        fun `found returns MeResponse`() {
            val u = user()
            whenever(appUserRepository.findByUsernameIgnoreCase(u.username)).thenReturn(u)
            whenever(userMapper.toMeResponse(u)).thenReturn(meResponse(u))

            assertThat(service.me(u.username).username).isEqualTo(u.username)
        }

        @Test
        fun `not found throws Unauthorized`() {
            whenever(appUserRepository.findByUsernameIgnoreCase("ghost")).thenReturn(null)

            assertThatThrownBy { service.me("ghost") }.isInstanceOf(UnauthorizedException::class.java)
        }
    }

    @Nested
    inner class UpdateProfile {
        private fun stubSave() {
            whenever(appUserRepository.save(any())).thenAnswer { it.arguments[0] }
            whenever(jwtService.generateToken(any(), any(), any())).thenReturn("jwt-token")
            whenever(userMapper.toMeResponse(any())).thenAnswer { meResponse(it.arguments[0] as AppUser) }
        }

        @Test
        fun `blank fullName throws BadRequest`() {
            val u = user()
            whenever(appUserRepository.findById(u.id!!)).thenReturn(Optional.of(u))

            assertThatThrownBy {
                service.updateProfile(UserPrincipal(u), UpdateProfileRequest(fullName = "   "))
            }.isInstanceOf(BadRequestException::class.java)
        }

        @Test
        fun `blank username throws BadRequest`() {
            val u = user()
            whenever(appUserRepository.findById(u.id!!)).thenReturn(Optional.of(u))

            assertThatThrownBy {
                service.updateProfile(UserPrincipal(u), UpdateProfileRequest(username = "   "))
            }.isInstanceOf(BadRequestException::class.java)
        }

        @Test
        fun `changing to an already used username throws Conflict`() {
            val u = user(username = "1000")
            whenever(appUserRepository.findById(u.id!!)).thenReturn(Optional.of(u))
            whenever(appUserRepository.existsByUsernameIgnoreCase("2000")).thenReturn(true)

            assertThatThrownBy {
                service.updateProfile(UserPrincipal(u), UpdateProfileRequest(username = "2000"))
            }.isInstanceOf(ConflictException::class.java)
        }

        @Test
        fun `changing username to itself with different case is not a conflict`() {
            val u = user(username = "1000")
            whenever(appUserRepository.findById(u.id!!)).thenReturn(Optional.of(u))
            stubSave()

            service.updateProfile(UserPrincipal(u), UpdateProfileRequest(username = "1000"))

            verify(appUserRepository, org.mockito.kotlin.never()).existsByUsernameIgnoreCase(any())
        }

        @Test
        fun `new password without currentPassword throws Unauthorized`() {
            val u = user()
            whenever(appUserRepository.findById(u.id!!)).thenReturn(Optional.of(u))

            assertThatThrownBy {
                service.updateProfile(UserPrincipal(u), UpdateProfileRequest(newPassword = "newpass1"))
            }.isInstanceOf(UnauthorizedException::class.java)
        }

        @Test
        fun `new password with wrong currentPassword throws Unauthorized`() {
            val u = user(passwordHash = "hash")
            whenever(appUserRepository.findById(u.id!!)).thenReturn(Optional.of(u))
            whenever(passwordEncoder.matches("wrong", "hash")).thenReturn(false)

            assertThatThrownBy {
                service.updateProfile(
                    UserPrincipal(u),
                    UpdateProfileRequest(currentPassword = "wrong", newPassword = "newpass1")
                )
            }.isInstanceOf(UnauthorizedException::class.java)
        }

        @Test
        fun `new password with correct currentPassword is encoded and saved`() {
            val u = user(passwordHash = "hash")
            whenever(appUserRepository.findById(u.id!!)).thenReturn(Optional.of(u))
            whenever(passwordEncoder.matches("correct", "hash")).thenReturn(true)
            whenever(passwordEncoder.encode("newpass1")).thenReturn("newhash")
            stubSave()

            service.updateProfile(UserPrincipal(u), UpdateProfileRequest(currentPassword = "correct", newPassword = "newpass1"))

            assertThat(u.passwordHash).isEqualTo("newhash")
        }

        @Test
        fun `partial update only touches provided fields`() {
            val u = user(username = "1000")
            u.fullName = "Original Name"
            whenever(appUserRepository.findById(u.id!!)).thenReturn(Optional.of(u))
            stubSave()

            service.updateProfile(UserPrincipal(u), UpdateProfileRequest(fullName = "New Name"))

            assertThat(u.fullName).isEqualTo("New Name")
            assertThat(u.username).isEqualTo("1000")
        }

        @Test
        fun `missing principal user throws NotFound`() {
            val u = user()
            whenever(appUserRepository.findById(u.id!!)).thenReturn(Optional.empty())

            assertThatThrownBy {
                service.updateProfile(UserPrincipal(u), UpdateProfileRequest(fullName = "New Name"))
            }.isInstanceOf(NotFoundException::class.java)
        }
    }
}
