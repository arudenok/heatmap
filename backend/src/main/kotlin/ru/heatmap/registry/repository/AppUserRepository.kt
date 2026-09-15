package ru.heatmap.registry.repository

import ru.heatmap.registry.domain.AppUser
import ru.heatmap.registry.domain.Role
import org.springframework.data.jpa.repository.JpaRepository

interface AppUserRepository : JpaRepository<AppUser, Long> {
    fun findByUsernameIgnoreCase(username: String): AppUser?
    fun existsByUsernameIgnoreCase(username: String): Boolean

    // Нужно, чтобы разослать уведомление о новой заявке на модерацию всем администраторам.
    fun findByRole(role: Role): List<AppUser>
}
