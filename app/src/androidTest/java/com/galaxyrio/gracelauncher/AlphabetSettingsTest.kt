package com.galaxyrio.gracelauncher

import android.content.ComponentName
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.galaxyrio.gracelauncher.data.AlphabetAppearance
import com.galaxyrio.gracelauncher.data.IconColor
import com.galaxyrio.gracelauncher.data.IconShape
import com.galaxyrio.gracelauncher.data.ListAppearance
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.LauncherScreen
import com.galaxyrio.gracelauncher.ui.components.AlphabetRail
import com.galaxyrio.gracelauncher.ui.settings.LauncherSettingsScreen
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlphabetSettingsTest {
    @get:Rule val compose = createComposeRule()
    private var state by mutableStateOf(LauncherUiState())

    @Test fun settingsRoundTripAndLegacyListsGetSafeDefaults() {
        assertEquals(AlphabetAppearance(), ListAppearance.decode(null).alphabet)
        assertEquals(AlphabetAppearance(), ListAppearance.decode("""{"fontSize":22}""").alphabet)
        assertNull(ListAppearance.decode(ListAppearance().encode()).alphabet.topPercent)
        assertTrue(ListAppearance.decode("""{"alphabet":{}}""").alphabet.freeMovement)
        for (enabled in listOf(false, true)) {
            val saved = ListAppearance.decode("""{"alphabet":{"freeMovement":$enabled}}""")
            assertEquals(enabled, saved.alphabet.freeMovement)
            assertEquals(saved, ListAppearance.decode(saved.encode()))
        }
        IconShape.entries.forEach { shape ->
            val alphabet = AlphabetAppearance(fontId = "imported.ttf", fontColor = IconColor(0xFF34ABCD.toInt()),
                fontSize = 24, indicatorShape = shape, pebbleRoundness = 64, squareCornerRadius = 45,
                cookieSides = 9, topPercent = 20, bottomPercent = 80, freeMovement = true)
            val list = ListAppearance(fontSize = 18, fontColor = IconColor(-1), alphabet = alphabet)
            assertEquals(list, ListAppearance.decode(list.encode()))
        }
    }

    @Test fun previouslySavedRangesIncludingTheOldDefaultsAreNeverMigrated() {
        for (top in listOf(0, 20, 35, 60)) {
            val old = ListAppearance.decode("""{"alphabet":{"topPercent":$top,"bottomPercent":85,"fontSize":22}}""")
            assertEquals(top, old.alphabet.topPercent)
            assertEquals(85, old.alphabet.bottomPercent)
            assertEquals(22, old.alphabet.fontSize)
            assertEquals(old, ListAppearance.decode(old.encode()))
        }
        val reset = ListAppearance(alphabet = AlphabetAppearance(topPercent = null, bottomPercent = 70))
        assertEquals(reset, ListAppearance.decode(reset.encode()))
    }

    @Test fun fourthPersonalizationEntryOpensTheRightSidePreview() {
        showSettings("Themes")
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_font"))
        val appList = compose.onNodeWithTag("settings_list_appearance").fetchSemanticsNode().boundsInRoot
        val alphabet = compose.onNodeWithTag("settings_alphabet").fetchSemanticsNode().boundsInRoot
        val font = compose.onNodeWithTag("settings_font").fetchSemanticsNode().boundsInRoot
        assertTrue(appList.top < alphabet.top && alphabet.top < font.top)
        compose.onNodeWithTag("settings_alphabet").performClick()
        val preview = compose.onNodeWithTag("alphabet_preview").fetchSemanticsNode().boundsInRoot
        val page = compose.onNodeWithTag("alphabet_settings").fetchSemanticsNode().boundsInRoot
        assertEquals(page.right, preview.right, 1f)
        assertTrue(preview.height > page.height * .65f)
        val touch = compose.onNodeWithTag("alphabet_rail").fetchSemanticsNode().boundsInRoot
        assertEquals(preview.top, touch.top, 1f)
        assertEquals(preview.bottom, touch.bottom, 1f)
        scrollTo("alphabet_free_movement")
        compose.onNodeWithTag("alphabet_preview").assertIsDisplayed()
        assertEquals(preview, compose.onNodeWithTag("alphabet_preview").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun resetColorAndFreeMovementDoNotAlterAppListAppearance() {
        val original = ListAppearance(fontSize = 18, fontColor = IconColor(-1),
            alphabet = AlphabetAppearance(fontSize = 22, topPercent = 60, bottomPercent = 90))
        state = state.copy(settings = state.settings.copy(listAppearance = original))
        showSettings()
        scrollTo("alphabet_font_size_control")
        compose.onNodeWithTag("alphabet_font_size_reset").performClick()
        scrollTo("alphabet_top_control")
        compose.onNodeWithTag("alphabet_top_reset").performClick()
        scrollTo("alphabet_free_movement")
        compose.onNodeWithTag("alphabet_free_movement").performClick()
        scrollTo("alphabet_color")
        compose.onNodeWithTag("alphabet_color").performClick()
        compose.onNodeWithTag("alphabet_preview").assertIsDisplayed()
        compose.onNodeWithTag("icon_designer_hex").performScrollTo().performTextReplacement("34ABCD")
        compose.onNodeWithTag("settings_back").performClick()
        compose.onNodeWithTag("alphabet_color").assertIsDisplayed()
        compose.runOnIdle {
            val list = state.settings.listAppearance
            assertEquals(original.copy(alphabet = list.alphabet), list)
            assertEquals(14, list.alphabet.fontSize)
            assertNull(list.alphabet.topPercent)
            assertEquals(90, list.alphabet.bottomPercent)
            assertFalse(list.alphabet.freeMovement)
            assertEquals(IconColor(0xFF34ABCD.toInt()), list.alphabet.fontColor)
        }
    }

    @Test fun previewLettersOccupyTheSameScreenPositionsAsTheActualDesktop() {
        var editor by mutableStateOf(false)
        state = state.copy(apps = ('A'..'Z').map { letter ->
            LauncherApp(ComponentName("test.$letter", "App$letter"), letter.toString(), null)
        }, isLoadingApps = false)
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                if (editor) LauncherSettingsScreen(state, LauncherActions(), onBack = {}, initialPage = "Alphabet")
                else LauncherScreen(state, onDateClick = {}, onClockClick = {}, onLaunchApp = {}, onToggleFavorite = {})
            }
        }
        for (alphabet in listOf(AlphabetAppearance(), AlphabetAppearance(topPercent = 35, fontSize = 22))) {
            compose.runOnIdle {
                editor = false
                state = state.copy(settings = state.settings.copy(listAppearance = ListAppearance(alphabet = alphabet)))
            }
            val tags = listOf("alphabet:M", "alphabet:S", "alphabet:Y")
            val actual = tags.map { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
            compose.runOnIdle { editor = true }
            tags.forEachIndexed { index, tag ->
                val preview = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                assertEquals(actual[index].top, preview.top, 1f)
                assertEquals(actual[index].left, preview.left, 1f)
                assertEquals(actual[index].width, preview.width, 1f)
                assertEquals(actual[index].height, preview.height, 1f)
            }
        }
    }

    @Test fun bothTouchEdgesFloatReverseAndReturnWithoutMovingTheHitSurface() {
        var selected by mutableStateOf<String?>(null)
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                Box(Modifier.fillMaxSize()) {
                    AlphabetRail(listOf("A", "B", "C", "D", "E"), selected, 240.dp, { selected = it },
                        Modifier.align(Alignment.CenterEnd), includeHome = false, leftTouchEnabled = true,
                        alphabet = AlphabetAppearance(freeMovement = true))
                }
            }
        }
        val initial = firstLetterTop()
        for (tag in listOf("alphabet_rail", "alphabet_left_touch")) {
            val rail = compose.onNodeWithTag(tag, useUnmergedTree = true)
            val hitArea = rail.fetchSemanticsNode().boundsInRoot
            rail.performTouchInput { down(Offset(centerX, centerY)); moveTo(Offset(centerX, -height / 2f)) }
            compose.runOnIdle { assertEquals("A", selected) }
            val moved = firstLetterTop()
            assertTrue("$tag: visual top $moved must be above $initial", moved < initial)
            assertEquals(hitArea, rail.fetchSemanticsNode().boundsInRoot)
            rail.performTouchInput { moveTo(Offset(centerX, -height / 2f + height / 5f * 1.1f)) }
            compose.runOnIdle { assertEquals("B", selected) }
            rail.performTouchInput { up() }
            compose.waitForIdle()
            assertEquals(initial, firstLetterTop(), .5f)
        }
    }

    @Test fun fullHeightEdgesActivateOutsideTheHiddenAlphabetAndReverseImmediately() {
        var selected by mutableStateOf<String?>(null)
        var finished = 0
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                BoxWithConstraints(Modifier.fillMaxSize().testTag("edge_test_viewport")) {
                    AlphabetRail(listOf("A", "B", "C", "D", "E"), selected, maxHeight * .30f, { selected = it },
                        Modifier.align(Alignment.TopEnd), includeHome = false, leftTouchEnabled = true, autoHide = true,
                        alphabet = AlphabetAppearance(freeMovement = true), topOffset = maxHeight * .35f,
                        touchHeight = maxHeight, onScrubFinished = { finished++ })
                }
            }
        }
        val viewport = compose.onNodeWithTag("edge_test_viewport").fetchSemanticsNode().boundsInRoot
        val initial = firstLetterTop()
        for (tag in listOf("alphabet_rail", "alphabet_left_touch")) {
            val rail = compose.onNodeWithTag(tag, useUnmergedTree = true)
            val touch = rail.fetchSemanticsNode().boundsInRoot
            assertEquals(viewport.top, touch.top, 1f)
            assertEquals(viewport.bottom, touch.bottom, 1f)
            // Both downs are outside the visible 35%–65% range. Keep the pointer
            // held while reversing one letter, then release/cancel back to the anchor.
            for (above in listOf(true, false)) {
                val start = if (above) .08f else .92f
                rail.performTouchInput { down(Offset(centerX, height * start)) }
                compose.runOnIdle { assertEquals(if (above) "A" else "E", selected) }
                val moved = firstLetterTop()
                assertTrue(if (above) moved < initial else moved > initial)
                rail.performTouchInput { moveTo(Offset(centerX, height * (start + if (above) .066f else -.066f))) }
                compose.runOnIdle { assertEquals(if (above) "B" else "D", selected) }
                assertEquals(touch, rail.fetchSemanticsNode().boundsInRoot)
                rail.performTouchInput { if (above) up() else cancel() }
                compose.waitForIdle()
                assertEquals(initial, firstLetterTop(), .5f)
            }
        }
        compose.runOnIdle { assertEquals(4, finished) }
    }

    @Test fun fullHeightFixedRailClampsAtEndsWithoutInterceptingCenterOrDisabledLeftEdge() {
        var selected by mutableStateOf<String?>(null)
        var backgroundClicks = 0
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().testTag("edge_test_background").clickable { backgroundClicks++ })
                    AlphabetRail(listOf("A", "B", "C", "D", "E"), selected, maxHeight * .30f, { selected = it },
                        Modifier.align(Alignment.TopEnd), includeHome = false, leftTouchEnabled = false,
                        alphabet = AlphabetAppearance(freeMovement = false), topOffset = maxHeight * .35f, touchHeight = maxHeight)
                }
            }
        }
        val initial = firstLetterTop()
        compose.onNodeWithTag("alphabet_rail").performTouchInput { click(Offset(centerX, height * .08f)) }
        compose.runOnIdle { assertEquals("A", selected) }
        compose.onNodeWithTag("alphabet_rail").performTouchInput { click(Offset(centerX, height * .92f)) }
        compose.runOnIdle { assertEquals("E", selected) }
        assertEquals(initial, firstLetterTop(), .5f)
        compose.onNodeWithTag("edge_test_background").performTouchInput {
            click(Offset(centerX, height * .08f))
            click(Offset(5f, height * .92f))
        }
        compose.runOnIdle { assertEquals(2, backgroundClicks); assertEquals("E", selected) }
    }

    @Test fun disabledHiddenRailPassesEdgeDragsThroughAndWorksAgainWhenEnabled() {
        var enabled by mutableStateOf(false)
        var selections = 0
        var drags = 0
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().testTag("resize_surface").pointerInput(Unit) {
                        detectDragGestures(onDragStart = { drags++ }) { change, _ -> change.consume() }
                    })
                    AlphabetRail(listOf("A", "B", "C"), null, maxHeight * .30f, { selections++ },
                        Modifier.align(Alignment.TopEnd), includeHome = false, leftTouchEnabled = true,
                        autoHide = true, topOffset = maxHeight * .35f, touchHeight = maxHeight, enabled = enabled)
                }
            }
        }
        // Real pointer input, not semantics actions: both invisible edge strips
        // overlap the surface below, just like the widget's resize handles.
        for (active in listOf(false, true, false)) {
            compose.runOnIdle { enabled = active }
            for (left in listOf(false, true)) {
                val previousDrags = drags
                val previousSelections = selections
                compose.onNodeWithTag("resize_surface").performTouchInput {
                    val x = if (left) 5f else width - 5f
                    swipe(Offset(x, height * .5f), Offset(x, height * .75f))
                }
                compose.runOnIdle {
                    assertEquals(previousDrags + if (active) 0 else 1, drags)
                    if (active) assertTrue(selections > previousSelections)
                    else assertEquals(previousSelections, selections)
                }
            }
        }
    }

    // The cell's test tag precedes graphicsLayer and describes its stationary
    // layout. The descendant Text includes the actual layer translation.
    private fun firstLetterTop() = compose.onNode(hasText("A") and hasAnyAncestor(hasTestTag("alphabet:A")),
        useUnmergedTree = true).getUnclippedBoundsInRoot().top.value

    private fun scrollTo(tag: String) = compose.onNodeWithTag("alphabet_controls").performScrollToNode(hasTestTag(tag))
    private fun showSettings(page: String = "Alphabet") {
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                LauncherSettingsScreen(state, LauncherActions(updateSettings = { state = state.copy(settings = it(state.settings)) }),
                    onBack = {}, initialPage = page)
            }
        }
    }
}
