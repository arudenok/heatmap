package ru.heatmap.registry.web

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
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NotificationsApiControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private fun login(username: String, password: String): String {
        val body = mapOf("username" to username, "password" to password)
        val result = mockMvc.perform(
            post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))
        ).andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("token").asString()
    }

    private fun adminToken() = login("1000", "admin123")
    private fun userToken() = login("1001", "user123")

    /** Заводит заявку от "1001" - это создаёт уведомление NEW_SUBMISSION всем админам ("1000"). */
    private fun triggerAdminNotification(): String {
        val body = mapOf(
            "name" to "Tool ${UUID.randomUUID()}",
            "description" to "Описание",
            "roles" to listOf("Разработка"),
            "sourceLabel" to "https://sc-ci.example.local/tools/${UUID.randomUUID()}"
        )
        mockMvc.perform(
            post("/api/tools").header("Authorization", "Bearer ${userToken()}")
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))
        ).andExpect(status().isCreated)
        return userToken()
    }

    @Test
    fun `all notification endpoints require auth`() {
        mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/notifications/unread-count")).andExpect(status().isUnauthorized)
        mockMvc.perform(post("/api/notifications/${UUID.randomUUID()}/read")).andExpect(status().isUnauthorized)
        mockMvc.perform(post("/api/notifications/read-all")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `new submission creates an unread notification for admins, then it can be marked read`() {
        triggerAdminNotification()
        val admin = adminToken()

        val listResult = mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer $admin"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].read").value(false))
            .andReturn()
        val notificationId = objectMapper.readTree(listResult.response.contentAsString)[0].get("id").asString()

        mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", "Bearer $admin"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.count", org.hamcrest.Matchers.greaterThanOrEqualTo(1)))

        mockMvc.perform(post("/api/notifications/$notificationId/read").header("Authorization", "Bearer $admin"))
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer $admin"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.id=='$notificationId')].read").value(true))
    }

    @Test
    fun `marking a notification owned by another user fails with 403`() {
        triggerAdminNotification()
        val admin = adminToken()
        val listResult = mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer $admin")).andReturn()
        val notificationId = objectMapper.readTree(listResult.response.contentAsString)[0].get("id").asString()

        mockMvc.perform(post("/api/notifications/$notificationId/read").header("Authorization", "Bearer ${userToken()}"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `marking an unknown notification fails with 404`() {
        mockMvc.perform(post("/api/notifications/${UUID.randomUUID()}/read").header("Authorization", "Bearer ${adminToken()}"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `markAllNotificationsRead marks every notification of the caller as read`() {
        triggerAdminNotification()
        triggerAdminNotification()
        val admin = adminToken()

        mockMvc.perform(post("/api/notifications/read-all").header("Authorization", "Bearer $admin"))
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer $admin"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.read==false)]").isEmpty)
    }
}
