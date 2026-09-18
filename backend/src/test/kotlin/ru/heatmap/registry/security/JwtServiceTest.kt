package ru.heatmap.registry.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.heatmap.registry.config.JwtProperties
import java.util.Base64
import java.util.UUID

class JwtServiceTest {

    private val validSecret = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
    private val jwtService = JwtService(JwtProperties(secret = validSecret, accessTokenTtlMinutes = 60))

    @Test
    fun `generateToken produces a well-formed JWT`() {
        val token = jwtService.generateToken("alice", "USER", UUID.randomUUID())
        assertThat(token).isNotBlank()
        assertThat(token.split(".")).hasSize(3)
    }

    @Test
    fun `extractUsername returns subject for a valid token`() {
        val token = jwtService.generateToken("bob", "ADMIN", UUID.randomUUID())
        assertThat(jwtService.extractUsername(token)).isEqualTo("bob")
    }

    @Test
    fun `extractUsername returns null for a garbage token`() {
        assertThat(jwtService.extractUsername("not-a-jwt")).isNull()
    }

    @Test
    fun `isTokenValid returns true for a token matching its own username`() {
        val token = jwtService.generateToken("carol", "USER", UUID.randomUUID())
        assertThat(jwtService.isTokenValid(token, "carol")).isTrue()
    }

    @Test
    fun `isTokenValid returns false for a mismatched username`() {
        val token = jwtService.generateToken("carol", "USER", UUID.randomUUID())
        assertThat(jwtService.isTokenValid(token, "someone-else")).isFalse()
    }

    @Test
    fun `isTokenValid returns false for an expired token`() {
        val expiringService = JwtService(JwtProperties(secret = validSecret, accessTokenTtlMinutes = 0))
        val token = expiringService.generateToken("dave", "USER", UUID.randomUUID())
        Thread.sleep(1100)
        assertThat(expiringService.isTokenValid(token, "dave")).isFalse()
    }

    @Test
    fun `isTokenValid returns false for a malformed token`() {
        assertThat(jwtService.isTokenValid("garbage", "dave")).isFalse()
    }
}
