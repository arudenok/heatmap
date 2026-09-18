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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ToolsApiControllerIntegrationTest {

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

    private fun validSourceLabel(): String = "https://sc-ci.example.local/tools/${UUID.randomUUID()}"

    private fun createToolAsUser(token: String, sourceLabel: String = validSourceLabel(), name: String = "Tool ${UUID.randomUUID()}"): JsonTool {
        val body = mapOf(
            "name" to name,
            "description" to "Описание инструмента для теста",
            "roles" to listOf("Разработка"),
            "sourceLabel" to sourceLabel
        )
        val result = mockMvc.perform(
            post("/api/tools").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))
        ).andExpect(status().isCreated).andReturn()
        val node = objectMapper.readTree(result.response.contentAsString)
        return JsonTool(node.get("id").asString(), node.get("status").asString())
    }

    private data class JsonTool(val id: String, val status: String)

    @Nested
    inner class ListAndReadEndpoints {

        @Test
        fun `list tools anonymously returns empty list when none exist`() {
            mockMvc.perform(get("/api/tools").param("tab", "TOP"))
                .andExpect(status().isOk)
        }

        @Test
        fun `getTool returns 404 for an unknown id`() {
            mockMvc.perform(get("/api/tools/${UUID.randomUUID()}")).andExpect(status().isNotFound)
        }

        @Test
        fun `getTool returns 200 for a known id`() {
            val tool = createToolAsUser(userToken())
            mockMvc.perform(get("/api/tools/${tool.id}")).andExpect(status().isOk)
        }

        @Test
        fun `listMyTools requires auth`() {
            mockMvc.perform(get("/api/tools/mine")).andExpect(status().isUnauthorized)
        }

        @Test
        fun `listMyTools succeeds when authenticated`() {
            mockMvc.perform(get("/api/tools/mine").header("Authorization", "Bearer ${userToken()}"))
                .andExpect(status().isOk)
        }

        @Test
        fun `listDownloadedTools requires auth`() {
            mockMvc.perform(get("/api/tools/downloaded")).andExpect(status().isUnauthorized)
        }

        @Test
        fun `listDownloadedTools succeeds when authenticated`() {
            mockMvc.perform(get("/api/tools/downloaded").header("Authorization", "Bearer ${userToken()}"))
                .andExpect(status().isOk)
        }

        @Test
        fun `counts stats and filter-options are public and well formed`() {
            mockMvc.perform(get("/api/tools/counts")).andExpect(status().isOk)
            mockMvc.perform(get("/api/tools/stats")).andExpect(status().isOk)
            mockMvc.perform(get("/api/tools/filter-options"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.hasItem("Разработка")))
                .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.hasItem("Аналитика")))
        }
    }

    @Nested
    inner class CreateTool {

        @Test
        fun `fails without auth`() {
            val body = mapOf("name" to "X", "description" to "Y", "roles" to listOf("Разработка"), "sourceLabel" to validSourceLabel())
            mockMvc.perform(
                post("/api/tools").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))
            ).andExpect(status().isUnauthorized)
        }

        @Test
        fun `non-admin without sourceLabel fails with 400`() {
            val body = mapOf("name" to "X", "description" to "Y", "roles" to listOf("Разработка"))
            mockMvc.perform(
                post("/api/tools").header("Authorization", "Bearer ${userToken()}")
                    .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))
            ).andExpect(status().isBadRequest)
        }

        @Test
        fun `non-admin with valid data creates a PENDING ACCESS tool`() {
            val body = mapOf(
                "name" to "Инструмент теста",
                "description" to "Описание",
                "roles" to listOf("Разработка"),
                "sourceLabel" to validSourceLabel(),
                // Обычному пользователю недоступны эти поля - должны игнорироваться сервисом.
                "stage" to "STANDARD",
                "status" to "PUBLISHED"
            )
            mockMvc.perform(
                post("/api/tools").header("Authorization", "Bearer ${userToken()}")
                    .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.stage").value("ACCESS"))
        }

        @Test
        fun `admin can create without sourceLabel and set stage and status explicitly`() {
            val body = mapOf(
                "name" to "Админский инструмент",
                "description" to "Описание",
                "roles" to listOf("Разработка"),
                "stage" to "USAGE",
                "status" to "PUBLISHED"
            )
            mockMvc.perform(
                post("/api/tools").header("Authorization", "Bearer ${adminToken()}")
                    .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.stage").value("USAGE"))
                .andExpect(jsonPath("$.sourceLabel").value(org.hamcrest.Matchers.nullValue()))
        }

        @Test
        fun `duplicate sourceLabel among active tools fails with 409`() {
            val label = validSourceLabel()
            val first = createToolAsUser(userToken(), sourceLabel = label)

            val body = mapOf(
                "name" to "Второй инструмент",
                "description" to "Описание",
                "roles" to listOf("Разработка"),
                "sourceLabel" to label
            )
            mockMvc.perform(
                post("/api/tools").header("Authorization", "Bearer ${userToken()}")
                    .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))
            )
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.conflictToolId").value(first.id))
        }
    }

    @Nested
    inner class ViewDownloadRate {

        @Test
        fun `registerToolView and registerToolDownload work anonymously`() {
            val tool = createToolAsUser(userToken())
            mockMvc.perform(post("/api/tools/${tool.id}/view")).andExpect(status().isOk)
            mockMvc.perform(post("/api/tools/${tool.id}/download")).andExpect(status().isOk)
        }

        @Test
        fun `authenticated user downloading the same tool twice only counts once`() {
            val tool = createToolAsUser(adminToken())
            val token = userToken()

            val first = mockMvc.perform(post("/api/tools/${tool.id}/download").header("Authorization", "Bearer $token"))
                .andExpect(status().isOk).andReturn()
            val firstDownloads = objectMapper.readTree(first.response.contentAsString).get("downloads").asInt()

            val second = mockMvc.perform(post("/api/tools/${tool.id}/download").header("Authorization", "Bearer $token"))
                .andExpect(status().isOk).andReturn()
            val secondDownloads = objectMapper.readTree(second.response.contentAsString).get("downloads").asInt()

            org.assertj.core.api.Assertions.assertThat(secondDownloads).isEqualTo(firstDownloads)
            org.assertj.core.api.Assertions.assertThat(firstDownloads).isEqualTo(1)
        }

        @Test
        fun `rateTool requires auth`() {
            val tool = createToolAsUser(adminToken())
            mockMvc.perform(
                post("/api/tools/${tool.id}/rating").contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf("rating" to 5)))
            ).andExpect(status().isUnauthorized)
        }

        @Test
        fun `rateTool rejects out-of-range values`() {
            val tool = createToolAsUser(adminToken())
            val token = userToken()
            mockMvc.perform(
                post("/api/tools/${tool.id}/rating").header("Authorization", "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(mapOf("rating" to 0)))
            ).andExpect(status().isBadRequest)
            mockMvc.perform(
                post("/api/tools/${tool.id}/rating").header("Authorization", "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(mapOf("rating" to 6)))
            ).andExpect(status().isBadRequest)
        }

        @Test
        fun `rateTool accepts a valid value and updates avgRating`() {
            val tool = createToolAsUser(adminToken())
            mockMvc.perform(
                post("/api/tools/${tool.id}/rating").header("Authorization", "Bearer ${userToken()}")
                    .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(mapOf("rating" to 4)))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.avgRating").value(4.0))
        }
    }

    @Nested
    inner class UpdateWithdrawDelete {

        @Test
        fun `updateTool by a non-owner non-admin fails with 403`() {
            val tool = createToolAsUser(userToken())
            // Ещё один обычный пользователь - не владелец, не админ.
            val otherToken = registerUser("3000")
            mockMvc.perform(
                patch("/api/tools/${tool.id}").header("Authorization", "Bearer $otherToken")
                    .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(mapOf("name" to "Hacked")))
            ).andExpect(status().isForbidden)
        }

        @Test
        fun `editing a published tool by its owner resubmits it to PENDING`() {
            val userTok = userToken()
            val tool = createToolAsUser(userTok)
            mockMvc.perform(post("/api/admin/tools/${tool.id}/approve").header("Authorization", "Bearer ${adminToken()}"))
                .andExpect(status().isOk).andExpect(jsonPath("$.status").value("PUBLISHED"))

            mockMvc.perform(
                patch("/api/tools/${tool.id}").header("Authorization", "Bearer $userTok")
                    .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(mapOf("description" to "Обновлённое описание")))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("PENDING"))
        }

        @Test
        fun `withdrawTool by a non-owner fails with 403`() {
            val tool = createToolAsUser(userToken())
            val otherToken = registerUser("3001")
            mockMvc.perform(post("/api/tools/${tool.id}/withdraw").header("Authorization", "Bearer $otherToken"))
                .andExpect(status().isForbidden)
        }

        @Test
        fun `withdrawTool on a non-pending tool fails with 400`() {
            val userTok = userToken()
            val tool = createToolAsUser(userTok)
            mockMvc.perform(post("/api/admin/tools/${tool.id}/approve").header("Authorization", "Bearer ${adminToken()}"))
                .andExpect(status().isOk)
            mockMvc.perform(post("/api/tools/${tool.id}/withdraw").header("Authorization", "Bearer $userTok"))
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `withdrawTool by the owner on a pending tool succeeds`() {
            val userTok = userToken()
            val tool = createToolAsUser(userTok)
            mockMvc.perform(post("/api/tools/${tool.id}/withdraw").header("Authorization", "Bearer $userTok"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("DRAFT"))
        }

        @Test
        fun `deleteTool by a non-owner non-admin fails with 403`() {
            val tool = createToolAsUser(userToken())
            val otherToken = registerUser("3002")
            mockMvc.perform(delete("/api/tools/${tool.id}").header("Authorization", "Bearer $otherToken"))
                .andExpect(status().isForbidden)
        }

        @Test
        fun `deleteTool by the owner succeeds`() {
            val userTok = userToken()
            val tool = createToolAsUser(userTok)
            mockMvc.perform(delete("/api/tools/${tool.id}").header("Authorization", "Bearer $userTok"))
                .andExpect(status().isNoContent)
        }

        @Test
        fun `deleteTool by an admin succeeds for any tool`() {
            val tool = createToolAsUser(userToken())
            mockMvc.perform(delete("/api/tools/${tool.id}").header("Authorization", "Bearer ${adminToken()}"))
                .andExpect(status().isNoContent)
        }
    }

    private fun registerUser(username: String): String {
        val body = mapOf("username" to username, "password" to "password1", "fullName" to "Тестовый Пользователь")
        mockMvc.perform(
            post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))
        ).andExpect(status().isCreated)
        return login(username, "password1")
    }
}
