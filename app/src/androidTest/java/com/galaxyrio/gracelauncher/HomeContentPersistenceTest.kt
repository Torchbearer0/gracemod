package com.galaxyrio.gracelauncher

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeContentPersistenceTest {
    @Test fun legacyWidgetMigratesOnceWithoutRebindingOrLosingOtherWidgets() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            LauncherDatabase::class.java).build()
        try {
            val settings = LauncherSettingsRepository(db)
            val items = LauncherItemsRepository(db)
            val original = HomeLayout(widgetId = 41, widgetProvider = "example/.Provider", widgetLabel = "Existing",
                widgetHeightDp = 240, topOffsetDp = 38f, baselineOffsetDp = 85f)
            settings.updateSettings(LauncherSettings(homeLayout = original))
            items.updatePopup(HomeContentKey, emptyList()) { it + PopupItem("widget:42", HomeLayout(widgetId = 42)) }
            settings.migrateLegacyHomeWidget()
            val saved = items.snapshots.first().popups.getValue(HomeContentKey)
            assertEquals(listOf(41, 42), saved.map { it.widget?.widgetId })
            assertEquals(original.widgetProvider, saved.first().widget?.widgetProvider)
            assertEquals(240, saved.first().widget?.widgetHeightDp)
            val home = settings.snapshots.first().settings.homeLayout
            assertFalse(home.hasWidget)
            assertEquals(38f, home.topOffsetDp)
            assertEquals(85f, home.baselineOffsetDp)
            assertTrue("widget:41" in home.homeOrder.orEmpty())
            assertEquals(listOf(41), items.updatePopup(HomeContentKey, emptyList()) { it.filterNot { item -> item.key == "widget:41" } })
            assertTrue(items.updatePopup(HomeContentKey, emptyList()) { it }.isEmpty())
        } finally { db.close() }
    }

    @Test fun homeMetadataRoundTripsWithoutChangingWidgetPresentation() {
        val layout = HomeLayout(homeOrder = listOf("grace:calendar", "grace:baseline", "widget:8"),
            baselineEnabled = false, baselineOffsetDp = 90f, popupContentScale = 120, popupFullWidth = true)
        assertEquals(layout, HomeLayout.decode(layout.encode()))
        assertTrue(HomeLayout.decode("{}").baselineEnabled)
        assertNull(HomeLayout.decode("{}").homeOrder)
    }
}
