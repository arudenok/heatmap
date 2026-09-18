package ru.heatmap.registry.config

import tools.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * Сквозные проверки SecurityConfig/JwtAuthenticationFilter/GlobalExceptionHandler через реальные
 * HTTP-запросы - в дополнение к точечным юнит-тестам самих этих классов, здесь важно убедиться,
 * что всё правильно связано друг с другом внутри реального Spring-контекста.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private fun userToken(): String {
        val body = mapOf("username" to "1001", "password" to "user123")
        val result = mockMvc.perform(
            post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))
        ).andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("token").asString()
    }

    @Test
    fun `public registry listing is open to anonymous callers`() {
        mockMvc.perform(get("/api/tools")).andExpect(status().isOk)
    }

    @Test
    fun `mine endpoint requires authentication`() {
        mockMvc.perform(get("/api/tools/mine")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `admin-only endpoint is unauthorized without a token and forbidden for a plain user`() {
        mockMvc.perform(get("/api/admin/tools/pending")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/admin/tools/pending").header("Authorization", "Bearer ${userToken()}"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `auth endpoints work without any Authorization header`() {
        val loginBody = mapOf("username" to "1000", "password" to "admin123")
        mockMvc.perform(
            post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(loginBody))
        ).andExpect(status().isOk)
    }

    @Test
    fun `a malformed bearer token on a protected endpoint fails with 401, not 500`() {
        mockMvc.perform(get("/api/tools/mine").header("Authorization", "Bearer not-a-real-jwt"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `validation failures surface as 400 with non-empty fieldErrors end to end`() {
        val body = mapOf("username" to "", "password" to "", "fullName" to "")
        mockMvc.perform(
            post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.fieldErrors").isNotEmpty)
    }
}
