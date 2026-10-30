package com.galaxyrio.gracelauncher

import android.content.ComponentName
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.galaxyrio.gracelauncher.data.IconDisplaySettings
import com.galaxyrio.gracelauncher.data.IconDisplayStyle
import com.galaxyrio.gracelauncher.data.IconDotColor
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.ui.components.LauncherAppRow
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import com.galaxyrio.gracelauncher.ui.theme.LauncherAppearance
import com.galaxyrio.gracelauncher.ui.theme.LocalLauncherAppearance
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/** Row-level guarantee: a hidden icon (global or per-app) can never hide the label too. */
@RunWith(AndroidJUnit4::class)
class IconDisplayRowTest {
    @get:Rule val compose = createComposeRule()

    private val app = LauncherApp(ComponentName("test.app", "App.Activity"), "App", null)

    // Row click semantics merge descendants — child tags live in the unmerged tree.
    private fun icon(key: String) = compose.onNodeWithTag("app:$key:icon", useUnmergedTree = true)
    private fun label(key: String) = compose.onNodeWithTag("app:$key:label", useUnmergedTree = true)

    private fun row(appearance: LauncherAppearance, icon: LauncherApp = app, showLabel: Boolean = true) {
        compose.setContent {
            GraceLauncherTheme(darkTheme = false, dynamicColor = false) {
                CompositionLocalProvider(LocalLauncherAppearance provides appearance) {
                    LauncherAppRow(icon, onClick = {}, onLongClick = {}, showLabel = showLabel)
                }
            }
        }
    }

    @Test
    fun hiddenGlobalStyleKeepsLabelsEvenWhenNamesAreTurnedOff() {
        // The forbidden combination: every icon hidden AND names hidden. The label
        // must still render — it is the only launch affordance and a11y target.
        row(LauncherAppearance(iconDisplay = IconDisplaySettings(style = IconDisplayStyle.Hidden)),
            showLabel = false)
        icon(app.key).assertDoesNotExist()
        label(app.key).assertIsDisplayed()
    }

    @Test
    fun hiddenPerAppOverrideKeepsItsLabelWhileSiblingsKeepIcons() {
        val sibling = LauncherApp(ComponentName("test.other", "Other.Activity"), "Other", null)
        compose.setContent {
            GraceLauncherTheme(darkTheme = false, dynamicColor = false) {
                CompositionLocalProvider(LocalLauncherAppearance provides LauncherAppearance()) {
                    LauncherAppRow(app.copy(iconDisplay = IconDisplaySettings(style = IconDisplayStyle.Hidden)),
                        onClick = {}, onLongClick = {}, showLabel = false)
                    LauncherAppRow(sibling, onClick = {}, onLongClick = {}, showLabel = false)
                }
            }
        }
        icon(app.key).assertDoesNotExist()
        label(app.key).assertIsDisplayed()
        icon(sibling.key).assertIsDisplayed()
        label(sibling.key).assertDoesNotExist()
    }

    @Test
    fun dotStyleKeepsTheIconSlotAndTheLabel() {
        row(LauncherAppearance(iconDisplay = IconDisplaySettings(
            style = IconDisplayStyle.Dot, dotColor = IconDotColor.Theme, dotSize = 10)))
        icon(app.key).assertIsDisplayed()
        label(app.key).assertIsDisplayed()
    }
}
