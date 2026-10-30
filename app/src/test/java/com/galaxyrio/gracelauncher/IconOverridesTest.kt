package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.*
import org.junit.Assert.*
import org.junit.Test

class IconOverridesTest {
    private val defaults = IconDesign.defaults()

    @Test fun inversionAloneDoesNotFreezeAllShapeColorsOrTransforms() {
        val saved = requireNotNull(defaults.copy(invertBackgroundDetection = true).overridesFrom(defaults))
        assertEquals(setOf("invertBackgroundDetection"), saved.overriddenFields)
        val next = defaults.copy(shape = IconShape.Pebble, pebbleRoundness = 55, iconSize = 135,
            background = IconColor.FromApp, foreground = IconColor(0xff123456.toInt()),
            x = 12f, y = -5f, rotation = 27f, size = 150, themeIcons = true, themeUnsupportedIcons = true)
        assertEquals(next.copy(invertBackgroundDetection = true), saved.withThemeDefaults(next))
    }

    @Test fun everyDesignPropertyCanBeOverriddenIndependently() {
        val cases = listOf<Pair<String, (IconDesign) -> IconDesign>>(
            "shape" to { it.copy(shape = IconShape.Gem) },
            "cookieSides" to { it.copy(cookieSides = 9) },
            "pebbleRoundness" to { it.copy(pebbleRoundness = 40) },
            "squareCornerRadius" to { it.copy(squareCornerRadius = 30) },
            "squareAspectRatio" to { it.copy(squareAspectRatio = 1.5f) },
            "background" to { it.copy(background = IconColor.FromApp.copy(hueShift = 20)) },
            "foreground" to { it.copy(foreground = IconColor(0xff123456.toInt())) },
            "addTray" to { it.copy(addTray = true) },
            "x" to { it.copy(x = 10f) }, "y" to { it.copy(y = -15f) },
            "size" to { it.copy(size = 150) }, "rotation" to { it.copy(rotation = -30f) },
            "themeIcons" to { it.copy(themeIcons = true) },
            "themeUnsupportedIcons" to { it.copy(themeUnsupportedIcons = true) },
            "invertBackgroundDetection" to { it.copy(invertBackgroundDetection = true) },
        )
        cases.forEach { (field, change) ->
            val desired = change(defaults)
            val saved = requireNotNull(desired.overridesFrom(defaults))
            assertEquals(field, setOf(field), saved.overriddenFields)
            assertEquals(field, desired, saved.withThemeDefaults(defaults))
            val inherited = defaults.copy(iconSize = 145)
            assertEquals(field, change(inherited), saved.withThemeDefaults(inherited))
        }
    }

    @Test fun falseAndNullAreRealOverridesRatherThanInheritance() {
        val all = defaults.copy(themeIcons = true, themeUnsupportedIcons = true, addTray = true,
            invertBackgroundDetection = true, background = IconColor.FromApp)
        val desired = all.copy(themeIcons = false, themeUnsupportedIcons = false,
            addTray = false, invertBackgroundDetection = false, background = null, foreground = null)
        val saved = requireNotNull(desired.overridesFrom(all))
        assertEquals(desired, saved.withThemeDefaults(all))
        assertEquals(desired.copy(shape = IconShape.Circle), saved.withThemeDefaults(all.copy(shape = IconShape.Circle)))
    }

    @Test fun savingMatchingPropertiesRemovesOverridesAndIconSizeIsAlwaysShared() {
        assertNull(defaults.overridesFrom(defaults))
        assertNull(defaults.copy(iconSize = 135).overridesFrom(defaults))
        val desired = defaults.copy(shape = IconShape.Circle)
        val saved = requireNotNull(desired.overridesFrom(defaults))
        assertNull(saved.withThemeDefaults(desired).overridesFrom(desired))
    }

    @Test fun dotStyleDoesNotFreezeSizeShapeOrColor() {
        val all = IconDisplaySettings()
        val saved = requireNotNull(all.copy(style = IconDisplayStyle.Dot).overridesFrom(all))
        assertEquals(setOf("style"), saved.overriddenFields)
        val next = all.copy(dotSize = 22, dotShape = IconShape.Square, dotSquareCornerRadius = 35,
            dotSquareAspectRatio = 1.5f).withColor(IconColor.Theme.copy(brightnessShift = -10))
        assertEquals(next.copy(style = IconDisplayStyle.Dot), saved.resolve(next))
    }

    @Test fun everyDotPropertyCanBeOverriddenIndependently() {
        val all = IconDisplaySettings()
        val cases = listOf<Pair<String, (IconDisplaySettings) -> IconDisplaySettings>>(
            "style" to { it.copy(style = IconDisplayStyle.Hidden) },
            "dotSize" to { it.copy(dotSize = 18) },
            "dotShape" to { it.copy(dotShape = IconShape.Cookie) },
            "dotCookieSides" to { it.copy(dotCookieSides = 7) },
            "dotPebbleRoundness" to { it.copy(dotPebbleRoundness = 45) },
            "dotSquareCornerRadius" to { it.copy(dotSquareCornerRadius = 65) },
            "dotSquareAspectRatio" to { it.copy(dotSquareAspectRatio = .5f) },
        )
        cases.forEach { (field, change) ->
            val desired = change(all)
            val saved = requireNotNull(desired.overridesFrom(all))
            assertEquals(field, setOf(field), saved.overriddenFields)
            val inherited = all.withColor(IconColor.Theme)
            assertEquals(field, change(inherited), saved.resolve(inherited))
        }
    }

    @Test fun dotColorSourceAndOffsetsStayTogetherWithoutFreezingOtherProperties() {
        val all = IconDisplaySettings().withColor(IconColor.FromApp.copy(hueShift = 30))
        for (color in listOf(IconColor.Theme, IconColor.FromApp,
            IconColor.Theme.copy(saturationShift = -25), IconColor(0xff654321.toInt()))) {
            val saved = requireNotNull(all.withColor(color).overridesFrom(all))
            assertEquals(setOf("dotColor", "customColor"), saved.overriddenFields)
            val next = all.copy(style = IconDisplayStyle.Dot, dotSize = 24).withColor(IconColor(0xff112233.toInt()))
            assertEquals(next.withColor(color), saved.resolve(next))
        }
        assertNull(all.overridesFrom(all))
    }

    @Test fun customSourceRetainsItsIdentityButInheritsAllOtherProperties() {
        val edited = ItemIcon("pack", "example.icons", "alternate", defaults.copy(invertBackgroundDetection = true))
        val saved = edited.overridesFrom(defaults, IconDisplaySettings())
        val next = defaults.copy(shape = IconShape.Square, background = IconColor.FromApp, iconSize = 140)
        assertEquals(edited.copy(design = next.copy(invertBackgroundDetection = true)), saved.resolve(next, IconDisplaySettings()))
        assertEquals(ItemIcon.Theme, ItemIcon.Theme.copy(design = defaults,
            display = IconDisplaySettings()).overridesFrom(defaults, IconDisplaySettings()))
    }

    @Test fun legacyCompleteDesignsRemainUnchangedUntilResaved() {
        val legacy = defaults.copy(shape = IconShape.Circle)
        val next = defaults.copy(shape = IconShape.Cookie, background = IconColor.FromApp)
        assertEquals(legacy, legacy.withThemeDefaults(next))
        val converted = requireNotNull(legacy.overridesFrom(defaults))
        assertEquals(next.copy(shape = IconShape.Circle), converted.withThemeDefaults(next))
    }
}
