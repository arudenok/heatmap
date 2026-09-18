package ru.heatmap.registry.web

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import ru.heatmap.registry.repository.ImpactBlockRepository
import ru.heatmap.registry.repository.ImpactRowRepository
import ru.heatmap.registry.domain.ImpactBlock
import ru.heatmap.registry.domain.ImpactRow
import java.util.UUID
import tools.jackson.databind.ObjectMapper

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminApiControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var impactBlockRepository: ImpactBlockRepository

    @Autowired
    private lateinit var impactRowRepository: ImpactRowRepository

    private fun login(username: String, password: String): String {
        val body = mapOf("username" to username, "password" to password)
        val result = mockMvc.perform(
            post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))
        ).andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("token").asString()
    }

    private fun adminToken() = login("1000", "admin123")
    private fun userToken() = login("1001", "user123")

    private fun registerUser(username: String, password: String = "password1"): String {
        val body = mapOf("username" to username, "password" to password, "fullName" to "Тестовый Пользователь $username")
        mockMvc.perform(
            post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))
        ).andExpect(status().isCreated)
        return login(username, password)
    }

    private fun createToolAsUser(token: String, name: String = "Tool ${UUID.randomUUID()}"): String {
        val body = mapOf(
            "name" to name,
            "description" to "Описание инструмента для теста",
            "roles" to listOf("Разработка"),
            "sourceLabel" to "https://sc-ci.example.local/tools/${UUID.randomUUID()}"
        )
        val result = mockMvc.perform(
            post("/api/tools").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))
        ).andExpect(status().isCreated).andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("id").asString()
    }

    @Nested
    inner class AccessControl {

        @Test
        fun `admin endpoint requires auth`() {
            mockMvc.perform(get("/api/admin/tools/pending")).andExpect(status().isUnauthorized)
        }

        @Test
        fun `admin endpoint forbidden for a plain USER`() {
            mockMvc.perform(get("/api/admin/tools/pending").header("Authorization", "Bearer ${userToken()}"))
                .andExpect(status().isForbidden)
        }
    }

    @Nested
    inner class ModerationFlow {

        @Test
        fun `pending list shows a freshly created tool, approve publishes it`() {
            val toolId = createToolAsUser(userToken())
            mockMvc.perform(get("/api/admin/tools/pending").header("Authorization", "Bearer ${adminToken()}"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[?(@.id=='$toolId')]").isNotEmpty)

            mockMvc.perform(post("/api/admin/tools/$toolId/approve").header("Authorization", "Bearer ${adminToken()}"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
        }

        @Test
        fun `reject requires a non-blank reason`() {
            val toolId = createToolAsUser(userToken())
            mockMvc.perform(
                post("/api/admin/tools/$toolId/reject").header("Authorization", "Bearer ${adminToken()}")
                    .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(mapOf("reason" to "")))
            ).andExpect(status().isBadRequest)
        }

        @Test
        fun `reject with a reason sets REJECTED and the reason`() {
            val toolId = createToolAsUser(userToken())
            mockMvc.perform(
                post("/api/admin/tools/$toolId/reject").header("Authorization", "Bearer ${adminToken()}")
                    .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(mapOf("reason" to "Не подходит")))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Не подходит"))
        }
    }

    @Nested
    inner class ArchiveFlow {

        @Test
        fun `archiving a non-published tool fails with 400`() {
            val toolId = createToolAsUser(userToken())
            mockMvc.perform(
                post("/api/admin/tools/$toolId/archive").header("Authorization", "Bearer ${adminToken()}")
                    .contentType(MediaType.APPLICATION_JSON).content("{}")
            ).andExpect(status().isBadRequest)
        }

        @Test
        fun `archive then restore round-trips through ARCHIVED back to PUBLISHED`() {
            val admin = adminToken()
            val toolId = createToolAsUser(userToken())
            mockMvc.perform(post("/api/admin/tools/$toolId/approve").header("Authorization", "Bearer $admin"))
                .andExpect(status().isOk)

            mockMvc.perform(
                post("/api/admin/tools/$toolId/archive").header("Authorization", "Bearer $admin")
                    .contentType(MediaType.APPLICATION_JSON).content("{}")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("ARCHIVED"))

            mockMvc.perform(get("/api/admin/tools/archived").header("Authorization", "Bearer $admin"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[?(@.id=='$toolId')]").isNotEmpty)

            mockMvc.perform(post("/api/admin/tools/$toolId/restore").header("Authorization", "Bearer $admin"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
        }

        @Test
        fun `restoring a non-archived tool fails with 400`() {
            val toolId = createToolAsUser(userToken())
            mockMvc.perform(post("/api/admin/tools/$toolId/restore").header("Authorization", "Bearer ${adminToken()}"))
                .andExpect(status().isBadRequest)
        }
    }

    @Nested
    inner class ToolNotes {

        @Test
        fun `create then list notes, only the author can edit or delete`() {
            val admin = adminToken()
            val toolId = createToolAsUser(userToken())

            val createResult = mockMvc.perform(
                post("/api/admin/tools/$toolId/notes").header("Authorization", "Bearer $admin")
                    .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(mapOf("text" to "Заметка")))
            ).andExpect(status().isOk).andReturn()
            val noteId = objectMapper.readTree(createResult.response.contentAsString).get("id").asString()

            mockMvc.perform(get("/api/admin/tools/$toolId/notes").header("Authorization", "Bearer $admin"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].canManage").value(true))

            // Второй администратор - не автор заметки.
            val secondAdminToken = registerUser("4001")
            mockMvc.perform(
                patch("/api/admin/users/${meId(secondAdminToken)}/role").header("Authorization", "Bearer $admin")
                    .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(mapOf("role" to "ADMIN")))
            ).andExpect(status().isOk)
            val secondAdmin = login("4001", "password1")

            mockMvc.perform(
                patch("/api/admin/tools/notes/$noteId").header("Authorization", "Bearer $secondAdmin")
                    .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(mapOf("text" to "Чужая правка")))
            ).andExpect(status().isForbidden)

            mockMvc.perform(delete("/api/admin/tools/notes/$noteId").header("Authorization", "Bearer $secondAdmin"))
                .andExpect(status().isForbidden)

            mockMvc.perform(delete("/api/admin/tools/notes/$noteId").header("Authorization", "Bearer $admin"))
                .andExpect(status().isNoContent)
        }

        private fun meId(token: String): String {
            val result = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer $token")).andReturn()
            return objectMapper.readTree(result.response.contentAsString).get("id").asString()
        }
    }

    @Nested
    inner class Users {

        @Test
        fun `list users includes seeded accounts`() {
            mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer ${adminToken()}"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[?(@.username=='1000')]").isNotEmpty)
                .andExpect(jsonPath("$[?(@.username=='1001')]").isNotEmpty)
        }

        @Test
        fun `admin cannot demote themselves`() {
            val admin = adminToken()
            val meResult = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer $admin")).andReturn()
            val myId = objectMapper.readTree(meResult.response.contentAsString).get("id").asString()

            mockMvc.perform(
                patch("/api/admin/users/$myId/role").header("Authorization", "Bearer $admin")
                    .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(mapOf("role" to "USER")))
            ).andExpect(status().isConflict)
        }

        @Test
        fun `admin cannot disable themselves`() {
            val admin = adminToken()
            val meResult = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer $admin")).andReturn()
            val myId = objectMapper.readTree(meResult.response.contentAsString).get("id").asString()

            mockMvc.perform(
                patch("/api/admin/users/$myId/enabled").header("Authorization", "Bearer $admin")
                    .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(mapOf("enabled" to false)))
            ).andExpect(status().isConflict)
        }

        @Test
        fun `admin cannot delete themselves but can delete another user`() {
            val admin = adminToken()
            val meResult = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer $admin")).andReturn()
            val myId = objectMapper.readTree(meResult.response.contentAsString).get("id").asString()

            mockMvc.perform(delete("/api/admin/users/$myId").header("Authorization", "Bearer $admin"))
                .andExpect(status().isConflict)

            registerUser("4002")
            val throwawayResult = mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer $admin")).andReturn()
            val throwawayId = objectMapper.readTree(throwawayResult.response.contentAsString)
                .first { it.get("username").asString() == "4002" }.get("id").asString()

            mockMvc.perform(delete("/api/admin/users/$throwawayId").header("Authorization", "Bearer $admin"))
                .andExpect(status().isNoContent)
        }
    }

    @Nested
    inner class Impact {

        @Test
        fun `updateImpactRow returns 404 for an unknown id`() {
            mockMvc.perform(
                patch("/api/admin/impact/rows/${UUID.randomUUID()}").header("Authorization", "Bearer ${adminToken()}")
                    .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(mapOf("value" to "42%")))
            ).andExpect(status().isNotFound)
        }

        @Test
        fun `updateImpactRow updates the value of an existing row`() {
            val block = impactBlockRepository.save(
                ImpactBlock(code = "TEST-${UUID.randomUUID()}", icon = "*", title = "Блок", badgeText = "Badge")
            )
            val row = impactRowRepository.save(ImpactRow(block = block, label = "Метрика", value = "10%"))

            mockMvc.perform(
                patch("/api/admin/impact/rows/${row.id}").header("Authorization", "Bearer ${adminToken()}")
                    .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(mapOf("value" to "42%")))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.value").value("42%"))
        }
    }
}
