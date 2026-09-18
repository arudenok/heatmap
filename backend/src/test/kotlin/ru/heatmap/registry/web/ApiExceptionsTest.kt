package ru.heatmap.registry.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.util.UUID

class ApiExceptionsTest {

    @Test
    fun `NotFoundException carries 404 and the given message`() {
        val ex = NotFoundException("not found")
        assertThat(ex.status).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(ex.message).isEqualTo("not found")
    }

    @Test
    fun `ConflictException carries 409 and the given message`() {
        val ex = ConflictException("conflict")
        assertThat(ex.status).isEqualTo(HttpStatus.CONFLICT)
        assertThat(ex.message).isEqualTo("conflict")
    }

    @Test
    fun `BadRequestException carries 400 and the given message`() {
        val ex = BadRequestException("bad request")
        assertThat(ex.status).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(ex.message).isEqualTo("bad request")
    }

    @Test
    fun `ForbiddenException carries 403 and the given message`() {
        val ex = ForbiddenException("forbidden")
        assertThat(ex.status).isEqualTo(HttpStatus.FORBIDDEN)
        assertThat(ex.message).isEqualTo("forbidden")
    }

    @Test
    fun `UnauthorizedException carries 401 and the given message`() {
        val ex = UnauthorizedException("unauthorized")
        assertThat(ex.status).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(ex.message).isEqualTo("unauthorized")
    }

    @Test
    fun `DuplicateSourceLabelException carries 409, tool id-name and a formatted message`() {
        val toolId = UUID.randomUUID()
        val ex = DuplicateSourceLabelException(toolId, "MyTool")

        assertThat(ex.status).isEqualTo(HttpStatus.CONFLICT)
        assertThat(ex.toolId).isEqualTo(toolId)
        assertThat(ex.toolName).isEqualTo("MyTool")
        assertThat(ex.message).isEqualTo("Такая ссылка уже используется инструментом «MyTool»")
    }
}
