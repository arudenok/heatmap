package ru.heatmap.registry.repository

import java.util.UUID

// Лёгкая проекция для AiToolResponseAssembler.computeTopIds - только числовые поля, нужные для
// расчёта скора "Топ", без eager-коллекций roles/framework/constraints, которые findAll(Specification)
// тянул бы вместе с каждым инструментом (см. AiTool.roles и др. - FetchType.EAGER).
interface AiToolScoreProjection {
    val id: UUID
    val ratingSum: Long
    val ratingsCount: Int
    val views: Long
    val downloads: Int
}
