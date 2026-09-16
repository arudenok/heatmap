package ru.heatmap.registry.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.util.UUID

data class RegisterRequest(
    @field:NotBlank(message = "Введите логин Сигма")
    @field:Pattern(regexp = "^[0-9]+$", message = "Логин Сигма должен содержать только цифры")
    val username: String,

    @field:NotBlank(message = "Введите пароль")
    @field:Size(min = 6, max = 128, message = "Пароль должен быть не короче 6 символов")
    val password: String,

    @field:NotBlank(message = "Введите ФИО")
    @field:Size(max = 255)
    val fullName: String
)

data class LoginRequest(
    @field:NotBlank(message = "Введите логин Сигма")
    val username: String,

    @field:NotBlank(message = "Введите пароль")
    val password: String
)

data class AuthResponse(
    val token: String,
    val user: MeResponse
)

data class MeResponse(
    val id: UUID,
    val username: String,
    val fullName: String,
    val role: String
)

data class UpdateProfileRequest(
    @field:Size(max = 255, message = "ФИО: до 255 символов")
    val fullName: String? = null,

    @field:Pattern(regexp = "^[0-9]+$", message = "Логин Сигма должен содержать только цифры")
    @field:Size(max = 255)
    val username: String? = null,

    val currentPassword: String? = null,

    @field:Size(min = 6, max = 128, message = "Пароль должен быть не короче 6 символов")
    val newPassword: String? = null
)
