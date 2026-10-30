package com.galaxyrio.gracelauncher

import android.content.ComponentName
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.data.FolderPlacement
import com.galaxyrio.gracelauncher.data.AlphabetAppearance
import com.galaxyrio.gracelauncher.data.IconColor
import com.galaxyrio.gracelauncher.data.IconShape
import com.galaxyrio.gracelauncher.data.ListAppearance
import com.galaxyrio.gracelauncher.data.IconDisplaySettings
import com.galaxyrio.gracelauncher.data.IconDisplayStyle
import com.galaxyrio.gracelauncher.data.IconDotColor
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.LauncherDatabase
import com.galaxyrio.gracelauncher.data.SearchSettings
import com.galaxyrio.gracelauncher.data.ShortcutsFoldersSettings
import com.galaxyrio.gracelauncher.data.NotificationDisplay
import com.galaxyrio.gracelauncher.data.LauncherFolder
import com.galaxyrio.gracelauncher.data.LauncherSettings
import com.galaxyrio.gracelauncher.data.LauncherSettingsRepository
import com.galaxyrio.gracelauncher.data.ThemeMode
import com.galaxyrio.gracelauncher.data.LauncherItemsRepository
import com.galaxyrio.gracelauncher.data.PopupItem
import com.galaxyrio.gracelauncher.data.HomeLayout
import com.galaxyrio.gracelauncher.data.ItemIcon
import com.galaxyrio.gracelauncher.data.ClockStyle
import com.galaxyrio.gracelauncher.data.ClockLayout
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import java.util.UUID
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class LauncherSettingsPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "launcher-settings-test-${UUID.randomUUID()}.db"
    private var database: LauncherDatabase? = null

    @After
    fun closeDatabase() {
        database?.close()
        context.deleteDatabase(databaseName)
    }

    private fun openRepository(): LauncherSettingsRepository {
        database?.close()
        val reopened = Room.databaseBuilder(context, LauncherDatabase::class.java, databaseName)
            .addMigrations(LauncherDatabase.Migration1To2, LauncherDatabase.Migration2To3,
                LauncherDatabase.Migration3To4, LauncherDatabase.Migration4To5, LauncherDatabase.Migration5To6,
                LauncherDatabase.Migration6To7, LauncherDatabase.Migration7To8, LauncherDatabase.Migration8To9,
                LauncherDatabase.Migration9To10, LauncherDatabase.Migration10To11, LauncherDatabase.Migration11To12,
                LauncherDatabase.Migration12To13, LauncherDatabase.Migration13To14,
                LauncherDatabase.Migration14To15, LauncherDatabase.Migration15To16,
                LauncherDatabase.Migration16To17, LauncherDatabase.Migration17To18,
                LauncherDatabase.Migration18To19, LauncherDatabase.Migration19To20,
                LauncherDatabase.Migration20To21, LauncherDatabase.Migration21To22,
                LauncherDatabase.Migration22To23, LauncherDatabase.Migration23To24,
                LauncherDatabase.Migration24To25, LauncherDatabase.Migration25To26,
                LauncherDatabase.Migration26To27).build()
        database = reopened
        return LauncherSettingsRepository(reopened)
    }

    @Test
    fun nonAccessibilityPreferencePersistsWithoutChangingOtherSettings() = runBlocking {
        val repository = openRepository()
        val defaults = withTimeout(10_000) { repository.snapshots.first().settings }
        assertFalse(defaults.preferNonAccessibility)
        repository.mutateSettings { it.copy(preferNonAccessibility = true) }
        val reopened = openRepository()
        assertEquals(defaults.copy(preferNonAccessibility = true),
            withTimeout(10_000) { reopened.snapshots.first().settings })
        reopened.mutateSettings { it.copy(preferNonAccessibility = false) }
        assertEquals(defaults, withTimeout(10_000) { openRepository().snapshots.first().settings })
    }

    @Test
    fun versionOneMigratesWithoutLosingSettingsHiddenAppsOrFolderOrder() = runBlocking {
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.galaxyrio.gracelauncher.data.LauncherDatabase/1.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
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
            legacy.execSQL("INSERT INTO launcher_settings VALUES (0, 0, 1, 0, 0, -14129574, 'Dark')")
            legacy.execSQL("INSERT INTO hidden_apps VALUES ('mail/MailActivity')")
            legacy.execSQL("INSERT INTO folders VALUES ('work', 'Work', 'Favorites')")
            legacy.execSQL("INSERT INTO folder_apps VALUES ('work', 'notes/NotesActivity', 0)")
            legacy.execSQL("INSERT INTO folder_apps VALUES ('work', 'mail/MailActivity', 1)")
            legacy.version = 1
        }
        val repository = openRepository()
        val migrated = withTimeout(10_000) { repository.snapshots.first() }
        assertEquals(LauncherSettings(calendarAgenda = false, allowHapticFeedback = false,
            useDynamicColors = false, themeColor = -14129574, darkMode = ThemeMode.Dark), migrated.settings)
        assertEquals(setOf("mail/MailActivity"), migrated.hiddenAppKeys)
        assertEquals(listOf(LauncherFolder("work", "Work", listOf("notes/NotesActivity", "mail/MailActivity"),
            FolderPlacement.Favorites)), migrated.folders)
        repository.mutateSettings { it.copy(iconPackPackage = "me.morirain.dev.iconpack.pure") }
        val reopened = withTimeout(10_000) { openRepository().snapshots.first() }
        assertEquals(migrated.copy(settings = migrated.settings.copy(iconPackPackage = "me.morirain.dev.iconpack.pure")), reopened)
    }

    @Test
    fun versionTwoPreservesIconPackAndAddsPersistentMediaPreference() = runBlocking {
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.galaxyrio.gracelauncher.data.LauncherDatabase/2.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
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
            legacy.execSQL("INSERT INTO launcher_settings VALUES (0, 0, 1, 0, 0, -14129574, 'Dark', 'me.morirain.dev.iconpack.pure')")
            legacy.version = 2
        }
        val repository = openRepository()
        val settings = withTimeout(10_000) { repository.snapshots.first().settings }
        assertTrue(settings.mediaPlayer)
        assertEquals("me.morirain.dev.iconpack.pure", settings.iconPackPackage)
        repository.mutateSettings { it.copy(mediaPlayer = false) }
        assertEquals(settings.copy(mediaPlayer = false), withTimeout(10_000) { openRepository().snapshots.first().settings })
    }

    @Test
    fun versionThreeAddsOptInWeatherWithoutChangingExistingPreferences() = runBlocking {
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.galaxyrio.gracelauncher.data.LauncherDatabase/3.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
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
            legacy.execSQL("INSERT INTO launcher_settings VALUES (0, 0, 1, 0, 0, -14129574, 'Dark', 'test.icons', 0)")
            legacy.version = 3
        }
        val settings = withTimeout(10_000) { openRepository().snapshots.first().settings }
        assertEquals(LauncherSettings(calendarAgenda = false, allowHapticFeedback = false,
            useDynamicColors = false, themeColor = -14129574, darkMode = ThemeMode.Dark,
            iconPackPackage = "test.icons", mediaPlayer = false), settings)
        assertFalse(settings.weatherEnabled)
        assertEquals(7, settings.weatherForecastDays)
        val selected = settings.copy(weatherEnabled = true, weatherLocationId = "city&source", weatherForecastDays = 5)
        LauncherSettingsRepository(requireNotNull(database)).updateSettings(selected)
        assertEquals(selected, withTimeout(10_000) { openRepository().snapshots.first().settings })
    }

    @Test
    fun automaticAlphabetSpacingAndAnExplicitOldDefaultSurviveDatabaseRecreation() = runBlocking {
        val automatic = LauncherSettings(listAppearance = ListAppearance(alphabet = AlphabetAppearance(bottomPercent = 80)))
        openRepository().updateSettings(automatic)
        assertEquals(automatic, withTimeout(10_000) { openRepository().snapshots.first().settings })
        val manual = automatic.copy(listAppearance = automatic.listAppearance.copy(alphabet = AlphabetAppearance(topPercent = 35)))
        openRepository().updateSettings(manual)
        assertEquals(manual, withTimeout(10_000) { openRepository().snapshots.first().settings })
    }

    @Test
    fun settingsHiddenAppsAndOrderedFoldersSurviveDatabaseRecreation() = runBlocking {
        val repository = openRepository()
        val settings = LauncherSettings(
            calendarAgenda = false,
            showBatteryPercentage = false,
            allowHapticFeedback = false,
            useDynamicColors = false,
            themeColor = 0xFF28665A.toInt(),
            darkMode = ThemeMode.Dark,
            amoledMode = true,
            iconPackPackage = "me.morirain.dev.iconpack.pure",
            mediaPlayer = false,
            weatherEnabled = true,
            weatherForecastDays = 10,
            weatherLocationId = "beijing&china",
            clockAppKey = "test.clock/test.clock.MainActivity",
            hideStatusBar = false,
            hideAlphabet = true,
            hideFavoriteNames = true,
            dimWallpaper = true,
            wallpaperDimAmount = 35,
            blurWallpaper = false,
            wallpaperBlurRadius = 24,
            digitalWellbeing = com.galaxyrio.gracelauncher.data.DigitalWellbeingSettings(enabled = true,
                display = setOf(com.galaxyrio.gracelauncher.data.UsageDisplay.Month,
                    com.galaxyrio.gracelauncher.data.UsageDisplay.Percentage),
                refresh = com.galaxyrio.gracelauncher.data.UsageRefresh.Never),
            listAppearance = ListAppearance(alphabet = AlphabetAppearance(fontId = "system", fontSize = 20,
                fontColor = IconColor(0xFFABCDEF.toInt()), indicatorShape = IconShape.Pebble, pebbleRoundness = 60,
                topPercent = 25, bottomPercent = 75, freeMovement = true)),
            search = SearchSettings(enabled = false, suggestions = false, contacts = true, fuzzy = false,
                internet = true, hiddenApps = false, recentAppKeys = listOf("mail/MailActivity", "notes/NotesActivity")),
            clockStyle = ClockStyle(layout = ClockLayout.TwoLines,
                singleLine = ClockStyle.defaults(ClockLayout.SingleLine).copy(weight = 700, showColon = true),
                twoLines = ClockStyle.defaults(ClockLayout.TwoLines).copy(fontId = "test.ttf", size = 100, letterSpacing = 2)),
        )
        val folder = LauncherFolder(
            id = "work",
            name = "Work",
            appKeys = listOf("mail/MailActivity", "notes/NotesActivity", "mail/MailActivity"),
            placement = FolderPlacement.Favorites,
        )
        repository.updateSettings(settings)
        repository.setHiddenApps(setOf("mail/MailActivity", "hidden/Activity"))
        repository.saveFolder(folder)

        val snapshot = withTimeout(10_000) { openRepository().snapshots.first() }
        assertEquals(settings, snapshot.settings)
        assertEquals(setOf("mail/MailActivity", "hidden/Activity"), snapshot.hiddenAppKeys)
        assertEquals(listOf(folder.copy(appKeys = folder.appKeys.distinct())), snapshot.folders)
    }

    @Test
    fun versionFourKeepsWeatherAndDefaultsClockThenPersistsAndResetsTheChoice() = runBlocking {
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.galaxyrio.gracelauncher.data.LauncherDatabase/4.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
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
            legacy.execSQL("INSERT INTO launcher_settings VALUES (0, 1, 1, 1, 1, -10006364, 'System', NULL, 1, 1, 5, 'city')")
            legacy.version = 4
        }
        val settings = withTimeout(10_000) { openRepository().snapshots.first().settings }
        assertEquals(null, settings.clockAppKey)
        assertTrue(settings.weatherEnabled)
        assertEquals(5, settings.weatherForecastDays)
        assertEquals("city", settings.weatherLocationId)
        val selected = settings.copy(clockAppKey = "clock/clock.Alarm")
        LauncherSettingsRepository(requireNotNull(database)).updateSettings(selected)
        assertEquals(selected, withTimeout(10_000) { openRepository().snapshots.first().settings })
        LauncherSettingsRepository(requireNotNull(database)).mutateSettings { it.copy(clockAppKey = null) }
        assertEquals(settings, withTimeout(10_000) { openRepository().snapshots.first().settings })
    }

    @Test
    fun queuedSettingTransformsPreserveEachOtherBeforeAnyUiEmission() = runBlocking {
        val repository = openRepository()
        val original = LauncherSettings(themeColor = 0xFF28665A.toInt())
        repository.updateSettings(original)
        // Both callbacks exist before either commit reaches a UI collector.
        val disableAgenda: (LauncherSettings) -> LauncherSettings = { it.copy(calendarAgenda = false) }
        val disableBattery: (LauncherSettings) -> LauncherSettings = { it.copy(showBatteryPercentage = false) }
        coroutineScope {
            launch { repository.mutateSettings(disableAgenda) }
            launch { repository.mutateSettings(disableBattery) }
        }
        val saved = withTimeout(10_000) { openRepository().snapshots.first().settings }
        assertEquals(original.copy(calendarAgenda = false, showBatteryPercentage = false), saved)
    }

    @Test
    fun editingMembershipReplacesRowsAndDeletingFolderDoesNotResurrectOldMembers() = runBlocking {
        val repository = openRepository()
        val original = LauncherFolder("tools", "Tools", listOf("one/A", "two/B"), FolderPlacement.AppList)
        repository.saveFolder(original)
        val edited = original.copy(name = " Daily ", appKeys = listOf("three/C", "two/B"))
        repository.saveFolder(edited)
        assertEquals(
            listOf(edited.copy(name = "Daily")),
            withTimeout(10_000) { repository.snapshots.first().folders },
        )
        repository.deleteFolder(original.id)
        assertTrue(withTimeout(10_000) { repository.snapshots.first().folders }.isEmpty())
        repository.saveFolder(original.copy(appKeys = emptyList()))
        val reopened = withTimeout(10_000) { openRepository().snapshots.first() }
        assertTrue(reopened.folders.single().appKeys.isEmpty())
    }

    @Test
    fun folderAndPopupShareOrderedWidgetsAndDeletingTheFolderReleasesTheirIds() = runBlocking {
        val repository = openRepository()
        val items = LauncherItemsRepository(requireNotNull(database))
        val folder = LauncherFolder("mixed", "Mixed", listOf("one/A", "two/B"), FolderPlacement.Favorites)
        repository.saveFolder(folder)
        val widget = PopupItem("widget:17", HomeLayout(widgetId = 17, widgetProvider = "widget/Provider", widgetLabel = "Widget"))
        items.updatePopup(folder.key, emptyList()) { listOf(it.last(), widget, it.first()) }
        items.saveIcon(folder.key, ItemIcon("pack", "icons", "folder"))
        repository.updateFolder(folder.id, name = "Renamed", placement = FolderPlacement.AppList)
        val saved = repository.snapshots.first().folders.single()
        assertEquals(folder.copy(name = "Renamed", appKeys = listOf("two/B", "one/A"), placement = FolderPlacement.AppList), saved)
        assertEquals(listOf(PopupItem("two/B"), widget, PopupItem("one/A")), items.snapshots.first().popups[folder.key])
        assertEquals(listOf(17), repository.deleteFolder(folder.id))
        assertFalse(folder.key in items.snapshots.first().popups)
        assertFalse(folder.key in items.snapshots.first().icons)
        // A provider returning from setup after deletion must not recreate the owner.
        assertTrue(runCatching { items.updatePopup(folder.key, emptyList()) { listOf(widget) } }.isFailure)
    }

    @Test
    fun hiddenAppsRemainAvailableOutsideTheAppList() = runBlocking {
        val app = LauncherApp(ComponentName("hidden", "hidden.Activity"), "Hidden", icon = null)
        val folder = LauncherFolder("folder", "Folder", listOf(app.key), FolderPlacement.Favorites)
        val repository = openRepository()
        repository.saveFolder(folder)
        repository.setHiddenApps(setOf(app.key))
        val snapshot = withTimeout(10_000) { repository.snapshots.first() }
        val state = LauncherUiState(
            apps = listOf(app), favoriteKeys = setOf(app.key),
            hiddenAppKeys = snapshot.hiddenAppKeys, folders = snapshot.folders,
        )
        assertTrue(state.appListApps.isEmpty())
        assertEquals(listOf(app), state.favoriteApps)
        assertEquals(app, state.findItem(app.key))
        assertEquals(listOf(PopupItem(app.key)), state.popupItems(folder.asApp(), emptyList()))
        assertTrue(app.key in state.favoriteKeys)
        assertEquals(listOf(app.key), state.folders.single().appKeys)

        repository.setHiddenApps(emptySet())
        val restored = state.copy(hiddenAppKeys = withTimeout(10_000) { repository.snapshots.first().hiddenAppKeys })
        assertEquals(listOf(app), restored.appListApps)
        assertEquals(listOf(app), restored.favoriteApps)
        assertEquals(listOf(app.key), restored.folders.single().appKeys)
    }

    @Test
    fun anEmptyDatabaseUsesDefaultsAndDoesNotResetLegacyPreferences() = runBlocking {
        val favorites = context.getSharedPreferences("grace_launcher_preferences", 0)
        val appearance = context.getSharedPreferences("launcher_appearance", 0)
        val favoritesBefore = favorites.all
        val appearanceBefore = appearance.all
        val snapshot = withTimeout(10_000) { openRepository().snapshots.first() }
        assertEquals(LauncherSettings(), snapshot.settings)
        assertTrue(snapshot.hiddenAppKeys.isEmpty())
        assertTrue(snapshot.folders.isEmpty())
        assertEquals(favoritesBefore, favorites.all)
        assertEquals(appearanceBefore, appearance.all)
    }

    @Test fun folderAutoHidesStaySeparateFromManualHidesAcrossMembershipChangesAndReopen() = runBlocking {
        var repository = openRepository()
        val folder = LauncherFolder("auto", "Auto", listOf("one/Activity", "two/Activity"), FolderPlacement.None, autoHideApps = true)
        repository.hideApp("one/Activity")
        repository.saveFolder(folder)
        repository.saveFolder(folder.copy(id = "overlap", appKeys = listOf("two/Activity")))
        repository.updateSettings(LauncherSettings(paletteStyle = com.materialkolor.PaletteStyle.Expressive))
        repository = openRepository()
        val reopened = repository.snapshots.first()
        assertEquals(com.materialkolor.PaletteStyle.Expressive, reopened.settings.paletteStyle)
        assertEquals(setOf("one/Activity"), reopened.hiddenAppKeys)
        assertEquals(setOf("one/Activity", "two/Activity"), reopened.folderHiddenAppKeys)

        // Editing the shared popup must change derived hiding without writing manual keys.
        LauncherItemsRepository(requireNotNull(database)).updatePopup(folder.key, emptyList()) { listOf(PopupItem("three/Activity")) }
        var current = repository.snapshots.first()
        assertEquals(setOf("two/Activity", "three/Activity"), current.folderHiddenAppKeys)
        assertEquals(setOf("one/Activity", "two/Activity", "three/Activity"), current.effectiveHiddenAppKeys)
        repository.deleteFolder("overlap")
        current = repository.snapshots.first()
        assertEquals(setOf("one/Activity", "three/Activity"), current.effectiveHiddenAppKeys)
        repository.deleteFolder(folder.id)
        assertEquals(setOf("one/Activity"), openRepository().snapshots.first().effectiveHiddenAppKeys)
    }

    @Test
    fun iconDisplaySurvivesRoundTripInsideIconDesignJson() = runBlocking {
        val repository = openRepository()
        val dotted = IconDisplaySettings(style = IconDisplayStyle.Dot, dotColor = IconDotColor.Theme, dotSize = 18,
            dotShape = IconShape.Pebble, dotPebbleRoundness = 63, dotSquareCornerRadius = 30, dotCookieSides = 7)
        repository.updateSettings(LauncherSettings(iconDesign = ItemIcon.Theme.copy(display = dotted)))
        val restored = withTimeout(10_000) { openRepository().snapshots.first().settings }
        // The shared display is derived from the bulk icon design, same field per-app
        // overrides use; the whole ItemIcon round-trips intact.
        assertEquals(ItemIcon.Theme.copy(display = dotted), restored.iconDesign)
        assertEquals(dotted, restored.iconDisplay)

        // Hidden does not rewrite the stored name preference — rows force labels
        // themselves, so the flag survives a Full→Hidden→Full round-trip.
        // Reopen above closed the first database; write through a live one.
        openRepository().updateSettings(restored.copy(hideFavoriteNames = true,
            iconDesign = ItemIcon.Theme.copy(display = IconDisplaySettings(style = IconDisplayStyle.Hidden))))
        val hidden = withTimeout(10_000) { openRepository().snapshots.first().settings }
        assertEquals(IconDisplayStyle.Hidden, hidden.iconDisplay.style)
        assertTrue(hidden.hideFavoriteNames)
    }

    @Test
    fun missingAndMalformedIconDisplayJsonFallBackToFullIcons() {
        assertEquals(IconDisplaySettings(), IconDisplaySettings.decode(null))
        assertEquals(IconDisplaySettings(), IconDisplaySettings.decode("not json"))
        assertEquals(IconDisplaySettings(), IconDisplaySettings.decode("{}"))
        // Unknown enum names and out-of-range sizes degrade field by field.
        assertEquals(IconDisplaySettings(style = IconDisplayStyle.Dot, dotSize = 32),
            IconDisplaySettings.decode("""{"style":"Dot","dotColor":"bogus","dotSize":99}"""))
        // Existing dots stay circular; malformed new fields cannot break shape rendering.
        assertEquals(IconShape.Circle, IconDisplaySettings.decode("""{"style":"Dot"}""").dotShape)
        assertEquals(IconDisplaySettings(style = IconDisplayStyle.Dot, dotPebbleRoundness = 0, dotSquareCornerRadius = 100),
            IconDisplaySettings.decode("""{"style":"Dot","dotShape":"bogus","dotCookieSides":3,"dotPebbleRoundness":-5,"dotSquareCornerRadius":200}"""))
    }

    @Test
    fun itemIconDisplayOverrideSurvivesEncodeAndStaysAbsentByDefault() {
        val override = IconDisplaySettings(style = IconDisplayStyle.Dot,
            dotColor = IconDotColor.Custom, dotSize = 14, customColor = IconColor(0xFF336699.toInt()),
            dotShape = IconShape.Cookie, dotCookieSides = 12)
        val icon = ItemIcon("pack", "icons", "name", display = override)
        assertEquals(override, ItemIcon.decode(icon.encode())?.display)
        // No override stays null so the app keeps inheriting the shared setting.
        assertEquals(null, ItemIcon.decode(ItemIcon.Theme.encode())?.display)
        // Icons saved before display overrides existed decode the same way.
        assertEquals(null, ItemIcon.decode("""{"kind":"theme"}""")?.display)
    }

    @Test
    fun appsDoNotFlashBeforeSettingsLoadOrAfterAReadFailure() {
        val app = LauncherApp(ComponentName("one", "one.Activity"), "One", icon = null)
        val loading = LauncherUiState(apps = listOf(app), favoriteKeys = setOf(app.key), isLoadingSettings = true)
        assertTrue(loading.appListApps.isEmpty())
        assertTrue(loading.favoriteApps.isEmpty())
        assertTrue(loading.copy(isLoadingSettings = false, settingsLoadFailed = true).appListApps.isEmpty())
        assertFalse(loading.copy(isLoadingSettings = false).appListApps.isEmpty())
    }

    @Test fun popupOptionsRoundTripAndMissingOrInvalidValuesKeepDefaults() = runBlocking {
        val options = ShortcutsFoldersSettings(enabled = false, swipeLeftToOpenFirst = true,
            silentNotifications = NotificationDisplay.ExpandedOnly, normalNotifications = NotificationDisplay.Hidden,
            popupSwipeDistanceDp = 24, popupSwipeAngleDegrees = 30,
            singleLineNotificationPreviews = true, multipleAppNotificationPreviews = true)
        assertEquals(options, ShortcutsFoldersSettings.decode(options.encode()))
        val clamped = ShortcutsFoldersSettings.decode("""{"popupSwipeDistanceDp":-1,"popupSwipeAngleDegrees":100}""")
        assertEquals(0, clamped.popupSwipeDistanceDp)
        assertEquals(90, clamped.popupSwipeAngleDegrees)
        val upperDistance = ShortcutsFoldersSettings.decode("""{"popupSwipeDistanceDp":100,"popupSwipeAngleDegrees":0}""")
        assertEquals(48, upperDistance.popupSwipeDistanceDp)
        assertEquals(10, upperDistance.popupSwipeAngleDegrees)
        assertEquals(ShortcutsFoldersSettings(), ShortcutsFoldersSettings.decode(null))
        assertEquals(ShortcutsFoldersSettings(), ShortcutsFoldersSettings.decode("invalid"))
        assertEquals(ShortcutsFoldersSettings(), ShortcutsFoldersSettings.decode("{\"normalNotifications\":\"unknown\"}"))
        val oldOptions = ShortcutsFoldersSettings.decode("""{"enabled":true,"swipeLeftToOpenFirst":true,"normalNotifications":"normal"}""")
        assertFalse(oldOptions.singleLineNotificationPreviews)
        assertFalse(oldOptions.multipleAppNotificationPreviews)
        openRepository().mutateSettings { it.copy(shortcutsFolders = options) }
        assertEquals(options, withTimeout(10_000) { openRepository().snapshots.first().settings.shortcutsFolders })
    }
}
