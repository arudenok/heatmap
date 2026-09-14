package ru.heatmap.registry.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "heatmap.jwt")
data class JwtProperties(
    var secret: String = "",
    var accessTokenTtlMinutes: Long = 60
)

@ConfigurationProperties(prefix = "heatmap.cors")
data class CorsProperties(
    var allowedOrigins: List<String> = listOf("http://localhost:5173")
)
