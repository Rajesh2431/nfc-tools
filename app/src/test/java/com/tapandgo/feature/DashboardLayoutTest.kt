package com.tapandgo.feature

import com.tapandgo.feature.home.DashboardCard
import com.tapandgo.feature.home.DashboardLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DashboardLayoutTest {

    @Test
    fun `pinned cards come first and hidden cards are dropped`() {
        val l = DashboardLayout(pinned = setOf(DashboardCard.FAVORITES), hidden = setOf(DashboardCard.DEVICE_INFO))
        assertEquals(DashboardCard.FAVORITES, l.visible.first())
        assertFalse(DashboardCard.DEVICE_INFO in l.visible)
        assertEquals(DashboardCard.entries.size - 1, l.visible.size)
    }

    @Test
    fun `move clamps at the ends`() {
        val l = DashboardLayout().move(DashboardCard.STATUS, -5)
        assertEquals(DashboardCard.STATUS, l.normalized().first())
        val m = DashboardLayout().move(DashboardCard.STATUS, 1)
        assertEquals(listOf(DashboardCard.QUICK_ACTIONS, DashboardCard.STATUS), m.normalized().take(2))
    }

    @Test
    fun `encode decode round trip ignores unknown names and restores missing cards`() {
        val l = DashboardLayout().move(DashboardCard.PROFILES, -3).toggleHidden(DashboardCard.RECENT_RUNS).togglePinned(DashboardCard.STATUS)
        val (o, h, p) = l.encode()
        assertEquals(l.copy(order = l.normalized()), DashboardLayout.decode(o, h, p))
        val partial = DashboardLayout.decode("PROFILES,BOGUS", "BOGUS", "")
        assertEquals(DashboardCard.PROFILES, partial.normalized().first())
        assertEquals(DashboardCard.entries.size, partial.normalized().size)
        assertEquals(emptySet<DashboardCard>(), partial.pinned)
    }

    @Test
    fun `defaults pin status when nothing is stored`() {
        assertEquals(setOf(DashboardCard.STATUS), DashboardLayout.decode(null, null, null).pinned)
    }
}
