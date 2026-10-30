package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.ui.components.PopupSwipeStart
import com.galaxyrio.gracelauncher.ui.components.PopupSwipeStart.Decision.*
import org.junit.Assert.assertEquals
import org.junit.Test

class PopupSwipeStartTest {
    @Test fun requiresExtraDistanceBeyondSystemSlop() {
        val start = PopupSwipeStart(8f, 8f, 45, false)
        assertEquals(Pending, start.evaluate(8f, 0f))
        assertEquals(Pending, start.evaluate(15.9f, 0f))
        assertEquals(Right, start.evaluate(16f, 0f))
    }

    @Test fun angleIsSymmetricAndIncludesTheBoundary() {
        val start = PopupSwipeStart(8f, 8f, 45, false)
        for (sign in listOf(-1f, 1f)) {
            assertEquals(Right, start.evaluate(16f, sign * 16f))
            assertEquals(Reject, start.evaluate(16f, sign * 17f))
        }
    }

    @Test fun configurableAngleAndDistanceChangeTheSameGesture() {
        assertEquals(Reject, PopupSwipeStart(8f, 8f, 30, false).evaluate(20f, 15f))
        assertEquals(Right, PopupSwipeStart(8f, 8f, 45, false).evaluate(20f, 15f))
        assertEquals(Pending, PopupSwipeStart(8f, 24f, 45, false).evaluate(20f, 15f))
        assertEquals(Right, PopupSwipeStart(8f, 0f, 90, false).evaluate(9f, 30f))
    }

    @Test fun leftActionRetainsSystemSlopAndDoesNotUseOpeningThresholds() {
        assertEquals(Left, PopupSwipeStart(8f, 48f, 10, true).evaluate(-9f, 5f))
        assertEquals(Pending, PopupSwipeStart(8f, 48f, 10, false).evaluate(-9f, 5f))
    }

    @Test fun thresholdScalesWithDensity() {
        for (density in listOf(1f, 2f, 3f)) {
            val start = PopupSwipeStart(8f * density, 8f * density, 45, false)
            assertEquals(Pending, start.evaluate(15f * density, 0f))
            assertEquals(Right, start.evaluate(16f * density, 12f * density))
        }
    }
}
