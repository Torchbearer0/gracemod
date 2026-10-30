package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.IconDesign
import com.galaxyrio.gracelauncher.data.IconShape
import com.galaxyrio.gracelauncher.data.squareAspectRatioFromSlider
import com.galaxyrio.gracelauncher.data.squareAspectRatioToSlider
import com.galaxyrio.gracelauncher.data.squareIconDimensions
import com.galaxyrio.gracelauncher.data.icons.pebbleShapePower
import kotlin.math.pow
import kotlin.math.sqrt
import org.junit.Assert.*
import org.junit.Test

class IconShapeParametersTest {
    @Test fun squareAspectRatioHasReciprocalEndpointsAndOneAtTheCenter() {
        assertEquals(.5f, squareAspectRatioFromSlider(-100f), .00001f)
        assertEquals(1f, squareAspectRatioFromSlider(0f), 0f)
        assertEquals(2f, squareAspectRatioFromSlider(100f), .00001f)
        assertEquals(0f, squareAspectRatioToSlider(1f), 0f)
        (-100..100 step 10).forEach { position ->
            val ratio = squareAspectRatioFromSlider(position.toFloat())
            assertEquals(position.toFloat(), squareAspectRatioToSlider(ratio), .0001f)
            assertEquals(1f, ratio * squareAspectRatioFromSlider(-position.toFloat()), .00001f)
        }
    }

    @Test fun squareAspectRatioUsesSafeDefaultsAndBounds() {
        assertEquals(1f, IconDesign().squareAspectRatio, 0f)
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { ratio ->
            assertEquals(1f, IconDesign(squareAspectRatio = ratio).normalized().squareAspectRatio, 0f)
        }
        assertEquals(.5f, IconDesign(squareAspectRatio = -5f).normalized().squareAspectRatio, 0f)
        assertEquals(2f, IconDesign(squareAspectRatio = 100f).normalized().squareAspectRatio, 0f)
    }

    @Test fun rectanglesFitTheExistingSlotAndKeepTheLongEdgeUnchanged() {
        assertEquals(1f to 1f, squareIconDimensions(1f))
        assertEquals(1f to .5f, squareIconDimensions(2f))
        assertEquals(.5f to 1f, squareIconDimensions(.5f))
        listOf(.5f, .75f, 1f, 1.25f, 2f).forEach { ratio ->
            val (width, height) = squareIconDimensions(ratio)
            assertEquals(ratio, width / height, .00001f)
            assertEquals(1f, maxOf(width, height), 0f)
        }
    }

    @Test fun themingDefaultsToOffButExplicitChoicesRemainEnabled() {
        assertFalse(IconDesign().themeIcons)
        assertFalse(IconDesign.defaults().themeIcons)
        assertTrue(IconDesign.defaults(themedIcons = true).themeIcons)
        assertTrue(IconDesign(themeIcons = true).withThemeDefaults(IconDesign.defaults()).themeIcons)
    }

    @Test fun shapeParametersAreClampedIndependently() {
        val normalized = IconDesign(pebbleRoundness = -20, squareCornerRadius = 150).normalized()
        assertEquals(0, normalized.pebbleRoundness)
        assertEquals(100, normalized.squareCornerRadius)
        val opposite = IconDesign(pebbleRoundness = 150, squareCornerRadius = -20).normalized()
        assertEquals(100, opposite.pebbleRoundness)
        assertEquals(0, opposite.squareCornerRadius)
    }

    @Test fun pebbleMovesContinuouslyFromBoxyToCircular() {
        val diagonal = sqrt(0.5)
        val extents = (0..100).map { diagonal.pow(pebbleShapePower(it)) }
        assertTrue(extents.zipWithNext().all { (previous, next) -> next < previous })
        assertEquals(diagonal, extents.last(), 0.000001)
        assertEquals(pebbleShapePower(0), pebbleShapePower(-1), 0.0)
        assertEquals(pebbleShapePower(100), pebbleShapePower(101), 0.0)
    }

    @Test fun defaultPebbleIsRounderThanTheOldFixedFourthPowerSquircle() {
        assertTrue(pebbleShapePower(IconDesign.DefaultPebbleRoundness) > 0.5)
        assertEquals(0, IconDesign().squareCornerRadius)
    }

    @Test fun switchingShapesKeepsTheirIndividualSliderValues() {
        val pebble = IconDesign(shape = IconShape.Pebble, pebbleRoundness = 33, squareCornerRadius = 58, squareAspectRatio = 1.5f)
        assertEquals(pebble, pebble.copy(shape = IconShape.Square).normalized().copy(shape = IconShape.Pebble))
    }
}
