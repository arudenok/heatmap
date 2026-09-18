package ru.heatmap.registry.repository

import ru.heatmap.registry.domain.AiTool
import ru.heatmap.registry.domain.ToolStatus
import org.springframework.data.jpa.domain.Specification

// Общая для ToolService/ToolModerationService/AiToolResponseAssembler спецификация по статусу -
// вынесена сюда, чтобы не дублировать один и тот же private fun в каждом из трёх классов.
fun statusSpec(status: ToolStatus): Specification<AiTool> =
    Specification { root, _, cb -> cb.equal(root.get<ToolStatus>("status"), status) }
