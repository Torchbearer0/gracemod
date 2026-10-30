package com.galaxyrio.gracelauncher

import android.content.ComponentName
import android.os.UserHandle
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.data.*
import com.galaxyrio.gracelauncher.platform.PrivateSpaceState
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.home.WidgetScreen
import com.galaxyrio.gracelauncher.ui.overlays.*
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AppGroupEditorsTest {
    @get:Rule val compose = createComposeRule()
    private val owner = LauncherApp(ComponentName("test.owner", "Main"), "Owner", null)
    private val member = LauncherApp(ComponentName("test.member", "Main"), "Member", null)
    private val folder = LauncherFolder("folder", "Test folder", listOf(member.key), FolderPlacement.AppList)
    private fun withProfiles(state: LauncherUiState): LauncherUiState = state.copy(
        privateSpace = PrivateSpaceState(supported = true, user = UserHandle.getUserHandleForUid(1_000_000), serial = 10, locked = true),
        workProfiles = listOf(WorkProfile(UserHandle.getUserHandleForUid(1_100_000), 11)),
    )

    @Test fun popupUsesCombinedGroupAndCanSelectFoldersWithoutChangingFavorites() {
        var state by mutableStateOf(LauncherUiState(apps = listOf(owner, member), folders = listOf(folder), isLoadingApps = false))
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                PopupEditorScreen(owner, state, LauncherActions(
                    shortcuts = { ShortcutResult(ShortcutStatus.Ready) },
                    updatePopup = { app, _, transform -> state = state.copy(popups = state.popups +
                        (app.key to transform(state.popups[app.key].orEmpty()))) }), onBack = {})
            }
        }
        compose.onNodeWithTag("popup_editor_app_groups").performScrollTo().assertExists()
        listOf("shortcuts", "folders", "stacks").forEach { compose.onNodeWithTag("popup_editor_$it").assertDoesNotExist() }
        listOf("shortcut", "folder", "stack").forEach { compose.onNodeWithTag("popup_editor_add_$it").performScrollTo().assertIsEnabled() }
        listOf(PrivateSpaceFolderKey, WorkProfileFolderKey).forEach {
            compose.onNodeWithTag("popup_editor_group:$it").assertDoesNotExist()
        }
        compose.onNodeWithTag("popup_editor_add_folder").performScrollTo().performClick()
        compose.onNodeWithTag("folder_create").assertExists()
        compose.onNodeWithTag(folder.key).performClick().assertIsOn()
        compose.onNodeWithTag("settings_back").performClick()
        compose.onNodeWithTag("favorite:${folder.key}").performScrollTo().assertIsOn()
        compose.runOnIdle {
            assertEquals(listOf(folder.key), state.popups.getValue(owner.key).map { it.key })
            assertEquals(FolderPlacement.AppList, state.folders.single().placement)
            assertTrue(state.favoriteKeys.isEmpty())
        }
    }

    @Test fun widgetEditorHasAvailabilityGatedProfileChoicesIndependentOfFavorites() {
        var state by mutableStateOf(LauncherUiState(isLoadingApps = false))
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                WidgetScreenEditor(state, LauncherActions(updatePopup = { app, _, transform ->
                    state = state.copy(popups = state.popups + (app.key to transform(state.popups[app.key].orEmpty()))) }), {})
            }
        }
        compose.onNodeWithTag("widget_screen_editor_app_groups").performScrollTo().assertExists()
        listOf("shortcut", "folder", "stack").forEach { compose.onNodeWithTag("widget_screen_editor_add_$it").performScrollTo().assertIsEnabled() }
        listOf(PrivateSpaceFolderKey, WorkProfileFolderKey).forEach {
            compose.onNodeWithTag("widget_screen_editor_group:$it").performScrollTo().assertIsNotEnabled().assertIsOff()
        }
        compose.runOnIdle { state = withProfiles(state) }
        listOf(PrivateSpaceFolderKey, WorkProfileFolderKey).forEach {
            compose.onNodeWithTag("widget_screen_editor_group:$it").performScrollTo().assertIsEnabled().performClick().assertIsOn()
        }
        compose.onNodeWithTag("favorite:$PrivateSpaceFolderKey").performScrollTo().performClick()
        compose.onNodeWithTag("widget_screen_editor_group:$PrivateSpaceFolderKey").performScrollTo().assertIsOff()
        compose.runOnIdle {
            assertEquals(listOf(WorkProfileFolderKey), state.popups.getValue(WidgetScreenKey).map { it.key })
            assertTrue(state.favoriteKeys.isEmpty())
        }
    }

    @Test fun widgetFoldersUseFolderOpeningNotAndroidActivityLaunching() {
        val keys = listOf(folder.key, PrivateSpaceFolderKey, WorkProfileFolderKey)
        var state by mutableStateOf(withProfiles(LauncherUiState(folders = listOf(folder), isLoadingApps = false,
            popups = mapOf(WidgetScreenKey to keys.map { PopupItem(it) }))))
        val opened = mutableListOf<String>()
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                WidgetScreen(state, LauncherActions(launchAppAt = { _, _ -> fail("Folder sent to Android") }),
                    0.dp, 0.dp, editing = false, enabled = true, onEditingChange = {}, onEdit = {},
                    onLaunchApp = { fail("Folder sent to Android") }, onAppDetails = {},
                    onOpenFolder = { folder, _ -> opened += folder.key })
            }
        }
        keys.forEach { compose.onNodeWithTag("widget_screen_item:$it").performClick() }
        compose.runOnIdle {
            assertEquals(keys, opened)
            assertTrue(state.privateSpace.locked)
            state = state.copy(settings = state.settings.copy(workProfile = state.settings.workProfile.copy(enabled = false)))
        }
        compose.onNodeWithTag("widget_screen_item:$WorkProfileFolderKey").assertDoesNotExist()
        compose.runOnIdle { assertTrue(state.popups.getValue(WidgetScreenKey).any { it.key == WorkProfileFolderKey }) }
    }

    @Test fun widgetScreenLockKeepsTapsButBlocksHeldEditing() {
        val settings = WidgetScreenSettings(editingLocked = true)
        assertEquals(settings, WidgetScreenSettings.decode(settings.encode()))
        assertFalse(WidgetScreenSettings.decode("{}").editingLocked)
        val widget = PopupItem("widget:locked", HomeLayout(widgetId = -1001))
        val state = LauncherUiState(apps = listOf(member), isLoadingApps = false,
            settings = LauncherSettings(widgetScreen = settings),
            popups = mapOf(WidgetScreenKey to listOf(PopupItem(member.key), widget)))
        val opened = mutableListOf<String>()
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                WidgetScreen(state, LauncherActions(), 0.dp, 0.dp, editing = false, enabled = true,
                    onEditingChange = { assertFalse(it) }, onEdit = {},
                    onLaunchApp = { opened += it.key }, onAppDetails = {}, onOpenFolder = { _, _ -> })
            }
        }
        val appNode = compose.onNodeWithTag("widget_screen_item:${member.key}")
        appNode.performTouchInput { longClick(durationMillis = 1600L) }
        compose.onNodeWithTag("widget_screen_item:${widget.key}").performTouchInput { longClick(durationMillis = 1600L) }
        compose.onNodeWithTag("widget_screen_delete").assertDoesNotExist()
        compose.onNodeWithTag("widget_resize:right").assertDoesNotExist()
        appNode.performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf(member.key), opened) }
    }

    @Test fun stackEditorDoneClosesWithoutReopeningSettings() = assertStackEditorCloses("popup_editor_done")

    @Test fun stackEditorBackClosesWithoutReopeningSettings() = assertStackEditorCloses("settings_back")

    private fun assertStackEditorCloses(exitTag: String) {
        val stack = folder.copy(isAppStack = true, placement = FolderPlacement.Favorites)
        val state = LauncherUiState(apps = listOf(member), folders = listOf(stack), isLoadingApps = false,
            popups = mapOf(stack.key to listOf(PopupItem(member.key))))
        var overlay by mutableStateOf<LauncherOverlay?>(LauncherOverlay.FolderSettings(stack.id))
        val transitions = mutableListOf<LauncherOverlay?>()
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                LauncherOverlays(overlay, state, LauncherActions(),
                    onChange = { transitions += it; overlay = it }, onLaunchApp = {},
                    onToggleFavorite = {}, onRequestCalendar = {})
            }
        }
        compose.onNodeWithTag(exitTag).performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertNull(overlay)
            assertEquals(listOf<LauncherOverlay?>(null), transitions)
        }
        compose.onNodeWithTag("settings_navigation").assertDoesNotExist()
    }

    @Test fun popupFolderClickReplacesThePanelWithoutImmediatelyDismissingIt() {
        val state = LauncherUiState(apps = listOf(owner, member), folders = listOf(folder), isLoadingApps = false,
            popups = mapOf(owner.key to listOf(PopupItem(folder.key))))
        var overlay by mutableStateOf<LauncherOverlay?>(LauncherOverlay.Shortcuts(owner, Rect(24f, 150f, 64f, 190f),
            ShortcutRevealState(animationsEnabled = false)))
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                LauncherOverlays(overlay, state, LauncherActions(launchAppAt = { _, _ -> fail("Folder sent to Android") }),
                    onChange = { overlay = it }, onLaunchApp = {}, onToggleFavorite = {}, onRequestCalendar = {})
            }
        }
        compose.onNodeWithTag("shortcut:${folder.key}").performClick()
        compose.onNodeWithTag("folder_popup").assertIsDisplayed()
        compose.onNodeWithTag("folder_app:${member.key}").assertIsDisplayed()
        compose.runOnIdle { assertEquals(folder.id, (overlay as? LauncherOverlay.Folder)?.folder?.id) }
    }
}
