package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.IconColor
import com.galaxyrio.gracelauncher.data.IconColorSource
import com.galaxyrio.gracelauncher.data.IconDisplaySettings
import com.galaxyrio.gracelauncher.data.IconDisplayStyle
import com.galaxyrio.gracelauncher.data.IconDotColor
import org.junit.Assert.*
import org.junit.Test

class IconColorSourceTest {
    private val theme = 0xFF663399.toInt()
    private val app = 0xFF11AA44.toInt()

    @Test fun sourcesStayLiveAndMissingAppColorFallsBackToTheme() {
        assertEquals(theme, IconColor.Theme.resolve(theme, app))
        assertEquals(app, IconColor.Theme.resolve(app, theme))
        assertEquals(app, IconColor.FromApp.resolve(theme, app))
        assertEquals(theme, IconColor.FromApp.resolve(theme))
        val fixed = IconColor(0xFF123456.toInt())
        assertEquals(fixed.argb, fixed.resolve(theme, app))
        assertEquals(fixed.argb, fixed.resolve(app, theme))
    }

    @Test fun oldDynamicIsThemeAndFixedRgbIsPreserved() {
        val migrated = IconColor.fromStored(app, dynamic = true)
        assertEquals(IconColor.Theme, migrated)
        assertEquals(theme, migrated.resolve(theme, app))
        assertEquals(IconColorSource.Theme, migrated.source)
        assertEquals(IconColor(app), IconColor.fromStored(app))
        assertEquals(IconColor.FromApp, IconColor.fromStored(0, fromApp = true))
    }

    @Test fun dotLegacySourcesMapToTheCommonPickerWithoutChangingSizeOrShape() {
        val dots = IconDisplaySettings(style = IconDisplayStyle.Dot, dotSize = 21)
        assertEquals(IconColor.FromApp, dots.color)
        assertEquals(IconColor.Theme, dots.copy(dotColor = IconDotColor.Theme).color)
        assertEquals(IconColor.Theme, dots.copy(dotColor = IconDotColor.Custom,
            customColor = IconColor.fromStored(app, dynamic = true)).color)
        listOf(IconColor.Theme, IconColor.FromApp, IconColor(app),
            IconColor.Theme.copy(hueShift = -30, brightnessShift = 20),
            IconColor.FromApp.copy(saturationShift = -15, brightnessShift = -10)).forEach { color ->
            val changed = dots.withColor(color)
            assertEquals(color, changed.color)
            assertEquals(dots.dotSize, changed.dotSize)
            assertEquals(dots.dotShape, changed.dotShape)
            assertEquals(dots.style, changed.style)
        }
    }
}
