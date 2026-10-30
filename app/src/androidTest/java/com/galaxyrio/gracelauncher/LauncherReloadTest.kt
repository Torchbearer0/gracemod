package com.galaxyrio.gracelauncher

import android.app.Application
import android.content.ComponentName
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.os.UserHandle
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.data.*
import com.galaxyrio.gracelauncher.data.icons.GraceButtonIcon
import com.galaxyrio.gracelauncher.ui.LauncherViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class LauncherReloadTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val target get() = instrumentation.targetContext
    private val directory = File(target.cacheDir, "startup-cache-test-${System.nanoTime()}").apply { mkdirs() }
    private val context = object : ContextWrapper(target) {
        override fun getNoBackupFilesDir() = directory
    }
    private val component = ComponentName("test.app", "Main")
    private val installed = setOf(component)
    private fun icon(color: Int) = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        .apply { eraseColor(color) }.asImageBitmap()
    private fun app() = LauncherApp(component, "Renamed", icon(Color.RED),
        monochromeIcon = icon(Color.WHITE), originalLabel = "Original", iconPackPackage = "test.pack",
        themeIconPackPackage = "test.theme", monochromeScale = 1.2f, isSystemApp = true, isAdaptiveIcon = true,
        firstInstallTime = 123L, englishLabel = "English", originalIconColor = Color.BLUE,
        iconDisplay = IconDisplaySettings(style = IconDisplayStyle.Dot, dotSize = 16))

    @After fun cleanUp() { directory.deleteRecursively() }

    @Test fun restartRestoresUniqueRowsAndDropsRemovedActivities() = runBlocking {
        val app = app()
        val shortcut = LauncherShortcut("compose/email", app.packageName, "Compose", icon(Color.GREEN), component).asApp(app)
        val folder = LauncherFolder("favorites", "Favorites", listOf(app.key), FolderPlacement.Both).asApp()
        val button = GraceButtonIcon.item(target).copy(icon = icon(Color.BLUE))
        val rows = listOf(app, shortcut, folder, button)
        LauncherAppCache(context).save("key", rows + shortcut)
        val restored = requireNotNull(LauncherAppCache(context).load("key", installed))
        assertEquals(rows.map { it.key }, restored.map { it.key })
        assertEquals(app.copy(icon = null, monochromeIcon = null), restored[0].copy(icon = null, monochromeIcon = null))
        assertEquals(Color.RED, restored[0].icon!!.asAndroidBitmap().getPixel(16, 16))
        assertEquals(Color.WHITE, restored[0].monochromeIcon!!.asAndroidBitmap().getPixel(16, 16))
        assertEquals(Color.GREEN, restored[1].shortcut!!.icon!!.asAndroidBitmap().getPixel(16, 16))
        assertEquals(folder, restored[2])
        assertEquals(Color.BLUE, restored[3].icon!!.asAndroidBitmap().getPixel(16, 16))
        assertEquals(listOf(folder.key, button.key), LauncherAppCache(context).load("key", emptySet())!!.map { it.key })
        LauncherAppCache(context).save("key", emptyList())
        assertEquals(emptyList<LauncherApp>(), LauncherAppCache(context).load("key", installed))
    }

    @Test fun missingMismatchedOrDamagedPreviewFallsBackAndCanBeReplaced() = runBlocking {
        val cache = LauncherAppCache(context)
        assertNull(cache.load("key", installed))
        cache.save("key", listOf(app()))
        assertNull(cache.load("changed appearance", installed))
        File(directory, "launcher-apps.json").writeText("{truncated")
        assertNull(cache.load("key", installed))
        cache.save("key", listOf(app()))
        assertNotNull(cache.load("key", installed))
    }

    @Test fun profileAppsAndShortcutsNeverEnterThePersonalPreview() = runBlocking {
        val app = app()
        val user = UserHandle.getUserHandleForUid(1_000_000)
        val privateApp = app.copy(user = user, userSerial = 10, isPrivateSpace = true)
        val workApp = app.copy(user = user, userSerial = 11, isWorkProfile = true)
        val shortcut = LauncherShortcut("secret", app.packageName, "Secret", null, component).asApp(privateApp)
        val cache = LauncherAppCache(context)
        cache.save("key", listOf(app, privateApp, workApp, shortcut))
        assertEquals(listOf(app.key), requireNotNull(cache.load("key", installed)).map { it.key })
        assertFalse(File(directory, "launcher-apps.json").readText().contains("secret"))
    }

    @Test fun returningHomeReusesLoadedAppsAndExplicitInvalidationStillReloads() = runBlocking {
        val store = ViewModelStore()
        lateinit var model: LauncherViewModel
        instrumentation.runOnMainSync {
            model = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(
                target.applicationContext as Application))[LauncherViewModel::class.java]
        }
        try {
            awaitApps(model)
            lateinit var loaded: List<LauncherApp>
            instrumentation.runOnMainSync {
                loaded = model.uiState.value.apps
                repeat(3) {
                    model.refreshOnResume()
                    assertFalse(model.uiState.value.isLoadingApps)
                    assertSame(loaded, model.uiState.value.apps)
                }
                model.refreshApps()
            }
            awaitApps(model, loaded)
        } finally { instrumentation.runOnMainSync { store.clear() } }
    }

    private suspend fun awaitApps(model: LauncherViewModel, previous: List<LauncherApp>? = null) = withTimeout(30_000) {
        val state = model.uiState.first { !it.isLoadingSettings && !it.isLoadingApps && it.apps !== previous }
        assertFalse(state.settingsLoadFailed)
        assertFalse(state.appLoadFailed)
    }
}
