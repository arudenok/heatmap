package ru.heatmap.registry.security

import ru.heatmap.registry.repository.AppUserRepository
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class AppUserDetailsService(private val appUserRepository: AppUserRepository) : UserDetailsService {

    override fun loadUserByUsername(usernameOrEmail: String): UserDetails {
        val user = appUserRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(usernameOrEmail, usernameOrEmail)
            ?: throw UsernameNotFoundException("Пользователь не найден: $usernameOrEmail")
        return UserPrincipal(user)
    }
}
