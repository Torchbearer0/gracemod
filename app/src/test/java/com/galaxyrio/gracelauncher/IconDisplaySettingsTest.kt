package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.IconColor
import com.galaxyrio.gracelauncher.data.IconDesign
import com.galaxyrio.gracelauncher.data.IconDisplaySettings
import com.galaxyrio.gracelauncher.data.IconDisplayStyle
import com.galaxyrio.gracelauncher.data.IconDotColor
import com.galaxyrio.gracelauncher.data.IconShape
import org.junit.Assert.*
import org.junit.Test

class IconDisplaySettingsTest {
    @Test fun dotsKeepTheirOriginalRoundTenDpDefault() {
        val defaults = IconDisplaySettings()
        assertEquals(IconDisplayStyle.Full, defaults.style)
        assertEquals(10, defaults.dotSize)
        assertEquals(IconShape.Circle, defaults.dotShape)
        assertEquals(1f, defaults.dotSquareAspectRatio, 0f)
        assertEquals(defaults, defaults.normalized())
    }

    @Test fun dotShapeParametersAreSafeAndHaveNoShapelessMode() {
        val safe = IconDisplaySettings(dotSize = 999, dotShape = IconShape.None,
            dotCookieSides = -1, dotPebbleRoundness = -30, dotSquareCornerRadius = 130, dotSquareAspectRatio = 10f).normalized()
        assertEquals(32, safe.dotSize)
        assertEquals(IconShape.Circle, safe.dotShape)
        assertEquals(4, safe.dotCookieSides)
        assertEquals(0, safe.dotPebbleRoundness)
        assertEquals(100, safe.dotSquareCornerRadius)
        assertEquals(2f, safe.dotSquareAspectRatio, 0f)
        assertEquals(4, IconDisplaySettings(dotSize = -1).normalized().dotSize)
    }

    @Test fun shapePickerOnlyUpdatesDotShapeParameters() {
        val display = IconDisplaySettings(style = IconDisplayStyle.Dot, dotSize = 18,
            dotColor = IconDotColor.Custom, customColor = IconColor(0xFF336699.toInt()))
        val full = IconDesign(shape = IconShape.Cookie, cookieSides = 9, pebbleRoundness = 42,
            squareCornerRadius = 27, squareAspectRatio = .75f, size = 140, x = 10f)
        val changed = display.withDotDesign(full)
        assertEquals(display.copy(dotShape = IconShape.Cookie, dotCookieSides = 9,
            dotPebbleRoundness = 42, dotSquareCornerRadius = 27, dotSquareAspectRatio = .75f), changed)
        assertEquals(IconDesign(shape = IconShape.Cookie, cookieSides = 9,
            pebbleRoundness = 42, squareCornerRadius = 27, squareAspectRatio = .75f), changed.dotDesign)
        assertEquals(140, full.size)
    }

    @Test fun switchingDisplayModesRetainsAllDotParameters() {
        val display = IconDisplaySettings(style = IconDisplayStyle.Dot, dotSize = 20,
            dotShape = IconShape.Square, dotSquareCornerRadius = 45, dotSquareAspectRatio = 1.75f,
            dotColor = IconDotColor.Custom, customColor = IconColor.Theme)
        val roundTrip = display.copy(style = IconDisplayStyle.Full).normalized()
            .copy(style = IconDisplayStyle.Hidden).normalized().copy(style = IconDisplayStyle.Dot)
        assertEquals(display, roundTrip)
    }
}
