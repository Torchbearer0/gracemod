package com.galaxyrio.gracelauncher.ui.overlays

import android.appwidget.AppWidgetManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.*
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.AppIcon
import com.galaxyrio.gracelauncher.ui.components.AppSelectionList
import com.galaxyrio.gracelauncher.ui.components.AppSelectionRow
import com.galaxyrio.gracelauncher.ui.components.LauncherIcon
import com.galaxyrio.gracelauncher.ui.components.LauncherSymbol
import com.galaxyrio.gracelauncher.ui.components.ShortcutPickerScreen
import com.galaxyrio.gracelauncher.ui.settings.FavoriteFoldersScreen
import com.galaxyrio.gracelauncher.ui.settings.AppGroupPickerScreen
import com.galaxyrio.gracelauncher.ui.settings.SettingsScaffold
import com.galaxyrio.gracelauncher.ui.settings.SettingsFeatureBanner
import com.galaxyrio.gracelauncher.ui.settings.SettingsActionItem
import com.galaxyrio.gracelauncher.ui.widgets.WidgetPreview
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private data class SelectionEntry(val key: String, val label: String, val app: LauncherApp? = null, val widget: HomeLayout? = null,
    val graceWidget: GraceWidget? = null)

@Composable
private fun graceWidgetLabel(widget: GraceWidget): String = stringResource(when (widget) {
    GraceWidget.Clock -> R.string.settings_clock
    GraceWidget.Calendar -> R.string.widget_calendar
    GraceWidget.Media -> R.string.settings_media_player
    GraceWidget.Baseline -> R.string.grace_widget_baseline
})

private fun GraceWidget.symbol() = when (this) {
    GraceWidget.Clock -> LauncherSymbol.Clock
    GraceWidget.Calendar -> LauncherSymbol.Calendar
    GraceWidget.Media -> LauncherSymbol.Music
    GraceWidget.Baseline -> LauncherSymbol.Move
}
private data class SelectionOption(val entry: SelectionEntry, val checked: Boolean, val enabled: Boolean)

@Composable
private fun profileGroupChoices(state: LauncherUiState, selectedKeys: Set<String>) = listOf(
    SelectionOption(SelectionEntry(PrivateSpaceFolderKey, stringResource(R.string.private_space_title), state.folderItem(state.privateFolder)),
        PrivateSpaceFolderKey in selectedKeys, state.canFavoritePrivateSpace),
    SelectionOption(SelectionEntry(WorkProfileFolderKey, stringResource(R.string.work_profile_title), state.folderItem(state.workFolder)),
        WorkProfileFolderKey in selectedKeys, state.canFavoriteWorkProfile),
)

@Composable
internal fun ProfileEditorScreen(owner: LauncherApp, uiState: LauncherUiState, actions: LauncherActions,
    onBack: () -> Unit, work: Boolean = false, header: @Composable () -> Unit) {
    val loading = if (work) uiState.isLoadingApps else uiState.privateAppsLoading
    val failed = if (work) uiState.appLoadFailed else uiState.privateAppsFailed
    ItemSelectionScreen(
        title = stringResource(R.string.edit_app_popup, owner.label), tag = "private_space_editor",
        selected = (if (work) uiState.workProfileApps else uiState.privateSpaceApps).map { SelectionEntry(it.key, it.label, it) },
        apps = emptyList(), enabled = !loading && !failed,
        onToggle = {}, onRemove = {}, onReorder = if (work) actions.reorderWorkApps else actions.reorderPrivateApps, onDone = onBack,
        header = header, reorderOnly = true, loading = loading,
    )
}

@Composable
internal fun HomepageEditorScreen(uiState: LauncherUiState, actions: LauncherActions, onToggle: (LauncherApp) -> Unit, onReorder: (List<String>) -> Unit,
    initialAppKey: String? = null, onDone: () -> Unit) {
    var initialSelectionApplied by rememberSaveable(initialAppKey) { mutableStateOf(false) }
    LaunchedEffect(initialAppKey, uiState.isLoadingApps, uiState.isLoadingSettings, uiState.settingsLoadFailed) {
        if (!initialSelectionApplied && initialAppKey != null && !uiState.isLoadingApps &&
            !uiState.isLoadingSettings && !uiState.settingsLoadFailed) {
            val app = uiState.findItem(initialAppKey) ?: return@LaunchedEffect
            initialSelectionApplied = true
            if (initialAppKey !in uiState.favoriteKeys) onToggle(app)
        }
    }
    var choosingShortcut by rememberSaveable { mutableStateOf(false) }
    var choosingFolders by rememberSaveable { mutableStateOf(false) }
    var choosingStacks by rememberSaveable { mutableStateOf(false) }
    if (choosingStacks) {
        FavoriteFoldersScreen(uiState, actions, appStacks = true) { choosingStacks = false }
        return
    }
    if (choosingFolders) {
        FavoriteFoldersScreen(uiState, actions) { choosingFolders = false }
        return
    }
    if (choosingShortcut) {
        ShortcutPickerScreen(uiState, actions, uiState.favoriteKeys, { choosingShortcut = false }, onToggle)
        return
    }
    ItemSelectionScreen(
        title = stringResource(R.string.edit_homepage), tag = "favorites",
        header = {
            Column {
                SettingsFeatureBanner(stringResource(R.string.favorites_lock_editing), uiState.settings.homeLayout.editingLocked,
                    "favorites_lock_editing") { locked ->
                    actions.updateSettings { it.copy(homeLayout = it.homeLayout.copy(editingLocked = locked)) }
                }
                SettingsActionItem(stringResource(R.string.homepage_adjust_height), null, 0, 1, "homepage_adjust_height",
                    enabled = !uiState.isLoadingSettings && !uiState.settingsLoadFailed,
                    leading = { LauncherIcon(LauncherSymbol.Move) }, onClick = actions.moveWidget)
                if (uiState.settings.homeLayout.homeOrder == null) {
                    val baselineSpacing = (uiState.settings.homeLayout.baselineOffsetDp ?: 120f).coerceIn(0f, 600f)
                    Text("Legacy spacer position: ${baselineSpacing.toInt()} dp", Modifier.padding(start = 16.dp, top = 12.dp),
                        style = MaterialTheme.typography.titleSmall)
                    Slider(value = baselineSpacing, onValueChange = { value ->
                        actions.updateSettings { it.copy(homeLayout = it.homeLayout.copy(baselineOffsetDp = value)) }
                    }, valueRange = 0f..600f, enabled = !uiState.isLoadingSettings && !uiState.settingsLoadFailed,
                        modifier = Modifier.padding(horizontal = 16.dp).testTag("homepage_favorite_spacing"))
                } else {
                    Text("Drag the divider in the list below to place it between home items.",
                        Modifier.padding(start = 16.dp, top = 12.dp), style = MaterialTheme.typography.bodyMedium)
                }
                val dividerHeight = uiState.settings.homeLayout.baselineHeightDp.toFloat()
                Text("Divider height: ${dividerHeight.toInt()} dp", Modifier.padding(start = 16.dp, top = 8.dp),
                    style = MaterialTheme.typography.titleSmall)
                Slider(value = dividerHeight, onValueChange = { value ->
                    actions.updateSettings { it.copy(homeLayout = it.homeLayout.copy(baselineHeightDp = value.toInt())) }
                }, valueRange = 2f..240f, enabled = !uiState.isLoadingSettings && !uiState.settingsLoadFailed,
                    modifier = Modifier.padding(horizontal = 16.dp).testTag("homepage_divider_height"))
            }
        },
        selected = uiState.homeItems.map { item ->
            val grace = GraceWidget.fromKey(item.key)
            val app = uiState.findItem(item.key)
            SelectionEntry(item.key, if (grace != null) graceWidgetLabel(grace) else
                item.widget?.widgetLabel ?: app?.label ?: stringResource(R.string.popup_item_unavailable), app, item.widget, grace)
        },
        apps = uiState.allApps, enabled = !uiState.isLoadingSettings && !uiState.settingsLoadFailed,
        onToggle = onToggle, onRemove = { key ->
            val grace = GraceWidget.fromKey(key)
            when {
                grace != null -> actions.updateSettings { grace.setEnabled(it, false) }
                uiState.homeItems.any { it.key == key && it.widget != null } ->
                    actions.updatePopup(homeContentOwner(), emptyList()) { it.filterNot { entry -> entry.key == key } }
                else -> uiState.findItem(key)?.let(onToggle)
            }
        }, onReorder = onReorder, onDone = onDone,
        onAddWidget = actions.addWidget,
        graceSettings = uiState.settings,
        onToggleGrace = { grace -> actions.updateSettings { grace.setEnabled(it, !grace.enabled(it)) } },
        onAddShortcut = { choosingShortcut = true },
        onAddFolder = { choosingFolders = true },
        onAddStack = { choosingStacks = true },
        appGroupChoices = profileGroupChoices(uiState, uiState.favoriteKeys),
    )
}

@Composable
internal fun PopupEditorScreen(owner: LauncherApp, uiState: LauncherUiState, actions: LauncherActions, onBack: () -> Unit,
    header: (@Composable () -> Unit)? = null, widgetScreen: Boolean = false) {
    val isFolder = owner.folderId != null || widgetScreen
    val isStack = uiState.folders.any { it.id == owner.folderId && it.isAppStack }
    var retry by remember { mutableIntStateOf(0) }
    val result by produceState(if (isFolder) ShortcutResult(ShortcutStatus.Ready) else actions.cachedShortcuts(owner) ?: ShortcutResult(ShortcutStatus.Loading),
        owner, uiState.hasShortcutAccess, retry, uiState.itemRevision, uiState.settings) { if (!isFolder) value = actions.shortcuts(owner) }
    val shortcuts = result.asApps(owner).map { uiState.findItem(it.key) ?: it }
    val entries = uiState.popupItems(owner, shortcuts)
    val missing = stringResource(R.string.popup_item_unavailable)
    val selected = entries.map { item ->
        val app = uiState.findItem(item.key) ?: shortcuts.firstOrNull { it.key == item.key }
        SelectionEntry(item.key, item.widget?.widgetLabel ?: app?.label ?: missing, app, item.widget)
    }
    val ready = !uiState.isLoadingSettings && !uiState.settingsLoadFailed &&
        (uiState.popups.containsKey(owner.key) || result.status == ShortcutStatus.Ready || result.status == ShortcutStatus.DefaultLauncherRequired)
    var choosingShortcut by rememberSaveable(owner.key) { mutableStateOf(false) }
    // null = editor, false = folders, true = app stacks.
    var choosingGroup by rememberSaveable(owner.key) { mutableStateOf<Boolean?>(null) }
    val groupMode = choosingGroup
    if (groupMode != null) {
        AppGroupPickerScreen(uiState, actions, entries.map { it.key }.toSet(), { choosingGroup = null },
            appStacks = groupMode, ownerKey = owner.key) { app ->
            actions.updatePopup(owner, shortcuts) { current ->
                if (current.any { it.key == app.key }) current.filterNot { it.key == app.key } else current + PopupItem(app.key)
            }
        }
        return
    }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    if (choosingShortcut) {
        ShortcutPickerScreen(uiState, actions, entries.map { it.key }.toSet(), { choosingShortcut = false }, busy = saving, onSelect = { app ->
            saving = true
            scope.launch {
                try {
                    if (actions.rememberShortcut(app)) actions.updatePopup(owner, shortcuts) { current ->
                        if (current.any { it.key == app.key }) current.filterNot { it.key == app.key } else current + PopupItem(app.key)
                    }
                } finally { saving = false }
            }
        })
        return
    }
    ItemSelectionScreen(
        title = if (widgetScreen) stringResource(R.string.widget_screen_edit) else if (isStack) stringResource(R.string.app_stack_edit) else stringResource(R.string.edit_app_popup, owner.label),
        tag = if (widgetScreen) "widget_screen_editor" else "popup_editor", selected = selected,
        emptyMessage = if (isStack) stringResource(R.string.app_stack_empty) else null,
        apps = uiState.allApps.filterNot { it.key == owner.key }, enabled = ready,
        shortcuts = shortcuts.takeUnless { isFolder }, shortcutTitle = stringResource(R.string.popup_app_shortcuts, owner.label),
        header = header,
        shortcutStatus = result.status, onRetry = { retry++ }, onRequestAccess = actions.requestDefaultHome,
        onAddWidget = if (isStack) null else ({ actions.addPopupWidget(owner, shortcuts) }),
        onAddFolder = if (!isStack) ({ choosingGroup = false }) else null,
        onAddStack = if (!isStack) ({ choosingGroup = true }) else null,
        onAddShortcut = { choosingShortcut = true },
        appGroupChoices = if (widgetScreen) profileGroupChoices(uiState, entries.map { it.key }.toSet()) else emptyList(),
        onToggle = { app -> actions.updatePopup(owner, shortcuts) { current ->
            if (current.any { it.key == app.key }) current.filterNot { it.key == app.key } else current + PopupItem(app.key)
        } },
        onRemove = { key -> actions.updatePopup(owner, shortcuts) { it.filterNot { item -> item.key == key } } },
        onReorder = { keys -> actions.updatePopup(owner, shortcuts) { current ->
            val byKey = current.associateBy { it.key }
            val ordered = keys.mapNotNull(byKey::get) + current.filterNot { it.key in keys }
            if (widgetScreen) ordered.map { it.copy(grid = it.grid?.copy(column = null, row = null)) } else ordered
        } }, onDone = onBack,
    )
}

@Composable
internal fun WidgetScreenEditor(uiState: LauncherUiState, actions: LauncherActions, onBack: () -> Unit) {
    val title = stringResource(R.string.widget_screen_title)
    val owner = remember(title) { widgetScreenOwner(title) }
    PopupEditorScreen(owner, uiState, actions, onBack, widgetScreen = true)
}

/** One full-screen editor for the homepage and mixed pop-up contents. Search only filters All apps. */
@Composable
private fun ItemSelectionScreen(
    title: String, tag: String, selected: List<SelectionEntry>, apps: List<LauncherApp>, enabled: Boolean,
    onToggle: (LauncherApp) -> Unit, onRemove: (String) -> Unit, onReorder: (List<String>) -> Unit, onDone: () -> Unit,
    shortcuts: List<LauncherApp>? = null, shortcutTitle: String = "", shortcutStatus: ShortcutStatus = ShortcutStatus.Ready,
    onRetry: () -> Unit = {}, onRequestAccess: () -> Unit = {}, onAddWidget: (() -> Unit)? = null,
    onAddShortcut: (() -> Unit)? = null,
    onAddFolder: (() -> Unit)? = null,
    onAddStack: (() -> Unit)? = null,
    graceSettings: LauncherSettings? = null,
    onToggleGrace: (GraceWidget) -> Unit = {},
    appGroupChoices: List<SelectionOption> = emptyList(),
    header: (@Composable () -> Unit)? = null,
    reorderOnly: Boolean = false,
    loading: Boolean = false,
    emptyMessage: String? = null,
) {
    val selectedKeys = selected.map { it.key }
    val selectedEntries = selected.associateBy { it.key }
    val list = rememberLazyListState()
    val query = rememberTextFieldState()
    val commit by rememberUpdatedState(onReorder)
    val haptics by rememberUpdatedState(LocalHapticFeedback.current)
    val reorder = remember(list) { FavoritesReorderState(list, selectedKeys, { commit(it) }) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    } }
    LaunchedEffect(selectedKeys) { reorder.synchronize(selectedKeys) }
    val density = LocalDensity.current
    LaunchedEffect(reorder.draggingKey, density) {
        if (reorder.draggingKey == null) return@LaunchedEffect
        val edge = with(density) { 64.dp.toPx() }
        val speed = with(density) { 640.dp.toPx() }
        var previous = withFrameNanos { it }
        while (isActive && reorder.draggingKey != null) {
            val frame = withFrameNanos { it }
            val seconds = ((frame - previous) / 1_000_000_000f).coerceIn(0f, 0.032f)
            previous = frame
            reorder.autoScroll(edge, speed * seconds)
        }
    }
    SettingsScaffold(title, "${tag}_screen", onDone, fixedCollapsed = true, actions = {
        TextButton(onClick = onDone, modifier = Modifier.testTag("${tag}_done")) { Text(stringResource(R.string.done)) }
    }) { padding ->
        AppSelectionList(
            apps = apps.filter { it.shortcut == null }, selectedKeys = selectedKeys.toSet(), query = query, onSelect = onToggle,
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
            state = list, listTag = "${tag}_list", searchTag = "${tag}_search", itemTagPrefix = "favorite_all",
            enabled = enabled && reorder.draggingKey == null, userScrollEnabled = reorder.draggingKey == null,
            showApps = !reorderOnly,
        ) {
            if (header != null) item(key = "editor_header", contentType = "header") { header() }
            item(key = "selected_header", contentType = "header") { SelectionHeading(stringResource(R.string.favorites_selected), "${tag}_selected") }
            if (loading) item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth().padding(8.dp)) }
            if (!loading && reorder.keys.isEmpty()) item(key = "empty") {
                Text(emptyMessage ?: stringResource(if (reorderOnly) R.string.private_space_empty else if (onAddWidget == null) R.string.favorites_empty else R.string.popup_empty), Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(reorder.keys.mapNotNull(selectedEntries::get), key = { FavoritesReorderState.itemKey(it.key) }, contentType = { if (it.widget == null) "selected" else "widget" }) { entry ->
                val dragging = reorder.draggingKey == entry.key
                val offset by rememberReorderTranslation(reorder, entry.key)
                val background by animateColorAsState(if (dragging) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
                    label = "selectionLift")
                val index = reorder.keys.indexOf(entry.key)
                val moveUp = stringResource(R.string.favorites_move_up)
                val moveDown = stringResource(R.string.favorites_move_down)
                Column(Modifier.then(if (dragging) Modifier else Modifier.animateItem())
                    .zIndex(if (dragging || reorder.drop?.key == entry.key) 1f else 0f)
                    .graphicsLayer { translationY = offset }
                    .clip(RoundedCornerShape(16.dp)).background(background)
                    .semantics {
                        customActions = buildList {
                            if (index > 0) add(CustomAccessibilityAction(moveUp) { reorder.moveBy(entry.key, -1) })
                            if (index < reorder.keys.lastIndex) add(CustomAccessibilityAction(moveDown) { reorder.moveBy(entry.key, 1) })
                        }
                    }) {
                    SelectionChoice(entry, true, "favorite:${entry.key}", enabled && reorder.draggingKey == null, { onRemove(entry.key) },
                        selectable = !reorderOnly) {
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menu = true }, enabled = enabled,
                                modifier = Modifier.testTag("favorite_drag:${entry.key}").pointerInput(reorder, entry.key, enabled) {
                                    if (enabled) detectDragGestures(
                                        onDragStart = { reorder.start(entry.key) }, onDragEnd = { reorder.finish(false) },
                                        onDragCancel = { reorder.finish(true) },
                                    ) { change, amount -> change.consume(); reorder.drag(amount.y) }
                                }) {
                                Icon(painterResource(R.drawable.ms_drag_indicator), stringResource(R.string.favorites_reorder, entry.label), Modifier.size(24.dp))
                            }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text(moveUp) }, enabled = index > 0, onClick = { reorder.moveBy(entry.key, -1); menu = false })
                                DropdownMenuItem(text = { Text(moveDown) }, enabled = index < reorder.keys.lastIndex, onClick = { reorder.moveBy(entry.key, 1); menu = false })
                            }
                        }
                    }
                    entry.widget?.let { SelectedWidgetPreview(it) }
                }
            }
            if (shortcuts != null) {
                item(key = "shortcuts_header", contentType = "header") { SelectionHeading(shortcutTitle, "popup_shortcuts") }
                when (shortcutStatus) {
                    ShortcutStatus.Loading -> item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(8.dp)) }
                    ShortcutStatus.DefaultLauncherRequired -> item {
                        Text(stringResource(R.string.shortcut_permission), Modifier.padding(8.dp), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = onRequestAccess) { Text(stringResource(R.string.set_default_launcher)) }
                    }
                    ShortcutStatus.Error -> item { TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) } }
                    ShortcutStatus.Ready -> if (shortcuts.isEmpty()) item {
                        Text(stringResource(R.string.no_shortcuts), Modifier.padding(8.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                items(shortcuts, key = { "shortcut:${it.key}" }, contentType = { "app" }) { app ->
                    SelectionChoice(SelectionEntry(app.key, app.label, app), app.key in selectedKeys, "popup_choice:${app.key}", enabled,
                        { onToggle(app) })
                }
            }
            if (onAddWidget != null) {
                item(key = "widgets_header", contentType = "header") {
                    SelectionHeading(stringResource(R.string.popup_custom_widget), "popup_widgets")
                }
                item(key = "add_widget") {
                    AddSelectionContent("popup_add_widget", enabled, onAddWidget)
                }
            }
            if (graceSettings != null) {
                item(key = "grace_widgets_header", contentType = "header") {
                    SelectionHeading(stringResource(R.string.grace_widgets_title), "${tag}_grace_widgets")
                }
                items(GraceWidget.entries, key = { "grace_choice:${it.key}" }) { grace ->
                    AppSelectionRow(graceWidgetLabel(grace), grace.enabled(graceSettings), { onToggleGrace(grace) },
                        Modifier.testTag("${tag}_${grace.key}"), enabled = enabled,
                        icon = { LauncherIcon(grace.symbol()) })
                }
            }
            if (onAddShortcut != null || onAddFolder != null || onAddStack != null || appGroupChoices.isNotEmpty()) item(key = "app_groups_header", contentType = "header") {
                SelectionHeading(stringResource(R.string.favorites_shortcuts_app_group), "${tag}_app_groups")
            }
            if (onAddShortcut != null) {
                item(key = "add_shortcut") {
                    AddSelectionContent("${tag}_add_shortcut", enabled, onAddShortcut,
                        stringResource(R.string.favorites_add_shortcut))
                }
            }
            if (onAddFolder != null) {
                item(key = "add_folder") {
                    AddSelectionContent("${tag}_add_folder", enabled, onAddFolder,
                        stringResource(R.string.favorites_add_folder))
                }
            }
            if (onAddStack != null) {
                item(key = "add_stack") {
                    AddSelectionContent("${tag}_add_stack", enabled, onAddStack,
                        stringResource(R.string.favorites_add_app_stack))
                }
            }
            appGroupChoices.forEach { choice ->
                item(key = "app_group:${choice.entry.key}") {
                    SelectionChoice(choice.entry, choice.checked, "${tag}_group:${choice.entry.key}",
                        enabled && choice.enabled && reorder.draggingKey == null,
                        { choice.entry.app?.let(onToggle) })
                }
            }
            if (!reorderOnly) item(key = "all_header", contentType = "header") {
                SelectionHeading(stringResource(R.string.favorites_all_apps), "${tag}_all_apps")
            }
        }
    }
}

@Composable
private fun AddSelectionContent(tag: String, enabled: Boolean, onClick: () -> Unit,
    label: String = stringResource(R.string.popup_add_new)) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag(tag).clip(RoundedCornerShape(16.dp))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { LauncherIcon(LauncherSymbol.Plus) }
        }
        Spacer(Modifier.width(16.dp))
        Text(label)
    }
}

@Composable
private fun SelectionHeading(text: String, tag: String) {
    Text(text, Modifier.fillMaxWidth().testTag(tag).semantics { heading() }.padding(horizontal = 8.dp, vertical = 16.dp),
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SelectionChoice(entry: SelectionEntry, checked: Boolean, tag: String, enabled: Boolean, onToggle: () -> Unit,
    modifier: Modifier = Modifier, selectable: Boolean = true, trailing: @Composable () -> Unit = {}) {
    AppSelectionRow(entry.label, checked, onToggle, modifier.testTag(tag), enabled = enabled,
        selectable = selectable, trailing = trailing, icon = {
            if (entry.graceWidget != null) LauncherIcon(entry.graceWidget.symbol())
            else if (entry.app != null) AppIcon(entry.app, size = 36.dp)
            else LauncherIcon(LauncherSymbol.Apps)
        })
}

@Composable
private fun SelectedWidgetPreview(layout: HomeLayout) {
    val context = LocalContext.current
    val info = remember(layout.widgetId) { runCatching { AppWidgetManager.getInstance(context).getAppWidgetInfo(layout.widgetId) }.getOrNull() }
    if (info != null) WidgetPreview(info, null, Modifier.fillMaxWidth().height(160.dp).padding(start = 48.dp, end = 16.dp, bottom = 12.dp))
    else Text(stringResource(R.string.widget_preview_unavailable), Modifier.padding(start = 48.dp, bottom = 16.dp), style = MaterialTheme.typography.bodySmall)
}
