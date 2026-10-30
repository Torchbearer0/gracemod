package com.galaxyrio.gracelauncher

import android.content.ComponentName
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.ShortcutsFoldersSettings
import com.galaxyrio.gracelauncher.ui.LauncherScreen
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.*
import com.galaxyrio.gracelauncher.ui.settings.AdvancedSettings
import com.galaxyrio.gracelauncher.ui.settings.LabPopupSettings
import com.galaxyrio.gracelauncher.ui.settings.SettingsPage
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PopupGestureSettingsTest {
    @get:Rule val compose = createComposeRule()
    private val app = LauncherApp(ComponentName("test.popup", "Activity"), "Popup test", null)
    private var settings by mutableStateOf(ShortcutsFoldersSettings())
    private var density = 1f
    private var slop = 0f
    private val expansions = mutableListOf<Boolean>()
    private val ends = mutableListOf<Boolean>()
    private lateinit var list: LazyListState
    private var firstLaunches = 0

    private fun showRows() {
        compose.setContent {
            density = LocalDensity.current.density
            slop = LocalViewConfiguration.current.touchSlop
            GraceLauncherTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalPopupGestureSettings provides settings) {
                    list = rememberLazyListState()
                    LazyColumn(Modifier.fillMaxSize().testTag("popup_list"), state = list) {
                        items(40) { index ->
                            LauncherAppRow(app.copy(label = "Item $index"), {}, {},
                                modifier = Modifier.height(64.dp).testTag("popup_row_$index"),
                                gestures = AppRowGestures(
                                    onDrag = { _, _, expanded -> expansions.add(expanded) },
                                    onDragEnd = { ends.add(it) },
                                    onOpenFirst = if (settings.swipeLeftToOpenFirst) ({ _, _ -> firstLaunches++ }) else null,
                                ))
                        }
                    }
                }
            }
        }
    }

    @Test fun shortSwipeDoesNotOpenAndCrossingThresholdOpensBeforeRelease() {
        showRows()
        compose.onNodeWithTag("popup_row_0").performTouchInput {
            down(center)
            moveBy(Offset(slop + 7f * density, 0f))
        }
        compose.runOnIdle { assertTrue(expansions.isEmpty()) }
        compose.onNodeWithTag("popup_row_0").performTouchInput { moveBy(Offset(2f * density, 0f)) }
        compose.runOnIdle {
            assertEquals(listOf(true), expansions)
            assertTrue(ends.isEmpty())
        }
        compose.onNodeWithTag("popup_row_0").performTouchInput {
            moveBy(Offset(-9f * density, 0f))
            moveBy(Offset(9f * density, 0f))
            up()
        }
        compose.runOnIdle {
            assertEquals(listOf(true, false, true), expansions)
            assertEquals(listOf(true), ends)
        }
    }

    @Test fun rejectedDiagonalSwipeStillScrollsTheList() {
        showRows()
        compose.onNodeWithTag("popup_row_4").performTouchInput {
            swipe(center, center + Offset(40f * density, -140f * density), 400)
        }
        compose.runOnIdle {
            assertTrue(expansions.isEmpty())
            assertTrue(list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0)
        }
    }

    @Test fun changedThresholdAppliesToRowsAndLeftActionStillWorks() {
        showRows()
        compose.runOnIdle { settings = settings.copy(popupSwipeDistanceDp = 32, popupSwipeAngleDegrees = 20) }
        compose.onNodeWithTag("popup_row_0").performTouchInput {
            swipe(center, center + Offset(slop + 16f * density, 0f), 200)
        }
        compose.runOnIdle { assertTrue(expansions.isEmpty()) }
        compose.runOnIdle { settings = settings.copy(popupSwipeDistanceDp = 8, swipeLeftToOpenFirst = true) }
        compose.onNodeWithTag("popup_row_0").performTouchInput {
            swipe(center, center + Offset(slop + 16f * density, 0f), 200)
        }
        compose.runOnIdle { assertEquals(listOf(true), expansions) }
        compose.onNodeWithTag("popup_row_0").performTouchInput {
            swipe(center, center - Offset(slop + 44f * density, 0f), 200)
        }
        compose.runOnIdle {
            assertEquals(1, firstLaunches)
            assertEquals(listOf(true), expansions)
        }
    }

    @Test fun slowRightSwipeOpensOnTheActualHomePager() {
        compose.setContent {
            density = LocalDensity.current.density
            slop = LocalViewConfiguration.current.touchSlop
            GraceLauncherTheme(dynamicColor = false) {
                LauncherScreen(LauncherUiState(
                    apps = listOf(app), favoriteKeys = setOf(app.key), favoriteOrder = listOf(app.key),
                    isLoadingApps = false, isLoadingSettings = false,
                ), onDateClick = {}, onClockClick = {}, onLaunchApp = {}, onToggleFavorite = {})
            }
        }
        compose.onNodeWithTag("app:${app.key}").performTouchInput {
            swipe(center, center + Offset(slop + 24f * density, 0f), 600)
        }
        compose.onNodeWithTag("shortcut_popup").assertIsDisplayed()
    }
    @Test fun labNavigationSlidersAndIndependentResets() {
        var state by mutableStateOf(LauncherUiState())
        var page by mutableStateOf(SettingsPage.Advanced)
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                if (page == SettingsPage.LabPopup) {
                    LabPopupSettings(state, LauncherActions(updateSettings = { update ->
                        state = state.copy(settings = update(state.settings))
                    }), onBack = { page = SettingsPage.Advanced })
                } else AdvancedSettings(onBack = {}, navigate = { page = it }, onChooseDefaultHome = {})
            }
        }
        compose.onNodeWithTag("lab_open_popup").performClick()
        compose.onNodeWithTag("lab_popup_distance").performSemanticsAction(SemanticsActions.SetProgress) { it(24f) }
        compose.onNodeWithTag("lab_popup_angle").performSemanticsAction(SemanticsActions.SetProgress) { it(30f) }
        compose.runOnIdle {
            assertEquals(24, state.settings.shortcutsFolders.popupSwipeDistanceDp)
            assertEquals(30, state.settings.shortcutsFolders.popupSwipeAngleDegrees)
        }
        compose.onNodeWithTag("lab_popup_distance_reset").performClick()
        compose.runOnIdle {
            assertEquals(8, state.settings.shortcutsFolders.popupSwipeDistanceDp)
            assertEquals(30, state.settings.shortcutsFolders.popupSwipeAngleDegrees)
        }
        compose.onNodeWithTag("lab_popup_angle_reset").performClick()
        compose.runOnIdle { assertEquals(45, state.settings.shortcutsFolders.popupSwipeAngleDegrees) }
    }
}
