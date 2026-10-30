package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.GraceButtonAction
import com.galaxyrio.gracelauncher.data.LauncherSettings
import com.galaxyrio.gracelauncher.platform.performGlobalSystemAction
import org.junit.Assert.*
import org.junit.Test

class SystemActionRoutingTest {
    private val panels = listOf(GraceButtonAction.Notifications, GraceButtonAction.QuickSettings)

    @Test fun defaultsKeepExistingAccessibilityBehavior() {
        assertFalse(LauncherSettings().preferNonAccessibility)
        (panels + GraceButtonAction.LockScreen).forEach { action ->
            assertTrue(action.requiresAccessibility(false))
            assertTrue(performGlobalSystemAction(action, false,
                accessibility = { assertEquals(action, it); true },
                statusBar = { error("Alternative must be opt-in") }))
        }
    }

    @Test fun optInUsesOnlyStatusBarApiForBothPanels() {
        panels.forEach { action ->
            assertFalse(action.requiresAccessibility(true))
            assertTrue(performGlobalSystemAction(action, true,
                accessibility = { error("Must not use accessibility even if available") },
                statusBar = { assertEquals(action, it); true }))
        }
    }

    @Test fun failedAlternativeNeverFallsBackEvenOnRepeatedAttempts() {
        panels.forEach { action ->
            var attempts = 0
            repeat(2) {
                assertFalse(performGlobalSystemAction(action, true,
                    accessibility = { error("No automatic fallback") },
                    statusBar = { attempts++; false }))
            }
            assertEquals(2, attempts)
        }
    }

    @Test fun lockStillRequiresAccessibilityWithExperimentEnabled() {
        val action = GraceButtonAction.LockScreen
        assertTrue(action.requiresAccessibility(true))
        assertFalse(performGlobalSystemAction(action, true,
            accessibility = { assertEquals(action, it); false },
            statusBar = { error("Lock has no lower-permission alternative") }))
    }

    @Test fun disablingExperimentRestoresAccessibilityRoute() {
        panels.forEach { action ->
            val calls = mutableListOf<String>()
            listOf(true, false).forEach { enabled ->
                performGlobalSystemAction(action, enabled,
                    accessibility = { calls += "accessibility"; true },
                    statusBar = { calls += "api"; false })
            }
            assertEquals(listOf("api", "accessibility"), calls)
        }
    }

    @Test fun otherActionsNeverUseEitherGlobalBackend() {
        GraceButtonAction.entries.filterNot { it.requiresAccessibility }.forEach { action ->
            listOf(false, true).forEach { enabled ->
                assertFalse(action.requiresAccessibility(enabled))
                assertFalse(performGlobalSystemAction(action, enabled,
                    accessibility = { error("Unexpected accessibility action") },
                    statusBar = { error("Unexpected status-bar action") }))
            }
        }
    }
}
