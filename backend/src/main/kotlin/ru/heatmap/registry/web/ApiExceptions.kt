package ru.heatmap.registry.web

import org.springframework.http.HttpStatus
import java.util.UUID

open class ApiException(val status: HttpStatus, message: String) : RuntimeException(message)

class NotFoundException(message: String) : ApiException(HttpStatus.NOT_FOUND, message)
class ConflictException(message: String) : ApiException(HttpStatus.CONFLICT, message)
class BadRequestException(message: String) : ApiException(HttpStatus.BAD_REQUEST, message)
class ForbiddenException(message: String) : ApiException(HttpStatus.FORBIDDEN, message)
class UnauthorizedException(message: String) : ApiException(HttpStatus.UNAUTHORIZED, message)

// Ссылка на инструмент должна быть уникальна среди активных карточек (На модерации/Опубликован) -
// отклонённые и архивные не мешают повторно завести ту же ссылку в новой заявке (см.
// ToolService.ACTIVE_SOURCE_LABEL_STATUSES). Несёт id и имя карточки-владельца ссылки, чтобы
// фронтенд мог показать предупреждение с кликабельной ссылкой на неё (см. GlobalExceptionHandler).
class DuplicateSourceLabelException(val toolId: UUID, val toolName: String) :
    ApiException(HttpStatus.CONFLICT, "Такая ссылка уже используется инструментом «$toolName»")
