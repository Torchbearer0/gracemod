package com.galaxyrio.gracelauncher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.ui.overlays.FavoritesReorderState
import com.galaxyrio.gracelauncher.ui.overlays.rememberReorderTranslation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

/** Covers the shared favorites/folder/font/icon-pack reorder path. */
class ReorderDragTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var reorder: FavoritesReorderState
    private var density = 1f
    private val initial = listOf("a", "b", "c")
    private val compositions = mutableMapOf<String, Int>()
    private val commits = mutableListOf<List<String>>()

    private fun show() {
        compose.setContent {
            density = LocalDensity.current.density
            val list = rememberLazyListState()
            reorder = remember(list) { FavoritesReorderState(list, initial, { commits += it }) {} }
            LazyColumn(Modifier.size(240.dp, 300.dp), state = list) {
                items(reorder.keys, key = { FavoritesReorderState.itemKey(it) }) { key ->
                    val translation by rememberReorderTranslation(reorder, key)
                    SideEffect { compositions[key] = compositions.getOrDefault(key, 0) + 1 }
                    Box(Modifier.fillMaxWidth().height(72.dp).graphicsLayer { translationY = translation })
                }
            }
        }
    }

    @Test fun pointerMovesOnlyInvalidatePlacementAndDrawingUntilOrderChanges() {
        show()
        compose.runOnIdle { reorder.start("a") }
        val before = compose.runOnIdle { compositions.toMap() }
        repeat(8) { compose.runOnIdle { reorder.drag(density) } }
        compose.runOnIdle {
            assertEquals(before, compositions)
            assertEquals(8 * density, reorder.translation, .01f)
            assertEquals(0, commits.size)
            reorder.drag(150 * density)
            assertNotEquals(initial, reorder.keys)
            assertEquals(0, commits.size)
        }
        compose.runOnIdle {
            reorder.finish(cancelled = false)
            assertEquals(listOf(reorder.keys), commits)
        }
    }

    @Test fun cancellingRestoresOrderWithoutSaving() {
        show()
        compose.runOnIdle {
            reorder.start("a")
            reorder.drag(150 * density)
            assertNotEquals(initial, reorder.keys)
        }
        compose.runOnIdle {
            reorder.finish(cancelled = true)
            assertEquals(initial, reorder.keys)
            assertEquals(0, commits.size)
        }
    }
}
