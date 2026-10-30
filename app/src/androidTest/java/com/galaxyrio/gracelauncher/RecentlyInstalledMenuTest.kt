package com.galaxyrio.gracelauncher

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.data.ListAppearance
import com.galaxyrio.gracelauncher.data.RecentlyInstalledFolderId
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.overlays.LauncherOverlay
import com.galaxyrio.gracelauncher.ui.overlays.LauncherOverlays
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RecentlyInstalledMenuTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun virtualFolderOffersLimitedActionsAndTheCommonSettingsFooter() {
        var state by mutableStateOf(LauncherUiState(isLoadingApps = false))
        val app = state.recentlyInstalledFolder(context.getString(R.string.recently_installed)).asApp()
        var requested: LauncherOverlay? = null
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                LauncherOverlays(LauncherOverlay.AppDetails(app), state,
                    LauncherActions(rename = { item, name ->
                        assertEquals(RecentlyInstalledFolderId, item.folderId)
                        state = state.copy(settings = state.settings.copy(listAppearance =
                            state.settings.listAppearance.copy(recentlyInstalledName = name)))
                    }), onChange = { requested = it }, onLaunchApp = {}, onToggleFavorite = {}, onRequestCalendar = {})
            }
        }
        // This virtual folder is deliberately absent from state.folders, but its sheet must stay open.
        compose.onNodeWithTag("hide_recently_installed").assertExists()
        compose.onNodeWithTag("grace_settings").assertExists()
        compose.onNodeWithTag("advanced").performClick()
        listOf("edit_favorites", "folder_delete", "edit_popup", "app_details_hide", "app_details_package")
            .forEach { compose.onNodeWithTag(it).assertDoesNotExist() }
        compose.onNodeWithText(context.getString(R.string.settings_folder_placement)).assertDoesNotExist()
        compose.onNodeWithTag(context.getString(R.string.rename_folder)).performClick()
        compose.onNodeWithTag("text_entry").performTextReplacement("New apps")
        compose.onNodeWithText(context.getString(R.string.save)).performClick()
        compose.onNodeWithTag("app_details_title").assertTextEquals("New apps")
        compose.onNodeWithTag(context.getString(R.string.icon_designer_title)).performClick()
        compose.runOnIdle {
            val target = requested as LauncherOverlay.IconDesigner
            assertEquals(RecentlyInstalledFolderId, target.app.folderId)
            assertEquals("New apps", target.app.label)
        }
        compose.onNodeWithTag("grace_settings").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(LauncherOverlay.Settings, requested) }
    }

    @Test fun hideActionDisablesTheSettingAndClosesTheSheetWithoutDeletingCustomization() {
        val initial = LauncherUiState(isLoadingApps = false)
        var state by mutableStateOf(initial.copy(settings = initial.settings.copy(
            listAppearance = ListAppearance(recentlyInstalledName = "New apps"))))
        val app = state.recentlyInstalledFolder("Recently Installed").asApp()
        var requested: LauncherOverlay? = LauncherOverlay.AppDetails(app)
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                LauncherOverlays(LauncherOverlay.AppDetails(app), state,
                    LauncherActions(updateSettings = { state = state.copy(settings = it(state.settings)) }),
                    onChange = { requested = it }, onLaunchApp = {}, onToggleFavorite = {}, onRequestCalendar = {})
            }
        }
        compose.onNodeWithTag("hide_recently_installed").performClick()
        compose.runOnIdle {
            assertFalse(state.settings.listAppearance.showRecentlyInstalled)
            assertEquals("New apps", state.settings.listAppearance.recentlyInstalledName)
            assertNull(requested)
        }
    }

    @Test fun visibilityAndNameRoundTripWhileOldSettingsKeepTheFolderVisible() {
        val value = ListAppearance(showRecentlyInstalled = false, recentlyInstalledName = "New apps")
        assertEquals(value, ListAppearance.decode(value.encode()))
        assertTrue(ListAppearance.decode("{}").showRecentlyInstalled)
        assertNull(ListAppearance.decode("{}").recentlyInstalledName)
    }
}
