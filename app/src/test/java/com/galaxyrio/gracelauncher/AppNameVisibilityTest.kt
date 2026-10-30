package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.AppNameVisibility
import com.galaxyrio.gracelauncher.data.ListAppearance
import org.junit.Assert.*
import org.junit.Test

class AppNameVisibilityTest {
    @Test fun fourModesControlTheOuterListsIncludingFolderRows() {
        val expected = mapOf(
            AppNameVisibility.Both to (true to true),
            AppNameVisibility.Favorites to (true to false),
            AppNameVisibility.AppList to (false to true),
            AppNameVisibility.Neither to (false to false),
        )
        expected.forEach { (mode, visible) ->
            listOf(false, true).forEach { applyToFolders ->
                val settings = ListAppearance(names = mode, applyNameVisibilityToFolders = applyToFolders)
                assertEquals(visible.first, settings.showNames(favorites = true))
                assertEquals(visible.second, settings.showNames(favorites = false))
            }
        }
    }

    @Test fun folderContentsKeepTheirNamesByDefaultInEveryMode() {
        AppNameVisibility.entries.forEach { mode ->
            val settings = ListAppearance(names = mode)
            assertFalse(settings.applyNameVisibilityToFolders)
            assertTrue(settings.showNames(favorites = true, insideFolder = true))
            assertTrue(settings.showNames(favorites = false, insideFolder = true))
        }
    }

    @Test fun optInMakesFolderContentsFollowTheirSourceListWithoutChangingTheMode() {
        AppNameVisibility.entries.forEach { mode ->
            val settings = ListAppearance(names = mode, applyNameVisibilityToFolders = true)
            listOf(false, true).forEach { favorites ->
                assertEquals(settings.showNames(favorites), settings.showNames(favorites, insideFolder = true))
                assertTrue(settings.copy(applyNameVisibilityToFolders = false).showNames(favorites, insideFolder = true))
            }
            assertEquals(mode, settings.copy(applyNameVisibilityToFolders = false).names)
        }
    }
}
