package com.galaxyrio.gracelauncher

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.settings.LauncherSettingsScreen
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductivityStructureTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun show(actions: LauncherActions = LauncherActions()) {
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                LauncherSettingsScreen(LauncherUiState(), actions, onBack = {}, initialPage = "Productivity")
            }
        }
    }

    private fun setting(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
    }

    @Test fun instantAccessUsesPopupTitleAndDescriptiveSubtitles() {
        show()
        setting("settings_grace_button").assertTextContains(context.getString(R.string.settings_grace_button_summary))
        setting("settings_shortcuts_folders")
            .assertTextContains(context.getString(R.string.popups_title))
            .assertTextContains(context.getString(R.string.shortcuts_folders_title)).performClick()
        compose.onNodeWithTag("shortcuts_folders_settings").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.popups_title)).assertIsDisplayed()
    }

    @Test fun wellbeingIsTheThirdAdvancedItemAndStillOpensItsSettings() {
        show()
        val wellbeing = setting("settings_digital_wellbeing")
        val gestures = compose.onNodeWithTag("settings_gestures").fetchSemanticsNode().boundsInRoot
        val haptics = compose.onNodeWithTag("allow_haptics").fetchSemanticsNode().boundsInRoot
        assertTrue(gestures.bottom <= haptics.top)
        assertTrue(haptics.bottom <= wellbeing.fetchSemanticsNode().boundsInRoot.top)
        wellbeing.performClick()
        compose.onNodeWithTag("digital_wellbeing").assertIsDisplayed()
    }

    @Test fun homepageEditorPlacesHeightActionBetweenBannerAndSelected() {
        var adjustments = 0
        show(LauncherActions(moveWidget = { adjustments++ }))
        setting("settings_edit_homepage").assertTextContains(context.getString(R.string.edit_homepage))
        compose.onNodeWithTag("settings_move_widget").assertDoesNotExist()
        compose.onNodeWithTag("settings_edit_homepage").performClick()
        compose.onNodeWithText(context.getString(R.string.edit_homepage)).assertIsDisplayed()
        val banner = compose.onNodeWithTag("favorites_lock_editing").fetchSemanticsNode().boundsInRoot
        val action = compose.onNodeWithTag("homepage_adjust_height").assertIsDisplayed()
        val selected = compose.onNodeWithTag("favorites_selected").fetchSemanticsNode().boundsInRoot
        assertTrue(banner.bottom < action.fetchSemanticsNode().boundsInRoot.top)
        assertTrue(action.fetchSemanticsNode().boundsInRoot.bottom <= selected.top)
        action.assertTextContains(context.getString(R.string.homepage_adjust_height)).performClick()
        compose.runOnIdle { assertEquals(1, adjustments) }
    }
}
