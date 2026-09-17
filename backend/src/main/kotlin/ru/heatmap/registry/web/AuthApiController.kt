package ru.heatmap.registry.web

import ru.heatmap.registry.dto.AuthResponse
import ru.heatmap.registry.dto.LoginRequest
import ru.heatmap.registry.dto.MeResponse
import ru.heatmap.registry.dto.RegisterRequest
import ru.heatmap.registry.dto.UpdateProfileRequest
import ru.heatmap.registry.security.currentPrincipal
import ru.heatmap.registry.service.AuthService
import ru.heatmap.registry.web.api.AuthApi
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class AuthApiController(private val authService: AuthService) : AuthApi {

    override fun register(registerRequest: RegisterRequest): ResponseEntity<AuthResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(authService.register(registerRequest))

    override fun login(loginRequest: LoginRequest): ResponseEntity<AuthResponse> =
        ResponseEntity.ok(authService.login(loginRequest))

    override fun getMe(): ResponseEntity<MeResponse> =
        ResponseEntity.ok(authService.me(currentPrincipal().username))

    override fun updateMe(updateProfileRequest: UpdateProfileRequest): ResponseEntity<AuthResponse> =
        ResponseEntity.ok(authService.updateProfile(currentPrincipal(), updateProfileRequest))
}
