package com.tapandgo.feature.home

/** Dashboard cards the user can reorder, hide and pin. */
enum class DashboardCard { STATUS, QUICK_ACTIONS, RECENT_SCANS, RECENT_RUNS, PROFILES, FAVORITES, DEVICE_INFO }

/** Pure layout rules, persisted as names so unknown/removed cards are dropped safely. */
data class DashboardLayout(
    val order: List<DashboardCard> = DashboardCard.entries,
    val hidden: Set<DashboardCard> = emptySet(),
    val pinned: Set<DashboardCard> = setOf(DashboardCard.STATUS),
) {
    /** Visible cards: pinned first (in order), then the rest. */
    val visible: List<DashboardCard>
        get() = normalized().let { o -> o.filter { it in pinned } + o.filter { it !in pinned } }.filter { it !in hidden }

    fun normalized(): List<DashboardCard> = (order + DashboardCard.entries).distinct()

    fun move(card: DashboardCard, delta: Int): DashboardLayout {
        val list = normalized().toMutableList()
        val i = list.indexOf(card)
        val j = (i + delta).coerceIn(0, list.lastIndex)
        if (i < 0 || i == j) return this
        list.add(j, list.removeAt(i))
        return copy(order = list)
    }

    fun toggleHidden(card: DashboardCard) = copy(hidden = if (card in hidden) hidden - card else hidden + card)
    fun togglePinned(card: DashboardCard) = copy(pinned = if (card in pinned) pinned - card else pinned + card)

    fun encode(): Triple<String, String, String> =
        Triple(normalized().joinToString(",") { it.name }, hidden.joinToString(",") { it.name }, pinned.joinToString(",") { it.name })

    companion object {
        private fun parse(s: String?): List<DashboardCard> =
            s.orEmpty().split(',').mapNotNull { n -> DashboardCard.entries.firstOrNull { it.name == n } }

        fun decode(order: String?, hidden: String?, pinned: String?): DashboardLayout {
            val d = DashboardLayout()
            return DashboardLayout(
                order = parse(order).ifEmpty { d.order },
                hidden = parse(hidden).toSet(),
                pinned = if (pinned == null) d.pinned else parse(pinned).toSet(),
            )
        }
    }
}
