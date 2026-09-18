package ru.heatmap.registry.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import jakarta.servlet.FilterChain

class JwtAuthenticationFilterTest {

    private val jwtService = mock<JwtService>()
    private val userDetailsService = mock<AppUserDetailsService>()
    private val filter = JwtAuthenticationFilter(jwtService, userDetailsService)

    @BeforeEach
    @AfterEach
    fun clearContext() {
        SecurityContextHolder.clearContext()
    }

    private fun enabledUser(username: String = "alice"): UserDetails =
        User.withUsername(username).password("pwd").authorities("ROLE_USER").build()

    // shouldNotFilter/doFilterInternal are protected (inherited from OncePerRequestFilter) -
    // not directly callable from a test in the same package under Kotlin's visibility rules
    // (unlike Java, Kotlin's protected excludes same-package access). Exercised instead through
    // the public doFilter(...) entry point, which internally consults shouldNotFilter before
    // delegating to doFilterInternal.
    @Test
    fun `public auth and actuator paths skip filter logic even with a bearer token`() {
        listOf("/api/auth/register", "/api/auth/login", "/actuator/health", "/actuator/info").forEach { path ->
            val request = MockHttpServletRequest("GET", path)
            request.addHeader("Authorization", "Bearer some-token")
            val response = MockHttpServletResponse()
            val chain = mock<FilterChain>()

            filter.doFilter(request, response, chain)

            verify(chain).doFilter(request, response)
        }
        verify(jwtService, never()).extractUsername(any())
    }

    @Test
    fun `protected paths run the filter logic`() {
        val request = MockHttpServletRequest("GET", "/api/tools")
        request.addHeader("Authorization", "Bearer some-token")
        val response = MockHttpServletResponse()
        val chain = mock<FilterChain>()
        whenever(jwtService.extractUsername("some-token")).thenReturn(null)

        filter.doFilter(request, response, chain)

        verify(jwtService).extractUsername("some-token")
        verify(chain).doFilter(request, response)
    }

    @Test
    fun `no Authorization header - passes through without setting authentication`() {
        val request = MockHttpServletRequest("GET", "/api/tools")
        val response = MockHttpServletResponse()
        val chain = mock<FilterChain>()

        filter.doFilter(request, response, chain)

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
        verify(chain).doFilter(request, response)
    }

    @Test
    fun `non-Bearer Authorization header - passes through without setting authentication`() {
        val request = MockHttpServletRequest("GET", "/api/tools")
        request.addHeader("Authorization", "Basic abc123")
        val response = MockHttpServletResponse()
        val chain = mock<FilterChain>()

        filter.doFilter(request, response, chain)

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
        verify(chain).doFilter(request, response)
    }

    @Test
    fun `valid token for an enabled user sets authentication`() {
        val request = MockHttpServletRequest("GET", "/api/tools")
        request.addHeader("Authorization", "Bearer valid-token")
        val response = MockHttpServletResponse()
        val chain = mock<FilterChain>()

        val principal = enabledUser()
        whenever(jwtService.extractUsername("valid-token")).thenReturn("alice")
        whenever(userDetailsService.loadUserByUsername("alice")).thenReturn(principal)
        whenever(jwtService.isTokenValid("valid-token", "alice")).thenReturn(true)

        filter.doFilter(request, response, chain)

        val authentication = SecurityContextHolder.getContext().authentication
        assertThat(authentication).isNotNull()
        assertThat(authentication!!.principal).isSameAs(principal)
        verify(chain).doFilter(request, response)
    }

    @Test
    fun `disabled user does not get authenticated`() {
        val request = MockHttpServletRequest("GET", "/api/tools")
        request.addHeader("Authorization", "Bearer valid-token")
        val response = MockHttpServletResponse()
        val chain = mock<FilterChain>()

        val disabledUser = User.withUsername("alice").password("pwd").disabled(true).authorities("ROLE_USER").build()
        whenever(jwtService.extractUsername("valid-token")).thenReturn("alice")
        whenever(userDetailsService.loadUserByUsername("alice")).thenReturn(disabledUser)
        whenever(jwtService.isTokenValid("valid-token", "alice")).thenReturn(true)

        filter.doFilter(request, response, chain)

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
        verify(chain).doFilter(request, response)
    }

    @Test
    fun `invalid token does not get authenticated`() {
        val request = MockHttpServletRequest("GET", "/api/tools")
        request.addHeader("Authorization", "Bearer bad-token")
        val response = MockHttpServletResponse()
        val chain = mock<FilterChain>()

        whenever(jwtService.extractUsername("bad-token")).thenReturn("alice")
        whenever(userDetailsService.loadUserByUsername("alice")).thenReturn(enabledUser())
        whenever(jwtService.isTokenValid("bad-token", "alice")).thenReturn(false)

        filter.doFilter(request, response, chain)

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
        verify(chain).doFilter(request, response)
    }

    @Test
    fun `unknown user from token does not propagate and does not authenticate`() {
        val request = MockHttpServletRequest("GET", "/api/tools")
        request.addHeader("Authorization", "Bearer some-token")
        val response = MockHttpServletResponse()
        val chain = mock<FilterChain>()

        whenever(jwtService.extractUsername("some-token")).thenReturn("ghost")
        whenever(userDetailsService.loadUserByUsername("ghost"))
            .thenThrow(org.springframework.security.core.userdetails.UsernameNotFoundException("nope"))

        filter.doFilter(request, response, chain)

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
        verify(chain).doFilter(request, response)
    }

    @Test
    fun `does not overwrite an already-set authentication`() {
        val existing = UsernamePasswordAuthenticationToken(enabledUser("someone-else"), null, emptyList())
        SecurityContextHolder.getContext().authentication = existing

        val request = MockHttpServletRequest("GET", "/api/tools")
        request.addHeader("Authorization", "Bearer valid-token")
        val response = MockHttpServletResponse()
        val chain = mock<FilterChain>()
        whenever(jwtService.extractUsername("valid-token")).thenReturn("alice")

        filter.doFilter(request, response, chain)

        assertThat(SecurityContextHolder.getContext().authentication).isEqualTo(existing)
        verify(userDetailsService, never()).loadUserByUsername(any())
        verify(chain).doFilter(request, response)
    }
}
