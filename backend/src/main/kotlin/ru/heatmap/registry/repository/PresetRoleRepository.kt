package ru.heatmap.registry.repository

import ru.heatmap.registry.domain.PresetRole
import org.springframework.data.jpa.repository.JpaRepository

interface PresetRoleRepository : JpaRepository<PresetRole, String>
