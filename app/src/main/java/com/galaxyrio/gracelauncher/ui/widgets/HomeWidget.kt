package com.galaxyrio.gracelauncher.ui.widgets

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.util.Log
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.HomeLayout
import com.galaxyrio.gracelauncher.platform.HomeWidgetHost
import com.galaxyrio.gracelauncher.platform.HomeWidgetHostView
import com.galaxyrio.gracelauncher.platform.heightBounds
import com.galaxyrio.gracelauncher.platform.widgetSizeOptions
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

internal data class HostedWidget(
    val host: HomeWidgetHost,
    val info: AppWidgetProviderInfo?,
    val defaultHeight: Int,
    val minHeight: Int,
    val maxHeight: Int,
)

@Stable
internal class WidgetHostState(val host: HomeWidgetHost) {
    var revision by mutableIntStateOf(0)
}

internal val LocalWidgetHost = staticCompositionLocalOf<WidgetHostState?> { null }

/** Only one listener per Activity/host ID, shared by HOME and pop-up widgets. */
@Composable
internal fun rememberWidgetHost(): WidgetHostState {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state = remember(context) { WidgetHostState(HomeWidgetHost(context)) }
    val host = state.host
    DisposableEffect(host, lifecycle) {
        fun start() { state.revision++; runCatching { host.startListening() } }
        host.onProvidersUpdated = { state.revision++ }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_STOP -> runCatching { host.stopListening() }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        onDispose { lifecycle.removeObserver(observer); host.onProvidersUpdated = null; runCatching { host.stopListening() } }
    }
    return state
}

@Composable
internal fun rememberHostedWidget(layout: HomeLayout): HostedWidget {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val state = LocalWidgetHost.current ?: rememberWidgetHost()
    val host = state.host
    val info = remember(layout.widgetId, layout.widgetProvider, state.revision) {
        runCatching { AppWidgetManager.getInstance(context).getAppWidgetInfo(layout.widgetId) }
            .getOrNull()?.takeIf { it.provider.flattenToString() == layout.widgetProvider }
    }
    return remember(host, info, density) {
        val padding = info?.let { AppWidgetHostView.getDefaultPaddingForWidget(context, it.provider, null) }
        val extra = (padding?.top ?: 0) + (padding?.bottom ?: 0)
        val bounds = info?.heightBounds(extra.toFloat(), density, 1600, 48)
        HostedWidget(host, info, bounds?.initial ?: 160, bounds?.min ?: 48, bounds?.max ?: 1600)
    }
}

@Composable
internal fun HomeWidget(
    layout: HomeLayout, widget: HostedWidget, height: Int, editing: Boolean,
    enabled: Boolean, hapticsEnabled: Boolean, onLongPress: () -> Unit,
    onDrag: ((Float, Float) -> Unit)? = null, onDragEnd: ((Boolean) -> Unit)? = null,
    onDragStart: ((Float, Float) -> Unit)? = null,
    contentScale: Float = 1f,
    removeHorizontalPadding: Boolean = false,
    editGesturesEnabled: Boolean = true,
) = WidgetLongPressConfiguration {
    val density = LocalDensity.current.density
    val latestLongPress by rememberUpdatedState(onLongPress)
    val latestDragStart by rememberUpdatedState(onDragStart)
    val latestDrag by rememberUpdatedState(onDrag)
    val latestDragEnd by rememberUpdatedState(onDragEnd)
    var size by remember { mutableStateOf(IntSize.Zero) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var view by remember(layout.widgetId) { mutableStateOf<HomeWidgetHostView?>(null) }
    var creationFailed by remember(layout.widgetId, widget.info) { mutableStateOf(false) }
    val scale = contentScale.coerceIn(.5f, 1.5f)
    // The slot never changes size. Measure RemoteViews in inverse-scaled space,
    // then scale its entire contents (including touch coordinates) into that slot.
    val modifier = Modifier.fillMaxWidth().height(height.dp).clipToBounds().onSizeChanged { size = it }.layout { measurable, constraints ->
        val width = constraints.maxWidth
        val outerHeight = constraints.maxHeight
        val child = measurable.measure(Constraints.fixed(
            (width / scale).roundToInt().coerceAtLeast(1), (outerHeight / scale).roundToInt().coerceAtLeast(1)))
        layout(width, outerHeight) {
            child.placeWithLayer(0, 0) {
                scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }.onGloballyPositioned { coordinates = it }
    if (widget.info == null || creationFailed) {
        // A failed RemoteViews must still finish/cancel its grid drag. Other surfaces
        // retain their existing tap/hold menu rather than entering a drag without UP.
        val fallback = if (!editGesturesEnabled) modifier else if (onDrag != null) modifier.pointerInput(layout.widgetId, enabled, editing) {
            var previous = Offset.Zero
            if (enabled && !editing) detectDragGesturesAfterLongPress(
                onDragStart = {
                    previous = coordinates?.localToRoot(it) ?: it
                    latestDragStart?.invoke(it.x, it.y) ?: latestLongPress()
                }, onDragEnd = { latestDragEnd?.invoke(false) },
                onDragCancel = { latestDragEnd?.invoke(true) },
            ) { change, _ ->
                val root = coordinates?.localToRoot(change.position) ?: change.position
                change.consume()
                latestDrag?.invoke(root.x - previous.x, root.y - previous.y)
                previous = root
            }
        } else modifier.combinedClickable(enabled = enabled && !editing, onClick = onLongPress, onLongClick = onLongPress)
        Surface(fallback,
            shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
            Text(stringResource(R.string.widget_unavailable), Modifier.padding(20.dp))
        }
    } else key(layout.widgetId, widget.info.provider) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                // A provider may be removed/updated between lookup and creation.
                runCatching { widget.host.createView(context, layout.widgetId, widget.info) as HomeWidgetHostView }
                    .getOrElse {
                        Log.e("GraceWidgetHost", "create failed id=${layout.widgetId} provider=${layout.widgetProvider}", it)
                        creationFailed = true; HomeWidgetHostView(context)
                    }
                    .also { view = it }
            },
            update = {
                it.onWidgetLongPress = if (editGesturesEnabled) ({ latestLongPress() }) else null
                it.onWidgetDragStart = if (!editGesturesEnabled || onDragStart == null) null else { x, y -> latestDragStart?.invoke(x, y) }
                it.onWidgetDrag = { x, y -> latestDrag?.invoke(x, y) }
                it.onWidgetDragEnd = { cancelled -> latestDragEnd?.invoke(cancelled) }
                it.interactionEnabled = enabled && !editing
                it.hapticsEnabled = hapticsEnabled
                val padding = AppWidgetHostView.getDefaultPaddingForWidget(it.context, widget.info.provider, null)
                if (removeHorizontalPadding) { padding.left = 0; padding.right = 0 }
                if (it.paddingLeft != padding.left || it.paddingTop != padding.top ||
                    it.paddingRight != padding.right || it.paddingBottom != padding.bottom) {
                    it.setPadding(padding.left, padding.top, padding.right, padding.bottom)
                }
            },
            onRelease = {
                it.onWidgetLongPress = null; it.onWidgetDragStart = null; it.onWidgetDrag = null; it.onWidgetDragEnd = null
                if (view === it) view = null
            },
        )
    }
    LaunchedEffect(view, size, editing, density, scale, removeHorizontalPadding, creationFailed) {
        if (size.width == 0 || size.height == 0 || creationFailed || widget.info == null) return@LaunchedEffect
        // Avoid flooding providers with RemoteViews rebuilds on every drag frame.
        if (editing) delay(100)
        val hostView = view ?: return@LaunchedEffect
        // Report the actual RemoteViews content space, including content zoom and full-width padding.
        // AppWidgetHostView.updateAppWidgetSize subtracts *default* padding, which is wrong when
        // full-width mode removes it. Updating the manager directly avoids double subtraction.
        val widthDp = ((size.width / scale).roundToInt() - hostView.paddingLeft - hostView.paddingRight) / density
        val heightDp = ((size.height / scale).roundToInt() - hostView.paddingTop - hostView.paddingBottom) / density
        runCatching {
            val manager = AppWidgetManager.getInstance(hostView.context)
            val options = widgetSizeOptions(widthDp, heightDp)
            val previous = manager.getAppWidgetOptions(layout.widgetId)
            @Suppress("DEPRECATION")
            val changed = options.keySet().any { options.get(it) != previous.get(it) }
            if (changed) manager.updateAppWidgetOptions(layout.widgetId, options)
        }.onFailure { Log.e("GraceWidgetHost", "resize failed id=${layout.widgetId} provider=${layout.widgetProvider} size=${widthDp}x$heightDp", it) }
    }
}
