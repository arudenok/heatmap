package ru.heatmap.registry.service

import ru.heatmap.registry.domain.AppUser
import ru.heatmap.registry.domain.Role
import ru.heatmap.registry.dto.UserResponse
import ru.heatmap.registry.repository.AppUserRepository
import ru.heatmap.registry.repository.NotificationRepository
import ru.heatmap.registry.repository.ToolDownloadRepository
import ru.heatmap.registry.repository.ToolNoteRepository
import ru.heatmap.registry.repository.ToolRatingRepository
import ru.heatmap.registry.web.BadRequestException
import ru.heatmap.registry.web.ConflictException
import ru.heatmap.registry.web.NotFoundException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AdminUserService(
    private val appUserRepository: AppUserRepository,
    private val toolRatingRepository: ToolRatingRepository,
    private val toolDownloadRepository: ToolDownloadRepository,
    private val notificationRepository: NotificationRepository,
    private val toolNoteRepository: ToolNoteRepository
) {

    /** Список пользователей с опциональным поиском по логину Сигма (=username) или ФИО. */
    fun findAll(search: String? = null): List<UserResponse> {
        val all = appUserRepository.findAll()
        val filtered = if (search.isNullOrBlank()) {
            all
        } else {
            val query = search.trim().lowercase()
            all.filter { user ->
                user.username.lowercase().contains(query) ||
                    user.fullName.lowercase().contains(query)
            }
        }
        return filtered.map { it.toResponse() }
    }

    @Transactional
    fun updateRole(userId: UUID, roleValue: String, actingAdminId: UUID): UserResponse {
        val user = appUserRepository.findByIdOrNull(userId) ?: throw NotFoundException("Пользователь не найден")
        val role = runCatching { Role.valueOf(roleValue.uppercase()) }
            .getOrElse { throw BadRequestException("Некорректная роль: $roleValue") }

        if (user.id == actingAdminId && role != Role.ADMIN) {
            throw ConflictException("Нельзя снять с себя права администратора")
        }
        user.role = role
        return appUserRepository.save(user).toResponse()
    }

    @Transactional
    fun updateEnabled(userId: UUID, enabled: Boolean, actingAdminId: UUID): UserResponse {
        val user = appUserRepository.findByIdOrNull(userId) ?: throw NotFoundException("Пользователь не найден")
        if (user.id == actingAdminId && !enabled) {
            throw ConflictException("Нельзя заблокировать собственную учётную запись")
        }
        user.enabled = enabled
        return appUserRepository.save(user).toResponse()
    }

    // Удаление аккаунта (например, тестовых/мусорных учёток). Заявки и инструменты
    // пользователя не удаляются - у ai_tool.created_by стоит ON DELETE SET NULL,
    // сам инструмент и его owner_name (текстовое поле) остаются как есть.
    // А вот оценки и скачивания пользователя удаляем явно - на эти таблицы
    // ON DELETE SET NULL не настроен, и без явной очистки FK не даст удалить app_user.
    @Transactional
    fun delete(userId: UUID, actingAdminId: UUID) {
        val user = appUserRepository.findByIdOrNull(userId) ?: throw NotFoundException("Пользователь не найден")
        if (user.id == actingAdminId) {
            throw ConflictException("Нельзя удалить собственную учётную запись")
        }
        toolRatingRepository.deleteAllByUserId(userId)
        toolDownloadRepository.deleteAllByUserId(userId)
        notificationRepository.deleteAllByUserId(userId)
        toolNoteRepository.deleteAllByAuthorId(userId)
        appUserRepository.delete(user)
    }

    private fun AppUser.toResponse() = UserResponse(
        id = this.id!!,
        username = this.username,
        fullName = this.fullName,
        role = this.role.name,
        enabled = this.enabled,
        createdAt = this.createdAt
    )
}
