package ru.heatmap.registry.mapper

import ru.heatmap.registry.domain.ToolNote
import ru.heatmap.registry.dto.ToolNoteResponse
import org.mapstruct.Mapper
import org.mapstruct.Mapping

// canManage зависит от текущего пользователя (см. ToolNoteService) - здесь всегда false,
// сервис переопределяет значение через .copy() после вызова toBaseResponse.
@Mapper(componentModel = "spring")
interface ToolNoteMapper {

    @Mapping(target = "toolId", source = "tool.id")
    @Mapping(target = "authorId", source = "author.id")
    @Mapping(target = "authorName", source = "author.fullName")
    @Mapping(target = "canManage", constant = "false")
    fun toBaseResponse(note: ToolNote): ToolNoteResponse
}
