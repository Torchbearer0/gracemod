package com.galaxyrio.gracelauncher

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.os.UserHandle
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.data.*
import com.galaxyrio.gracelauncher.data.icons.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Synthetic profile identities/artwork; does not create, unlock or modify a profile. */
class ProfileIconRenderingTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun bitmap(color: Int) = Bitmap.createBitmap(144, 144, Bitmap.Config.ARGB_8888)
        .apply { eraseColor(color) }.asImageBitmap()
    private fun app(work: Boolean) = LauncherApp(ComponentName("test.profile.only", "Main"), "Profile app",
        bitmap(Color.GREEN), user = UserHandle.getUserHandleForUid(if (work) 1_000_000 else 1_100_000),
        userSerial = if (work) 10 else 11, isWorkProfile = work, isPrivateSpace = !work,
        profileIconSource = ProfileIconSource.fromDrawable(
            AdaptiveIconDrawable(ColorDrawable(Color.BLUE), ColorDrawable(Color.WHITE))))
    private fun design(color: Int) = ItemIcon.Theme.copy(design = IconDesign(shape = IconShape.Circle,
        themeIcons = true, themeUnsupportedIcons = true, foreground = IconColor(color)))

    @Test fun bothProfilesFollowSharedDesignWithoutLookingUpTheActivityAgain() = runBlocking {
        val store = ItemIconStore(context, IconPackRepository(context))
        for (work in listOf(false, true)) {
            val app = app(work)
            val red = design(Color.RED)
            val blue = design(Color.BLUE)
            val first = store.applyDesign(app, null, red, LauncherSettings(iconDesign = red))
            val changed = store.applyDesign(app, null, blue, LauncherSettings(iconDesign = blue))
            assertEquals(Color.RED, first.icon!!.asAndroidBitmap().getPixel(72, 72))
            assertEquals(Color.BLUE, changed.icon!!.asAndroidBitmap().getPixel(72, 72))
            assertEquals(Color.GREEN, app.icon!!.asAndroidBitmap().getPixel(72, 72))
        }
    }

    @Test fun repeatedLoadsReusePixelsButNotOldLabelsOrBadges() = runBlocking {
        val store = ItemIconStore(context, IconPackRepository(context))
        val app = app(true)
        val style = design(Color.RED)
        val settings = LauncherSettings(iconDesign = style)
        val first = store.applyDesign(app, null, style, settings)
        val again = store.applyDesign(app.copy(label = "Renamed", showWorkIndicator = false), null, style, settings)
        assertSame(first.icon, again.icon)
        assertEquals("Renamed", again.label)
        assertFalse(again.showWorkIndicator)
    }

    @Test fun explicitPerProfileOverridesStillWin() = runBlocking {
        val store = ItemIconStore(context, IconPackRepository(context))
        val app = app(false)
        val special = design(Color.YELLOW)
        for (bulk in listOf(design(Color.RED), design(Color.BLUE))) {
            val result = store.applyDesign(app, special, bulk, LauncherSettings(iconDesign = bulk))
            assertEquals(Color.YELLOW, result.icon!!.asAndroidBitmap().getPixel(72, 72))
        }
    }

    @Test fun diskFallbackKeepsTheMonochromeLayerWhenTheProfileIsUnavailable() = runBlocking {
        val store = ItemIconStore(context, IconPackRepository(context))
        val original = bitmap(Color.GREEN)
        val app = app(false).copy(icon = original,
            profileIconSource = ProfileIconSource.fromCache(original, bitmap(Color.WHITE)))
        val style = ItemIcon.Theme.copy(design = IconDesign(themeIcons = true, foreground = IconColor(Color.RED)))
        val result = store.applyDesign(app, null, style, LauncherSettings(iconDesign = style))
        assertEquals(Color.RED, result.icon!!.asAndroidBitmap().getPixel(72, 72))
    }
}
