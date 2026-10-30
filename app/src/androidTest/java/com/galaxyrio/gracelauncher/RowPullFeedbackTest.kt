package com.galaxyrio.gracelauncher

import android.content.ComponentName
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.galaxyrio.gracelauncher.data.FolderPlacement
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.LauncherFolder
import com.galaxyrio.gracelauncher.ui.components.*
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RowPullFeedbackTest {
    @get:Rule val compose = createComposeRule()
    private val app = LauncherApp(ComponentName("fixture.app", "Activity"), "App", null)
    private val folder = LauncherFolder("tools", "Tools", listOf(app.key), FolderPlacement.Favorites)
    private val launches = mutableListOf<Rect>()
    private val expansions = mutableListOf<Boolean>()
    private var inputEnabled by mutableStateOf(true)
    private var leftEnabled by mutableStateOf(true)

    @Test fun appsAndFoldersMoveTogetherWhileTheirHitAreasStayFixedThenSpringBack() {
        showRows()
        listOf("app:${app.key}", "folder:${folder.id}").forEachIndexed { index, tag ->
            val row = compose.onNodeWithTag(tag)
            val hit = bounds(tag)
            val icon = bounds("$tag:icon")
            val label = bounds("$tag:label")
            row.performTouchInput {
                down(Offset(width * .8f, centerY))
                moveTo(Offset(width * .55f, centerY), delayMillis = 100)
            }
            val shifted = bounds("$tag:icon")
            val delta = shifted.left - icon.left
            assertTrue("Follow the finger, but move much less than it", delta < 0 && -delta < hit.width * .25f)
            assertEquals(hit, bounds(tag))
            assertEquals(delta, bounds("$tag:label").left - label.left, 1f)
            compose.runOnIdle { assertEquals(index, launches.size); assertTrue(expansions.isEmpty()) }
            row.performTouchInput { moveTo(Offset(width * .25f, centerY), delayMillis = 100) }
            val launchIcon = bounds("$tag:icon")
            row.performTouchInput { up() }
            compose.waitForIdle()
            compose.runOnIdle { assertEquals(index + 1, launches.size); assertEquals(launchIcon.center.x, launches.last().center.x, 1f) }
            assertEquals(icon.left, bounds("$tag:icon").left, 1f)
        }
    }

    @Test fun cancellationAndInputDisableClearThePullWithoutLaunching() {
        showRows()
        val tag = "app:${app.key}"
        val initial = bounds("$tag:icon")
        val row = compose.onNodeWithTag(tag)
        fun pull() = row.performTouchInput {
            down(Offset(width * .8f, centerY))
            moveTo(Offset(width * .2f, centerY), delayMillis = 100)
        }
        pull()
        row.performTouchInput { cancel() }
        compose.waitForIdle()
        assertEquals(initial.left, bounds("$tag:icon").left, 1f)
        pull()
        compose.runOnIdle { inputEnabled = false }
        row.performTouchInput { cancel() }
        compose.waitForIdle()
        assertEquals(initial.left, bounds("$tag:icon").left, 1f)
        compose.runOnIdle { assertTrue(launches.isEmpty()) }
    }

    @Test fun rightPopupReversalAndDisabledLeftActionDoNotTranslateTheRow() {
        showRows()
        val tag = "app:${app.key}"
        val initial = bounds("$tag:icon")
        val row = compose.onNodeWithTag(tag)
        row.performTouchInput {
            down(Offset(width * .2f, centerY))
            moveTo(Offset(width * .75f, centerY), delayMillis = 100)
        }
        assertEquals(initial.left, bounds("$tag:icon").left, 1f)
        row.performTouchInput { moveTo(Offset(width * .2f, centerY), delayMillis = 100); up() }
        compose.runOnIdle { assertEquals(listOf(true, false), expansions); assertTrue(launches.isEmpty()); leftEnabled = false }
        row.performTouchInput {
            down(Offset(width * .8f, centerY))
            moveTo(Offset(width * .2f, centerY), delayMillis = 100)
        }
        assertEquals(initial.left, bounds("$tag:icon").left, 1f)
        row.performTouchInput { up() }
        compose.runOnIdle { assertTrue(launches.isEmpty()) }
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun showRows() {
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalLauncherInputEnabled provides inputEnabled) {
                    Column(Modifier.padding(40.dp)) {
                        LauncherAppRow(app, {}, {}, gestures = AppRowGestures(
                            onDrag = { _, _, expanded -> expansions += expanded },
                            onOpenFirst = if (leftEnabled) ({ _, rect -> launches += rect }) else null))
                        FolderRow(folder, {}, {}, onSwipeLeft = if (leftEnabled) ({ launches += it }) else null)
                    }
                }
            }
        }
    }
}
