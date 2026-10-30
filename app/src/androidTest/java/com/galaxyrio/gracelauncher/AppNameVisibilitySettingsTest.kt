package com.galaxyrio.gracelauncher

import android.content.ComponentName
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.galaxyrio.gracelauncher.data.*
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.overlays.LauncherOverlay
import com.galaxyrio.gracelauncher.ui.overlays.LauncherOverlays
import com.galaxyrio.gracelauncher.ui.overlays.ShortcutRevealState
import com.galaxyrio.gracelauncher.ui.settings.ListAppearanceSettings
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AppNameVisibilitySettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun dialogHasNeitherThenTheIndependentFolderSwitch() {
        var state by mutableStateOf(LauncherUiState(isLoadingApps = false))
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                ListAppearanceSettings(state, LauncherActions(updateSettings = { state = state.copy(settings = it(state.settings)) }),
                    onBack = {}, onManageFonts = {})
            }
        }
        compose.onNodeWithTag("list_appearance_controls").performScrollToNode(hasTestTag("list_show_names"))
        compose.onNodeWithTag("list_show_names").performClick()
        compose.onNodeWithTag("list_names_in_folders").performScrollTo().assertIsOff()
        val neither = compose.onNodeWithTag("list_names:Neither").fetchSemanticsNode().boundsInRoot
        val toggle = compose.onNodeWithTag("list_names_in_folders").fetchSemanticsNode().boundsInRoot
        assertTrue(neither.bottom <= toggle.top)
        compose.onNodeWithTag("list_names_in_folders").performClick().assertIsOn()
        compose.onNodeWithTag("list_names:Neither").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(AppNameVisibility.Neither, state.settings.listAppearance.names)
            assertTrue(state.settings.listAppearance.applyNameVisibilityToFolders)
        }
        compose.onNodeWithTag("list_show_names").performClick()
        compose.onNodeWithTag("list_names:Neither").assertIsSelected()
        compose.onNodeWithTag("list_names_in_folders").performScrollTo().performClick().assertIsOff()
        compose.runOnIdle { assertEquals(AppNameVisibility.Neither, state.settings.listAppearance.names) }
    }

    @Test fun homeFolderNamesAreVisibleUntilOptedInAndFollowTheSourceSurface() {
        val member = LauncherApp(ComponentName("test.mail", "MailActivity"), "Folder member", null)
        val folder = LauncherFolder("one", "Folder title", listOf(member.key), FolderPlacement.Favorites)
        val initial = LauncherUiState(apps = listOf(member), folders = listOf(folder), isLoadingApps = false)
        var state by mutableStateOf(initial.copy(settings = initial.settings.copy(listAppearance = ListAppearance(names = AppNameVisibility.AppList))))
        var favorites by mutableStateOf(true)
        val overlay = LauncherOverlay.Folder(folder, Rect(40f, 120f, 80f, 160f), ShortcutRevealState(animationsEnabled = false))
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                LauncherOverlays(overlay, state, LauncherActions(), onChange = {}, onLaunchApp = {},
                    onToggleFavorite = {}, onRequestCalendar = {}, favoritesSurface = favorites)
            }
        }
        compose.onNodeWithText(member.label).assertIsDisplayed()
        compose.runOnIdle { state = state.copy(settings = state.settings.copy(listAppearance =
            state.settings.listAppearance.copy(applyNameVisibilityToFolders = true))) }
        compose.onNodeWithText(member.label).assertDoesNotExist()
        compose.runOnIdle { favorites = false }
        compose.onNodeWithText(member.label).assertIsDisplayed()
    }

    @Test fun settingsRoundTripAndExistingDataDefaultsToUnaffectedFolderContents() {
        val settings = ListAppearance(names = AppNameVisibility.Neither, applyNameVisibilityToFolders = true)
        assertEquals(settings, ListAppearance.decode(settings.encode()))
        val legacy = ListAppearance.decode("""{"names":"AppList"}""")
        assertEquals(AppNameVisibility.AppList, legacy.names)
        assertFalse(legacy.applyNameVisibilityToFolders)
        assertTrue(legacy.showNames(favorites = true, insideFolder = true))
    }
}
