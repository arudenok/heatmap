package ru.heatmap.registry.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.security.web.util.matcher.OrRequestMatcher
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val userDetailsService: AppUserDetailsService
) : OncePerRequestFilter() {

    private val publicMatcher = OrRequestMatcher(
        pathPattern("/api/auth/register"),
        pathPattern("/api/auth/login"),
        pathPattern("/actuator/health"),
        pathPattern("/actuator/info")
    )

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        publicMatcher.matches(request)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")
        if (authHeader.isNullOrBlank() || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        val token = authHeader.substring(7)
        val username = jwtService.extractUsername(token)

        if (username != null && SecurityContextHolder.getContext().authentication == null) {
            val userDetails = runCatching { userDetailsService.loadUserByUsername(username) }.getOrNull()
            if (userDetails != null && jwtService.isTokenValid(token, username) && userDetails.isEnabled) {
                val authToken = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
                authToken.details = WebAuthenticationDetailsSource().buildDetails(request)
                SecurityContextHolder.getContext().authentication = authToken
            }
        }
        filterChain.doFilter(request, response)
    }
}
