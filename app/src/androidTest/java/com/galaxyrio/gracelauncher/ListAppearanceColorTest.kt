package com.galaxyrio.gracelauncher

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.galaxyrio.gracelauncher.data.IconColor
import com.galaxyrio.gracelauncher.data.ListAppearance
import com.galaxyrio.gracelauncher.data.LauncherSettings
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.settings.LauncherSettingsScreen
import com.galaxyrio.gracelauncher.ui.settings.WallpaperEffectItem
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListAppearanceColorTest {
    @get:Rule val compose = createComposeRule()
    private var state by mutableStateOf(LauncherUiState())

    @Test fun overlayColorRoundTripsIndependentlyAndOldSettingsKeepAutomaticTint() {
        assertNull(ListAppearance.decode(null).wallpaperDimColor)
        assertNull(ListAppearance.decode("""{"fontSize":20,"fontColor":{"argb":-1}}""").wallpaperDimColor)
        listOf(null, IconColor(0xFF274B62.toInt()), IconColor.Theme).forEach { color ->
            val original = ListAppearance(fontColor = IconColor(-1), fontSize = 20, wallpaperDimColor = color)
            assertEquals(original, ListAppearance.decode(original.encode()))
        }
        val legacy = ListAppearance.decode("""{"fontColor":{"argb":-1,"dynamic":true},"wallpaperDimColor":{"argb":-1,"dynamic":true}}""")
        assertEquals(IconColor.Theme, legacy.fontColor)
        assertEquals(IconColor.Theme, legacy.wallpaperDimColor)
    }

    @Test fun dimSwitchRevealsSliderAndColorWhilePreservingTheChoiceWhenDisabled() {
        showSettings()
        scrollTo("settings_dim_wallpaper")
        compose.onNodeWithTag("settings_dim_wallpaper_amount").assertDoesNotExist()
        compose.onNodeWithTag("settings_dim_wallpaper_color").assertDoesNotExist()
        compose.onNodeWithTag("settings_dim_wallpaper").performClick()
        scrollTo("settings_dim_wallpaper_color")
        compose.onNodeWithTag("settings_dim_wallpaper_amount").assertExists()
        compose.onNodeWithTag("settings_dim_wallpaper_color").performClick()
        compose.onNodeWithTag("list_appearance_preview").assertIsDisplayed()
        compose.onNodeWithTag("icon_designer_color_source:FromApp", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("icon_designer_color_source:Theme", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("icon_designer_color_plane").assertDoesNotExist()
        compose.onNodeWithTag("icon_designer_hex").assertDoesNotExist()
        compose.onNodeWithTag("icon_designer_brightness_shift").performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(-15f) }
        compose.onNodeWithTag("icon_designer_color_source:Fixed", useUnmergedTree = true).performScrollTo()
        compose.onNodeWithTag("icon_designer_color_source:Fixed", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("icon_designer_hex").performTextReplacement("274B62")
        compose.onNodeWithTag("icon_designer_color_close").performClick()
        // The source row is still in view after the hero returns to the list.
        compose.onNodeWithTag("settings_dim_wallpaper_color").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(IconColor(0xFF274B62.toInt()), state.settings.listAppearance.wallpaperDimColor)
            assertNull(state.settings.listAppearance.fontColor)
        }
        scrollTo("settings_dim_wallpaper")
        compose.onNodeWithTag("settings_dim_wallpaper").performClick()
        compose.onNodeWithTag("settings_dim_wallpaper_color").assertDoesNotExist()
        compose.runOnIdle { assertEquals(IconColor(0xFF274B62.toInt()), state.settings.listAppearance.wallpaperDimColor) }
    }

    @Test fun bothColorPickersSaveOnBackAndResetIndependently() {
        state = state.copy(settings = state.settings.copy(dimWallpaper = true,
            listAppearance = ListAppearance(wallpaperDimColor = IconColor(0xFF274B62.toInt()))))
        showSettings()
        scrollTo("list_font_color")
        compose.onNodeWithTag("list_font_color").performClick()
        compose.onNodeWithTag("list_appearance_preview").assertIsDisplayed()
        compose.onNodeWithTag("icon_designer_color_source:FromApp", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("icon_designer_color_source:Fixed", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("icon_designer_hex").performTextReplacement("FFCC00")
        compose.onNodeWithTag("settings_back").performClick()
        compose.onNodeWithTag("list_font_color").assertIsDisplayed()
        compose.runOnIdle { assertEquals(IconColor(0xFFFFCC00.toInt()), state.settings.listAppearance.fontColor) }
        scrollTo("settings_dim_wallpaper_color")
        compose.onNodeWithTag("settings_dim_wallpaper_color").performClick()
        compose.onNodeWithTag("icon_designer_color_reset").performScrollTo().performClick()
        compose.onNodeWithTag("settings_dim_wallpaper_color").assertIsDisplayed()
        compose.runOnIdle {
            assertNull(state.settings.listAppearance.wallpaperDimColor)
            assertEquals(IconColor(0xFFFFCC00.toInt()), state.settings.listAppearance.fontColor)
        }
    }

    @Test fun wallpaperEffectResetsRestoreOnlyTheirStrengthAndDisableAtDefault() {
        val defaults = LauncherSettings()
        val customColor = IconColor(0xFF274B62.toInt())
        state = state.copy(settings = state.settings.copy(dimWallpaper = true, wallpaperDimAmount = 73,
            blurWallpaper = true, wallpaperBlurRadius = 37, listAppearance = ListAppearance(wallpaperDimColor = customColor)))
        // Exercise blur controls independently of the test device's system blur support.
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                Column {
                    WallpaperEffectItem("Dim", "", state.settings.dimWallpaper, state.settings.wallpaperDimAmount, 0..100,
                        "Opacity", 0, "dim", defaults.wallpaperDimAmount, onToggle = {},
                        onAmount = { state = state.copy(settings = state.settings.copy(wallpaperDimAmount = it)) })
                    WallpaperEffectItem("Blur", "", state.settings.blurWallpaper, state.settings.wallpaperBlurRadius, 0..48,
                        "Blur", 1, "blur", defaults.wallpaperBlurRadius, suffix = "dp", onToggle = {},
                        onAmount = { state = state.copy(settings = state.settings.copy(wallpaperBlurRadius = it)) })
                }
            }
        }
        compose.onNodeWithTag("dim_amount_reset").assertIsEnabled().performClick().assertIsNotEnabled()
        compose.onNodeWithTag("blur_amount_reset").assertIsEnabled().performClick().assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(defaults.wallpaperDimAmount, state.settings.wallpaperDimAmount)
            assertEquals(defaults.wallpaperBlurRadius, state.settings.wallpaperBlurRadius)
            assertEquals(customColor, state.settings.listAppearance.wallpaperDimColor)
            assertTrue(state.settings.dimWallpaper)
            assertTrue(state.settings.blurWallpaper)
        }
    }

    private fun scrollTo(tag: String) = compose.onNodeWithTag("list_appearance_controls").performScrollToNode(hasTestTag(tag))

    private fun showSettings() {
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                LauncherSettingsScreen(state, LauncherActions(updateSettings = { state = state.copy(settings = it(state.settings)) }),
                    onBack = {}, initialPage = "ListAppearance")
            }
        }
    }
}
