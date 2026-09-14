package ru.heatmap.registry.web

import org.springframework.http.HttpStatus

open class ApiException(val status: HttpStatus, message: String) : RuntimeException(message)

class NotFoundException(message: String) : ApiException(HttpStatus.NOT_FOUND, message)
class ConflictException(message: String) : ApiException(HttpStatus.CONFLICT, message)
class BadRequestException(message: String) : ApiException(HttpStatus.BAD_REQUEST, message)
class ForbiddenException(message: String) : ApiException(HttpStatus.FORBIDDEN, message)
class UnauthorizedException(message: String) : ApiException(HttpStatus.UNAUTHORIZED, message)
