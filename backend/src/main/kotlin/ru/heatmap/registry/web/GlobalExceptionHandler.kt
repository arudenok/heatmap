package ru.heatmap.registry.web

import ru.heatmap.registry.dto.ErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

// ErrorResponse - модель, сгенерированная из registry-api.yaml (см. components/schemas/ErrorResponse).
// conflictToolId/conflictToolName заполнены только для DuplicateSourceLabelException (см. ниже) -
// фронтенд (AddToolModal) использует их, чтобы показать имя существующего инструмента кликабельной
// ссылкой на его карточку, а не просто текст ошибки.
@RestControllerAdvice
class GlobalExceptionHandler {

    // Более специфичный обработчик, чем handleApiException ниже - Spring выбирает его для
    // DuplicateSourceLabelException независимо от порядка объявления методов.
    @ExceptionHandler(DuplicateSourceLabelException::class)
    fun handleDuplicateSourceLabel(ex: DuplicateSourceLabelException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(ex.status).body(
            ErrorResponse(
                ex.status.value(),
                ex.status.reasonPhrase,
                ex.message ?: "Ссылка уже используется",
                fieldErrors = emptyMap(),
                conflictToolId = ex.toolId.toString(),
                conflictToolName = ex.toolName
            )
        )

    @ExceptionHandler(ApiException::class)
    fun handleApiException(ex: ApiException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(ex.status)
            .body(ErrorResponse(ex.status.value(), ex.status.reasonPhrase, ex.message ?: "Ошибка", emptyMap()))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val fieldErrors = ex.bindingResult.fieldErrors.associate {
            it.field to (it.defaultMessage ?: "Некорректное значение")
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(400, "Bad Request", "Проверьте правильность заполнения полей", fieldErrors))
    }

    // Ловит гонки на уровне БД, которые не успели превратиться в понятную бизнес-ошибку раньше -
    // например, узкое окно между удалением инструмента администратором и вставкой новой оценки/
    // скачивания от пользователя (см. ToolService.delete/rate/incrementDownload). Раньше такое
    // падало в handleGeneric ниже и отдавало клиенту сырой текст SQL-исключения с именами таблиц
    // и constraint'ов - теперь отдаём нейтральный, безопасный для показа пользователю текст.
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(ex: DataIntegrityViolationException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse(
                409,
                "Conflict",
                "Не удалось выполнить операцию - похоже, связанные данные были изменены или удалены. Обновите страницу и попробуйте снова.",
                emptyMap()
            )
        )

    @ExceptionHandler(BadCredentialsException::class)
    fun handleBadCredentials(ex: BadCredentialsException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse(401, "Unauthorized", "Неверное имя пользователя или пароль", emptyMap()))

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(403, "Forbidden", "Недостаточно прав для выполнения действия", emptyMap()))

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(500, "Internal Server Error", ex.message ?: "Внутренняя ошибка сервера", emptyMap()))
}
