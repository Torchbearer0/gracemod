package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.GraceButtonAlignment
import com.galaxyrio.gracelauncher.data.GraceButtonLayout
import com.galaxyrio.gracelauncher.data.GraceButtonSettings
import com.galaxyrio.gracelauncher.data.GraceButtonVisibility
import org.junit.Assert.*
import org.junit.Test

class GraceButtonLayoutTest {
    @Test fun defaultsPreserveThePreviousDesktopPlacement() {
        val bounds = GraceButtonLayout().bounds(400f, 800f, topInset = 24f, bottomInset = 28f)
        assertEquals(290f, bounds.left, .001f)
        assertEquals(696f, bounds.top, .001f)
        assertEquals(54f, bounds.size, .001f)
    }

    @Test fun centerIgnoresButPreservesTheSideDistance() {
        val layout = GraceButtonLayout(alignment = GraceButtonAlignment.Center, sideMargin = 73f)
        assertEquals(173f, layout.bounds(400f, 800f).left, .001f)
        assertEquals(73f, layout.copy(alignment = GraceButtonAlignment.Left).bounds(400f, 800f).left, .001f)
        assertEquals(273f, layout.copy(alignment = GraceButtonAlignment.Right).bounds(400f, 800f).left, .001f)
    }

    @Test fun dragUsesTheSameGeometryAsTheDesktopForBothSidesAndCenter() {
        val initial = GraceButtonLayout()
        for (left in listOf(18f, 173f, 300f)) {
            val moved = initial.movedTo(left, 420f, 400f, 800f, 24f, 28f)
            val bounds = moved.bounds(400f, 800f, 24f, 28f)
            assertEquals(left, bounds.left, .001f)
            assertEquals(420f, bounds.top, .001f)
        }
        assertEquals(GraceButtonAlignment.Left, initial.movedTo(18f, 420f, 400f, 800f).alignment)
        assertEquals(GraceButtonAlignment.Center, initial.movedTo(177f, 420f, 400f, 800f).alignment)
        assertEquals(GraceButtonAlignment.Right, initial.movedTo(300f, 420f, 400f, 800f).alignment)
    }

    @Test fun foldingOrResizingKeepsTheButtonInsideTheSafeArea() {
        val layout = GraceButtonLayout(sideMargin = 400f, bottomMargin = 900f, size = 120f)
        val bounds = layout.bounds(280f, 360f, 32f, 24f)
        assertEquals(0f, bounds.left, .001f)
        assertEquals(180f, bounds.top, .001f)
        assertTrue(bounds.left + bounds.size <= 280f)
        assertTrue(bounds.top + bounds.size <= 336f)
        val tiny = layout.bounds(40f, 80f, 30f, 30f)
        assertEquals(20f, tiny.size, .001f)
        assertEquals(30f, tiny.top, .001f)
    }

    @Test fun sizesAndInvalidNumbersAreNormalized() {
        val layout = GraceButtonLayout(sideMargin = Float.NaN, bottomMargin = Float.POSITIVE_INFINITY, size = Float.NaN).normalized()
        assertEquals(GraceButtonLayout(), layout)
        assertEquals(32f, GraceButtonLayout(size = -1f).normalized().size, .001f)
        assertEquals(120f, GraceButtonLayout(size = 1000f).normalized().size, .001f)
    }

    @Test fun maximumBottomDistanceKeepsEveryButtonSizeBelowTheMidpoint() {
        for (size in listOf(32f, 54f, 120f)) {
            val layout = GraceButtonLayout(size = size)
            val maximum = layout.maxBottomMargin(800f, 24f, 28f)
            assertEquals(400f - 28f - size, maximum, .001f)
            val bounds = layout.copy(bottomMargin = maximum).bounds(400f, 800f, 24f, 28f)
            assertEquals(400f, bounds.top, .001f)
            assertEquals(size, bounds.size, .001f)
        }
    }

    @Test fun draggingAndOldDistancesUseTheSameHalfScreenLimit() {
        val layout = GraceButtonLayout(bottomMargin = 900f)
        assertEquals(400f, layout.bounds(400f, 800f, 24f, 28f).top, .001f)
        val moved = layout.movedTo(20f, 80f, 400f, 800f, 24f, 28f)
        assertEquals(layout.maxBottomMargin(800f, 24f, 28f), moved.bottomMargin, .001f)
        assertEquals(400f, moved.bounds(400f, 800f, 24f, 28f).top, .001f)
        // Bottom and side margins still refer to the screen, not the preview's surrounding mask.
        val edge = layout.movedTo(0f, 800f, 400f, 800f, 24f, 28f).bounds(400f, 800f, 24f, 28f)
        assertEquals(0f, edge.left, .001f)
        assertEquals(718f, edge.top, .001f)
    }

    @Test fun changingAppearancePreservesActions() {
        val previous = GraceButtonSettings()
        val changed = previous.copy(layout = GraceButtonLayout(alignment = GraceButtonAlignment.Left))
        assertEquals(previous.tap, changed.tap)
        assertEquals(previous.longPress, changed.longPress)
        assertEquals(previous.swipeUp, changed.swipeUp)
        assertEquals(previous.animationsEnabled, changed.animationsEnabled)
    }

    @Test fun visibilityDefaultsToFavoritesAndRespectsTheMasterSwitch() {
        val defaults = GraceButtonSettings()
        assertTrue(defaults.isVisible(appList = false))
        assertFalse(defaults.isVisible(appList = true))
        GraceButtonVisibility.entries.forEach { visibility ->
            val settings = defaults.copy(layout = defaults.layout.copy(showOn = visibility))
            assertEquals(visibility != GraceButtonVisibility.AppList, settings.isVisible(appList = false))
            assertEquals(visibility != GraceButtonVisibility.Favorites, settings.isVisible(appList = true))
            assertFalse(settings.copy(enabled = false).isVisible(appList = false))
            assertFalse(settings.copy(enabled = false).isVisible(appList = true))
        }
    }

    @Test fun draggingAndNormalizingPreserveVisibility() {
        val layout = GraceButtonLayout(showOn = GraceButtonVisibility.Both)
        assertEquals(layout.showOn, layout.normalized().showOn)
        assertEquals(layout.showOn, layout.movedTo(15f, 610f, 400f, 800f).showOn)
    }
}
