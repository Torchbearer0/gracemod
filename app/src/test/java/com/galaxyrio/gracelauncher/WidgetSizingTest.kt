package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.platform.widgetAxisBounds
import com.galaxyrio.gracelauncher.platform.widgetTargetSize
import org.junit.Assert.*
import org.junit.Test

/** JVM-only checks: no device, installation, widget IDs, or launcher storage. */
class WidgetSizingTest {
    @Test fun maximumRoundsUpToContainingCell() {
        val bounds = widgetAxisBounds(100, 50, 190, true, 0f, 100f, 4)
        assertEquals(2, bounds.max)
        assertTrue(bounds.canResize)
    }

    @Test fun invalidMinimumAndMaximumFallBackIndependently() {
        val bounds = widgetAxisBounds(200, 400, 100, true, 0f, 100f, 4)
        assertEquals(2, bounds.initial)
        assertEquals(2, bounds.min)
        assertEquals(4, bounds.max)
    }

    @Test fun missingResizeMetadataUsesDefaultMinimumAndHostMaximum() {
        val bounds = widgetAxisBounds(200, 0, 0, true, 0f, 100f, 4)
        assertEquals(2, bounds.min)
        assertEquals(4, bounds.max)
    }

    @Test fun nonResizableAxisIsFixed() {
        val bounds = widgetAxisBounds(120, 40, 400, false, 16f, 100f, 4)
        assertEquals(2, bounds.initial)
        assertEquals(bounds.min, bounds.max)
        assertFalse(bounds.canResize)
    }

    @Test fun noHandleWhenGridCapacityClampsBothBounds() {
        val bounds = widgetAxisBounds(500, 400, 800, true, 16f, 100f, 4)
        assertEquals(4, bounds.min)
        assertEquals(4, bounds.max)
        assertFalse(bounds.canResize)
    }

    @Test fun dpSizingIncludesPaddingAndDensityOnlyOnce() {
        val bounds = widgetAxisBounds(300, 150, 600, true, 24f, 3f, 1600, 48)
        assertEquals(108, bounds.initial)
        assertEquals(58, bounds.min)
        assertEquals(208, bounds.max)
    }

    @Test fun targetSizeRequiresBothAxesWithinBounds() {
        val x = widgetAxisBounds(200, 100, 400, true, 0f, 100f, 4)
        val y = widgetAxisBounds(200, 100, 400, true, 0f, 100f, 20)
        assertEquals(3 to 4, widgetTargetSize(x, y, 3, 4))
        assertEquals(2 to 2, widgetTargetSize(x, y, 3, 5))
        assertEquals(2 to 2, widgetTargetSize(x, y, 0, 0))
    }
}
