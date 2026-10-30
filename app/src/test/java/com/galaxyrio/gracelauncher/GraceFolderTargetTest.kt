package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.GraceButtonAction
import com.galaxyrio.gracelauncher.data.GraceButtonGesture
import com.galaxyrio.gracelauncher.data.GraceButtonSettings
import com.galaxyrio.gracelauncher.data.GraceButtonTarget
import org.junit.Assert.*
import org.junit.Test

class GraceFolderTargetTest {
    private val folder = GraceButtonTarget(GraceButtonAction.Folder, "folder:tools")

    @Test fun everyButtonGestureCanTargetAFolderWithoutChangingOtherActions() {
        val defaults = GraceButtonSettings()
        GraceButtonGesture.entries.forEach { gesture ->
            val configured = defaults.withTarget(gesture, folder)
            assertEquals(folder, configured.target(gesture))
            GraceButtonGesture.entries.filterNot { it == gesture }.forEach {
                assertEquals(defaults.target(it), configured.target(it))
            }
        }
    }

    @Test fun disablingAnActionPreservesItsSelectedFolder() {
        val settings = GraceButtonSettings().withTarget(GraceButtonGesture.SwipeUp, folder)
        val disabled = settings.withEnabled(GraceButtonGesture.SwipeUp, false)
        assertFalse(disabled.swipeUp.active)
        assertEquals(folder.itemKey, disabled.swipeUp.itemKey)
        assertEquals(settings, disabled.withEnabled(GraceButtonGesture.SwipeUp, true))
        assertFalse(folder.action.requiresAccessibility)
    }

    @Test fun homeGesturesReuseTheSameFolderTarget() {
        val defaults = GraceButtonSettings.homeDefaults()
        val configured = defaults.withTarget(GraceButtonGesture.SwipeUp, folder)
        assertEquals(folder, configured.swipeUp)
        assertEquals(defaults.swipeDown, configured.swipeDown)
        assertEquals(defaults.tap, configured.tap)
    }
}
