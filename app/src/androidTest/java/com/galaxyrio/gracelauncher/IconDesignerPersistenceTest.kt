package com.galaxyrio.gracelauncher

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.data.*
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class IconDesignerPersistenceTest {
    @Test fun shortcutPolicyRoundTripsAndOlderSettingsStayOptedOut() {
        val shared = ItemIcon.Theme.copy(design = IconDesign.defaults().copy(applyToShortcuts = true))
        assertEquals(shared, ItemIcon.decode(shared.encode()))
        assertFalse(IconDesign.decode(JSONObject("""{"themeIcons":true}"""))!!.applyToShortcuts)
    }

    @Test fun sparseDesignsRoundTripWithoutCapturingInheritedValues() {
        val all = IconDesign.defaults().copy(shape = IconShape.Cookie, themeIcons = true,
            background = IconColor.FromApp, size = 130)
        val display = IconDisplaySettings(style = IconDisplayStyle.Dot, dotSize = 18)
            .withColor(IconColor.FromApp.copy(brightnessShift = 20))
        val saved = ItemIcon.Theme.copy(design = all.copy(invertBackgroundDetection = true,
            themeIcons = false, background = null), display = display.withColor(IconColor.Theme))
            .overridesFrom(all, display)
        val json = JSONObject(saved.encode())
        assertEquals(setOf("overrides", "invertBackgroundDetection", "themeIcons", "background"),
            json.getJSONObject("design").keys().asSequence().toSet())
        assertTrue(json.getJSONObject("design").isNull("background"))
        assertEquals(setOf("overrides", "dotColor", "customColor"),
            json.getJSONObject("display").keys().asSequence().toSet())
        assertTrue(json.getJSONObject("display").isNull("customColor"))
        val restored = requireNotNull(ItemIcon.decode(saved.encode()))
        assertEquals(saved, restored)
        val next = all.copy(shape = IconShape.Square, foreground = IconColor.FromApp)
        val nextDisplay = display.copy(dotSize = 25)
        assertEquals(next.copy(invertBackgroundDetection = true, themeIcons = false, background = null),
            restored.resolve(next, nextDisplay).design)
        assertEquals(nextDisplay.withColor(IconColor.Theme), restored.resolve(next, nextDisplay).display)
    }

    @Test fun builtInFolderIconsPersistWithoutCreatingEditableFolderRows() = runBlocking {
        var db = open()
        val base = IconDesign.defaults()
        val saved = ItemIcon.Theme.copy(design = base.copy(shape = IconShape.Pebble)).overridesFrom(base, IconDisplaySettings())
        val keys = listOf(RecentlyInstalledFolderKey, PrivateSpaceFolderKey, WorkProfileFolderKey)
        keys.forEach { LauncherItemsRepository(db).saveIcon(it, saved) }
        db = open()
        val repository = LauncherItemsRepository(db)
        val restored = withTimeout(10_000) { repository.snapshots.first().icons }
        keys.forEach { key ->
            assertEquals(saved, restored[key])
            assertNull(db.settingsDao().folder(key.removePrefix("folder:")))
            repository.saveIcon(key, null)
        }
        assertTrue(withTimeout(10_000) { repository.snapshots.first().icons }.isEmpty())
        // The exception is limited to icon data; deleted normal folders still fail validation.
        assertTrue(runCatching { repository.saveIcon("folder:deleted", saved) }.isFailure)
        assertTrue(runCatching { repository.updatePopup(RecentlyInstalledFolderKey, emptyList()) { it } }.isFailure)
    }

    @Test fun relativeColorOffsetsRoundTripForIconsDotsAndNonAppColors() {
        val tray = IconColor.FromApp.copy(hueShift = 30, saturationShift = -25, brightnessShift = 10)
        val symbol = IconColor.Theme.copy(hueShift = -15, brightnessShift = -20)
        val original = ItemIcon.Theme.copy(design = IconDesign(background = tray, foreground = symbol),
            display = IconDisplaySettings(style = IconDisplayStyle.Dot).withColor(tray))
        assertEquals(original, ItemIcon.decode(original.encode()))
        assertEquals(tray, ItemIcon.decode(original.encode())?.display?.color)
        val list = ListAppearance(fontColor = symbol, wallpaperDimColor = symbol)
        assertEquals(list, ListAppearance.decode(list.encode()))
        assertEquals(IconColor.Theme, IconColor.decode(JSONObject("""{"theme":true}""")))
        assertEquals(IconColor.FromApp, IconColor.decode(JSONObject("""{"fromApp":true}""")))
    }

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "icon-designer-${UUID.randomUUID()}.db"
    private var database: LauncherDatabase? = null

    private fun open(): LauncherDatabase {
        database?.close()
        return Room.databaseBuilder(context, LauncherDatabase::class.java, databaseName)
            .addMigrations(LauncherDatabase.Migration10To11, LauncherDatabase.Migration11To12,
                LauncherDatabase.Migration12To13, LauncherDatabase.Migration13To14,
                LauncherDatabase.Migration14To15, LauncherDatabase.Migration15To16,
                LauncherDatabase.Migration16To17, LauncherDatabase.Migration17To18,
                LauncherDatabase.Migration18To19, LauncherDatabase.Migration19To20,
                LauncherDatabase.Migration20To21, LauncherDatabase.Migration21To22).build().also { database = it }
    }

    @After fun close() { database?.close(); context.deleteDatabase(databaseName) }

    @Test fun migrationPreservesTheOldPackAndDesktopOverridesWhileAddingPersistentPriority() = runBlocking {
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.galaxyrio.gracelauncher.data.LauncherDatabase/10.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val appKey = "test.app/test.app.Activity"
        val override = ItemIcon("pack", "custom.icons", "alternate")
        context.openOrCreateDatabase(databaseName, 0, null).use { legacy ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                fun execute(sql: String) = legacy.execSQL(sql.replace('$' + "{TABLE_NAME}", table))
                execute(entity.getString("createSql"))
                entity.optJSONArray("indices")?.let { indices ->
                    for (i in 0 until indices.length()) execute(indices.getJSONObject(i).getString("createSql"))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (index in 0 until setup.length()) legacy.execSQL(setup.getString(index))
            legacy.execSQL("INSERT INTO launcher_settings (id, calendarAgenda, showBatteryPercentage, allowHapticFeedback, useDynamicColors, themeColor, darkMode, iconPackPackage) VALUES (0, 1, 1, 1, 1, -1, 'System', 'old.icons')")
            legacy.execSQL("INSERT INTO item_icons VALUES (?, ?)", arrayOf(appKey, override.encode()))
            legacy.version = 10
        }
        var db = open()
        var settings = LauncherSettingsRepository(db)
        assertEquals(listOf("old.icons"), withTimeout(10_000) { settings.snapshots.first().settings.enabledIconPackPackages })
        assertEquals(override, withTimeout(10_000) { LauncherItemsRepository(db).snapshots.first().icons[appKey] })
        settings.mutateSettings { it.withIconPacks(listOf("second.icons", "first.icons", "second.icons")) }
        db = open()
        settings = LauncherSettingsRepository(db)
        assertEquals(listOf("second.icons", "first.icons"), withTimeout(10_000) { settings.snapshots.first().settings.enabledIconPackPackages })
        assertEquals(override, withTimeout(10_000) { LauncherItemsRepository(db).snapshots.first().icons[appKey] })
        settings.mutateSettings { it.withIconPacks(emptyList()) }
        db = open()
        assertTrue(withTimeout(10_000) { LauncherSettingsRepository(db).snapshots.first().settings.enabledIconPackPackages }.isEmpty())
        assertEquals(override, withTimeout(10_000) { LauncherItemsRepository(db).snapshots.first().icons[appKey] })
        val bulk = ItemIcon.Theme.copy(design = IconDesign(shape = IconShape.Gem, size = 125,
            background = IconColor.FromApp, foreground = IconColor.Theme, x = 15f))
        val special = override.copy(design = IconDesign(shape = IconShape.Cookie, cookieSides = 12, y = -10f))
        LauncherSettingsRepository(db).mutateSettings { it.copy(iconDesign = bulk) }
        LauncherItemsRepository(db).saveIcon(appKey, special)
        db = open()
        assertEquals(bulk, withTimeout(10_000) { LauncherSettingsRepository(db).snapshots.first().settings.iconDesign })
        assertEquals(special, withTimeout(10_000) { LauncherItemsRepository(db).snapshots.first().icons[appKey] })
    }
}
