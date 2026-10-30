package com.galaxyrio.gracelauncher.ui.overlays

import androidx.compose.animation.core.Animatable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import android.appwidget.AppWidgetManager
import android.content.Intent
import com.galaxyrio.gracelauncher.WidgetSetupActivity
import com.galaxyrio.gracelauncher.data.widgetScreenOwner
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.IconDisplayStyle
import com.galaxyrio.gracelauncher.data.HomeLayout
import com.galaxyrio.gracelauncher.data.HomeContentKey
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.LauncherFolder
import com.galaxyrio.gracelauncher.data.PrivateSpaceFolderId
import com.galaxyrio.gracelauncher.data.ShortcutResult
import com.galaxyrio.gracelauncher.data.ShortcutStatus
import com.galaxyrio.gracelauncher.data.notifications.AppNotification
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.AppIcon
import com.galaxyrio.gracelauncher.ui.components.AppNotificationLabel
import com.galaxyrio.gracelauncher.ui.components.AppStack
import com.galaxyrio.gracelauncher.ui.components.LauncherIcon
import com.galaxyrio.gracelauncher.ui.components.LauncherSymbol
import com.galaxyrio.gracelauncher.ui.components.LocalListAppearance
import com.galaxyrio.gracelauncher.ui.theme.LocalLauncherAppearance
import com.galaxyrio.gracelauncher.ui.theme.effectiveDisplay
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

@Stable
class ShortcutRevealState(expanded: Boolean = true, dragging: Boolean = false, val animationsEnabled: Boolean = true) {
    var dragging by mutableStateOf(dragging)
    var expanded by mutableStateOf(expanded)
}

/** Folders differ only in their owner and initial contents, not their popup UI. */
@Composable
fun FolderPopup(
    folder: LauncherFolder, apps: List<LauncherApp>, anchor: Rect, onDismiss: () -> Unit,
    onLaunchApp: (LauncherApp, Rect) -> Unit, onEdit: (() -> Unit)?,
    reveal: ShortcutRevealState = remember { ShortcutRevealState() },
    uiState: LauncherUiState = LauncherUiState(apps = apps, folders = listOf(folder)),
    actions: LauncherActions = LauncherActions(), onDetails: (LauncherApp) -> Unit = {},
    showNames: Boolean = true,
    onOpenAppPopup: ((LauncherApp, Rect) -> Unit)? = null,
) = ShortcutPopup(
    app = uiState.folderItem(folder), anchor = anchor, hasAccess = uiState.hasShortcutAccess,
    actions = actions, onLaunchApp = {}, onDismiss = onDismiss, reveal = reveal,
    uiState = uiState, onEdit = onEdit, onDetails = onDetails, onLaunchItem = onLaunchApp,
    showNames = showNames, onOpenAppPopup = onOpenAppPopup,
)

/** Same-window overlay: adding a popup window during DOWN would cancel the row's drag. */
@Composable
fun ShortcutPopup(
    app: LauncherApp,
    anchor: Rect,
    hasAccess: Boolean,
    actions: LauncherActions,
    onLaunchApp: () -> Unit,
    onDismiss: () -> Unit,
    reveal: ShortcutRevealState = remember { ShortcutRevealState() },
    notifications: List<AppNotification> = emptyList(),
    uiState: LauncherUiState = LauncherUiState(),
    onEdit: (() -> Unit)? = {},
    onDetails: (LauncherApp) -> Unit = {},
    showNames: Boolean = true,
    onOpenAppPopup: ((LauncherApp, Rect) -> Unit)? = null,
    onLaunchItem: (LauncherApp, Rect) -> Unit = { item, bounds ->
        item.shortcut?.let { actions.launchShortcutAt?.invoke(it, bounds) ?: actions.launchShortcut(it) }
    },
) {
    val isFolder = app.folderId != null
    val context = LocalContext.current
    val offeredWidgets = remember(app.packageName) {
        if (isFolder || app.shortcut != null) emptyList() else runCatching {
            AppWidgetManager.getInstance(context).installedProviders.filter { it.provider.packageName == app.packageName }
        }.getOrDefault(emptyList())
    }
    var selectedWidgetProvider by remember(app.key) { mutableStateOf<android.appwidget.AppWidgetProviderInfo?>(null) }
    var chooseWidgetDestination by remember(app.key) { mutableStateOf(false) }
    val listAppearance = LocalListAppearance.current
    val isPrivateFolder = app.folderId == PrivateSpaceFolderId
    var retry by remember { mutableIntStateOf(0) }
    val loadShortcuts by rememberUpdatedState(actions.shortcuts)
    val initialResult = remember(app.key, hasAccess) {
        if (isFolder) ShortcutResult(ShortcutStatus.Ready) else actions.cachedShortcuts(app) ?: ShortcutResult(ShortcutStatus.Loading)
    }
    val result by produceState(initialResult, app, hasAccess, retry, uiState.itemRevision, uiState.settings) {
        // Keep cached content while refreshing; never insert a progress indicator.
        if (!isFolder) value = loadShortcuts(app)
    }
    val shortcuts = result.asApps(app).map { uiState.findItem(it.key) ?: it }
    val customItems = uiState.popups[app.key]
    val entries = uiState.popupItems(app, shortcuts)
    val list = rememberLazyListState()
    var editingWidget by remember(app.key) { mutableStateOf<String?>(null) }
    var resizingWidget by remember(app.key) { mutableStateOf(false) }
    var widgetDragMoved by remember(app.key) { mutableStateOf(false) }
    val drafts = remember(app.key) { mutableStateMapOf<String, HomeLayout>() }
    val updateWidget: (String, HomeLayout, Boolean) -> Unit = { key, layout, save ->
        drafts[key] = layout
        if (save) actions.updatePopup(app, shortcuts) { current ->
            current.map { if (it.key == key && it.widget != null) it.copy(widget = layout) else it }
        }
    }
    val saveWidget by rememberUpdatedState(updateWidget)
    val saveOrder by rememberUpdatedState<(List<String>) -> Unit>({ keys ->
        actions.updatePopup(app, shortcuts) { current ->
            val byKey = current.associateBy { it.key }
            keys.mapNotNull(byKey::get) + current.filterNot { it.key in keys }
        }
    })
    val haptics by rememberUpdatedState(LocalHapticFeedback.current)
    val reorder = remember(app.key, list) {
        FavoritesReorderState(list, entries.map { it.key }, { saveOrder(it) }) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    LaunchedEffect(entries) {
        reorder.synchronize(entries.map { it.key })
        drafts.keys.toList().forEach { key ->
            if (entries.none { it.key == key } || entries.firstOrNull { it.key == key }?.widget == drafts[key]) drafts.remove(key)
        }
        if (entries.none { it.key == editingWidget && it.widget != null }) editingWidget = null
    }
    val density = LocalDensity.current
    LaunchedEffect(reorder.draggingKey, density) {
        if (reorder.draggingKey == null) return@LaunchedEffect
        val edge = with(density) { 48.dp.toPx() }
        val speed = with(density) { 480.dp.toPx() }
        var previous = withFrameNanos { it }
        while (isActive && reorder.draggingKey != null) {
            val frame = withFrameNanos { it }
            if (widgetDragMoved) reorder.autoScroll(edge, speed * ((frame - previous) / 1_000_000_000f).coerceIn(0f, .032f))
            previous = frame
        }
    }
    val finishEditing = {
        editingWidget?.let { key -> drafts[key]?.let { saveWidget(key, it, true) } }
        editingWidget = null
    }
    BackHandler(enabled = editingWidget != null) { finishEditing() }
    val selectedLayout = editingWidget?.let { key -> drafts[key] ?: entries.firstOrNull { it.key == key }?.widget }
    val entriesByKey = entries.associateBy { it.key }
    SwipeRevealPanel(
        anchor = anchor,
        reveal = reveal,
        panelTag = if (isFolder) "folder_popup" else "shortcut_popup",
        title = if (isFolder) app.label else stringResource(R.string.app_shortcuts),
        onDismiss = onDismiss,
        edgeToEdgeContent = true,
    ) { maxListHeight ->
        if (selectedLayout != null) PopupWidgetControls(selectedLayout,
            onChange = { layout, save -> editingWidget?.let { updateWidget(it, layout, save) } },
            onDone = finishEditing)
        else {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp)
                .heightIn(min = 48.dp)
                .testTag("shortcut_header")
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(enabled = !isFolder || onEdit != null,
                    onLongClick = { onDetails(app) }, onClick = { if (isFolder) onDetails(app) else onLaunchApp() })
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LauncherIcon(LauncherSymbol.Outbound, Modifier.size(19.dp))
            Spacer(Modifier.width(10.dp))
            Text(app.label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (notifications.isNotEmpty()) TextButton(
                onClick = { notifications.forEach { actions.dismissNotification(it.key, it.revision) } },
                modifier = Modifier.sizeIn(minWidth = 40.dp).testTag("dismiss_all_notifications"),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) { Text("×", fontSize = 24.sp, lineHeight = 24.sp, color = MaterialTheme.colorScheme.onSurface) }
        }
        }
        LazyColumn(Modifier.heightIn(max = maxListHeight).testTag(if (isFolder) "folder_members" else "shortcut_list"),
            state = list, userScrollEnabled = reorder.draggingKey == null && !resizingWidget) {
            items(notifications, key = { "notification:${it.key}" }) { notification ->
                NotificationPopupItem(
                    app, notification,
                    onOpen = { if (actions.openNotification(notification.key, notification.revision)) onDismiss() },
                    onDismiss = { actions.dismissNotification(notification.key, notification.revision) },
                    modifier = Modifier.animateItem().padding(horizontal = 6.dp),
                    gesturesEnabled = !reveal.dragging && reveal.expanded,
                    dismissResistance = uiState.settings.dismissResistance,
                    dismissThresholdDp = uiState.settings.dismissThresholdDp,
                )
            }
        if (isFolder && entries.isEmpty()) item {
            if (isPrivateFolder && uiState.privateAppsLoading) {
                Text(stringResource(R.string.private_space_loading), Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
            } else if (isPrivateFolder) {
                Text(stringResource(if (uiState.privateAppsFailed) R.string.private_space_unavailable else R.string.private_space_empty),
                    Modifier.testTag("private_space_empty").padding(horizontal = 12.dp, vertical = 18.dp), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = if (uiState.privateAppsFailed) ({ actions.requestPrivateSpace(true, actions.refreshApps) }) else actions.openPrivateSpaceSettings) {
                    Text(stringResource(if (uiState.privateAppsFailed) R.string.retry else R.string.private_space_setup))
                }
            } else if (app.folderId == com.galaxyrio.gracelauncher.data.WorkProfileFolderId) {
                Text(stringResource(R.string.work_profile_no_apps), Modifier.padding(horizontal = 12.dp, vertical = 18.dp), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = actions.openWorkProfileSettings) { Text(stringResource(R.string.work_profile_setup)) }
            } else {
                Text(stringResource(R.string.folder_contents_empty), Modifier.testTag("folder_empty").padding(horizontal = 12.dp, vertical = 18.dp), style = MaterialTheme.typography.bodyMedium)
                if (onEdit != null) TextButton(onClick = onEdit, modifier = Modifier.testTag("folder_edit")) { Text(stringResource(R.string.settings_folder_edit)) }
            }
        }
        if (!isFolder && customItems == null) when (result.status) {
            // Cold apps may need one binder query; keep the layout quiet.
            ShortcutStatus.Loading -> if (notifications.isEmpty()) item { Spacer(Modifier.height(56.dp)) }
            ShortcutStatus.DefaultLauncherRequired -> item {
                Column {
                Text(stringResource(R.string.shortcut_permission), Modifier.padding(horizontal = 12.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = actions.requestDefaultHome) { Text(stringResource(R.string.set_default_launcher)) }
                }
            }
            ShortcutStatus.Error -> item {
                Column {
                Text(stringResource(R.string.shortcut_error), Modifier.padding(horizontal = 12.dp, vertical = 12.dp))
                TextButton(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
                }
            }
            ShortcutStatus.Ready -> {
                if (result.shortcuts.isEmpty() && notifications.isEmpty()) item {
                    Text(stringResource(R.string.no_shortcuts), Modifier.padding(horizontal = 12.dp, vertical = 18.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        items(reorder.keys.mapNotNull(entriesByKey::get), key = { FavoritesReorderState.itemKey(it.key) }) { entry ->
            val widget = drafts[entry.key] ?: entry.widget
            val dragging = reorder.draggingKey == entry.key
            val offset by rememberReorderTranslation(reorder, entry.key)
            val itemApp = uiState.findItem(entry.key) ?: shortcuts.firstOrNull { it.key == entry.key }



            val stack = uiState.folders.firstOrNull { it.id == itemApp?.folderId && it.isAppStack }
            Box(Modifier.fillMaxWidth().then(if (dragging) Modifier else Modifier.animateItem())
                .zIndex(if (dragging || reorder.drop?.key == entry.key) 1f else 0f)
                .graphicsLayer { translationY = offset }
                .padding(horizontal = if (widget == null) 6.dp else 0.dp)) {
            when {
                stack != null -> AppStack(stack, uiState, onLaunch = { item, bounds ->
                    onLaunchItem(item, bounds); if (!isPrivateFolder) onDismiss()
                }, onDetails = onDetails, onEdit = { onDetails(uiState.folderItem(stack)) },
                    enabled = !reveal.dragging && reveal.expanded,
                    iconSize = 36.dp, onWallpaper = false,
                    modifier = Modifier.padding(horizontal = (12f - com.galaxyrio.gracelauncher.data.AppStackInset).dp, vertical = 4.dp))
                widget != null -> {
                    PopupWidgetItem(widget, selected = editingWidget == entry.key,
                        enabled = !reveal.dragging && reveal.expanded && !uiState.isLoadingSettings && !uiState.settingsLoadFailed,
                        haptics = uiState.settings.allowHapticFeedback,
                        maxHeight = (maxListHeight.value - 16).roundToInt(),
                        onChange = { layout, save -> updateWidget(entry.key, layout, save) },
                        onResizing = { resizingWidget = it },
                        onDragStart = { widgetDragMoved = false; reorder.start(entry.key) },
                        onDrag = { delta -> if (delta != 0f) widgetDragMoved = true; reorder.drag(delta) },
                        onDragEnd = { cancelled ->
                            reorder.finish(cancelled)
                            if (!cancelled) editingWidget = entry.key
                        })
                }
                itemApp != null -> {
                        var iconBounds by remember { mutableStateOf(Rect.Zero) }
                        val itemDisplay = itemApp.effectiveDisplay(LocalLauncherAppearance.current)
                        val hiddenIcon = itemDisplay.style == IconDisplayStyle.Hidden
                        val notification = uiState.ownNotificationsFor(itemApp, expanded = true).firstOrNull()
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(if (isFolder) "folder_app:${itemApp.key}" else "shortcut:${itemApp.shortcut?.id ?: itemApp.key}")
                                .semantics { if (!showNames && !hiddenIcon) contentDescription = itemApp.label }
                                .clip(RoundedCornerShape(16.dp))
                                .combinedClickable(enabled = !reveal.dragging && reveal.expanded, onLongClick = { onDetails(itemApp) }, onClick = {
                                    onLaunchItem(itemApp, iconBounds)
                                    // A folder replaces this panel with its own hero transition.
                                    if (!isPrivateFolder && itemApp.folderId == null) onDismiss()
                                }).padding(horizontal = 12.dp, vertical = (listAppearance.appSpacing / 2f).dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (!hiddenIcon) {
                                AppIcon(itemApp, Modifier.onGloballyPositioned { iconBounds = it.boundsInWindow() },
                                    size = 36.dp, display = itemDisplay)
                                if (showNames || notification != null) {
                                    Spacer(Modifier.width(listAppearance.iconNameGap.dp))
                                }
                            }
                            AppNotificationLabel(itemApp.label, listOfNotNull(notification), showNames || hiddenIcon,
                                modifier = Modifier.weight(1f).then(if (hiddenIcon)
                                    Modifier.onGloballyPositioned { iconBounds = it.boundsInWindow() } else Modifier),
                                onWallpaper = false, maxLines = 2)
                            if (notification != null && onOpenAppPopup != null) IconButton(
                                onClick = { onOpenAppPopup(itemApp, iconBounds) },
                                enabled = !reveal.dragging && reveal.expanded,
                                modifier = Modifier.size(48.dp).testTag("popup_notification_arrow:${itemApp.key}")
                                    .semantics { contentDescription = itemApp.label },
                            ) {
                                LauncherIcon(LauncherSymbol.Chevron,
                                    Modifier.rotate(if (LocalLayoutDirection.current == LayoutDirection.Ltr) -90f else 90f))
                            }
                        }
                }
                else -> Text(stringResource(R.string.popup_item_unavailable), Modifier.fillMaxWidth().clickable(enabled = onEdit != null, onClick = { onEdit?.invoke() }).padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            }
        }
        if (offeredWidgets.isNotEmpty()) {
            item(key = "available_widgets_header") {
                Text("Widgets", Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(offeredWidgets, key = { "provider:${it.provider.flattenToString()}" }) { provider ->
                Row(Modifier.fillMaxWidth().clickable(enabled = !reveal.dragging && reveal.expanded) {
                    selectedWidgetProvider = provider; chooseWidgetDestination = true
                }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(runCatching { provider.loadLabel(context.packageManager).toString() }.getOrDefault(app.label),
                        Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text("＋", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        }
    }
    if (chooseWidgetDestination && selectedWidgetProvider != null) {
        AlertDialog(
            onDismissRequest = { chooseWidgetDestination = false },
            title = { Text("Add widget") },
            text = { Text("Where would you like to add this widget?") },
            confirmButton = {
                TextButton(onClick = {
                    val provider = selectedWidgetProvider
                    chooseWidgetDestination = false
                    if (provider != null) context.startActivity(Intent(context, WidgetSetupActivity::class.java)
                        .putExtra(WidgetSetupActivity.EXTRA_POPUP_OWNER, HomeContentKey)
                        .putExtra(WidgetSetupActivity.EXTRA_PROVIDER, provider.provider.flattenToString())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("Home widget stack") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        val provider = selectedWidgetProvider
                        chooseWidgetDestination = false
                        if (provider != null) context.startActivity(Intent(context, WidgetSetupActivity::class.java)
                            .putExtra(WidgetSetupActivity.EXTRA_POPUP_OWNER, widgetScreenOwner(context.getString(R.string.widget_screen_title)).key)
                            .putExtra(WidgetSetupActivity.EXTRA_PROVIDER, provider.provider.flattenToString())
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }) { Text("Widget screen") }
                    TextButton(onClick = { chooseWidgetDestination = false }) { Text("Cancel") }
                }
            },
        )
    }
}

/** One same-window reveal/positioning implementation for app shortcuts and folders. */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun SwipeRevealPanel(
    anchor: Rect,
    reveal: ShortcutRevealState,
    panelTag: String,
    title: String,
    onDismiss: () -> Unit,
    edgeToEdgeContent: Boolean = false,
    content: @Composable ColumnScope.(maxListHeight: Dp) -> Unit,
) {
    val windowSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val margin = with(density) { 32.dp.roundToPx() }
    val maxListHeight = with(density) { windowSize.height.toDp() } * 0.55f
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val haptics by rememberUpdatedState(LocalHapticFeedback.current)
    var feedbackExpanded by remember(reveal) { mutableStateOf(false) }
    LaunchedEffect(reveal) {
        snapshotFlow { reveal.expanded }.collect { expanded ->
            if (expanded != feedbackExpanded) {
                haptics.performHapticFeedback(if (expanded) HapticFeedbackType.GestureThresholdActivate else HapticFeedbackType.GestureEnd)
                feedbackExpanded = expanded
            }
        }
    }
    DisposableEffect(reveal) {
        // Back, launching an item, and opening its details can remove the panel
        // directly. A swipe/tap close has already emitted its feedback above.
        onDispose { if (feedbackExpanded) haptics.performHapticFeedback(HapticFeedbackType.GestureEnd) }
    }
    val spatial = remember(reveal) { Animatable(if (!reveal.animationsEnabled && reveal.expanded) 1f else 0f) }
    val effects = remember(reveal) { Animatable(spatial.value) }
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    LaunchedEffect(reveal, spatialSpec, effectsSpec) {
        snapshotFlow { reveal.expanded }.collect { expanded ->
            val destination = if (expanded) 1f else 0f
            // Let Animatable interrupt its previous animateTo. Cancelling the
            // owning effect on every target change would reset its velocity.
            launch { if (reveal.animationsEnabled) effects.animateTo(destination, effectsSpec) else effects.snapTo(destination) }
            launch { if (reveal.animationsEnabled) spatial.animateTo(destination, spatialSpec) else spatial.snapTo(destination) }
        }
    }
    val finishedClosing = !reveal.dragging && !reveal.expanded && !spatial.isRunning && spatial.value == 0f
    LaunchedEffect(reveal, finishedClosing) {
        // Keep the original gesture alive at zero so a rightward reversal can
        // reopen the same panel without lifting the finger.
        if (finishedClosing) currentOnDismiss()
    }
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.boundsInWindow().topLeft }) {
        Box(Modifier.matchParentSize().clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null,
            onClick = { reveal.expanded = false },
        ).clearAndSetSemantics {})
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                LauncherPanelTheme {
                    Surface(
                        modifier = Modifier.testTag(panelTag).semantics {
                            paneTitle = title
                            dismiss { reveal.expanded = false; true }
                            progressBarRangeInfo = ProgressBarRangeInfo(spatial.value.coerceIn(0f, 1f), 0f..1f)
                        }.graphicsLayer { alpha = (spatial.value * 12f).coerceIn(0f, 1f) },
                        shape = PopupContainerShape(anchor, spatial.value),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 16.dp * spatial.value.coerceIn(0f, 1f),
                    ) {}
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                        Column(Modifier.padding(horizontal = if (edgeToEdgeContent) 0.dp else 6.dp, vertical = 12.dp)) {
                            content(maxListHeight)
                        }
                    }
                }
            },
        ) { measurables, constraints ->
            val panelWidth = (constraints.maxWidth - margin * 2).coerceIn(1, 440.dp.roundToPx())
            // Measure content at its final size: only the container/clip morphs,
            // so neither text nor icons are stretched during the hero transition.
            val panel = measurables[1].measure(Constraints(
                minWidth = panelWidth, maxWidth = panelWidth,
                maxHeight = (constraints.maxHeight - margin * 2).coerceAtLeast(1),
            ))
            val left = (constraints.maxWidth - panel.width) / 2
            val top = (anchor.center.y - origin.y - panel.height / 2f).roundToInt()
                .coerceIn(margin, (constraints.maxHeight - panel.height - margin).coerceAtLeast(margin))
            val target = Rect(left.toFloat(), top.toFloat(), (left + panel.width).toFloat(), (top + panel.height).toFloat())
            val frame = popupMorphFrame(anchor.translate(-origin), target, spatial.value, 24.dp.toPx())
            val container = measurables[0].measure(Constraints.fixed(
                frame.bounds.width.roundToInt().coerceAtLeast(1), frame.bounds.height.roundToInt().coerceAtLeast(1),
            ))
            layout(constraints.maxWidth, constraints.maxHeight) {
                container.place(frame.bounds.left.roundToInt(), frame.bounds.top.roundToInt())
                panel.placeWithLayer(left, top) {
                    shape = PopupContentClip(frame.bounds.translate(-target.topLeft), frame.radius)
                    clip = true
                    alpha = effects.value * ((spatial.value - 0.15f) / 0.85f).coerceIn(0f, 1f)
                }
            }
        }
    }
}

/** The source is always circular, regardless of the actual icon pack's outline. */
private class PopupContainerShape(private val anchor: Rect, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val start = minOf(anchor.width, anchor.height).coerceAtLeast(1f) / 2f
        val radius = androidx.compose.ui.util.lerp(start, with(density) { 24.dp.toPx() }, progress.coerceAtLeast(0f))
            .coerceIn(0f, size.minDimension / 2f)
        return Outline.Rounded(RoundRect(Rect(Offset.Zero, size), CornerRadius(radius)))
    }
}

private class PopupContentClip(private val bounds: Rect, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(bounds, CornerRadius(radius)))
}
