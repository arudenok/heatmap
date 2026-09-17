package ru.heatmap.registry.repository

import ru.heatmap.registry.domain.PresetConstraint
import org.springframework.data.jpa.repository.JpaRepository

interface PresetConstraintRepository : JpaRepository<PresetConstraint, String>
