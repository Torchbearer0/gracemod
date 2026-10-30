package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.FolderPlacement
import com.galaxyrio.gracelauncher.data.LauncherFolder
import com.galaxyrio.gracelauncher.data.PopupItem
import com.galaxyrio.gracelauncher.ui.settings.groupPickerExcludedIds
import org.junit.Assert.*
import org.junit.Test

class AppGroupPickerTest {
    private fun folder(id: String, vararg members: String) = LauncherFolder(id, id, members.toList(), FolderPlacement.None)

    @Test fun excludesSelfAndTransitiveAncestorsButNotOtherFolders() {
        val folders = listOf(folder("self"), folder("parent", "folder:self"), folder("grandparent", "folder:parent"), folder("other"))
        assertEquals(setOf("self", "parent", "grandparent"), groupPickerExcludedIds("folder:self", folders, emptyMap()))
    }

    @Test fun usesEditedPopupMembershipInsteadOfOldFolderDefaults() {
        val folders = listOf(folder("self"), folder("old-parent", "folder:self"), folder("new-parent"))
        val popups = mapOf("folder:old-parent" to emptyList(), "folder:new-parent" to listOf(PopupItem("folder:self")))
        assertEquals(setOf("self", "new-parent"), groupPickerExcludedIds("folder:self", folders, popups))
    }

    @Test fun appAndWidgetEditorsCanPickAnyUserFolder() {
        val folders = listOf(folder("one", "test.app/Main"), folder("two"))
        assertTrue(groupPickerExcludedIds("test.app/Main", folders, emptyMap()).isEmpty())
        assertTrue(groupPickerExcludedIds("com.galaxyrio.gracelauncher/widget-screen", folders, emptyMap()).isEmpty())
    }
}
