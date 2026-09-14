package ru.heatmap.registry.service

import ru.heatmap.registry.domain.AppUser
import ru.heatmap.registry.domain.Role
import ru.heatmap.registry.dto.*
import ru.heatmap.registry.repository.AppUserRepository
import ru.heatmap.registry.security.JwtService
import ru.heatmap.registry.security.UserPrincipal
import ru.heatmap.registry.web.BadRequestException
import ru.heatmap.registry.web.ConflictException
import ru.heatmap.registry.web.NotFoundException
import ru.heatmap.registry.web.UnauthorizedException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.AuthenticationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val appUserRepository: AppUserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val authenticationManager: AuthenticationManager
) {

    @Transactional
    fun register(request: RegisterRequest): AuthResponse {
        if (appUserRepository.existsByUsernameIgnoreCase(request.username)) {
            throw ConflictException("Пользователь с таким именем уже существует")
        }
        if (appUserRepository.existsByEmailIgnoreCase(request.email)) {
            throw ConflictException("Пользователь с таким логином Сигма уже зарегистрирован")
        }

        val user = AppUser(
            username = request.username.trim(),
            email = request.email.trim(),
            passwordHash = passwordEncoder.encode(request.password),
            fullName = request.fullName.trim(),
            role = Role.USER
        )
        val saved = appUserRepository.save(user)
        val token = jwtService.generateToken(saved.username, saved.role.name, saved.id!!)
        return AuthResponse(token, saved.toMeResponse())
    }

    fun login(request: LoginRequest): AuthResponse {
        val authentication = try {
            authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken(request.usernameOrEmail.trim(), request.password)
            )
        } catch (ex: AuthenticationException) {
            throw UnauthorizedException("Неверное имя пользователя или пароль, либо учётная запись заблокирована")
        }

        val principal = authentication.principal as UserPrincipal
        val user = appUserRepository.findByUsernameIgnoreCase(principal.username)
            ?: throw UnauthorizedException("Пользователь не найден")
        val token = jwtService.generateToken(user.username, user.role.name, user.id!!)
        return AuthResponse(token, user.toMeResponse())
    }

    fun me(username: String): MeResponse {
        val user = appUserRepository.findByUsernameIgnoreCase(username)
            ?: throw UnauthorizedException("Пользователь не найден")
        return user.toMeResponse()
    }

    /** Пользователь редактирует собственный профиль: ФИО, логин Сигма и, опционально, пароль. */
    @Transactional
    fun updateProfile(principal: UserPrincipal, request: UpdateProfileRequest): MeResponse {
        val user = appUserRepository.findByIdOrNull(principal.id)
            ?: throw NotFoundException("Пользователь не найден")

        request.fullName?.let {
            val trimmed = it.trim()
            if (trimmed.isBlank()) throw BadRequestException("ФИО не может быть пустым")
            user.fullName = trimmed
        }

        request.email?.let {
            val normalized = it.trim()
            if (normalized.isBlank()) throw BadRequestException("Логин Сигма не может быть пустым")
            if (!normalized.equals(user.email, ignoreCase = true) &&
                appUserRepository.existsByEmailIgnoreCase(normalized)
            ) {
                throw ConflictException("Этот логин Сигма уже используется другим пользователем")
            }
            user.email = normalized
        }

        if (!request.newPassword.isNullOrBlank()) {
            if (request.currentPassword.isNullOrBlank() ||
                !passwordEncoder.matches(request.currentPassword, user.passwordHash)
            ) {
                throw UnauthorizedException("Неверный текущий пароль")
            }
            user.passwordHash = passwordEncoder.encode(request.newPassword)
        }

        return appUserRepository.save(user).toMeResponse()
    }
}

// Логин в Spring Security по умолчанию ищет пользователя через UserDetailsService по "username",
// поэтому вход по логину Сигма поддержан отдельно на уровне AppUserDetailsService не требуется:
// достаточно, что username совпадает с логином, введённым пользователем (см. LoginRequest).
fun AppUser.toMeResponse() = MeResponse(
    id = this.id!!,
    username = this.username,
    email = this.email,
    fullName = this.fullName,
    role = this.role.name
)
