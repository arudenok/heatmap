package ru.heatmap.registry.security

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import ru.heatmap.registry.config.JwtProperties
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.Date
import java.util.Base64
import java.util.UUID
import javax.crypto.SecretKey

@Component
class JwtService(private val jwtProperties: JwtProperties) {

    private val signingKey: SecretKey by lazy {
        val keyBytes = Base64.getDecoder().decode(jwtProperties.secret)
        Keys.hmacShaKeyFor(keyBytes)
    }

    fun generateToken(username: String, role: String, userId: UUID): String {
        val now = Instant.now()
        val expiry = now.plusSeconds(jwtProperties.accessTokenTtlMinutes * 60)
        return Jwts.builder()
            .subject(username)
            .claim("role", role)
            .claim("uid", userId.toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(signingKey)
            .compact()
    }

    fun extractUsername(token: String): String? = runCatching {
        parseClaims(token).subject
    }.getOrNull()

    fun extractRole(token: String): String? = runCatching {
        parseClaims(token)["role"] as? String
    }.getOrNull()

    fun isTokenValid(token: String, username: String): Boolean = runCatching {
        val claims = parseClaims(token)
        claims.subject == username && claims.expiration.after(Date())
    }.getOrDefault(false)

    private fun parseClaims(token: String) =
        Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .payload
}
