package com.galaxyrio.gracelauncher

import android.content.ComponentName
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.galaxyrio.gracelauncher.data.*
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.AppSelectionList
import com.galaxyrio.gracelauncher.ui.components.ShortcutPickerScreen
import com.galaxyrio.gracelauncher.ui.search.AppSearchScreen
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalizedSearchUiTest {
    @get:Rule val compose = createComposeRule()
    private val app = LauncherApp(ComponentName("fixture.browser", "Activity"), "Яндекс", null, englishLabel = "Yandex")
    private val other = LauncherApp(ComponentName("fixture.camera", "Activity"), "Camera", null)

    @Test fun iconPackSelectorSearchesProviderEnglishNames() {
        val state = LauncherUiState(isLoadingApps = false, iconPacks = listOf(
            com.galaxyrio.gracelauncher.data.icons.IconPackInfo("fixture.icons", "Яркие значки", null, "Bright Icons"),
            com.galaxyrio.gracelauncher.data.icons.IconPackInfo("fixture.other", "Other Icons", null),
        ))
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                com.galaxyrio.gracelauncher.ui.settings.IconPackSettings(state, LauncherActions(), {}, {})
            }
        }
        compose.onNodeWithTag("icon_pack_query").performScrollTo().performTextInput("Bright")
        compose.onNodeWithTag("icon_pack:fixture.icons").assertExists()
        compose.onNodeWithTag("icon_pack:fixture.other").assertDoesNotExist()
        compose.onNodeWithTag("icon_pack_query").performTextReplacement("Yarkie")
        compose.onNodeWithTag("icon_pack:fixture.icons").assertExists()
    }
    @Test fun mainSearchAcceptsEnglishAliasWithFuzzyDisabled() {
        val query = TextFieldState("Yandex")
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                AppSearchScreen(LauncherUiState(apps = listOf(app, other), isLoadingApps = false,
                    settings = LauncherSettings(search = SearchSettings(fuzzy = false, internet = false))),
                    LauncherActions(), {}, {}, {}, queryState = query)
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("app:" + app.key).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("app:" + app.key).assertIsDisplayed()
        compose.onNodeWithText("Camera").assertDoesNotExist()
        compose.runOnIdle { query.edit { replace(0, length, "Ya") } }
        compose.onNodeWithTag("app:" + app.key).assertIsDisplayed()
        compose.runOnIdle { query.edit { replace(0, length, "Яндекс") } }
        compose.onNodeWithTag("app:" + app.key).assertIsDisplayed()
    }

    @Test fun mainSearchUpdatesAfterRenameRemovalAndHiddenAppChanges() {
        var state by mutableStateOf(LauncherUiState(apps = listOf(app, other), isLoadingApps = false,
            settings = LauncherSettings(search = SearchSettings(fuzzy = false, hiddenApps = false))))
        val query = TextFieldState("Yandex")
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                AppSearchScreen(state, LauncherActions(), {}, {}, {}, queryState = query)
            }
        }
        fun awaitApp() {
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("app:" + app.key).fetchSemanticsNodes().isNotEmpty() }
        }
        awaitApp()
        compose.runOnIdle { state = state.copy(apps = listOf(app.copy(label = "Браузер"), other)) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Браузер").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { query.edit { replace(0, length, "Brauzer") } }
        compose.onNodeWithTag("app:" + app.key).assertIsDisplayed()
        compose.runOnIdle { state = state.copy(hiddenAppKeys = setOf(app.key)) }
        compose.onNodeWithTag("app:" + app.key).assertDoesNotExist()
        compose.runOnIdle { state = state.copy(settings = state.settings.copy(search = state.settings.search.copy(hiddenApps = true))) }
        awaitApp()
        compose.runOnIdle { state = state.copy(apps = listOf(other)) }
        compose.onNodeWithTag("app:" + app.key).assertDoesNotExist()
    }

    @Test fun sharedAppSelectorRefreshesAliasesAfterRename() {
        var apps by mutableStateOf(listOf(app, other))
        val query = TextFieldState("Yandex")
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                AppSelectionList(apps, emptySet(), query, {}, modifier = Modifier.fillMaxSize())
            }
        }
        compose.onNodeWithTag("app_choice:" + app.key).assertIsDisplayed()
        compose.onNodeWithTag("app_choice:" + other.key).assertDoesNotExist()
        compose.runOnIdle { apps = listOf(app.copy(label = "Браузер"), other) }
        compose.onNodeWithText("Браузер").assertIsDisplayed()
        compose.runOnIdle { query.edit { replace(0, length, "Brauzer") } }
        compose.onNodeWithTag("app_choice:" + app.key).assertIsDisplayed()
    }

    @Test fun shortcutPickerSearchesOwnerAliasAndShortcutTransliteration() {
        val shortcut = LauncherShortcut("chat", app.packageName, "Чат", null)
        val actions = LauncherActions(shortcuts = { owner ->
            ShortcutResult(ShortcutStatus.Ready,
                if (owner.key == app.key) listOf(shortcut) else listOf(LauncherShortcut("photo", owner.packageName, "Photo", null)))
        })
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                ShortcutPickerScreen(LauncherUiState(apps = listOf(app, other), isLoadingApps = false, hasShortcutAccess = true),
                    actions, emptySet(), {}, {})
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("shortcut_group:" + app.key).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("shortcut_query").performTextInput("Yandex")
        compose.onNodeWithTag("shortcut_group:" + app.key).assertIsDisplayed()
        compose.onNodeWithTag("shortcut_group:" + other.key).assertDoesNotExist()
        compose.onNodeWithTag("shortcut_query").performTextReplacement("Chat")
        compose.onNodeWithTag("shortcut_group:" + app.key).assertIsDisplayed()
    }
}
