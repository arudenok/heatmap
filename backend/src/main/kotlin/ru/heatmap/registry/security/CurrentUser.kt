package ru.heatmap.registry.security

import org.springframework.security.core.context.SecurityContextHolder

// Delegate-реализации *ApiDelegate (см. ru.heatmap.registry.web) генерируются из OpenAPI-спецификации
// и не могут принимать @AuthenticationPrincipal - контракт метода задан схемой. Аутентифицированный
// пользователь (когда он есть) достаётся напрямую из контекста Spring Security.
fun currentPrincipalOrNull(): UserPrincipal? =
    SecurityContextHolder.getContext().authentication?.principal as? UserPrincipal

// Для эндпоинтов, доступных только аутентифицированным пользователям (см. SecurityConfig) -
// на момент вызова delegate principal уже гарантированно есть.
fun currentPrincipal(): UserPrincipal =
    currentPrincipalOrNull() ?: throw IllegalStateException("Требуется аутентификация")
