package ru.heatmap.registry.security

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import ru.heatmap.registry.domain.AppUser
import ru.heatmap.registry.domain.Role
import java.util.UUID

class CurrentUserTest {

    @BeforeEach
    @AfterEach
    fun clearContext() {
        SecurityContextHolder.clearContext()
    }

    private fun principal() = UserPrincipal(
        AppUser(id = UUID.randomUUID(), username = "ivan", passwordHash = "h", fullName = "Ivan", role = Role.USER)
    )

    @Test
    fun `currentPrincipalOrNull returns null when unauthenticated`() {
        assertThat(currentPrincipalOrNull()).isNull()
    }

    @Test
    fun `currentPrincipalOrNull returns the principal when authenticated`() {
        val principal = principal()
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(principal, null, principal.authorities)

        assertThat(currentPrincipalOrNull()).isSameAs(principal)
    }

    @Test
    fun `currentPrincipal throws when unauthenticated`() {
        assertThatThrownBy { currentPrincipal() }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `currentPrincipal returns the principal when authenticated`() {
        val principal = principal()
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(principal, null, principal.authorities)

        assertThat(currentPrincipal()).isSameAs(principal)
    }
}
