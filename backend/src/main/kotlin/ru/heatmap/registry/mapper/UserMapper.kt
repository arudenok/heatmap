package ru.heatmap.registry.mapper

import ru.heatmap.registry.domain.AppUser
import ru.heatmap.registry.dto.MeResponse
import ru.heatmap.registry.dto.UserResponse
import org.mapstruct.Mapper

@Mapper(componentModel = "spring")
interface UserMapper {
    fun toMeResponse(user: AppUser): MeResponse
    fun toUserResponse(user: AppUser): UserResponse
}
