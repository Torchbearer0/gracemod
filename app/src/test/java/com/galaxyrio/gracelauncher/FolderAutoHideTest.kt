package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.*
import org.junit.Assert.*
import org.junit.Test

class FolderAutoHideTest {
    private val folder = LauncherFolder("one", "One", listOf("app/A", "app/B"), FolderPlacement.AppList)

    @Test fun defaultOffDoesNotHideMembers() {
        assertFalse(folder.autoHideApps)
        assertTrue(hiddenAppsFromFolders(listOf(folder)).isEmpty())
    }

    @Test fun currentAndFutureMembersFollowTheSwitchAndAreReleasedOnRemoval() {
        val enabled = folder.copy(autoHideApps = true)
        assertEquals(setOf("app/A", "app/B"), hiddenAppsFromFolders(listOf(enabled)))
        assertEquals(setOf("app/B", "app/C"), hiddenAppsFromFolders(listOf(enabled.copy(appKeys = listOf("app/B", "app/C")))))
        assertTrue(hiddenAppsFromFolders(listOf(enabled.copy(autoHideApps = false))).isEmpty())
    }

    @Test fun overlappingFoldersKeepTheAppHiddenUntilTheLastSourceIsGone() {
        val first = folder.copy(autoHideApps = true)
        val second = first.copy(id = "two", appKeys = listOf("app/B"))
        val snapshot = LauncherStorageSnapshot(folders = listOf(first, second))
        assertEquals(setOf("app/A", "app/B"), snapshot.effectiveHiddenAppKeys)
        assertEquals(setOf("app/B"), snapshot.copy(folders = listOf(second)).effectiveHiddenAppKeys)
        assertTrue(snapshot.copy(folders = emptyList()).effectiveHiddenAppKeys.isEmpty())
    }

    @Test fun manualHideSurvivesDoubleHidingDisablingAndDeletingFolders() {
        val enabled = folder.copy(autoHideApps = true)
        val snapshot = LauncherStorageSnapshot(hiddenAppKeys = setOf("app/A"), folders = listOf(enabled))
        assertEquals(setOf("app/A"), snapshot.hiddenAppKeys)
        assertEquals(setOf("app/A", "app/B"), snapshot.folderHiddenAppKeys)
        assertEquals(setOf("app/A"), snapshot.copy(folders = listOf(enabled.copy(autoHideApps = false))).effectiveHiddenAppKeys)
        assertEquals(setOf("app/A"), snapshot.copy(folders = emptyList()).effectiveHiddenAppKeys)
    }

    @Test fun hidingUsesItemIdentityAndDoesNotDependOnFolderPlacement() {
        val workKey = "profile:10:app/A"
        val hidden = hiddenAppsFromFolders(listOf(folder.copy(autoHideApps = true, placement = FolderPlacement.None,
            appKeys = listOf(workKey, workKey, ""))))
        assertEquals(setOf(workKey), hidden)
        assertFalse("app/A" in hidden)
    }
}
