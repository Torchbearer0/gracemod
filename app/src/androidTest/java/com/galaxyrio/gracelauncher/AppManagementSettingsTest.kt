package com.galaxyrio.gracelauncher

import android.content.ComponentName
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.galaxyrio.gracelauncher.data.*
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.overlays.LauncherOverlay
import com.galaxyrio.gracelauncher.ui.overlays.LauncherOverlays
import com.galaxyrio.gracelauncher.ui.settings.LauncherSettingsScreen
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import com.materialkolor.PaletteStyle
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AppManagementSettingsTest {
    @get:Rule val compose = createComposeRule()
    private val apps = listOf("Alpha", "Beta").map { LauncherApp(ComponentName("test.${it.lowercase()}", "Activity"), it, null) }

    @Test fun hiddenListLocksFolderSourcesAndSaveDoesNotConvertThemIntoManualHides() {
        val folder = LauncherFolder("one", "One", apps.map { it.key }, FolderPlacement.None, autoHideApps = true)
        var state by mutableStateOf(LauncherUiState(apps = apps, isLoadingApps = false,
            hiddenAppKeys = setOf(apps[0].key), folders = listOf(folder)))
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                LauncherSettingsScreen(state, LauncherActions(setHiddenApps = { state = state.copy(hiddenAppKeys = it) }),
                    onBack = {}, initialPage = "HiddenApps")
            }
        }
        apps.forEach { compose.onNodeWithTag("hidden_app:${it.key}").assertIsOn().assertIsNotEnabled() }
        compose.onNodeWithTag("hidden_apps_save").performClick()
        compose.runOnIdle {
            assertEquals(setOf(apps[0].key), state.hiddenAppKeys)
            state = state.copy(folders = emptyList())
            assertEquals(setOf(apps[0].key), state.effectiveHiddenAppKeys)
        }
        compose.onNodeWithTag("hidden_app:${apps[0].key}").assertIsOn().assertIsEnabled()
        compose.onNodeWithTag("hidden_app:${apps[1].key}").assertIsOff().assertIsEnabled()
    }

    @Test fun paletteStyleIsThirdAndSavesTheChosenStyle() {
        var state by mutableStateOf(LauncherUiState(isLoadingApps = false))
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                LauncherSettingsScreen(state, LauncherActions(updateSettings = { state = state.copy(settings = it(state.settings)) }),
                    onBack = {}, initialPage = "Themes")
            }
        }
        compose.onNodeWithTag("settings_palette_style").performScrollTo()
        val dynamic = compose.onNodeWithTag("settings_dynamic_colors").fetchSemanticsNode().boundsInRoot
        val style = compose.onNodeWithTag("settings_palette_style").fetchSemanticsNode().boundsInRoot
        val dark = compose.onNodeWithTag("settings_theme_mode").fetchSemanticsNode().boundsInRoot
        assertTrue(dynamic.bottom <= style.top && style.bottom <= dark.top)
        compose.onNodeWithTag("settings_palette_style").performClick()
        compose.onNodeWithTag("palette_style:Expressive").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(PaletteStyle.Expressive, state.settings.paletteStyle) }
    }

    @Test fun appListDetailsPassTheCurrentAppToFavorites() {
        var requested: LauncherOverlay? = null
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                LauncherOverlays(LauncherOverlay.AppDetails(apps[0], fromAppList = true),
                    LauncherUiState(apps = apps, isLoadingApps = false), LauncherActions(),
                    onChange = { requested = it }, onLaunchApp = {}, onToggleFavorite = {}, onRequestCalendar = {})
            }
        }
        compose.onNodeWithTag("edit_favorites").performClick()
        compose.runOnIdle { assertEquals(LauncherOverlay.Homepage(apps[0].key), requested) }
    }

    @Test fun advancedHideActionPrecedesThePackageRowAndHidesTheCurrentApp() {
        var hidden: LauncherApp? = null
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                LauncherOverlays(LauncherOverlay.AppDetails(apps[0]), LauncherUiState(apps = apps, isLoadingApps = false),
                    LauncherActions(hideApp = { hidden = it }), onChange = {}, onLaunchApp = {},
                    onToggleFavorite = {}, onRequestCalendar = {})
            }
        }
        compose.onNodeWithTag("advanced").performScrollTo().performClick()
        compose.onNodeWithTag("app_details_hide").performScrollTo()
        assertTrue(compose.onNodeWithTag("app_details_hide").fetchSemanticsNode().boundsInRoot.bottom <=
            compose.onNodeWithTag("app_details_package").fetchSemanticsNode().boundsInRoot.top)
        compose.onNodeWithTag("app_details_hide").performClick()
        compose.runOnIdle { assertEquals(apps[0], hidden) }
    }
}
