package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.*
import org.junit.Assert.*
import org.junit.Test

/** Pure layout checks. No Android device, installation, database, or launcher data is used. */
class WidgetGridLayoutTest {
    private fun cell(key: String, x: Int, y: Int, w: Int = 1, h: Int = 1) =
        WidgetGridCell(key, x, y, WidgetGridSize(w, h))

    private fun assertFits(cells: List<WidgetGridCell>, columns: Int) {
        assertEquals(cells.size, cells.map { it.key }.distinct().size)
        for (a in cells) {
            assertTrue(a.column >= 0 && a.column + a.size.columns <= columns && a.row >= 0)
            for (b in cells) if (a.key != b.key) assertFalse("${a.key} overlaps ${b.key}", a.overlaps(b))
        }
    }

    @Test fun compactionRemovesOnlyWholeEmptyRows() {
        val cells = compactWidgetRows(listOf(cell("a", 3, 0), cell("b", 1, 3)))
        assertEquals(listOf(cell("a", 3, 0), cell("b", 1, 1)), cells)
    }

    @Test fun rowsCoveredByTallWidgetsAreNotEmpty() {
        val cells = compactWidgetRows(listOf(cell("w", 0, 1, 2, 3), cell("a", 3, 4), cell("b", 1, 7)))
        assertEquals(listOf(cell("w", 0, 0, 2, 3), cell("a", 3, 3), cell("b", 1, 4)), cells)
        assertFits(cells, 4)
    }

    @Test fun draggingPushesCollisionsButKeepsUnrelatedHoles() {
        val original = listOf(cell("a", 0, 0), cell("b", 0, 1), cell("c", 1, 1), cell("d", 3, 2))
        val cells = moveWidgetGrid(original, cell("a", 0, 1), 4).associateBy { it.key }
        assertEquals(cell("b", 1, 1), cells["b"])
        assertEquals(cell("c", 2, 1), cells["c"])
        assertEquals(cell("d", 3, 2), cells["d"])
        assertFits(cells.values.toList(), 4)
    }

    @Test fun resizingAWidgetPushesWholeRectanglesWithoutOverlap() {
        val original = listOf(cell("w", 0, 0, 2, 1), cell("a", 2, 0), cell("b", 0, 1, 2, 2), cell("c", 3, 3))
        val cells = moveWidgetGrid(original, cell("w", 0, 0, 3, 3), 4)
        assertEquals(cell("w", 0, 0, 3, 3), cells.first { it.key == "w" })
        assertFits(cells, 4)
    }

    @Test fun savedPositionsSurviveNewItemsAndColumnChanges() {
        val entries = listOf(PopupItem("new"), PopupItem("a", grid = WidgetGridPlacement(3, 0)),
            PopupItem("w", HomeLayout(widgetId = 1), WidgetGridPlacement(0, 1, 3, 2)))
        val restored = restoreWidgetGrid(entries, 4, emptyMap())
        assertEquals(cell("a", 3, 0), restored.first { it.key == "a" })
        assertEquals(cell("w", 0, 1, 3, 2), restored.first { it.key == "w" })
        assertFits(restored, 4)
        val narrower = restoreWidgetGrid(entries, 2, emptyMap())
        assertEquals(2, narrower.first { it.key == "w" }.size.columns)
        assertFits(narrower, 2)
    }

    @Test fun movingAFullWidthWidgetUpSurvivesCompactionAndReload() {
        val original = listOf(cell("above", 0, 0, 4, 3), cell("moving", 0, 3, 4, 4), cell("below", 0, 7, 4, 3))
        val moved = compactWidgetRows(moveWidgetGrid(original, cell("moving", 0, 0, 4, 4), 4))
        assertEquals(cell("moving", 0, 0, 4, 4), moved.first { it.key == "moving" })
        assertEquals(cell("above", 0, 4, 4, 3), moved.first { it.key == "above" })
        assertFits(moved, 4)
        val saved = moved.mapIndexed { index, cell -> PopupItem(cell.key, HomeLayout(widgetId = index), cell.placement()) }
        assertEquals(moved, restoreWidgetGrid(saved, 4, emptyMap()))
    }
}
