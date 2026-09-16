package ru.heatmap.registry.domain

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "impact_block")
class ImpactBlock(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(nullable = false, unique = true, length = 16)
    var code: String,

    @Column(nullable = false, length = 8)
    var icon: String,

    @Column(nullable = false)
    var title: String,

    @Column(name = "badge_text", nullable = false, length = 64)
    var badgeText: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "badge_status", nullable = false, length = 16)
    var badgeStatus: ImpactBadgeStatus = ImpactBadgeStatus.ACHIEVED,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,

    @OneToMany(mappedBy = "block", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    var rows: MutableList<ImpactRow> = mutableListOf()
)

enum class ImpactBadgeStatus {
    ACHIEVED, MISSED
}
