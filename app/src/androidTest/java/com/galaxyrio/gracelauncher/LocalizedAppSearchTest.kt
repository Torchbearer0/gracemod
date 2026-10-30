package com.galaxyrio.gracelauncher

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.content.res.Configuration
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class LocalizedAppSearchTest {
    private val app = LauncherApp(ComponentName("fixture.browser", "Activity"), "Яндекс", null, englishLabel = "Yandex")

    @Test fun originalEnglishAndTransliteratedNamesMatchWithoutFuzzySearch() {
        val names = app.searchNames()
        for (fuzzy in listOf(false, true)) {
            for (query in listOf("Я", "Яндекс", "Ya", "Yandex", "Yandeks", "YANDEX")) {
                assertNotNull(query, names.score(SearchQuery(query, fuzzy)))
            }
        }
        assertEquals("Яндекс", app.label)
        assertEquals("Y", app.section)
        assertNull(SearchNames("Яндекс").score(SearchQuery("Yandex", false)))
        assertNull(SearchNames("Yandeks").score(SearchQuery("Yandex", false)))
        assertNull(SearchNames("x").score(SearchQuery("ks", true)))
        assertNull(SearchNames("ks").score(SearchQuery("x", true)))
    }

    @Test fun renamedAppsRetainBothProviderNamesAndSelectorPackageLookup() {
        val renamed = app.copy(label = "Браузер")
        for (query in listOf("Браузер", "Brauzer", "Яндекс", "Yandex")) {
            assertNotNull(query, renamed.searchNames().score(SearchQuery(query, false)))
            assertTrue(AppSearchIndex(renamed).matches(SearchQuery(query, false)))
        }
        assertTrue(AppSearchIndex(renamed).matches(SearchQuery("fixture.browser", false)))
        assertEquals("B", renamed.section)
        assertEquals("Yandex", renamed.copy(userSerial = 10, isWorkProfile = true).englishLabel)
        val shortcut = LauncherShortcut("new", app.packageName, "Новая вкладка", null).asApp(app)
        assertNull(shortcut.englishLabel) // An owner's name is not a translation of a shortcut.
    }

    @Test fun ordinaryMatchesRankBeforeFuzzyMatchesAndPunctuationStillMatches() {
        assertTrue(SearchNames("Yandex").score(SearchQuery("Yandex", true))!! <
            SearchNames("Яндекс").score(SearchQuery("Yandeks", true))!!)
        assertNull(SearchNames("Gmail").score(SearchQuery("gml", false)))
        assertNotNull(SearchNames("Gmail").score(SearchQuery("gml", true)))
        assertNotNull(SearchNames("C++").score(SearchQuery("++", true)))
        assertTrue(SearchNames("Яндекс").matches(SearchQuery("", false)))
    }

    @Test fun englishResourcesRespectActivityOverridesAndLeaveDisplayConfigurationAlone() {
        val base = InstrumentationRegistry.getInstrumentation().context
        val russian = base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag("ru")) })
        fun resource(name: String) = base.resources.getIdentifier(name, "string", base.packageName).also { assertNotEquals(0, it) }
        val appId = resource("search_fixture_app")
        val activityId = resource("search_fixture_activity")
        val info = ApplicationInfo(base.applicationInfo).apply { labelRes = appId; nonLocalizedLabel = null }
        val activity = ActivityInfo().apply {
            applicationInfo = info
            packageName = info.packageName
            name = "FixtureActivity"
            labelRes = activityId
        }
        val reader = EnglishLabelReader(russian)
        assertEquals("Яндекс", russian.getString(appId))
        assertEquals("Yandex", reader.label(info))
        assertEquals("Chat", reader.label(activity))
        assertEquals("Chat", reader.label(ResolveInfo().apply { activityInfo = activity }))
        assertEquals("Yandex", reader.label(ResolveInfo().apply { activityInfo = activity; labelRes = appId }))
        assertEquals("Fixed", reader.label(ResolveInfo().apply { activityInfo = activity; nonLocalizedLabel = "Fixed" }))
        assertEquals("Yandex", reader.label(ActivityInfo(activity).apply { labelRes = 0 }))
        assertEquals("Только русский", reader.label(ApplicationInfo(info).apply { labelRes = resource("search_fixture_untranslated") }))
        assertEquals("Яндекс", russian.getString(appId))
        assertEquals("ru", russian.resources.configuration.locales[0].language)
        assertNull(reader.label(ApplicationInfo(info).apply { labelRes = 0x7fffffff }))
    }

    @Test fun cachedProfileAliasesSurviveReload() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = java.nio.file.Files.createTempDirectory(target.cacheDir.toPath(), "localized-search").toFile()
        val context = object : android.content.ContextWrapper(target) {
            override fun getNoBackupFilesDir() = directory
        }
        try {
            val cache = PrivateAppCache(context)
            val profile = app.copy(user = Process.myUserHandle(), userSerial = 42, isPrivateSpace = true)
            cache.save(42, listOf(profile))
            val loaded = cache.load(Process.myUserHandle(), 42).single()
            assertEquals("Yandex", loaded.englishLabel)
            assertNotNull(loaded.searchNames().score(SearchQuery("Yandex", false)))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun russianSectionsAndOrderAreSharedWithNonAppEntries() {
        val labels = listOf("Яндекс", "Юла", "Чат", "Ёж", "Еда", "1Password")
        assertEquals(listOf("Y", "Y", "C", "Y", "E", "#"), labels.map(::sectionForLabel))
        val apps = labels.mapIndexed { i, label -> LauncherApp(ComponentName("fixture", "Activity$i"), label, null) }
        assertEquals(labels.sortedWith(LauncherLabelOrder), apps.sortedWith(LauncherAppOrder).map { it.label })
        assertEquals("C", app.copy(label = "Чат").section)
    }
}
