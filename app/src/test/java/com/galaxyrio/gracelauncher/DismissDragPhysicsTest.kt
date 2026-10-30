package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.ui.components.dismissBreakawayDirection
import com.galaxyrio.gracelauncher.ui.components.resistedDismissOffset
import com.galaxyrio.gracelauncher.ui.components.releasedDismissOffset
import com.galaxyrio.gracelauncher.ui.components.shouldDismissDrag
import org.junit.Assert.*
import org.junit.Test

/** Pure math only; no device, notification cancellation, or media commands. */
class DismissDragPhysicsTest {
    @Test fun defaultResistanceIsSeventyFivePercent() {
        assertEquals(75, com.galaxyrio.gracelauncher.data.LauncherSettings().dismissResistance)
        assertEquals(resistedDismissOffset(40f, 72f, 75), resistedDismissOffset(40f, 72f), 0f)
    }

    @Test fun protectedContentNeverBreaksAwayEvenIfAlreadyReleased() {
        for (previous in -1..1) {
            for (distance in listOf(-400f, -72f, 0f, 72f, 400f)) {
                assertEquals(0, dismissBreakawayDirection(previous, distance, 72f, canDismiss = false))
            }
        }
        assertEquals(resistedDismissOffset(72f, 72f), resistedDismissOffset(400f, 72f), .001f)
    }

    @Test fun protectedPlayingMediaNeverDismissesRegardlessOfDistance() {
        assertFalse(shouldDismissDrag(400f, 72f, false, canDismiss = false))
        assertFalse(shouldDismissDrag(-400f, 72f, false, canDismiss = false))
        assertTrue(shouldDismissDrag(400f, 72f, false, canDismiss = true))
        assertTrue(com.galaxyrio.gracelauncher.data.LauncherSettings().mediaKeepWhilePlaying)
    }

    @Test fun resistanceSettingIsOrderedAndZeroFollowsFinger() {
        for (distance in listOf(-72f, -36f, 36f, 72f)) {
            assertEquals(distance, resistedDismissOffset(distance, 72f, 0), .001f)
            var previous = kotlin.math.abs(distance)
            for (strength in 0..90) {
                val offset = kotlin.math.abs(resistedDismissOffset(distance, 72f, strength))
                assertTrue(offset <= previous + .001f)
                previous = offset
            }
        }
    }

    @Test fun customizedThresholdChangesBreakthroughAndDismissDistanceTogether() {
        assertEquals(1, dismissBreakawayDirection(0, 50f, 24f))
        assertEquals(0, dismissBreakawayDirection(0, 50f, 160f))
        assertTrue(shouldDismissDrag(50f, 24f, false))
        assertFalse(shouldDismissDrag(50f, 160f, false))
    }

    @Test fun resistanceIsSymmetricAndMovesLessThanTheFinger() {
        assertEquals(0f, resistedDismissOffset(0f, 72f), 0f)
        for (distance in 1..72) {
            val offset = resistedDismissOffset(distance.toFloat(), 72f)
            assertTrue(offset > 0f && offset < distance)
            assertEquals(-offset, resistedDismissOffset(-distance.toFloat(), 72f), .001f)
        }
    }

    @Test fun resistanceRemainsMonotonicAndSaturates() {
        var previous = 0f
        for (distance in 0..144) {
            val offset = resistedDismissOffset(distance.toFloat(), 72f)
            assertTrue(offset >= previous)
            previous = offset
        }
        assertEquals(72f * .25f, previous, .001f)
    }

    @Test fun breakthroughRequiresThresholdAndDoesNotChatterOnReversal() {
        assertEquals(0, dismissBreakawayDirection(0, 71f, 72f))
        assertEquals(1, dismissBreakawayDirection(0, 72f, 72f))
        assertEquals(1, dismissBreakawayDirection(1, 50f, 72f))
        assertEquals(0, dismissBreakawayDirection(1, 0f, 72f))
        assertEquals(0, dismissBreakawayDirection(1, -30f, 72f))
        assertEquals(-1, dismissBreakawayDirection(1, -80f, 72f))
    }

    @Test fun releaseAndCancellationKeepShortSwipesAndReversedSwipes() {
        assertFalse(shouldDismissDrag(50f, 72f, false))
        assertFalse(shouldDismissDrag(-50f, 72f, false))
        assertTrue(shouldDismissDrag(72f, 72f, false))
        assertTrue(shouldDismissDrag(-72f, 72f, false))
        assertFalse(shouldDismissDrag(200f, 72f, true))
        assertFalse(shouldDismissDrag(0f, 72f, false))
    }

    @Test fun catchUpTracksTheFingerWithoutCrossingTheOriginOnFastReversal() {
        assertEquals(60f, releasedDismissOffset(100f, 40f), 0f)
        assertEquals(80f, releasedDismissOffset(120f, 40f), 0f)
        assertEquals(120f, releasedDismissOffset(120f, 0f), 0f)
        assertEquals(0f, releasedDismissOffset(10f, 40f), 0f)
        assertEquals(0f, releasedDismissOffset(-10f, -40f), 0f)
        assertEquals(-120f, releasedDismissOffset(-120f, 0f), 0f)
    }
}
