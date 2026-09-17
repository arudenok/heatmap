package ru.heatmap.registry.mapper

import ru.heatmap.registry.domain.AiTool
import ru.heatmap.registry.dto.ToolResponse
import org.mapstruct.Mapper
import org.mapstruct.Mapping

// isTop/myRating/canManage/notesCount зависят от контекста запроса (topIds, текущий пользователь,
// заметки на модерации - см. AiToolResponseAssembler) и здесь не считаются - сервис переопределяет
// их через .copy() после вызова toBaseResponse.
@Mapper(componentModel = "spring")
abstract class ToolMapper {

    @Mapping(target = "roles", expression = "java(sorted(tool.getRoles()))")
    @Mapping(target = "framework", expression = "java(sorted(tool.getFramework()))")
    @Mapping(target = "constraints", expression = "java(sorted(tool.getConstraints()))")
    @Mapping(target = "avgRating", expression = "java(averageRating(tool))")
    @Mapping(target = "isTop", constant = "false")
    @Mapping(target = "canManage", constant = "false")
    @Mapping(target = "notesCount", constant = "0L")
    @Mapping(target = "myRating", ignore = true)
    abstract fun toBaseResponse(tool: AiTool): ToolResponse

    protected fun sorted(values: Set<String>): List<String> = values.sorted()

    protected fun averageRating(tool: AiTool): Double =
        if (tool.ratingsCount > 0) tool.ratingSum.toDouble() / tool.ratingsCount else 0.0
}
