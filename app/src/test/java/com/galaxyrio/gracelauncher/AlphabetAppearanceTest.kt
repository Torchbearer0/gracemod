package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.AlphabetAppearance
import com.galaxyrio.gracelauncher.data.IconShape
import com.galaxyrio.gracelauncher.ui.components.alphabetDriftAt
import com.galaxyrio.gracelauncher.ui.components.alphabetIndexAt
import org.junit.Assert.*
import org.junit.Test

class AlphabetAppearanceTest {
    @Test fun defaultsKeepTheOriginalGlyphSizeAndCircularIndicator() {
        val value = AlphabetAppearance()
        assertEquals(14, value.fontSize)
        assertEquals(IconShape.Circle, value.indicatorShape)
        assertEquals(85, value.pebbleRoundness)
        assertTrue(value.freeMovement)
        assertNull(value.fontId)
        assertNull(value.fontColor)
        assertNull(value.topPercent)
    }

    @Test fun limitsAlwaysDescribeANonEmptyRangeWithinTheScreen() {
        for (top in listOf(-100, 0, 35, 85, 99, 100, 1000)) {
            for (bottom in listOf(-100, 0, 35, 85, 99, 100, 1000)) {
                val value = AlphabetAppearance(topPercent = top, bottomPercent = bottom).normalized()
                val normalizedTop = requireNotNull(value.topPercent)
                assertTrue(normalizedTop in 0..99)
                assertTrue(value.bottomPercent in (normalizedTop + 1)..100)
            }
        }
    }

    @Test fun automaticLayoutAnchorsTheBottomAndKeeps18DpForFewOrManyLetters() {
        val value = AlphabetAppearance()
        for (count in 1..36) {
            val layout = value.layout(800f, count)
            assertEquals(18f, layout.heightDp / count, .001f)
            assertEquals(680f, layout.topDp + layout.heightDp, .001f)
        }
        val before = value.layout(800f, 15)
        val after = value.layout(800f, 16)
        assertEquals(18f, before.topDp - after.topDp, .001f)
    }

    @Test fun changingTheBottomTranslatesTheAutomaticRailWithoutChangingSpacing() {
        val first = AlphabetAppearance(bottomPercent = 85).layout(800f, 20)
        val moved = AlphabetAppearance(bottomPercent = 75).layout(800f, 20)
        assertEquals(first.heightDp, moved.heightDp, .001f)
        assertEquals(80f, first.topDp - moved.topDp, .001f)
    }

    @Test fun explicitRangesStillUseTheirSavedPercentagesAndResetRestoresSpacing() {
        val value = AlphabetAppearance(topPercent = 35, bottomPercent = 85)
        val custom = value.layout(800f, 20)
        assertEquals(280f, custom.topDp, .001f)
        assertEquals(400f, custom.heightDp, .001f)
        val reset = value.copy(topPercent = null).layout(800f, 20)
        assertEquals(360f, reset.heightDp, .001f)
        assertEquals(custom.topDp + custom.heightDp, reset.topDp + reset.heightDp, .001f)
    }

    @Test fun automaticSpacingDoesNotStretchOnTallerScreens() {
        val value = AlphabetAppearance()
        val phone = value.layout(800f, 28)
        val tablet = value.layout(1200f, 28)
        assertEquals(504f, phone.heightDp, .001f)
        assertEquals(phone.heightDp, tablet.heightDp, .001f)
        assertEquals(1200f * .85f, tablet.topDp + tablet.heightDp, .001f)
    }

    @Test fun tinyOrEmptyViewportsRemainFiniteAndNeverRunOffTheTop() {
        val value = AlphabetAppearance()
        val tiny = value.layout(240f, 30)
        assertEquals(0f, tiny.topDp, .001f)
        assertEquals(204f, tiny.heightDp, .001f)
        for (height in listOf(0f, -100f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(0f, value.layout(height, 30).heightDp, 0f)
        }
        assertEquals(0f, value.layout(800f, 0).heightDp, 0f)
    }

    @Test fun invalidSizesAndShapeParametersAreSanitized() {
        val value = AlphabetAppearance(fontSize = 90, pebbleRoundness = -10, squareCornerRadius = 300, cookieSides = 99).normalized()
        assertEquals(28, value.fontSize)
        assertEquals(0, value.pebbleRoundness)
        assertEquals(100, value.squareCornerRadius)
        assertEquals(4, value.cookieSides)
        assertEquals(10, value.copy(fontSize = -1).normalized().fontSize)
    }

    @Test fun topOvershootReversesFromTheNewOriginWithoutUndoingTheOvershoot() {
        val drift = alphabetDriftAt(-90f, 300f, 0f, true)
        assertEquals(-90f, drift, 0f)
        assertEquals(0, alphabetIndexAt(-90f - drift, 300f, 6))
        val reversed = alphabetDriftAt(-35f, 300f, drift, true)
        assertEquals(drift, reversed, 0f)
        assertEquals(1, alphabetIndexAt(-35f - reversed, 300f, 6))
    }

    @Test fun bottomOvershootHasTheSameImmediateReverseBehavior() {
        val drift = alphabetDriftAt(410f, 300f, 0f, true)
        assertEquals(110f, drift, 0f)
        assertEquals(5, alphabetIndexAt(410f - drift, 300f, 6))
        val reversed = alphabetDriftAt(350f, 300f, drift, true)
        assertEquals(drift, reversed, 0f)
        assertEquals(4, alphabetIndexAt(350f - reversed, 300f, 6))
    }

    @Test fun stationaryAndDisabledRailsClampInsteadOfDrifting() {
        assertEquals(0f, alphabetDriftAt(100f, 300f, 0f, true), 0f)
        assertEquals(0f, alphabetDriftAt(-90f, 300f, -40f, false), 0f)
        assertEquals(0, alphabetIndexAt(-90f, 300f, 6))
        assertEquals(5, alphabetIndexAt(900f, 300f, 6))
        assertEquals(0, alphabetIndexAt(90f, 0f, 0))
        assertEquals(0f, alphabetDriftAt(-90f, 0f, -40f, true), 0f)
    }

    @Test fun driftAndSelectionScaleWithPreviewOrDisplayDensity() {
        for (scale in listOf(.5f, 1f, 2.5f)) {
            val drift = alphabetDriftAt(-90f * scale, 300f * scale, 0f, true)
            assertEquals(-90f * scale, drift, .001f)
            assertEquals(1, alphabetIndexAt(-35f * scale - drift, 300f * scale, 6))
        }
    }
}
