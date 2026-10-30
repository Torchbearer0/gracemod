package com.galaxyrio.gracelauncher

import android.content.ComponentName
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.data.*
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherScreen
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.settings.LauncherSettingsScreen
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GraceFolderActionTest {
    @get:Rule val compose = createComposeRule()
    private val app = LauncherApp(ComponentName("fixture.member", "Activity"), "Member", null)
    private val folder = LauncherFolder("tools", "Tools", listOf(app.key), FolderPlacement.None)
    private val target = GraceButtonTarget(GraceButtonAction.Folder, folder.key)

    @Test fun folderTargetsRoundTripWithStableKeysAndEnabledFlags() {
        var settings = GraceButtonSettings()
        GraceButtonGesture.entries.forEachIndexed { index, gesture ->
            settings = settings.withTarget(gesture, target.copy(enabled = index % 2 == 0))
        }
        assertEquals(settings, GraceButtonSettings.decode(settings.encode()))
        val home = GraceButtonSettings.homeDefaults().withTarget(GraceButtonGesture.SwipeUp, target)
        assertEquals(home, GraceButtonSettings.decode(home.encode(), GraceButtonSettings.homeDefaults()))
    }

    @Test fun folderPickerIsSingleChoiceAndDoesNotChangePlacement() {
        val second = folder.copy(id = "games", name = "Games", placement = FolderPlacement.AppList)
        var state by mutableStateOf(LauncherUiState(folders = listOf(folder, second)))
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                LauncherSettingsScreen(state, LauncherActions(updateSettings = { state = state.copy(settings = it(state.settings)) }),
                    onBack = {}, initialPage = "GraceButton")
            }
        }
        compose.onNodeWithTag("grace_gesture:Tap").performClick()
        compose.onNodeWithTag("grace_action:Folder").performClick()
        compose.onNodeWithTag("folder:${folder.id}").assertIsNotSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).performClick()
        compose.onNodeWithTag("grace_action:Folder").assertIsSelected().assertTextContains(folder.name)
        compose.runOnIdle {
            assertEquals(target, state.settings.graceButton.tap)
            assertEquals(listOf(folder, second), state.folders)
        }
        compose.onNodeWithTag("grace_action:Folder").performClick()
        compose.onNodeWithTag("folder:${folder.id}").assertIsSelected()
        compose.onNodeWithTag("folder:${second.id}").assertIsNotSelected().performClick()
        compose.runOnIdle {
            assertEquals(second.key, state.settings.graceButton.tap.itemKey)
            state = state.copy(folders = state.folders.map { if (it.id == second.id) it.copy(name = "Renamed") else it })
        }
        compose.onNodeWithTag("grace_action:Folder").assertTextContains("Renamed")
        compose.onNodeWithTag("grace_action:Folder").performClick()
        compose.onNodeWithTag("folder:${folder.id}").assertIsNotSelected()
        compose.onNodeWithTag("folder:${second.id}").assertIsSelected()
    }

    @Test fun createFolderReturnsThroughTheExistingEditorAndCanBeSelectedForHomeGestures() {
        var state by mutableStateOf(LauncherUiState())
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                LauncherSettingsScreen(state, LauncherActions(
                    updateSettings = { state = state.copy(settings = it(state.settings)) },
                    saveFolder = { state = state.copy(folders = state.folders + it) }),
                    onBack = {}, initialPage = "Gestures")
            }
        }
        compose.onNodeWithTag("grace_gesture:SwipeUp").performClick()
        compose.onNodeWithTag("grace_action:Folder").performClick()
        compose.onNodeWithTag("folder_create").performClick()
        compose.onNodeWithTag("text_entry").performTextInput("New folder")
        val save = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.save)
        compose.onNodeWithText(save).performClick()
        compose.onNodeWithTag("folder_name").assertIsDisplayed()
        compose.onNodeWithTag("settings_back").performClick()
        val created = compose.runOnIdle { state.folders.single() }
        compose.onNodeWithTag("folder:${created.id}").assertIsNotSelected().performClick()
        compose.runOnIdle {
            assertEquals(GraceButtonTarget(GraceButtonAction.Folder, created.key), state.settings.homeGestures.swipeUp)
            assertEquals(GraceButtonSettings(), state.settings.graceButton)
        }
    }

    @Test fun buttonOpensAnUnplacedFolderUsingTheExistingHeroAndPopupContents() {
        showLauncher(animations = true)
        val button = compose.onNodeWithTag("launcher_fab").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("launcher_fab").performClick()
        compose.mainClock.advanceTimeByFrame()
        val initial = compose.onNodeWithTag("folder_popup").fetchSemanticsNode().boundsInRoot
        assertEquals(button.center.x, initial.center.x, 2f)
        assertEquals(button.center.y, initial.center.y, 2f)
        assertEquals(button.width, initial.width, 2f)
        compose.mainClock.advanceTimeBy(80)
        val progress = compose.onNodeWithTag("folder_popup").fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
        assertTrue(progress > 0f && progress < 1f)
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText(app.label).assertIsDisplayed()
        compose.onNodeWithTag("folder_popup").performSemanticsAction(SemanticsActions.Dismiss) { it() }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("folder_popup").assertDoesNotExist()
    }

    @Test fun animationSwitchAlsoDisablesTheFolderHero() {
        showLauncher(animations = false)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("launcher_fab").performClick()
        compose.mainClock.advanceTimeByFrame()
        assertEquals(1f, compose.onNodeWithTag("folder_popup").fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo].current, 0f)
        compose.onNodeWithTag("folder_popup").performSemanticsAction(SemanticsActions.Dismiss) { it() }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("folder_popup").assertDoesNotExist()
    }

    private fun showLauncher(animations: Boolean) {
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                LauncherScreen(LauncherUiState(apps = listOf(app), folders = listOf(folder), isLoadingApps = false,
                    settings = LauncherSettings(graceButton = GraceButtonSettings(tap = target, animationsEnabled = animations))),
                    onDateClick = {}, onClockClick = {}, onLaunchApp = { fail("A folder must not be launched as an Android activity") },
                    onToggleFavorite = {})
            }
        }
    }
}
