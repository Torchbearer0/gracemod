package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.*
import org.junit.Assert.*
import org.junit.Test

class ShortcutIconPolicyTest {
    private val all = IconDesign.defaults().copy(shape = IconShape.Square, themeIcons = true,
        themeUnsupportedIcons = true, background = IconColor.FromApp, iconSize = 125)
    private val display = IconDisplaySettings(style = IconDisplayStyle.Dot, dotSize = 18)

    @Test fun shortcutsOptOutByDefaultWithoutChangingOrdinaryApps() {
        assertFalse(all.applyToShortcuts)
        val shortcut = ItemIcon.Theme.resolve(all, display, isShortcut = true)
        assertEquals(IconDesign.defaults().copy(iconSize = 125), shortcut.design)
        assertEquals(IconDisplaySettings(), shortcut.display)
        val app = ItemIcon.Theme.resolve(all, display)
        assertEquals(all, app.design)
        assertNull(app.display)
    }

    @Test fun enablingIncludesBothIconDesignAndDisplaySettings() {
        val enabled = all.copy(applyToShortcuts = true)
        val shortcut = ItemIcon.Theme.resolve(enabled, display, isShortcut = true)
        assertEquals(enabled, shortcut.design)
        assertNull(shortcut.display) // Inherit the launcher's current dot/hidden mode.
        val next = enabled.copy(shape = IconShape.Pebble, foreground = IconColor.Theme.copy(hueShift = 30))
        assertEquals(next, ItemIcon.Theme.resolve(next, display, isShortcut = true).design)
    }

    @Test fun individualShortcutOverridesSurviveSwitchingTheSharedPolicy() {
        val enabled = all.copy(applyToShortcuts = true)
        val saved = ItemIcon.Theme.copy(design = enabled.copy(invertBackgroundDetection = true))
            .overridesFrom(enabled, display, isShortcut = true)
        assertEquals(setOf("invertBackgroundDetection"), saved.design?.overriddenFields)
        assertFalse(saved.design!!.applyToShortcuts)
        assertEquals(enabled.copy(invertBackgroundDetection = true), saved.resolve(enabled, display, true).design)
        assertEquals(IconDesign.defaults().copy(iconSize = 125, invertBackgroundDetection = true),
            saved.resolve(all, display, true).design)
    }

    @Test fun editingAShortcutWithTheSwitchOffUsesItsActualDefaults() {
        val desired = IconDesign.defaults().copy(shape = IconShape.Circle)
        val saved = ItemIcon.Theme.copy(design = desired).overridesFrom(all, display, isShortcut = true)
        assertEquals(setOf("shape"), saved.design?.overriddenFields)
        assertEquals(desired.copy(iconSize = 125), saved.resolve(all, display, true).design)
        assertEquals(all.copy(applyToShortcuts = true, shape = IconShape.Circle),
            saved.resolve(all.copy(applyToShortcuts = true), display, true).design)
    }
}
