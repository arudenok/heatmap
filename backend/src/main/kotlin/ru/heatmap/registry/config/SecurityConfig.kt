package ru.heatmap.registry.config

import ru.heatmap.registry.security.AppUserDetailsService
import ru.heatmap.registry.security.JwtAuthenticationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val userDetailsService: AppUserDetailsService,
    private val corsProperties: CorsProperties
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun authenticationProvider(): DaoAuthenticationProvider {
        val provider = DaoAuthenticationProvider(userDetailsService)
        provider.setPasswordEncoder(passwordEncoder())
        return provider
    }

    @Bean
    fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager =
        config.authenticationManager

    // Без httpBasic()/formLogin() Spring Security не регистрирует свой AuthenticationEntryPoint
    // автоматически и по умолчанию откатывается на Http403ForbiddenEntryPoint - отсутствующий
    // или невалидный/просроченный Bearer-токен тогда даёт 403 вместо 401. Для JWT-API это не
    // просто "не тот код": фронтенд (см. services/api.js) разлогинивает пользователя именно
    // по 401 у запроса, ушедшего с токеном - без явного entry point он никогда не сработает,
    // и пользователь с просроченным токеном застревает без возможности перелогиниться.
    @Bean
    fun authenticationEntryPoint(): AuthenticationEntryPoint = HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOrigins = corsProperties.allowedOrigins
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        configuration.allowedHeaders = listOf("*")
        configuration.allowCredentials = true
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                    .requestMatchers("/h2-console/**").permitAll()
                    // Главная страница реестра доступна без входа; "мои заявки"/"мои инструменты" - только своим
                    .requestMatchers(HttpMethod.GET, "/api/tools/mine", "/api/tools/downloaded").authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/tools/**", "/api/impact/**").permitAll()
                    // Счётчики просмотров и скачиваний доступны и анонимам - реестр открыт без входа
                    .requestMatchers(HttpMethod.POST, "/api/tools/*/view").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/tools/*/download").permitAll()
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                    .anyRequest().authenticated()
            }
            .exceptionHandling { it.authenticationEntryPoint(authenticationEntryPoint()) }
            .headers { it.frameOptions { frame -> frame.sameOrigin() } }
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }
}
