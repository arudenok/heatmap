package ru.heatmap.registry.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.core.authority.SimpleGrantedAuthority
import ru.heatmap.registry.domain.AppUser
import ru.heatmap.registry.domain.Role
import java.util.UUID

class UserPrincipalTest {

    private fun user(role: Role = Role.USER, enabled: Boolean = true) = AppUser(
        id = UUID.randomUUID(),
        username = "ivan",
        passwordHash = "hash",
        fullName = "Ivan Ivanov",
        role = role,
        enabled = enabled
    )

    @Test
    fun `exposes id, role, username and password from the wrapped user`() {
        val appUser = user()
        val principal = UserPrincipal(appUser)

        assertThat(principal.id).isEqualTo(appUser.id)
        assertThat(principal.role).isEqualTo("USER")
        assertThat(principal.username).isEqualTo("ivan")
        assertThat(principal.password).isEqualTo("hash")
    }

    @Test
    fun `authorities reflect ADMIN role`() {
        val principal = UserPrincipal(user(role = Role.ADMIN))
        assertThat(principal.authorities).containsExactly(SimpleGrantedAuthority("ROLE_ADMIN"))
    }

    @Test
    fun `authorities reflect USER role`() {
        val principal = UserPrincipal(user(role = Role.USER))
        assertThat(principal.authorities).containsExactly(SimpleGrantedAuthority("ROLE_USER"))
    }

    @Test
    fun `isEnabled reflects the user's enabled flag`() {
        assertThat(UserPrincipal(user(enabled = true)).isEnabled).isTrue()
        assertThat(UserPrincipal(user(enabled = false)).isEnabled).isFalse()
    }

    @Test
    fun `account status flags are always true`() {
        val principal = UserPrincipal(user())
        assertThat(principal.isAccountNonExpired).isTrue()
        assertThat(principal.isAccountNonLocked).isTrue()
        assertThat(principal.isCredentialsNonExpired).isTrue()
    }
}
