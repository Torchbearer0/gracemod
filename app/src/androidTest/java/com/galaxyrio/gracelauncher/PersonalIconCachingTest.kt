package com.galaxyrio.gracelauncher

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Process
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.data.*
import com.galaxyrio.gracelauncher.data.icons.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PersonalIconCachingTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun bitmap(color: Int) = Bitmap.createBitmap(144, 144, Bitmap.Config.ARGB_8888)
        .apply { eraseColor(color) }.asImageBitmap()
    private fun app() = LauncherApp(ComponentName("test.personal.icon", "Main"), "Original", bitmap(Color.GREEN),
        iconSourceRevision = "revision-1")
    private fun design(color: Int) = ItemIcon.Theme.copy(design = IconDesign(
        themeIcons = true, themeUnsupportedIcons = true, foreground = IconColor(color)))

    @Test fun transientShortcutsUseSharedDesignsWithoutReplacingProviderArtwork() = runBlocking {
        val store = ItemIconStore(context, IconPackRepository(context))
        val owner = app()
        val raw = LauncherShortcut("action", owner.packageName, "Action", bitmap(Color.BLACK))
        val shortcut = raw.asApp(owner)
        val enabled = design(Color.RED).copy(design = design(Color.RED).design!!.copy(applyToShortcuts = true))
        val settings = LauncherSettings(iconDesign = enabled)
        val styled = store.applyDesign(shortcut, null, enabled, settings)
        assertEquals(Color.RED, styled.icon!!.asAndroidBitmap().getPixel(72, 72))
        assertSame(styled.icon, store.applyDesign(shortcut, null, enabled, settings).icon)
        assertEquals(Color.BLACK, raw.icon!!.asAndroidBitmap().getPixel(72, 72))
        val result = ShortcutResult(ShortcutStatus.Ready, listOf(raw), mapOf(raw.key to styled))
        assertSame(styled, result.asApps(owner).single())
        assertSame(raw, result.shortcuts.single())
        val disabled = enabled.copy(design = enabled.design!!.copy(applyToShortcuts = false))
        assertSame(raw.icon, store.applyDesign(shortcut, null, disabled, settings.copy(iconDesign = disabled)).icon)
    }

    @Test fun folderIconsFollowAllWhileSparseOverridesAndRenderCachingArePreserved() = runBlocking {
        val store = ItemIconStore(context, IconPackRepository(context))
        val initial = IconDesign.defaults().copy(shape = IconShape.Circle, themeIcons = true)
        val special = ItemIcon.Theme.copy(design = initial.copy(invertBackgroundDetection = true))
            .overridesFrom(initial, IconDisplaySettings())
        val updated = initial.copy(shape = IconShape.Square, background = IconColor(Color.GREEN))
        for (id in listOf("ordinary", RecentlyInstalledFolderId, PrivateSpaceFolderId, WorkProfileFolderId)) {
            val app = LauncherFolder(id, "Group", emptyList(), FolderPlacement.AppList).asApp()
            suspend fun rendered(style: IconDesign, override: ItemIcon? = null): LauncherApp {
                val bulk = ItemIcon.Theme.copy(design = style)
                return store.applyDesign(app, override, bulk, LauncherSettings(iconDesign = bulk))
            }
            val first = rendered(initial)
            assertEquals(0, Color.alpha(first.icon!!.asAndroidBitmap().getPixel(4, 4)))
            val next = rendered(updated, special)
            assertEquals(Color.GREEN, next.icon!!.asAndroidBitmap().getPixel(4, 4))
            assertNotSame(first.icon, next.icon)
            assertSame(next.icon, rendered(updated, special).icon)
        }
    }

    @Test fun inventoryRefreshReusesSourcePixelsUntilThatPackageChanges() = runBlocking {
        val repository = AppRepository(context)
        fun List<LauncherApp>.ownApp() = single { it.packageName == context.packageName }
        val first = repository.loadApps().ownApp()
        val again = repository.loadApps().ownApp()
        assertNotNull(first.icon)
        assertSame(first.icon, again.icon)
        assertEquals(first.iconSourceRevision, again.iconSourceRevision)

        repository.invalidateApps(Process.myUserHandle(), listOf(context.packageName))
        val updated = repository.loadApps().ownApp()
        assertNotEquals(first.iconSourceRevision, updated.iconSourceRevision)
    }

    @Test fun unchangedArtworkReusesTheRenderWithoutRestoringAnOldLabel() = runBlocking {
        val store = ItemIconStore(context, IconPackRepository(context))
        val original = app()
        val style = design(Color.RED)
        val settings = LauncherSettings(iconDesign = style)
        val first = store.applyDesign(original, null, style, settings)
        val again = store.applyDesign(original.copy(label = "Renamed", icon = bitmap(Color.GREEN)), null, style, settings)
        assertSame(first.icon, again.icon)
        assertEquals("Renamed", again.label)
        assertEquals(Color.RED, again.icon!!.asAndroidBitmap().getPixel(72, 72))
        val shortcutsEnabled = style.copy(design = style.design!!.copy(applyToShortcuts = true))
        assertSame(first.icon, store.applyDesign(original, null, shortcutsEnabled,
            settings.copy(iconDesign = shortcutsEnabled)).icon)
    }

    @Test fun newSourceAndPerAppDesignsInvalidateOnlyTheAffectedRender() = runBlocking {
        val store = ItemIconStore(context, IconPackRepository(context))
        val original = app()
        val red = design(Color.RED)
        val settings = LauncherSettings(iconDesign = red)
        val first = store.applyDesign(original, null, red, settings)
        val newSource = store.applyDesign(original.copy(iconSourceRevision = "revision-2"), null, red, settings)
        assertNotSame(first.icon, newSource.icon)
        val blue = store.applyDesign(original.copy(iconSourceRevision = "revision-2"), design(Color.BLUE), red, settings)
        assertEquals(Color.BLUE, blue.icon!!.asAndroidBitmap().getPixel(72, 72))
    }

}
