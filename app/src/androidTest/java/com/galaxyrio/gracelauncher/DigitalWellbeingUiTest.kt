package com.galaxyrio.gracelauncher

import android.content.ComponentName
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.galaxyrio.gracelauncher.data.*
import com.galaxyrio.gracelauncher.ui.*
import com.galaxyrio.gracelauncher.ui.drawer.AppListModel
import com.galaxyrio.gracelauncher.ui.drawer.DrawerItem
import com.galaxyrio.gracelauncher.ui.settings.LauncherSettingsScreen
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DigitalWellbeingUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun settingsRoundTripIncludingNoDisplayOptions() {
        val value = DigitalWellbeingSettings(enabled = true, showUsage = false, sortApps = true,
            display = setOf(UsageDisplay.Month, UsageDisplay.Percentage), refresh = UsageRefresh.Never, hideZeroValues = false)
        assertEquals(value, DigitalWellbeingSettings.decode(value.encode()))
        assertTrue(DigitalWellbeingSettings.decode(value.copy(display = emptySet()).encode()).display.isEmpty())
        assertEquals(DigitalWellbeingSettings(), DigitalWellbeingSettings.decode(null))
        assertTrue(DigitalWellbeingSettings.decode("{}").hideZeroValues)
        assertEquals(setOf(UsageDisplay.Day, UsageDisplay.Percentage), DigitalWellbeingSettings.decode(
            """{"display":["Day","Week","Month","Percentage"]}""").display)
    }

    @Test fun permissionAndSplitRowsKeepSwitchesSeparateFromDialogs() {
        var state by mutableStateOf(LauncherUiState())
        var permissions = 0
        var refreshes = 0
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                LauncherSettingsScreen(state, LauncherActions(
                    updateSettings = { state = state.copy(settings = it(state.settings)) },
                    requestUsageAccess = { permissions++ }, refreshUsageRanking = { refreshes++ }),
                    onBack = {}, initialPage = "DigitalWellbeing")
            }
        }
        compose.onNodeWithTag("usage_enabled").assertIsOff().performClick()
        compose.onNodeWithTag("usage_grant").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, permissions); state = state.copy(usage = UsageSnapshot(hasAccess = true)) }
        compose.onNodeWithTag("usage_show_favorites_switch").assertIsOn().performClick().assertIsOff()
        compose.onNodeWithTag("usage_display:Day").assertDoesNotExist()
        compose.onNodeWithTag("usage_show_favorites").performClick()
        compose.onNodeWithTag("usage_display:Day").assertIsSelected()
        compose.onNodeWithTag("usage_hide_zero").performScrollTo().assertIsOn().performClick().assertIsOff()
        compose.runOnIdle { assertFalse(state.settings.digitalWellbeing.hideZeroValues) }
        compose.onNodeWithTag("usage_display:Week").performScrollTo()
        compose.onNodeWithTag("usage_display:Week").performClick().assertIsSelected()
        compose.onNodeWithTag("usage_display:Day").assertIsNotSelected()
        compose.onNodeWithTag("usage_display:Percentage").performClick().assertIsOn()
        compose.runOnIdle {
            assertEquals(setOf(UsageDisplay.Week, UsageDisplay.Percentage), state.settings.digitalWellbeing.display)
            assertFalse(state.settings.digitalWellbeing.showUsage)
        }
        compose.onNodeWithTag("usage_display:None").performClick().assertIsSelected()
        compose.onNodeWithTag("usage_display:Percentage").assertIsOn()
        compose.runOnIdle { assertEquals(setOf(UsageDisplay.Percentage), state.settings.digitalWellbeing.display) }
        compose.onNodeWithTag("usage_display:Month").performClick().assertIsSelected()
        compose.onNodeWithTag("usage_display:Percentage").performClick().assertIsOff()
        compose.runOnIdle { assertEquals(setOf(UsageDisplay.Month), state.settings.digitalWellbeing.display) }
        compose.onNodeWithTag("usage_details_done").performClick()
        compose.onNodeWithTag("usage_sort").performClick()
        compose.onNodeWithTag("usage_frequency:Never").performClick().assertIsSelected()
        compose.onNodeWithTag("usage_refresh_now").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(UsageRefresh.Never, state.settings.digitalWellbeing.refresh)
            assertEquals(1, refreshes)
        }
    }

    @Test fun rankOnlyReordersPersonalAppsInsideEachLetterAndPreservesFolderSlots() {
        fun app(pkg: String, name: String) = LauncherApp(ComponentName(pkg, "Activity"), name, null)
        val alpha = app("alpha", "Alpha")
        val azure = app("azure", "Azure")
        val beta = app("beta", "Beta")
        val folder = LauncherFolder("folder", "Amber", emptyList(), FolderPlacement.AppList, appListAtBottom = false)
        val original = AppListModel(listOf(alpha, azure, beta), listOf(folder))
        val ranked = AppListModel(listOf(alpha, azure, beta), listOf(folder), usageRanking = mapOf("azure" to 50L, "beta" to 100L))
        assertEquals(original.letters, ranked.letters)
        assertEquals(listOf("azure", "alpha", "beta"), ranked.items.filterIsInstance<DrawerItem.App>().map { it.app.packageName })
        assertEquals(original.items.indexOfFirst { it is DrawerItem.Folder }, ranked.items.indexOfFirst { it is DrawerItem.Folder })
        assertEquals(original.items, AppListModel(listOf(alpha, azure, beta), listOf(folder), usageRanking = mapOf("alpha" to 10L, "azure" to 10L)).items)
    }
}
