package ru.heatmap.registry.domain

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * Справочник пресетов "Роль / направление" (см. AiTool.roles). Раньше варианты для выбора
 * в форме добавления и в фильтре на главной собирались только из уже существующих у
 * инструментов значений (см. ToolService.filterOptions) - из-за этого роль, которую ещё
 * никому не присвоили, нельзя было выбрать иначе как через "свой вариант". Теперь базовый
 * набор - реальные строки в этой таблице (заполняется в 010-create-preset-tables.sql), а не
 * константа в коде сервиса - то же самое, что и раньше сделали для PresetConstraint.
 */
@Entity
@Table(name = "preset_role")
class PresetRole(
    @Id
    var role: String = ""
)
