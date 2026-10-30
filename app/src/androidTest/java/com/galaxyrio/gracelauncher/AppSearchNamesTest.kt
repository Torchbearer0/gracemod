package com.galaxyrio.gracelauncher

import android.content.ComponentName
import android.os.Looper
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.SearchNames
import com.galaxyrio.gracelauncher.data.SearchQuery
import com.galaxyrio.gracelauncher.ui.search.rememberAppSearchNames
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSearchNamesTest {
    @get:Rule val compose = createComposeRule()
    private val app = LauncherApp(ComponentName("fixture.browser", "Activity"), "Browser", null)

    @Test fun buildsOffMainThreadAndReusesNamesAcrossQueryChanges() {
        val buildThread = AtomicReference<Thread>()
        val apps = object : List<LauncherApp> by listOf(app) {
            override fun iterator(): Iterator<LauncherApp> {
                buildThread.set(Thread.currentThread())
                return listOf(app).iterator()
            }
        }
        var query by mutableStateOf("")
        val published = AtomicReference<Map<String, SearchNames>?>()
        val renderedQuery = AtomicReference<String>()
        compose.setContent {
            val currentQuery = query
            val names = rememberAppSearchNames(apps)
            SideEffect {
                published.set(names)
                renderedQuery.set(currentQuery)
            }
        }
        compose.waitUntil(5_000) { published.get() != null }
        val original = published.get()
        assertNotSame(Looper.getMainLooper().thread, buildThread.get())
        compose.runOnIdle { query = "Bro" }
        compose.waitUntil(5_000) { renderedQuery.get() == "Bro" }
        assertSame(original, published.get())
    }

    @Test fun appChangesClearTheIndexAndDiscardObsoleteBuilds() {
        val dispatcher = QueuedDispatcher()
        var apps by mutableStateOf(listOf(app))
        val published = AtomicReference<Map<String, SearchNames>?>()
        compose.setContent {
            val currentApps = apps
            val names = rememberAppSearchNames(currentApps, dispatcher)
            SideEffect {
                // Every published index must belong to this exact input, even
                // when a rename retains the same keys as the previous list.
                if (names != null) {
                    assertEquals(currentApps.map { it.key }.toSet(), names.keys)
                    currentApps.forEach {
                        assertNotNull(names.getValue(it.key).score(SearchQuery(it.label, false)))
                    }
                }
                published.set(names)
            }
        }
        compose.waitUntil(5_000) { dispatcher.size == 1 }
        val replacement = app.copy(componentName = ComponentName("fixture.replacement", "Activity"), label = "Replacement",
            originalLabel = "Provider", englishLabel = "English")
        compose.runOnIdle { apps = listOf(replacement) }
        compose.waitUntil(5_000) { dispatcher.size == 2 }
        assertNull(published.get())
        dispatcher.runNext() // The cancelled initial build must never publish.
        compose.waitForIdle()
        assertNull(published.get())
        dispatcher.runNext()
        compose.waitUntil(5_000) { published.get() != null }
        assertEquals(setOf(replacement.key), published.get()!!.keys)

        compose.runOnIdle { apps = listOf(replacement.copy(label = "Renamed")) }
        compose.waitUntil(5_000) { dispatcher.size == 1 }
        assertNull(published.get())
        dispatcher.runNext()
        compose.waitUntil(5_000) { published.get() != null }
        val names = published.get()!!.getValue(replacement.key)
        for (label in listOf("Renamed", "Provider", "English")) {
            assertNotNull(label, names.score(SearchQuery(label, false)))
        }

        compose.runOnIdle { apps = emptyList() }
        compose.waitUntil(5_000) { dispatcher.size == 1 }
        assertNull(published.get())
        dispatcher.runNext()
        compose.waitUntil(5_000) { published.get() != null }
        assertTrue(published.get()!!.isEmpty())
    }

    private class QueuedDispatcher : CoroutineDispatcher() {
        private val tasks = ConcurrentLinkedQueue<Runnable>()
        val size: Int get() = tasks.size
        override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.add(block) }
        fun runNext() { requireNotNull(tasks.poll()).run() }
    }
}
