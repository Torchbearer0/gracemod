package com.galaxyrio.gracelauncher

import android.content.ComponentName
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.galaxyrio.gracelauncher.data.GraceButtonGesture
import com.galaxyrio.gracelauncher.data.GraceButtonSettings
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.LauncherSettings
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.LauncherScreen
import com.galaxyrio.gracelauncher.ui.home.HomeScreen
import com.galaxyrio.gracelauncher.ui.search.AppSearchScreen
import com.galaxyrio.gracelauncher.ui.settings.LabGestureSettings
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchGestureInteractionTest {
    @get:Rule val compose = createComposeRule()
    private var state by mutableStateOf(LauncherUiState())
    private var page by mutableStateOf("search")
    private var dismissals = 0
    private var launches = 0
    private var keyboardHides = 0
    private var pointerPressed = false
    private var dismissedWhilePressed = false
    private val homeGestures = mutableListOf<GraceButtonGesture>()
    private lateinit var effects: RecordingOverscrollFactory
    private val keyboard = object : SoftwareKeyboardController {
        override fun show() = Unit
        override fun hide() { keyboardHides++ }
    }

    @Test fun pullingSearchDownReturnsToTheActualHomeScreen() {
        compose.setContent {
            CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                GraceLauncherTheme(dynamicColor = false) {
                    LauncherScreen(LauncherUiState(isLoadingApps = false),
                        onDateClick = {}, onClockClick = {}, onLaunchApp = {}, onToggleFavorite = {})
                }
            }
        }
        compose.onNodeWithTag("launcher_fab").performClick()
        compose.onNodeWithTag("app_search").assertIsDisplayed()
        drag(140f)
        compose.onNodeWithTag("app_search").assertDoesNotExist()
        compose.onNodeWithTag("home_content").assertIsDisplayed()
        compose.runOnIdle { assertTrue("Dismissing search also hides the keyboard", keyboardHides > 0) }
    }

    @Test fun shortResultsUseNativeStretchAtBothEdgesWithoutDismissing() {
        showSearch()
        val range = results().fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertEquals("Every result fits without scrolling", 0f, range.maxValue(), 0f)
        for (distance in listOf(64f, -64f)) {
            drag(distance)
            compose.runOnIdle {
                assertTrue("The platform effect must stretch for both drag directions",
                    effects.created.any { it.stretchedDeltas.any { delta -> delta * distance > 0f } })
                assertEquals(0, dismissals)
            }
        }
    }

    @Test fun emptyResultsStillStretchAndPullDownClosesSearch() {
        showSearch(appCount = 0)
        drag(-64f)
        compose.runOnIdle { assertTrue(effects.created.any { it.stretchedDeltas.any { delta -> delta < 0f } }) }
        drag(140f)
        compose.runOnIdle {
            assertEquals(1, dismissals)
            assertEquals(1, keyboardHides)
            assertFalse("A held pull must not close search", dismissedWhilePressed)
        }
    }

    @Test fun labSensitivityControlsTheSamePullOnHomeAndSearch() {
        showSearch()
        drag(80f)
        compose.runOnIdle {
            assertEquals(0, dismissals)
            page = "home"
        }
        drag(80f, tag = "home_content")
        compose.runOnIdle {
            assertTrue(homeGestures.isEmpty())
            page = "lab"
        }
        compose.onNodeWithTag("lab_swipe_sensitivity").performSemanticsAction(SemanticsActions.SetProgress) { it(200f) }
        compose.runOnIdle {
            assertEquals(200, state.settings.homeGestures.swipeSensitivity)
            page = "search"
        }
        drag(80f)
        compose.runOnIdle {
            assertEquals(1, dismissals)
            page = "home"
        }
        drag(80f, tag = "home_content")
        compose.runOnIdle {
            assertEquals(listOf(GraceButtonGesture.SwipeDown), homeGestures)
            page = "lab"
        }
        compose.onNodeWithTag("lab_swipe_sensitivity_reset").performClick()
        compose.runOnIdle {
            assertEquals(100, state.settings.homeGestures.swipeSensitivity)
            page = "search"
        }
        drag(80f)
        compose.runOnIdle { assertEquals(1, dismissals) }
    }

    @Test fun searchDismissalRemainsAvailableWhenHomeActionsAreDisabled() {
        showSearch(homeEnabled = false)
        drag(140f)
        compose.runOnIdle {
            assertEquals(1, dismissals)
            assertTrue(homeGestures.isEmpty())
        }
    }

    @Test fun longResultsScrollBeforeTheTopEdgeCanDismissSearch() {
        showSearch(appCount = 60)
        results().performScrollToIndex(30)
        val before = results().fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        drag(140f)
        val after = results().fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        compose.runOnIdle {
            assertTrue("Downward dragging must scroll toward earlier results", after < before)
            assertEquals(0, dismissals)
        }
        results().performScrollToIndex(0)
        drag(140f)
        compose.runOnIdle { assertEquals(1, dismissals) }
    }

    @Test fun reverseAndCancelledPullsDoNotDismissOrLaunchResults() {
        showSearch()
        drag(140f) {
            moveBy(Offset(0f, -140f * compose.density.density), delayMillis = 400)
            advanceEventTime(120)
            up()
        }
        compose.runOnIdle { assertEquals(0, dismissals) }
        drag(140f) { cancel() }
        compose.runOnIdle {
            assertEquals(0, dismissals)
            assertEquals(0, launches)
        }
        // A cancelled stream must not carry distance into a fresh, short pull.
        drag(40f)
        compose.runOnIdle { assertEquals(0, dismissals) }
        compose.onNodeWithTag("app:${state.apps.first().key}").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, launches) }
    }

    @Test fun fastDownwardFlingUsesTheSharedVelocityThreshold() {
        showSearch()
        results().performTouchInput {
            val start = Offset(centerX, height * 0.35f)
            swipeWithVelocity(start, start + Offset(0f, 80f * compose.density.density),
                endVelocity = 4000f * compose.density.density, durationMillis = 40)
        }
        compose.runOnIdle { assertEquals(1, dismissals) }
    }

    @Test fun multiplePointersCancelSearchDismissal() {
        showSearch()
        val scale = compose.density.density
        results().performTouchInput {
            val start = Offset(centerX, height * 0.35f)
            down(0, start)
            down(1, start + Offset(40f * scale, 0f))
            repeat(12) { step ->
                moveTo(0, start + Offset(0f, (step + 1) * 12f * scale), delayMillis = 32)
            }
            up(1)
            advanceEventTime(120)
            up(0)
        }
        compose.runOnIdle { assertEquals(0, dismissals) }
        drag(140f)
        compose.runOnIdle { assertEquals(1, dismissals) }
    }

    private fun results() = compose.onNodeWithTag("app_search_results")

    private fun drag(distanceDp: Float, tag: String = "app_search_results",
        finish: TouchInjectionScope.() -> Unit = { advanceEventTime(120); up() }) {
        val distance = distanceDp * compose.density.density
        // Inject the entire stream before synchronizing with Compose: the native
        // stretch keeps invalidating while held, so it cannot become idle then.
        compose.onNodeWithTag(tag).performTouchInput {
            val start = Offset(centerX, height * 0.45f)
            down(start)
            repeat(12) { step -> moveTo(start + Offset(0f, distance * (step + 1) / 12), delayMillis = 32) }
            finish()
        }
    }

    private fun showSearch(appCount: Int = 2, homeEnabled: Boolean = true) {
        state = LauncherUiState(
            apps = List(appCount) { index -> LauncherApp(ComponentName("test.item$index", "Activity"), "Item %02d".format(index), null) },
            isLoadingApps = false,
            settings = LauncherSettings(
                clockEnabled = false, calendarAgenda = false, weatherEnabled = false, mediaPlayer = false,
                homeGestures = GraceButtonSettings.homeDefaults().copy(enabled = homeEnabled),
            ),
        )
        compose.setContent {
            val platform = requireNotNull(LocalOverscrollFactory.current)
            val recording = remember(platform) { RecordingOverscrollFactory(platform).also { effects = it } }
            val query = remember { TextFieldState(if (appCount == 0) "" else "Item") }
            val actions = LauncherActions(updateSettings = { transform -> state = state.copy(settings = transform(state.settings)) })
            CompositionLocalProvider(LocalOverscrollFactory provides recording, LocalSoftwareKeyboardController provides keyboard) {
                GraceLauncherTheme(dynamicColor = false) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF46372E)).pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) pointerPressed = awaitPointerEvent(PointerEventPass.Initial).changes.any { it.pressed }
                        }
                    }) {
                        when (page) {
                            "lab" -> LabGestureSettings(state, actions, onBack = { page = "search" })
                            "home" -> HomeScreen(state, topSpace = 128.dp, onDateClick = {}, onClockClick = {},
                                onLaunchApp = {}, onAppDetails = {}, onAppShortcuts = { _, _ -> },
                                onHomeGesture = { gesture, _ -> homeGestures += gesture })
                            else -> AppSearchScreen(state, actions, onLaunch = { launches++ }, onDetails = {},
                                onDismiss = { dismissedWhilePressed = dismissedWhilePressed || pointerPressed; dismissals++ }, queryState = query)
                        }
                    }
                }
            }
        }
        if (appCount > 0) compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("app:${state.apps.first().key}").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Observe the real platform effect, retaining its drawing node and event handling. */
    private class RecordingOverscrollFactory(private val platform: OverscrollFactory) : OverscrollFactory {
        val created = mutableListOf<RecordingOverscrollEffect>()
        override fun createOverscrollEffect() = RecordingOverscrollEffect(platform.createOverscrollEffect()).also { created += it }
        override fun equals(other: Any?) = this === other
        override fun hashCode() = System.identityHashCode(this)
    }

    private class RecordingOverscrollEffect(private val platform: OverscrollEffect) : OverscrollEffect by platform {
        val stretchedDeltas = mutableListOf<Float>()
        override fun applyToScroll(delta: Offset, source: NestedScrollSource, performScroll: (Offset) -> Offset): Offset {
            val consumed = platform.applyToScroll(delta, source, performScroll)
            if (source == NestedScrollSource.UserInput && platform.isInProgress) stretchedDeltas += delta.y
            return consumed
        }
    }
}
