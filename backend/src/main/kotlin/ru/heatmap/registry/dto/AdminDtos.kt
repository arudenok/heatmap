package ru.heatmap.registry.dto

import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class UserResponse(
    val id: UUID,
    val username: String,
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
