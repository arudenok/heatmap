package ru.heatmap.registry.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import java.util.UUID

class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    @Suppress("unused")
    private fun dummyTarget(x: String) {}

    @Test
    fun `handleDuplicateSourceLabel returns 409 with conflict tool info`() {
        val toolId = UUID.randomUUID()
        val response = handler.handleDuplicateSourceLabel(DuplicateSourceLabelException(toolId, "Foo"))

        assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        assertThat(response.body!!.conflictToolId).isEqualTo(toolId.toString())
        assertThat(response.body!!.conflictToolName).isEqualTo("Foo")
    }

    @Test
    fun `handleApiException maps status and message`() {
        val response = handler.handleApiException(NotFoundException("msg"))

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(response.body!!.message).isEqualTo("msg")
        assertThat(response.body!!.error).isEqualTo("Not Found")
    }

    @Test
    fun `handleValidation collects field errors into a 400`() {
        val method = this::class.java.getDeclaredMethod("dummyTarget", String::class.java)
        val methodParameter = MethodParameter(method, 0)
        val bindingResult = BeanPropertyBindingResult(Any(), "obj")
        bindingResult.addError(FieldError("obj", "name", "Введите имя"))
        val ex = MethodArgumentNotValidException(methodParameter, bindingResult)

        val response = handler.handleValidation(ex)

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body!!.fieldErrors).isEqualTo(mapOf("name" to "Введите имя"))
    }

    @Test
    fun `handleBadCredentials returns 401`() {
        val response = handler.handleBadCredentials(BadCredentialsException("bad"))
        assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `handleAccessDenied returns 403`() {
        val response = handler.handleAccessDenied(AccessDeniedException("denied"))
        assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
    }

    @Test
    fun `handleGeneric returns 500 with the exception message`() {
        val response = handler.handleGeneric(RuntimeException("boom"))
        assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        assertThat(response.body!!.message).isEqualTo("boom")
    }

    @Test
    fun `handleGeneric falls back to a default message when none is provided`() {
        val response = handler.handleGeneric(RuntimeException())
        assertThat(response.body!!.message).isEqualTo("Внутренняя ошибка сервера")
    }
}
