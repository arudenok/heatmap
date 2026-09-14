package ru.heatmap.registry.web

import ru.heatmap.registry.dto.AuthResponse
import ru.heatmap.registry.dto.LoginRequest
import ru.heatmap.registry.dto.MeResponse
import ru.heatmap.registry.dto.RegisterRequest
import ru.heatmap.registry.dto.UpdateProfileRequest
import ru.heatmap.registry.security.UserPrincipal
import ru.heatmap.registry.service.AuthService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@Valid @RequestBody request: RegisterRequest): AuthResponse = authService.register(request)

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): AuthResponse = authService.login(request)

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: UserPrincipal): MeResponse = authService.me(principal.username)

    @PatchMapping("/me")
    fun updateMe(
        @Valid @RequestBody request: UpdateProfileRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): MeResponse = authService.updateProfile(principal, request)
}
