package ru.heatmap.registry.security

import ru.heatmap.registry.repository.AppUserRepository
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class AppUserDetailsService(private val appUserRepository: AppUserRepository) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val user = appUserRepository.findByUsernameIgnoreCase(username)
            ?: throw UsernameNotFoundException("Пользователь не найден: $username")
        return UserPrincipal(user)
    }
}
