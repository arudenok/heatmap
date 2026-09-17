package ru.heatmap.registry.mapper

import ru.heatmap.registry.domain.ImpactBlock
import ru.heatmap.registry.domain.ImpactRow
import ru.heatmap.registry.dto.ImpactBlockResponse
import ru.heatmap.registry.dto.ImpactRowResponse
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(componentModel = "spring")
abstract class ImpactMapper {

    @Mapping(target = "rows", expression = "java(toSortedRowResponses(block.getRows()))")
    abstract fun toResponse(block: ImpactBlock): ImpactBlockResponse

    // isFooter не мапится автоматически: для булевых свойств вида "isX" MapStruct определяет
    // имя свойства источника по JavaBean-соглашению (без "is"), а имя параметра конструктора
    // цели - буквально ("isFooter"), из-за чего они не совпадают. Обращаемся к геттеру напрямую.
    @Mapping(target = "isFooter", expression = "java(row.isFooter())")
    abstract fun toResponse(row: ImpactRow): ImpactRowResponse

    protected fun toSortedRowResponses(rows: List<ImpactRow>): List<ImpactRowResponse> =
        rows.sortedBy { it.sortOrder }.map { toResponse(it) }
}
