package ru.heatmap.registry.repository

import ru.heatmap.registry.domain.AppUser
import org.springframework.data.jpa.repository.JpaRepository

interface AppUserRepository : JpaRepository<AppUser, Long> {
    fun findByUsernameIgnoreCase(username: String): AppUser?
    fun findByUsernameIgnoreCaseOrEmailIgnoreCase(username: String, email: String): AppUser?
    fun existsByUsernameIgnoreCase(username: String): Boolean
    fun existsByEmailIgnoreCase(email: String): Boolean
}
