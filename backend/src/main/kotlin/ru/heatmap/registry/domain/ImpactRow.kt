package ru.heatmap.registry.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "impact_row")
class ImpactRow(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "block_id", nullable = false)
    var block: ImpactBlock,

    @Column(nullable = false)
    var label: String,

    // "value" - зарезервированное слово в H2 2.x, поэтому колонка называется иначе
    @Column(name = "metric_value", nullable = false, length = 64)
    var value: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "color_variant", nullable = false, length = 16)
    var colorVariant: ImpactColorVariant = ImpactColorVariant.DEFAULT,

    @Column(name = "is_footer", nullable = false)
    var isFooter: Boolean = false,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0
)

enum class ImpactColorVariant {
    DEFAULT, BLUE, GREEN, GOLD
}
