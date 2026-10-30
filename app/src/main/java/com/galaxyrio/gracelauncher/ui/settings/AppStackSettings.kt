package com.galaxyrio.gracelauncher.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.galaxyrio.gracelauncher.data.*
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState

/** The same create/manage screen, with membership checkboxes rather than another app list. */
@Composable
internal fun AppGroupPickerScreen(state: LauncherUiState, actions: LauncherActions, selectedKeys: Set<String>,
    onBack: () -> Unit, appStacks: Boolean, ownerKey: String, onToggle: (LauncherApp) -> Unit) {
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = editingId == null, onBack = onBack)
    if (editingId != null) {
        LauncherSettingsScreen(state, actions, { editingId = null }, initialFolderId = editingId, closePrivateSpaceOnDispose = false)
    } else FolderSettings(state, actions, onBack, appStacks = appStacks, selectionChangesFavorites = false,
        newPlacement = FolderPlacement.None,
        excludedIds = groupPickerExcludedIds(ownerKey, state.folders, state.popups),
        selectedIds = state.folders.filter { it.key in selectedKeys }.map { it.id }.toSet(),
        onToggle = { id -> state.folders.firstOrNull { it.id == id }?.let { onToggle(state.folderItem(it)) } },
        onEdit = { editingId = it })
}

/** Never let a folder contain itself or an ancestor, including through saved pop-up contents. */
internal fun groupPickerExcludedIds(ownerKey: String, folders: List<LauncherFolder>, popups: Map<String, List<PopupItem>>): Set<String> {
    if (!ownerKey.startsWith("folder:")) return emptySet()
    val excluded = mutableSetOf(ownerKey)
    do {
        val previousSize = excluded.size
        folders.forEach { folder ->
            val members = popups[folder.key]?.map { it.key } ?: folder.appKeys
            if (members.any { it in excluded }) excluded += folder.key
        }
    } while (excluded.size != previousSize)
    return folders.filter { it.key in excluded }.map { it.id }.toSet()
}
