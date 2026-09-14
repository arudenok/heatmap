package ru.heatmap.registry.dto

import jakarta.validation.constraints.NotBlank
import java.time.Instant

data class UserResponse(
    val id: Long,
    val username: String,
    val email: String,
    val fullName: String,
    val role: String,
    val enabled: Boolean,
    val createdAt: Instant
)

data class UpdateUserRoleRequest(
    @field:NotBlank
    val role: String
)

data class UpdateUserEnabledRequest(
    val enabled: Boolean
)
