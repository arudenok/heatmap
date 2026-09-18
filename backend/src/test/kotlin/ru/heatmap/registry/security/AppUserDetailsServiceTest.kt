package ru.heatmap.registry.security

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.security.core.userdetails.UsernameNotFoundException
import ru.heatmap.registry.domain.AppUser
import ru.heatmap.registry.domain.Role
import ru.heatmap.registry.repository.AppUserRepository
import java.util.UUID

class AppUserDetailsServiceTest {

    private val appUserRepository = mock<AppUserRepository>()
    private val service = AppUserDetailsService(appUserRepository)

    @Test
    fun `returns a UserPrincipal wrapping the found user`() {
        val appUser = AppUser(
            id = UUID.randomUUID(),
            username = "ivan",
            passwordHash = "hash",
            fullName = "Ivan Ivanov",
            role = Role.USER
        )
        whenever(appUserRepository.findByUsernameIgnoreCase("ivan")).thenReturn(appUser)

        val result = service.loadUserByUsername("ivan")

        assertThat(result).isInstanceOf(UserPrincipal::class.java)
        assertThat(result.username).isEqualTo("ivan")
    }

    @Test
    fun `throws UsernameNotFoundException when the user does not exist`() {
        whenever(appUserRepository.findByUsernameIgnoreCase("ghost")).thenReturn(null)

        assertThatThrownBy { service.loadUserByUsername("ghost") }
            .isInstanceOf(UsernameNotFoundException::class.java)
    }
}
