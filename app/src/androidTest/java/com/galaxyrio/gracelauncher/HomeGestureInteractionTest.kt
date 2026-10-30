package com.galaxyrio.gracelauncher

import android.content.ComponentName
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.galaxyrio.gracelauncher.data.FolderPlacement
import com.galaxyrio.gracelauncher.data.GraceButtonAction
import com.galaxyrio.gracelauncher.data.GraceButtonGesture
import com.galaxyrio.gracelauncher.data.GraceButtonSettings
import com.galaxyrio.gracelauncher.data.GraceButtonTarget
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.LauncherFolder
import com.galaxyrio.gracelauncher.data.LauncherSettings
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.AppRowGestures
import com.galaxyrio.gracelauncher.ui.home.HomeScreen
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeGestureInteractionTest {
    @get:Rule val compose = createComposeRule()

    private val app = LauncherApp(ComponentName("test.mail", "Mail"), "Mail", null)
    private val folder = LauncherFolder("favorites", "Folder", listOf(app.key), FolderPlacement.Favorites)
    private var covered by mutableStateOf(false)
    private val expansions = mutableListOf<Boolean>()
    private val dragEnds = mutableListOf<Boolean>()
    private val homeGestures = mutableListOf<GraceButtonGesture>()
    private var appClicks = 0

    @Test fun appSwipeSurvivesHomeGesturesBeingDisabledWhenPopupOpens() {
        showHome()
        assertSwipeSurvivesCover("app:${app.key}")
    }

    @Test fun folderSwipeSurvivesHomeGesturesBeingDisabledWhenFolderOpens() {
        showHome()
        assertSwipeSurvivesCover("folder:${folder.id}")
    }

    @Test fun doubleTapOnlyRunsOnBlankSpaceAndResumesAfterOverlayCloses() {
        showHome()
        compose.onNodeWithTag("app:${app.key}").performTouchInput { doubleClick() }
        compose.runOnIdle {
            assertEquals(2, appClicks)
            assertTrue(homeGestures.isEmpty())
        }
        doubleTapBlank()
        compose.runOnIdle {
            assertEquals(listOf(GraceButtonGesture.DoubleTap), homeGestures)
            covered = true
        }
        doubleTapBlank()
        compose.runOnIdle {
            assertEquals(1, homeGestures.size)
            covered = false
        }
        doubleTapBlank()
        compose.runOnIdle { assertEquals(List(2) { GraceButtonGesture.DoubleTap }, homeGestures) }
    }

    private fun assertSwipeSurvivesCover(tag: String) {
        val row = compose.onNodeWithTag(tag)
        row.performTouchInput {
            down(Offset(width * 0.1f, centerY))
            moveTo(Offset(width * 0.55f, centerY), delayMillis = 120)
        }
        // Flush the recomposition WHILE the pointer is held. A one-shot swipe
        // can miss the old bug: removing the parent's observer cancelled the row.
        compose.runOnIdle {
            assertTrue(covered)
            assertEquals(listOf(true), expansions)
            assertTrue("Opening the popup must not cancel its initiating drag", dragEnds.isEmpty())
        }
        row.performTouchInput { moveTo(Offset(width * 0.25f, centerY), delayMillis = 80) }
        compose.runOnIdle { assertEquals(listOf(true, false), expansions) }
        row.performTouchInput {
            moveTo(Offset(width * 0.65f, centerY), delayMillis = 80)
            up()
        }
        compose.runOnIdle {
            assertEquals(listOf(true, false, true), expansions)
            assertEquals(listOf(true), dragEnds)
            assertEquals(0, appClicks)
            assertTrue(homeGestures.isEmpty())
            covered = false
        }
        doubleTapBlank()
        compose.runOnIdle { assertEquals(listOf(GraceButtonGesture.DoubleTap), homeGestures) }
    }

    private fun doubleTapBlank() {
        compose.onNodeWithTag("home_content").performTouchInput {
            doubleClick(Offset(centerX, 20f))
        }
    }

    private fun reveal(expanded: Boolean) {
        expansions += expanded
        // LauncherScreen disables HomeScreen's gestures as soon as an overlay
        // is present, even while the app/folder row is still driving its reveal.
        covered = true
    }

    private fun showHome() {
        val state = LauncherUiState(
            apps = listOf(app), favoriteKeys = setOf(app.key), folders = listOf(folder),
            isLoadingApps = false,
            settings = LauncherSettings(
                clockEnabled = false, calendarAgenda = false, weatherEnabled = false, mediaPlayer = false,
                homeGestures = GraceButtonSettings(doubleTap = GraceButtonTarget(GraceButtonAction.Search)),
            ),
        )
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                HomeScreen(
                    uiState = state, topSpace = 128.dp,
                    onLaunchApp = { appClicks++ }, onAppDetails = {}, onAppShortcuts = { _, _ -> },
                    onDateClick = {}, onClockClick = {},
                    widgetInputEnabled = !covered,
                    onHomeGesture = { gesture, _ -> homeGestures += gesture },
                    rowGestures = AppRowGestures(
                        onDrag = { _, _, expanded -> reveal(expanded) },
                        onDragEnd = { dragEnds += it },
                    ),
                    onFolderDrag = { _, _, expanded -> reveal(expanded) },
                    onFolderDragEnd = { dragEnds += it },
                )
            }
        }
    }
}
