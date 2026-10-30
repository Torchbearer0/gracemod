package com.galaxyrio.gracelauncher

import android.content.ComponentName
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.galaxyrio.gracelauncher.data.*
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.overlays.ShortcutPopup
import com.galaxyrio.gracelauncher.ui.overlays.ShortcutRevealState
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Memory-only fixtures. Never bind real widgets or write the launcher's database. */
class PopupWidgetEditingTest {
    @get:Rule val compose = createComposeRule()
    private val owner = LauncherApp(ComponentName("test.owner", "Main"), "Owner", null)
    private val app = LauncherApp(ComponentName("test.member", "Main"), "Member", null)
    private val widget = PopupItem("widget:test", HomeLayout(widgetId = -1001, widgetProvider = "test/Widget"))
    private var saved by mutableStateOf(listOf(widget, PopupItem(app.key)))
    private val details = mutableListOf<String>()

    private fun show() {
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                ShortcutPopup(owner, Rect(60f, 300f, 100f, 340f), true,
                    actions = LauncherActions(
                        shortcuts = { ShortcutResult(ShortcutStatus.Ready) },
                        updatePopup = { _, _, transform -> saved = transform(saved) }),
                    onLaunchApp = {}, onDismiss = {},
                    reveal = remember { ShortcutRevealState(animationsEnabled = false) },
                    uiState = LauncherUiState(apps = listOf(owner, app), isLoadingApps = false,
                        popups = mapOf(owner.key to saved)),
                    onDetails = { details += it.key })
            }
        }
    }

    @Test fun oldWidgetsKeepDefaultsAndPresentationSurvivesPopupEncoding() {
        val old = HomeLayout.decode("""{"widgetId":7,"widgetHeightDp":180}""")
        assertEquals(100, old.popupWidthPercent)
        assertEquals(100, old.popupContentScale)
        assertFalse(old.popupFullWidth)
        assertFalse(old.popupBackground)
        val changed = old.copy(popupWidthPercent = 70, popupContentScale = 125,
            popupFullWidth = true, popupBackground = true)
        val entry = PopupItem("widget:7", changed)
        assertEquals(listOf(entry), PopupItem.decode(PopupItem.encode(listOf(entry))))
        assertEquals(HomeLayout(), changed.withoutWidget())
    }

    @Test fun releaseShowsHandlesAndResizeChangesBothWidgetAndPanel() {
        show()
        compose.onNodeWithTag("popup_widget:-1001").performTouchInput { longClick(durationMillis = 1600L) }
        compose.onNodeWithTag("popup_widget_controls").assertExists()
        val before = compose.onNodeWithTag("popup_widget:-1001").fetchSemanticsNode().boundsInRoot
        val panel = compose.onNodeWithTag("shortcut_popup").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("popup_widget_resize:bottom").performTouchInput {
            down(center); moveBy(Offset(0f, 40f)); up()
        }
        val after = compose.onNodeWithTag("popup_widget:-1001").fetchSemanticsNode().boundsInRoot
        val resizedPanel = compose.onNodeWithTag("shortcut_popup").fetchSemanticsNode().boundsInRoot
        assertTrue(after.height > before.height)
        assertTrue(resizedPanel.height > panel.height)
        compose.runOnIdle { assertTrue(saved.first().widget!!.widgetHeightDp > 160) }
    }

    @Test fun fullWidthRestoresInsetAndScaleDoesNotChangeOuterSize() {
        show()
        compose.onNodeWithTag("popup_widget:-1001").performTouchInput { longClick(durationMillis = 1600L) }
        val normal = compose.onNodeWithTag("popup_widget:-1001").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("popup_widget_full_width").performClick()
        val wide = compose.onNodeWithTag("popup_widget:-1001").fetchSemanticsNode().boundsInRoot
        assertTrue(wide.width > normal.width)
        compose.onNodeWithTag("popup_widget_full_width").performClick()
        compose.onNodeWithTag("popup_widget_background").performClick()
        compose.onNodeWithTag("popup_widget_scale").performSemanticsAction(SemanticsActions.SetProgress) { it(125f) }
        val scaled = compose.onNodeWithTag("popup_widget:-1001").fetchSemanticsNode().boundsInRoot
        assertEquals(normal.size, scaled.size)
        compose.onNodeWithTag("popup_widget_done").performClick()
        compose.onNodeWithTag("popup_widget_controls").assertDoesNotExist()
        compose.runOnIdle {
            val layout = saved.first().widget!!
            assertFalse(layout.popupFullWidth)
            assertTrue(layout.popupBackground)
            assertEquals(125, layout.popupContentScale)
        }
    }

    @Test fun widgetCanReorderButAppLongPressStillOpensDetails() {
        show()
        compose.onNodeWithTag("popup_widget:-1001").performTouchInput {
            down(center); advanceEventTime(1600); moveBy(Offset(0f, 240f)); up()
        }
        compose.runOnIdle { assertEquals(listOf(app.key, widget.key), saved.map { it.key }) }
        compose.onNodeWithTag("popup_widget_done").performClick()
        compose.onNodeWithTag("shortcut:${app.key}").performTouchInput { longClick() }
        compose.runOnIdle {
            assertEquals(listOf(app.key), details)
            assertEquals(listOf(app.key, widget.key), saved.map { it.key })
        }
    }
}

