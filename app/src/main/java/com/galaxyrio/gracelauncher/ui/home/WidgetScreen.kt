package com.galaxyrio.gracelauncher.ui.home

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.semantics.scrollBy as semanticsScrollBy
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.*
import com.galaxyrio.gracelauncher.platform.widthBounds
import com.galaxyrio.gracelauncher.platform.heightBounds
import com.galaxyrio.gracelauncher.platform.widgetTargetSize
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.*
import com.galaxyrio.gracelauncher.ui.theme.LocalLauncherAppearance
import com.galaxyrio.gracelauncher.ui.theme.effectiveDisplay
import com.galaxyrio.gracelauncher.ui.widgets.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.roundToInt

/** An unbounded grid with a stationary touch surface and viewport-only composition. */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun WidgetScreen(
    uiState: LauncherUiState, actions: LauncherActions, topInset: Dp, bottomInset: Dp,
    editing: Boolean, enabled: Boolean, onEditingChange: (Boolean) -> Unit, onEdit: () -> Unit,
    onLaunchApp: (LauncherApp) -> Unit, onAppDetails: (LauncherApp) -> Unit,
    onOpenFolder: (LauncherFolder, Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.widget_screen_title)
    val owner = remember(title) { widgetScreenOwner(title) }
    // Keep disabled/unavailable profiles saved, but do not expose their launchers.
    val entries = uiState.popupItems(owner, emptyList()).filter {
        (it.key != PrivateSpaceFolderKey || uiState.canFavoritePrivateSpace) &&
            (it.key != WorkProfileFolderKey || uiState.canFavoriteWorkProfile)
    }
    val byKey = entries.associateBy { it.key }
    val stacks = uiState.folders.filter { it.isAppStack && it.key in byKey }.associateBy { it.key }
    val currentStackKeys by rememberUpdatedState(stacks.keys)
    var expandedStacks by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val widgetKeys = entries.filter { it.widget != null }.map { it.key }.toSet()
    val inputEnabled = enabled && !uiState.isLoadingSettings && !uiState.settingsLoadFailed
    val editable = inputEnabled && !uiState.settings.widgetScreen.editingLocked
    val currentInputEnabled by rememberUpdatedState(inputEnabled)
    val currentEditable by rememberUpdatedState(editable)
    val currentEditingChange by rememberUpdatedState(onEditingChange)
    val currentEdit by rememberUpdatedState(onEdit)
    val currentWidgetKeys by rememberUpdatedState(widgetKeys)
    val persist by rememberUpdatedState<(List<WidgetGridCell>, String?) -> Unit>({ cells, removed ->
        actions.updatePopup(owner, emptyList()) { current ->
            val positions = collapseWidgetStacks(cells, currentStackKeys).associateBy { it.key }
            current.filterNot { it.key == removed }.map { entry ->
                positions[entry.key]?.let { entry.copy(grid = it.placement()) } ?: entry
            }.sortedWith(compareBy({ it.grid?.row ?: Int.MAX_VALUE }, { it.grid?.column ?: Int.MAX_VALUE }))
        }
    })
    val grid = remember { WidgetGridState({ cells, removed -> persist(cells, removed) }, { currentEditingChange(it) }) }
    LaunchedEffect(editing, enabled, editable) {
        if (!editing || !editable) grid.cancelInteraction()
        if (!enabled) grid.scrolling.stopScroll()
    }
    DisposableEffect(grid) { onDispose { grid.cancelInteraction() } }
    val appearance = LocalLauncherAppearance.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val currentHaptics by rememberUpdatedState(haptics)
    val iconCoordinates = remember { mutableMapOf<String, LayoutCoordinates>() }
    val launch by rememberUpdatedState<(String) -> Unit>({ key ->
        uiState.findItem(key)?.let { app ->
            val bounds = iconCoordinates[key]?.takeIf { it.isAttached }?.boundsInWindow()
            if (app.folderId != null) {
                val folder = when (app.folderId) {
                    PrivateSpaceFolderId -> uiState.privateFolder
                    WorkProfileFolderId -> uiState.workFolder
                    else -> uiState.folders.firstOrNull { it.id == app.folderId }
                }
                if (folder != null) onOpenFolder(folder, bounds ?: Rect.Zero)
            } else if (bounds != null && actions.launchAppAt != null) actions.launchAppAt.invoke(app, bounds) else onLaunchApp(app)
        }
    })
    val startDrag: (String, Offset) -> Unit = { key, pointer ->
        if (currentEditable && !grid.busy) {
            grid.startDrag(key, pointer)
            currentHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    val currentStart by rememberUpdatedState(startDrag)
    val removeLabel = stringResource(R.string.widget_remove)
    val resizeLabel = stringResource(R.string.widget_resize)
    val previousLabel = stringResource(R.string.favorites_move_up)
    val nextLabel = stringResource(R.string.favorites_move_down)
    val infoLabel = stringResource(R.string.app_info)
    val editLabel = stringResource(R.string.widget_screen_edit)

    BoxWithConstraints(modifier.fillMaxSize().clipToBounds().testTag("widget_screen")) {
        val density = LocalDensity.current
        val context = LocalContext.current
        val columns = uiState.settings.widgetScreen.columns.coerceIn(2, 8)
        // Symmetric page margins; the alphabet is an overlay, not reserved grid space.
        val side = 16.dp
        val gap = 8.dp
        val cellWidth = ((maxWidth - side * 2) / columns).coerceAtLeast(1.dp)
        val cellHeight = cellWidth.coerceIn(88.dp, 112.dp)
        val widthPx = with(density) { cellWidth.toPx() }
        val heightPx = with(density) { cellHeight.toPx() }
        val gapPx = with(density) { gap.toPx() }
        val origin = with(density) { Offset(side.toPx(), (topInset + 12.dp).toPx()) }
        val viewportPx = with(density) { maxHeight.toPx() }
        val bottomPx = with(density) { bottomInset.toPx() + 12.dp.toPx() }
        val deleteWidth = 180.dp.coerceAtMost(maxWidth - 32.dp)
        val deleteLeft = (maxWidth - deleteWidth) / 2
        val deleteTop = topInset + 8.dp
        val deleteRect = with(density) {
            Rect(deleteLeft.toPx(), deleteTop.toPx(), (deleteLeft + deleteWidth).toPx(), (deleteTop + 56.dp).toPx())
        }
        val widgetRevision = LocalWidgetHost.current?.revision
        val specs = remember(entries.map { it.key to it.widget }, columns, widthPx, heightPx, gapPx, widgetRevision) {
            val manager = AppWidgetManager.getInstance(context)
            entries.filter { it.widget != null }.associate { entry ->
                val info = runCatching { manager.getAppWidgetInfo(entry.widget!!.widgetId) }.getOrNull()
                val padding = info?.let { AppWidgetHostView.getDefaultPaddingForWidget(context, it.provider, null) }
                entry.key to widgetGridSpec(info, columns, widthPx, heightPx,
                    gapPx + (padding?.left ?: 0) + (padding?.right ?: 0),
                    gapPx + (padding?.top ?: 0) + (padding?.bottom ?: 0))
            }
        }
        val stackIconSize = 40f * appearance.iconSize / 100f
        val stackColumns = appStackColumns((maxWidth - side * 2 - gap).value, stackIconSize)
        val stackRows = stacks.mapValues { (key, stack) ->
            val count = uiState.popupItems(uiState.folderItem(stack), emptyList()).count { it.widget == null && uiState.findItem(it.key) != null }
            ceil((appStackHeight(appStackRows(count, stackColumns, key in expandedStacks), stackIconSize) + gap.value) / cellHeight.value).toInt().coerceAtLeast(1)
        }
        val baseCells = remember(entries, columns, specs, stacks.keys) {
            restoreWidgetGrid(entries, columns, specs.mapValues { it.value.first } + stacks.keys.associateWith { WidgetGridSize(columns, 1) })
        }
        val resolved = remember(baseCells, stackRows) { expandWidgetStacks(baseCells, stackRows) }
        LaunchedEffect(resolved, entries, uiState.isLoadingSettings, uiState.settingsLoadFailed) {
            if (!grid.busy) {
                grid.synchronize(resolved)
                if (!uiState.isLoadingSettings && !uiState.settingsLoadFailed &&
                    baseCells.any { byKey[it.key]?.grid != it.placement() }) persist(resolved, null)
            }
        }
        SideEffect { grid.configure(columns, widthPx, heightPx, origin, viewportPx, bottomPx, deleteRect, widgetKeys) }
        LaunchedEffect(grid.draggingKey, heightPx) {
            if (grid.draggingKey != null) {
                var last = withFrameNanos { it }
                while (isActive && grid.draggingKey != null) {
                    val now = withFrameNanos { it }
                    val seconds = ((now - last) / 1_000_000_000f).coerceIn(0f, .032f)
                    last = now
                    grid.autoScroll(with(density) { 72.dp.toPx() }, with(density) { 640.dp.toPx() } * seconds)
                }
            }
        }
        LaunchedEffect(grid.overDelete) {
            if (grid.overDelete) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        Box(Modifier.fillMaxSize().testTag("widget_screen_grid")
            // Keep this node enabled during a held drag. Consumed pointer changes stop scrolling;
            // toggling/removing gesture modifiers here would cancel the same pointer stream.
            .scrollable(grid.scrolling, Orientation.Vertical, enabled = enabled)
            .widgetGridInput(grid, { currentInputEnabled }, { currentEditable }, { currentWidgetKeys }, { currentStackKeys }, { launch(it) },
                { key, point -> currentStart(key, point) }, { currentEdit() })
            .semantics {
                verticalScrollAxisRange = ScrollAxisRange({ grid.scroll }, { grid.maxScroll })
                customActions = listOf(CustomAccessibilityAction(editLabel) { if (inputEnabled) onEdit(); inputEnabled })
                semanticsScrollBy { _, y ->
                    if (!enabled || grid.busy) false else { scope.launch { grid.scrolling.scrollBy(-y) }; true }
                }
            }) {
            if (entries.isEmpty()) FilledTonalButton(onClick = onEdit, enabled = inputEnabled,
                modifier = Modifier.align(Alignment.Center).testTag("widget_screen_add")) {
                LauncherIcon(LauncherSymbol.Plus)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.popup_add_new))
            }
            // Render identity is independent of row order. In particular, moving a native
            // widget upward must not detach its AndroidView while it owns a touch stream.
            // Keep the key outside visibility branches so animation state survives culling.
            val renderCells = remember(grid.cells) { grid.cells.sortedBy { it.key } }
            renderCells.forEach { cell -> key(cell.key) {
                val entry = byKey[cell.key]
                val dragging = grid.draggingKey == cell.key
                val selected = grid.selectedKey == cell.key
                val destination = IntOffset((origin.x + cell.column * widthPx).roundToInt(), (origin.y + cell.row * heightPx).roundToInt())
                val dragPosition = grid.dragPosition + origin
                val target = if (dragging) IntOffset(dragPosition.x.roundToInt(), dragPosition.y.roundToInt()) else destination
                val animated by animateIntOffsetAsState(target,
                    if (dragging || (selected && grid.resizing)) snap() else MaterialTheme.motionScheme.defaultSpatialSpec(), label = "gridReflow")
                val scale by animateFloatAsState(if (dragging) 1.06f else 1f, spring(dampingRatio = .7f), label = "gridLift")
                val position = if (dragging) dragPosition else if (grid.resizing && selected)
                    Offset(destination.x.toFloat(), destination.y.toFloat()) else Offset(animated.x.toFloat(), animated.y.toFloat())
                val width = cellWidth * cell.size.columns
                val stackHeight by animateDpAsState(cellHeight * cell.size.rows,
                    MaterialTheme.motionScheme.defaultSpatialSpec(), label = "stackGridHeight")
                val height = if (cell.key in stacks) stackHeight else cellHeight * cell.size.rows
                val top = position.y - grid.scroll
                val destinationTop = destination.y - grid.scroll
                fun inViewport(y: Float) = y + cell.size.rows * heightPx >= -heightPx && y <= viewportPx + heightPx
                // Keep departing content until its animated position leaves the viewport,
                // and create incoming content at the start of its move, not at the endpoint.
                if (entry != null && (dragging || selected || inViewport(top) || inViewport(destinationTop))) {
                    val app = uiState.findItem(entry.key)
                    val hosted = entry.widget?.let { rememberHostedWidget(it) }
                    val label = app?.label ?: entry.widget?.widgetLabel ?: stringResource(R.string.popup_item_unavailable)
                    DisposableEffect(cell.key) { onDispose { iconCoordinates.remove(cell.key) } }
                    Box(Modifier.absoluteOffset { IntOffset(position.x.roundToInt(), (position.y - grid.scroll).roundToInt()) }
                        .wrapContentSize(Alignment.TopStart, unbounded = true).size(width, height)
                        .zIndex(if (dragging) 3f else if (selected) 2f else 0f)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .padding(gap / 2).testTag("widget_screen_item:${entry.key}")
                        .semantics {
                            contentDescription = label
                            if (app != null && cell.key !in stacks) onClick { if (enabled) launch(cell.key); enabled }
                            customActions = if (!editable) emptyList() else buildList {
                                add(CustomAccessibilityAction(previousLabel) { grid.moveBy(cell.key, -1); true })
                                add(CustomAccessibilityAction(nextLabel) { grid.moveBy(cell.key, 1); true })
                                add(CustomAccessibilityAction(removeLabel) { grid.remove(cell.key); true })
                                if (entry.widget != null) add(CustomAccessibilityAction(resizeLabel) { grid.selectWidget(cell.key); true })
                                if (app != null) add(CustomAccessibilityAction(infoLabel) { onAppDetails(app); true })
                            }
                        }) {
                        Box(Modifier.matchParentSize().clip(RoundedCornerShape(16.dp))
                            .background(if (dragging) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .3f) else Color.Transparent)) {
                            val stack = stacks[cell.key]
                            if (stack != null) AppStack(stack, uiState,
                                onLaunch = { item, bounds -> actions.launchAppAt?.invoke(item, bounds) ?: onLaunchApp(item) },
                                onDetails = onAppDetails, onEdit = { onAppDetails(uiState.folderItem(stack)) },
                                enabled = enabled && !grid.busy, gridManaged = true,
                                expanded = cell.key in expandedStacks,
                                onExpandedChange = { open -> expandedStacks = if (open) expandedStacks + cell.key else expandedStacks - cell.key },
                                modifier = Modifier.fillMaxWidth())
                            else if (entry.widget != null && hosted != null) {
                                HomeWidget(entry.widget, hosted, (height - gap).value.roundToInt(),
                                    editing = selected && editable, enabled = enabled, hapticsEnabled = false,
                                    editGesturesEnabled = editable,
                                    onLongPress = {},
                                    onDragStart = { x, y ->
                                        currentStart(cell.key, grid.viewportPosition(cell) + Offset(x + gapPx / 2, y + gapPx / 2))
                                    },
                                    onDrag = { x, y -> grid.drag(Offset(x, y)) }, onDragEnd = grid::finishDrag)
                            } else Column(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                if (app != null) AppIcon(app, Modifier.onGloballyPositioned { iconCoordinates[cell.key] = it },
                                    size = 44.dp.coerceAtMost((cellWidth - 16.dp).coerceAtLeast(16.dp)), display = app.effectiveDisplay(appearance))
                                else LauncherIcon(LauncherSymbol.Apps, tint = appearance.text)
                                Spacer(Modifier.height(6.dp))
                                Text(label, style = listLabelStyle().copy(fontSize = MaterialTheme.typography.labelMedium.fontSize),
                                    maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            }
                        }
                        if (editable && selected && entry.widget != null) {
                            // Hit-test above the native view; the stable viewport owns the next held drag.
                            Box(Modifier.matchParentSize().pointerInput(Unit) {
                                awaitPointerEventScope { while (true) awaitPointerEvent() }
                            })
                            val limits = specs[cell.key]?.second ?: WidgetResizeLimits(1, columns, 1, 20, true, true)
                            WidgetResizeFrame(grid, cell.key, limits, resizeLabel)
                        }
                    }
                }
            } }
        }
        val selectedWidget = byKey[grid.selectedKey]?.widget
        if (editable && !grid.busy && selectedWidget != null) {
            Row(Modifier.align(Alignment.TopCenter).padding(top = topInset + 8.dp).zIndex(5f)
                .clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                WidgetConfigurationButton(selectedWidget, onBeforeOpen = grid::deselect)
            }
        }
        val deleteAlpha by animateFloatAsState(if (grid.draggingKey != null) 1f else 0f, label = "gridDelete")
        val deleteScale by animateFloatAsState(if (grid.overDelete) 1.08f else 1f, spring(dampingRatio = .75f), label = "gridDeleteHover")
        if (editable && deleteAlpha > 0f) Row(
            Modifier.absoluteOffset(x = deleteLeft, y = deleteTop).size(deleteWidth, 56.dp).zIndex(5f)
                .graphicsLayer { alpha = deleteAlpha; scaleX = deleteScale; scaleY = deleteScale }
                .clip(CircleShape).background(if (grid.overDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceContainerHigh)
                .testTag("widget_screen_delete"),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            val color = if (grid.overDelete) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurface
            LauncherIcon(LauncherSymbol.Delete, tint = color)
            Spacer(Modifier.width(10.dp))
            Text(removeLabel, color = color, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Apps and blank space share one recognizer. No moving hit target or clickable can cancel a held drag. */
private fun Modifier.widgetGridInput(
    grid: WidgetGridState, enabled: () -> Boolean, canEdit: () -> Boolean, nativeWidgets: () -> Set<String>,
    interactiveItems: () -> Set<String>,
    onTap: (String) -> Unit, onHold: (String, Offset) -> Unit, onEmptyHold: () -> Unit,
) = pointerInput(grid) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!enabled() || down.isConsumed) return@awaitEachGesture
        val cell = grid.cellAt(down.position)
        val interactive = cell?.key in interactiveItems()
        // Native widgets retain button/collection interaction and report their own long press.
        if (cell != null && cell.key in nativeWidgets() && grid.selectedKey != cell.key) {
            if (grid.selectedKey != null) grid.deselect()
            return@awaitEachGesture
        }
        var released = false
        var cancelled = false
        var pointer = down.position
        val holdTimeout = if (cell?.key in nativeWidgets())
            com.galaxyrio.gracelauncher.platform.widgetLongPressTimeout(viewConfiguration.longPressTimeoutMillis)
        else viewConfiguration.longPressTimeoutMillis
        withTimeoutOrNull(holdTimeout) {
            while (true) {
                val event = awaitPointerEvent(if (interactive) PointerEventPass.Initial else PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || event.changes.count { it.pressed } > 1 || (!interactive && change.isConsumed) ||
                    (change.position - down.position).getDistance() > viewConfiguration.touchSlop || !enabled()) {
                    cancelled = true; break
                }
                pointer = change.position
                if (!change.pressed) { released = true; break }
                // A parent pager/scrollable may claim this movement after our Main pass.
                if (!interactive && awaitPointerEvent(PointerEventPass.Final).changes.any { it.isConsumed }) { cancelled = true; break }
            }
        }
        if (cancelled) return@awaitEachGesture
        if (released) {
            if (grid.selectedKey != null) grid.deselect()
            else if (cell != null && !interactive) onTap(cell.key)
        } else if (!canEdit()) {
            // Still recognize taps while locked; only the held editing path is blocked.
            return@awaitEachGesture
        } else if (cell == null) {
            grid.deselect()
            onEmptyHold()
        } else {
            onHold(cell.key, pointer)
            var completed = false
            try {
                while (grid.draggingKey == cell.key && enabled() && canEdit()) {
                    // Consume before the pager/native descendants; especially UP, even without a MOVE.
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (event.changes.count { it.pressed } > 1) break
                    grid.drag(change.position - pointer)
                    pointer = change.position
                    change.consume()
                    if (!change.pressed) { grid.finishDrag(false); completed = true; break }
                }
            } finally { if (!completed) grid.finishDrag(true) }
        }
    }
}

@Composable
private fun BoxScope.WidgetResizeFrame(grid: WidgetGridState, key: String, limits: WidgetResizeLimits, label: String) {
    val color = MaterialTheme.colorScheme.primary
    val latestLimits by rememberUpdatedState(limits)
    Box(Modifier.matchParentSize().border(2.dp, color, RoundedCornerShape(16.dp)))
    val edges = buildList {
        if (limits.horizontal && limits.minColumns < limits.maxColumns) { add(WidgetResizeEdge.Left); add(WidgetResizeEdge.Right) }
        if (limits.vertical && limits.minRows < limits.maxRows) { add(WidgetResizeEdge.Top); add(WidgetResizeEdge.Bottom) }
    }
    for (edge in edges) key(edge) {
        val alignment = when (edge) {
            WidgetResizeEdge.Left -> Alignment.CenterStart
            WidgetResizeEdge.Right -> Alignment.CenterEnd
            WidgetResizeEdge.Top -> Alignment.TopCenter
            WidgetResizeEdge.Bottom -> Alignment.BottomCenter
        }
        var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
        Box(Modifier.align(alignment).size(40.dp)
            .onGloballyPositioned { coordinates = it }
            .semantics { contentDescription = label }
            .testTag("widget_resize:${edge.name.lowercase()}")
            .pointerInput(grid, key, edge) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    grid.startResize(key)
                    var previous = coordinates?.localToRoot(down.position) ?: down.position
                    var completed = false
                    try {
                        while (grid.resizing) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (event.changes.count { it.pressed } > 1) break
                            val position = coordinates?.localToRoot(change.position) ?: change.position
                            grid.resize(edge, position - previous, latestLimits)
                            previous = position
                            change.consume()
                            if (!change.pressed) { grid.finishResize(false); completed = true; break }
                        }
                    } finally { if (!completed) grid.finishResize(true) }
                }
            }, contentAlignment = Alignment.Center) {
            Box(Modifier.size(20.dp).background(color, CircleShape).border(2.dp, MaterialTheme.colorScheme.surface, CircleShape))
        }
    }
}

/** Provider dimensions are pixels; convert once to grid spans, including the host's widget padding. */
private fun widgetGridSpec(info: AppWidgetProviderInfo?, columns: Int, width: Float, height: Float,
    horizontalPadding: Float, verticalPadding: Float): Pair<WidgetGridSize, WidgetResizeLimits> {
    if (info == null) return WidgetGridSize(2.coerceAtMost(columns), 2) to WidgetResizeLimits(1, columns, 1, 20, true, true)
    val x = info.widthBounds(horizontalPadding, width, columns)
    val y = info.heightBounds(verticalPadding, height, 20)
    val target = if (Build.VERSION.SDK_INT >= 31) widgetTargetSize(x, y, info.targetCellWidth, info.targetCellHeight)
        else x.initial to y.initial
    return WidgetGridSize(target.first, target.second) to WidgetResizeLimits(x.min, x.max, y.min, y.max, x.canResize, y.canResize)
}
