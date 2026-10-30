package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.*
import org.junit.Assert.*
import org.junit.Test

/** Pure layout/data checks; no device, installation or launcher database. */
class AppStackTest {
    private fun cell(key: String, row: Int, columns: Int = 4, rows: Int = 1) =
        WidgetGridCell(key, 0, row, WidgetGridSize(columns, rows))

    @Test fun overflowReservesTheLastCellForAnAlignedToggle() {
        assertEquals(1, appStackRows(0, 4, true))
        assertEquals(1, appStackRows(4, 4, true))
        assertEquals(1, appStackRows(5, 4, false))
        assertEquals(2, appStackRows(5, 4, true))
        assertEquals(2, appStackRows(7, 4, true))
        assertEquals(3, appStackRows(8, 4, true))
    }

    @Test fun compactSurfaceTracksActualIconSizeWithoutWideLeadingCells() {
        assertEquals(56f, appStackHeight(1, 40f), 0.001f)
        assertEquals(76f, appStackHeight(1, 60f), 0.001f)
        assertEquals(104f, appStackHeight(2, 40f), 0.001f)
        assertEquals(5, appStackColumns(280f, 40f))
        assertEquals(4, appStackColumns(280f, 48f))
        assertEquals(8f, AppStackInset, 0.001f)
    }

    @Test fun stackRestoresAsAFullWidthRowWithoutChangingAppsOrWidgets() {
        val entries = listOf(PopupItem("app"), PopupItem("stack", grid = WidgetGridPlacement(1, 1, 1, 3)),
            PopupItem("widget", HomeLayout(widgetId = 1), WidgetGridPlacement(0, 2, 2, 2)))
        val result = restoreWidgetGrid(entries, 4, mapOf("stack" to WidgetGridSize(4, 1)))
        assertEquals(WidgetGridSize(4, 1), result.first { it.key == "stack" }.size)
        assertEquals(0, result.first { it.key == "stack" }.column)
        assertEquals(WidgetGridSize(), result.first { it.key == "app" }.size)
        assertEquals(WidgetGridSize(2, 2), result.first { it.key == "widget" }.size)
    }

    @Test fun expansionPushesLaterRowsAndCollapsingRestoresExactly() {
        val base = listOf(cell("above", 0, rows = 2), cell("stack", 2), cell("app", 3, 1), cell("second", 4), cell("below", 5))
        val expanded = expandWidgetStacks(base, mapOf("stack" to 3, "second" to 2))
        assertEquals(2, expanded.first { it.key == "stack" }.row)
        assertEquals(5, expanded.first { it.key == "app" }.row)
        assertEquals(8, expanded.first { it.key == "below" }.row)
        assertEquals(base, collapseWidgetStacks(expanded, setOf("stack", "second")))
    }

    @Test fun draggingAnExpandedStackPersistsOnlyItsCollapsedFootprint() {
        val expanded = listOf(cell("above", 0, rows = 2), cell("stack", 2, rows = 3), cell("below", 5, rows = 2))
        val moved = compactWidgetRows(moveWidgetGrid(expanded, cell("stack", 0, rows = 3), 4))
        val saved = collapseWidgetStacks(moved, setOf("stack"))
        assertEquals(cell("stack", 0), saved.first { it.key == "stack" })
        assertEquals(1, saved.first { it.key == "above" }.row)
        for (a in saved) for (b in saved) if (a.key != b.key) assertFalse(a.overlaps(b))
        assertEquals(moved, expandWidgetStacks(saved, mapOf("stack" to 3)))
    }
}
