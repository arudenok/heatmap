package ru.heatmap.registry.repository

import ru.heatmap.registry.domain.AppUser
import ru.heatmap.registry.domain.Role
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AppUserRepository : JpaRepository<AppUser, UUID> {
    fun findByUsernameIgnoreCase(username: String): AppUser?
    fun existsByUsernameIgnoreCase(username: String): Boolean

    // Нужно, чтобы разослать уведомление о новой заявке на модерацию всем администраторам.
    fun findByRole(role: Role): List<AppUser>

    // Поиск по логину Сигма (=username) или ФИО (см. AdminUserService.findAll) - оба параметра
    // получают одну и ту же строку поиска, отфильтровано на уровне БД, а не в памяти.
    fun findByUsernameContainingIgnoreCaseOrFullNameContainingIgnoreCase(
        username: String,
        fullName: String
    ): List<AppUser>
}
