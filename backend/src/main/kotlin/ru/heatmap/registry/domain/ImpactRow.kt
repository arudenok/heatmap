package ru.heatmap.registry.domain

import jakarta.persistence.*

@Entity
@Table(name = "impact_row")
class ImpactRow(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

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
