package ru.heatmap.registry.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * Справочник пресетов "Ограничения" (см. AiTool.constraints). Раньше базовый набор значений
 * был захардкожен константой PRESET_CONSTRAINTS прямо в ToolService.kt - список менялся бы
 * только новым деплоем кода. Теперь это реальные строки в этой таблице (заполняется в
 * 010-create-preset-tables.sql), как и PresetRole выше.
 */
@Entity
@Table(name = "preset_constraint")
class PresetConstraint(
    @Id
    @Column(name = "constraint_value")
    var value: String = ""
)
