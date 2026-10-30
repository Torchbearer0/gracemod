package com.galaxyrio.gracelauncher

import android.content.ComponentName
import android.os.UserHandle
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.galaxyrio.gracelauncher.data.*
import com.galaxyrio.gracelauncher.platform.PrivateSpaceState
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherScreen
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.overlays.HomepageEditorScreen
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ProfileFavoritesTest {
    @get:Rule val compose = createComposeRule()
    private val privateUser = UserHandle.getUserHandleForUid(1_000_000)
    private val workUser = UserHandle.getUserHandleForUid(1_100_000)
    private val personal = LauncherApp(ComponentName("fixture.personal", "Activity"), "Personal", null)
    private val privateApp = LauncherApp(ComponentName("fixture.private", "Activity"), "Private member", null,
        user = privateUser, userSerial = 10, isPrivateSpace = true)
    private val workApp = LauncherApp(ComponentName("fixture.work", "Activity"), "Work member", null,
        user = workUser, userSerial = 11, isWorkProfile = true)
    private fun availableState() = LauncherUiState(apps = listOf(personal, workApp), privateApps = listOf(privateApp),
        isLoadingApps = false, isDefaultHome = true,
        privateSpace = PrivateSpaceState(supported = true, user = privateUser, serial = 10, locked = true),
        workProfiles = listOf(WorkProfile(workUser, 11)))

    @Test fun combinedGroupHasThreeAddActionsThenDisabledOrSelectableProfileCheckboxes() {
        var state by mutableStateOf(LauncherUiState(isLoadingApps = false))
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                HomepageEditorScreen(state, LauncherActions(), onToggle = { app ->
                    val order = if (app.key in state.favoriteKeys) state.favoriteOrder - app.key else state.favoriteOrder + app.key
                    state = state.copy(favoriteKeys = order.toSet(), favoriteOrder = order)
                }, onReorder = { state = state.copy(favoriteOrder = it) }, onDone = {})
            }
        }
        compose.onNodeWithTag("favorites_app_groups").performScrollTo().assertExists()
        listOf("favorites_shortcuts", "favorites_folders", "favorites_stacks").forEach {
            compose.onNodeWithTag(it).assertDoesNotExist()
        }
        listOf("favorites_add_shortcut", "favorites_add_folder", "favorites_add_stack").forEach {
            compose.onNodeWithTag(it).performScrollTo().assertIsEnabled()
        }
        listOf(PrivateSpaceFolderKey, WorkProfileFolderKey).forEach {
            compose.onNodeWithTag("favorites_group:$it").performScrollTo().assertIsOff().assertIsNotEnabled()
        }
        compose.runOnIdle { state = availableState() }
        listOf(PrivateSpaceFolderKey, WorkProfileFolderKey).forEach {
            compose.onNodeWithTag("favorites_group:$it").performScrollTo().assertIsEnabled().performClick().assertIsOn()
        }
        compose.onNodeWithTag("favorite:$PrivateSpaceFolderKey").performScrollTo().assertIsOn()
        compose.onNodeWithTag("favorite_drag:$PrivateSpaceFolderKey").assertExists()
        compose.onNodeWithTag("favorite:$PrivateSpaceFolderKey").performClick()
        compose.onNodeWithTag("favorites_group:$PrivateSpaceFolderKey").performScrollTo().assertIsOff()
        compose.runOnIdle {
            state = state.copy(settings = state.settings.copy(workProfile = state.settings.workProfile.copy(enabled = false)))
        }
        compose.onNodeWithTag("favorites_group:$WorkProfileFolderKey").performScrollTo().assertIsOn().assertIsNotEnabled()
        compose.runOnIdle {
            assertTrue(WorkProfileFolderKey in state.favoriteKeys)
            assertTrue(state.favoriteItems.isEmpty())
        }
    }

    @Test fun profileFoldersShareFavoriteOrderAndSurviveLockingAndTemporaryUnavailability() {
        val ordinary = LauncherFolder("ordinary", "Ordinary", emptyList(), FolderPlacement.Favorites)
        val order = listOf(WorkProfileFolderKey, personal.key, ordinary.key, PrivateSpaceFolderKey)
        val state = availableState().copy(favoriteKeys = order.toSet(), favoriteOrder = order, folders = listOf(ordinary))
        assertEquals(order, state.favoriteItems.map { it.key })
        assertEquals(order.reversed(), state.copy(favoriteOrder = order.reversed()).favoriteItems.map { it.key })
        assertTrue(state.privateFolder.appKeys.isEmpty())
        assertTrue(state.privateSpaceApps.isEmpty())
        val disabled = state.copy(settings = state.settings.copy(privateSpace = state.settings.privateSpace.copy(enabled = false),
            workProfile = state.settings.workProfile.copy(enabled = false)))
        assertEquals(listOf(personal.key, ordinary.key), disabled.favoriteItems.map { it.key })
        assertEquals(order.toSet(), disabled.favoriteKeys)
        assertEquals(order, disabled.copy(settings = state.settings).favoriteItems.map { it.key })
        assertEquals(listOf(personal.key, ordinary.key), state.copy(privateSpace = PrivateSpaceState(), workProfiles = emptyList()).favoriteItems.map { it.key })
        PrivateSpaceDisplay.entries.forEach { display ->
            val alternate = state.copy(settings = state.settings.copy(
                privateSpace = state.settings.privateSpace.copy(display = display), workProfile = state.settings.workProfile.copy(display = display)))
            assertEquals(order, alternate.favoriteItems.map { it.key })
        }
    }

    @Test fun privateFavoriteAuthenticatesThenOpensOnHomeEvenWhenDrawerModeIsList() {
        val initial = availableState()
        var state by mutableStateOf(initial.copy(favoriteKeys = setOf(PrivateSpaceFolderKey), favoriteOrder = listOf(PrivateSpaceFolderKey),
            settings = initial.settings.copy(clockEnabled = false, calendarAgenda = false, hideStatusBar = false,
                privateSpace = initial.settings.privateSpace.copy(display = PrivateSpaceDisplay.List))))
        var ready: (() -> Unit)? = null
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                LauncherScreen(state, onDateClick = {}, onClockClick = {}, onLaunchApp = {}, onToggleFavorite = {},
                    actions = LauncherActions(requestPrivateSpace = { editing, callback -> assertFalse(editing); ready = callback },
                        closePrivateSpace = { state = state.copy(privateSpace = state.privateSpace.copy(locked = true, accessible = false)) }))
            }
        }
        compose.onNodeWithTag(PrivateSpaceFolderKey).performClick()
        compose.onNodeWithTag("folder_popup").assertDoesNotExist()
        compose.onNodeWithText(privateApp.label).assertDoesNotExist()
        compose.runOnIdle {
            assertNotNull(ready)
            state = state.copy(privateSpace = state.privateSpace.copy(locked = false, accessible = true))
        }
        compose.runOnIdle { ready!!.invoke() }
        compose.onNodeWithTag("folder_popup").assertIsDisplayed()
        compose.onNodeWithTag("home_content").assertIsDisplayed()
        compose.onNodeWithText(privateApp.label).assertIsDisplayed()
        compose.onNodeWithTag("folder_popup").performSemanticsAction(SemanticsActions.Dismiss) { it() }
        compose.onNodeWithTag("folder_popup").assertDoesNotExist()
        compose.runOnIdle { assertTrue(state.privateSpace.locked) }
        compose.onNodeWithTag(PrivateSpaceFolderKey).assertIsDisplayed()
    }

    @Test fun workFavoriteOpensAPopupOnHomeEvenWhenDrawerModeIsList() {
        val initial = availableState()
        val state = initial.copy(favoriteKeys = setOf(WorkProfileFolderKey), favoriteOrder = listOf(WorkProfileFolderKey),
            settings = initial.settings.copy(clockEnabled = false, calendarAgenda = false, hideStatusBar = false,
                workProfile = initial.settings.workProfile.copy(display = PrivateSpaceDisplay.List)))
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                LauncherScreen(state, onDateClick = {}, onClockClick = {}, onLaunchApp = {}, onToggleFavorite = {})
            }
        }
        compose.onNodeWithTag(WorkProfileFolderKey).performClick()
        compose.onNodeWithTag("folder_popup").assertIsDisplayed()
        compose.onNodeWithTag("home_content").assertIsDisplayed()
        compose.onNodeWithText(workApp.label).assertIsDisplayed()
    }
}
