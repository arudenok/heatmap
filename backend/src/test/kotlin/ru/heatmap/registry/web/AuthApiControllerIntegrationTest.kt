package ru.heatmap.registry.web

import tools.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthApiControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private fun adminToken(): String {
        val body = mapOf("username" to "1000", "password" to "admin123")
        val result = mockMvc.perform(
            post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))
        ).andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("token").asString()
    }

    @Nested
    inner class Register {

        @Test
        fun `succeeds with a fresh valid username`() {
            val body = mapOf("username" to "2000", "password" to "password1", "fullName" to "Тестов Тест Тестович")
            mockMvc.perform(
                post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.token").isNotEmpty)
                .andExpect(jsonPath("$.user.username").value("2000"))
                .andExpect(jsonPath("$.user.role").value("USER"))
        }

        @Test
        fun `fails with 409 for a duplicate username`() {
            val body = mapOf("username" to "1000", "password" to "password1", "fullName" to "Дубликат Дубликатов")
            mockMvc.perform(
                post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))
            ).andExpect(status().isConflict)
        }

        @Test
        fun `fails validation for a blank username`() {
            val body = mapOf("username" to "", "password" to "password1", "fullName" to "Тестов Тест Тестович")
            mockMvc.perform(
                post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.fieldErrors.username").exists())
        }

        @Test
        fun `fails validation for a non-digit username`() {
            val body = mapOf("username" to "abc123", "password" to "password1", "fullName" to "Тестов Тест Тестович")
            mockMvc.perform(
                post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.fieldErrors.username").exists())
        }

        @Test
        fun `fails validation for a too-short password`() {
            val body = mapOf("username" to "2001", "password" to "123", "fullName" to "Тестов Тест Тестович")
            mockMvc.perform(
                post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.fieldErrors.password").exists())
        }
    }

    @Nested
    inner class Login {

        @Test
        fun `succeeds with correct admin credentials`() {
            val body = mapOf("username" to "1000", "password" to "admin123")
            mockMvc.perform(
                post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.token").isNotEmpty)
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
        }

        @Test
        fun `fails with 401 for wrong password`() {
            val body = mapOf("username" to "1000", "password" to "wrong-password")
            mockMvc.perform(
                post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))
            ).andExpect(status().isUnauthorized)
        }

        @Test
        fun `fails with 401 for unknown username`() {
            val body = mapOf("username" to "999999", "password" to "whatever1")
            mockMvc.perform(
                post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))
            ).andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class Me {

        @Test
        fun `getMe fails without a token`() {
            mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized)
        }

        @Test
        fun `getMe succeeds with a valid admin token`() {
            mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer ${adminToken()}"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.username").value("1000"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
        }

        @Test
        fun `updateMe fails without a token`() {
            mockMvc.perform(
                patch("/api/auth/me").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(mapOf("fullName" to "X")))
            ).andExpect(status().isUnauthorized)
        }

        @Test
        fun `updateMe changes fullName only, leaving username untouched`() {
            val token = adminToken()
            val body = mapOf("fullName" to "Новое Имя Администратора")
            mockMvc.perform(
                patch("/api/auth/me").header("Authorization", "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.user.fullName").value("Новое Имя Администратора"))
                .andExpect(jsonPath("$.user.username").value("1000"))
        }

        @Test
        fun `updateMe fails with 401 for wrong current password when changing password`() {
            // Логинимся как обычный пользователь "1001" - меняем его пароль в рамках теста,
            // изменения откатятся благодаря @Transactional, поэтому это безопасно для других тестов.
            val loginBody = mapOf("username" to "1001", "password" to "user123")
            val loginResult = mockMvc.perform(
                post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(loginBody))
            ).andReturn()
            val token = objectMapper.readTree(loginResult.response.contentAsString).get("token").asString()

            val body = mapOf("currentPassword" to "wrong-current-password", "newPassword" to "brandNewPass1")
            mockMvc.perform(
                patch("/api/auth/me").header("Authorization", "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))
            ).andExpect(status().isUnauthorized)
        }
    }
}
