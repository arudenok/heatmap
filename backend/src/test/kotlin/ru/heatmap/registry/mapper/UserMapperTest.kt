package ru.heatmap.registry.mapper

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.heatmap.registry.domain.AppUser
import ru.heatmap.registry.domain.Role
import java.time.Instant
import java.util.UUID

class UserMapperTest {

    private val mapper: UserMapper = UserMapperImpl()

    private fun user() = AppUser(
        id = UUID.randomUUID(),
        username = "ivan",
        passwordHash = "hash",
        fullName = "Ivan Ivanov",
        role = Role.ADMIN,
        enabled = true,
        createdAt = Instant.parse("2024-01-01T00:00:00Z")
    )

    @Test
    fun `toMeResponse maps id, username, fullName and role`() {
        val source = user()
        val response = mapper.toMeResponse(source)

        assertThat(response.id).isEqualTo(source.id)
        assertThat(response.username).isEqualTo("ivan")
        assertThat(response.fullName).isEqualTo("Ivan Ivanov")
        assertThat(response.role).isEqualTo("ADMIN")
    }

    @Test
    fun `toUserResponse maps all fields including enabled and createdAt`() {
        val source = user()
        val response = mapper.toUserResponse(source)

        assertThat(response.id).isEqualTo(source.id)
        assertThat(response.username).isEqualTo("ivan")
        assertThat(response.fullName).isEqualTo("Ivan Ivanov")
        assertThat(response.role).isEqualTo("ADMIN")
        assertThat(response.enabled).isTrue()
        assertThat(response.createdAt).isEqualTo(source.createdAt)
    }
}
