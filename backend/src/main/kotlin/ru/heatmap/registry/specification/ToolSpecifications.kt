package ru.heatmap.registry.specification

import ru.heatmap.registry.domain.AiTool
import ru.heatmap.registry.domain.ToolStage
import ru.heatmap.registry.domain.ToolStatus
import org.springframework.data.jpa.domain.Specification
import java.util.UUID

// Спецификации JPA-запросов по AiTool - используются в ToolService (каталог/фильтры реестра),
// ToolModerationService (модерация/архив) и AiToolResponseAssembler (расчёт плашки "Топ").
// Вынесены в отдельный пакет, а не в repository (там только интерфейсы Spring Data) и не
// в service (это не бизнес-логика, а построение условий выборки).

fun statusSpec(status: ToolStatus): Specification<AiTool> =
    Specification { root, _, cb -> cb.equal(root.get<ToolStatus>("status"), status) }

fun tabSpec(tab: String, topIds: Set<UUID>): Specification<AiTool>? =
    when (tab.uppercase()) {
        "TOP" -> Specification { root, _, _ -> root.get<UUID>("id").`in`(topIds) }
        "ACCESS" -> Specification { root, _, cb -> cb.equal(root.get<ToolStage>("stage"), ToolStage.ACCESS) }
        "USAGE" -> Specification { root, _, cb -> cb.equal(root.get<ToolStage>("stage"), ToolStage.USAGE) }
        "HABIT" -> Specification { root, _, cb -> cb.equal(root.get<ToolStage>("stage"), ToolStage.HABIT) }
        "STANDARD" -> Specification { root, _, cb -> cb.equal(root.get<ToolStage>("stage"), ToolStage.STANDARD) }
        else -> null
    }

/** "Тип инструмента" в фильтре - совпадение с любым из выбранных значений (см. AiTool.toolType). */
fun toolTypeSpec(values: List<String>?): Specification<AiTool>? {
    val cleaned = values?.filter { it.isNotBlank() }
    if (cleaned.isNullOrEmpty()) return null
    return Specification { root, _, _ -> root.get<String>("toolType").`in`(cleaned) }
}

/** "Содержит хотя бы одно из значений" - для полей-коллекций (roles, framework, constraints). */
fun collectionContainsAnySpec(field: String, values: List<String>?): Specification<AiTool>? {
    val cleaned = values?.filter { it.isNotBlank() }
    if (cleaned.isNullOrEmpty()) return null
    return Specification { root, query, _ ->
        query.distinct(true)
        root.join<AiTool, String>(field).`in`(cleaned)
    }
}

fun searchSpec(search: String?): Specification<AiTool>? =
    if (search.isNullOrBlank()) null else Specification { root, _, cb ->
        val like = "%${search.trim().lowercase()}%"
        cb.or(
            cb.like(cb.lower(root.get("name")), like),
            cb.like(cb.lower(root.get("description")), like),
            cb.like(cb.lower(root.get("ownerName")), like)
        )
    }
