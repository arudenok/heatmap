package ru.heatmap.registry.mapper

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.heatmap.registry.domain.AppUser
import ru.heatmap.registry.domain.Notification
import ru.heatmap.registry.domain.NotificationType
import ru.heatmap.registry.domain.Role
import java.util.UUID

class NotificationMapperTest {

    private val mapper: NotificationMapper = NotificationMapperImpl()

    private val user = AppUser(id = UUID.randomUUID(), username = "u", passwordHash = "h", fullName = "U", role = Role.USER)

    private fun notification(type: NotificationType, reason: String? = null) = Notification(
        id = UUID.randomUUID(),
        user = user,
        type = type,
        toolId = UUID.randomUUID(),
        toolName = "MyTool",
        reason = reason
    )

    @Test
    fun `NEW_SUBMISSION message`() {
        val response = mapper.toResponse(notification(NotificationType.NEW_SUBMISSION))
        assertThat(response.message).isEqualTo("Новая заявка на модерацию: «MyTool»")
    }

    @Test
    fun `SUBMISSION_APPROVED message`() {
        val response = mapper.toResponse(notification(NotificationType.SUBMISSION_APPROVED))
        assertThat(response.message).isEqualTo("Ваша заявка «MyTool» одобрена и опубликована")
    }

    @Test
    fun `SUBMISSION_REJECTED message with a reason`() {
        val response = mapper.toResponse(notification(NotificationType.SUBMISSION_REJECTED, reason = "Плохо описано"))
        assertThat(response.message).isEqualTo("Ваша заявка «MyTool» отклонена модератором: Плохо описано")
    }

    @Test
    fun `SUBMISSION_REJECTED message without a reason`() {
        val response = mapper.toResponse(notification(NotificationType.SUBMISSION_REJECTED, reason = null))
        assertThat(response.message).isEqualTo("Ваша заявка «MyTool» отклонена модератором")
    }

    @Test
    fun `NEW_TOOL_NOTE message`() {
        val response = mapper.toResponse(notification(NotificationType.NEW_TOOL_NOTE))
        assertThat(response.message).isEqualTo("Новая заметка к инструменту «MyTool»")
    }

    @Test
    fun `TOOL_ARCHIVED message with a reason`() {
        val response = mapper.toResponse(notification(NotificationType.TOOL_ARCHIVED, reason = "Устарел"))
        assertThat(response.message).isEqualTo("Ваш инструмент «MyTool» архивирован администратором: Устарел")
    }

    @Test
    fun `TOOL_ARCHIVED message without a reason`() {
        val response = mapper.toResponse(notification(NotificationType.TOOL_ARCHIVED, reason = null))
        assertThat(response.message).isEqualTo("Ваш инструмент «MyTool» архивирован администратором")
    }

    @Test
    fun `scalar fields map through unchanged`() {
        val source = notification(NotificationType.NEW_SUBMISSION)
        val response = mapper.toResponse(source)

        assertThat(response.id).isEqualTo(source.id)
        assertThat(response.type).isEqualTo("NEW_SUBMISSION")
        assertThat(response.toolName).isEqualTo("MyTool")
        assertThat(response.toolId).isEqualTo(source.toolId)
        assertThat(response.read).isFalse()
    }
}
