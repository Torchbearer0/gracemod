package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.*
import org.junit.Assert.*
import org.junit.Test

class HomeContentTest {
    private val clock = GraceWidget.Clock.key
    private val calendar = GraceWidget.Calendar.key
    private val media = GraceWidget.Media.key
    private val baseline = GraceWidget.Baseline.key

    @Test fun legacyLayoutKeepsItsWidgetAndFavoriteOrder() {
        val settings = LauncherSettings(homeLayout = HomeLayout(widgetId = 41, widgetHeightDp = 240))
        val items = orderedHomeItems(settings, listOf("app:a", "folder:b"), emptyList())
        assertEquals(listOf(clock, baseline, calendar, "widget:41", media, "app:a", "folder:b"), items.map { it.key })
        assertEquals(240, items.first { it.key == "widget:41" }.widget?.widgetHeightDp)
        assertEquals(listOf(calendar, baseline, clock), legacyHomeOrder(settings.copy(calendarAboveClock = true)).take(3))
    }

    @Test fun mixedOrderCanInterleaveAllKindsAndMultipleWidgets() {
        val keys = listOf("widget:2", "app:a", calendar, "folder:b", baseline, clock, "widget:1", media)
        val settings = LauncherSettings(homeLayout = HomeLayout(homeOrder = keys))
        val widgets = listOf(PopupItem("widget:1", HomeLayout(widgetId = 1)), PopupItem("widget:2", HomeLayout(widgetId = 2)))
        assertEquals(keys, orderedHomeItems(settings, listOf("app:a", "folder:b"), widgets).map { it.key })
    }

    @Test fun graceMembershipAndBannersUseTheSameFlagsAndReenableAtTop() {
        var settings = LauncherSettings(homeLayout = HomeLayout(homeOrder = listOf("app:a", baseline, clock, calendar, media)))
        for (widget in GraceWidget.entries) {
            val disabled = synchronizeGraceWidgetOrder(settings, widget.setEnabled(settings, false))
            assertFalse(widget.enabled(disabled))
            assertFalse(orderedHomeItems(disabled, listOf("app:a"), emptyList()).any { it.key == widget.key })
            settings = synchronizeGraceWidgetOrder(disabled, widget.setEnabled(disabled, true))
            assertTrue(widget.enabled(settings))
            assertEquals(widget.key, orderedHomeItems(settings, listOf("app:a"), emptyList()).first().key)
        }
    }

    @Test fun reorderingRetainsUnavailableSlotsWithoutDuplicatingNewItems() {
        assertEquals(listOf("new", "unavailable", "b", "a"),
            mergeHomeOrder(listOf("new", "b", "a", "b"), listOf("a", "unavailable", "b")))
    }

    @Test fun savedWidgetWinsDuringMigrationAndRemovingLegacyPreservesAnchor() {
        val layout = HomeLayout(widgetId = 41, topOffsetDp = 20f, baselineOffsetDp = 85f,
            baselineEnabled = false, homeOrder = listOf("widget:41", clock))
        val stored = PopupItem("widget:41", HomeLayout(widgetId = 41, widgetHeightDp = 300))
        val items = orderedHomeItems(LauncherSettings(homeLayout = layout), emptyList(), listOf(stored))
        assertEquals(listOf(stored), items.filter { it.widget != null })
        val cleared = layout.withoutWidget()
        assertFalse(cleared.hasWidget)
        assertEquals(layout.homeOrder, cleared.homeOrder)
        assertEquals(layout.baselineOffsetDp, cleared.baselineOffsetDp)
        assertEquals(layout.topOffsetDp, cleared.topOffsetDp)
        assertFalse(cleared.baselineEnabled)
    }
}
