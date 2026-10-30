package com.galaxyrio.gracelauncher.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/** Shared bidirectional drag: resistance, spring breakaway, then direct finger tracking. */
@Composable
internal fun SwipeDismissContainer(
    itemKey: Any,
    onDismiss: () -> Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    canDismiss: Boolean = true,
    resistance: Int = 75,
    thresholdDp: Int = 72,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = MaterialTheme.colorScheme.primary,
    clipContent: Boolean = true,
    content: @Composable () -> Unit,
) = key(itemKey) {
    val maxThreshold = with(LocalDensity.current) { thresholdDp.coerceIn(24, 160).dp.toPx() }
    val scope = rememberCoroutineScope()
    val currentDismiss by rememberUpdatedState(onDismiss)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentCanDismiss by rememberUpdatedState(canDismiss)
    var width by remember { mutableIntStateOf(1) }
    val threshold = minOf(maxThreshold, width * .85f)
    var dragging by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf(false) }
    var travel by remember { mutableFloatStateOf(0f) }
    var breakaway by remember { mutableIntStateOf(0) }
    val gap = remember { Animatable(0f) }
    val restingOffset = remember { Animatable(0f) }
    val tracker = remember { VelocityTracker() }
    var lastMovementTime by remember { mutableLongStateOf(0L) }
    var catchUp by remember { mutableStateOf<Job?>(null) }
    val label = stringResource(R.string.notification_dismiss)

    fun displayedOffset(): Float = if (!dragging) restingOffset.value else
        if (!currentCanDismiss || breakaway == 0) resistedDismissOffset(travel, threshold, resistance) else releasedDismissOffset(travel, gap.value)

    fun finishDrag(cancelled: Boolean) {
        if (!dragging || settling) return
        val from = displayedOffset()
        val dismiss = currentEnabled && shouldDismissDrag(travel, threshold, cancelled, currentCanDismiss)
        val direction = sign(travel)
        // The drag callback does not receive UP. Do not reuse a fling velocity after a held pause.
        val velocity = if (cancelled || SystemClock.uptimeMillis() - lastMovementTime > 100L) 0f
            else tracker.calculateVelocity().x.coerceIn(-width * 8f, width * 8f)
        catchUp?.cancel()
        settling = true
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            // Switch from finger space to spring space at the exact visible position.
            restingOffset.snapTo(from)
            dragging = false
            try {
                if (dismiss) {
                    restingOffset.animateTo(direction * width,
                        spring(dampingRatio = 1f, stiffness = 800f),
                        initialVelocity = if (velocity * direction > 0f) velocity else 0f)
                    // Data owns removal. If cancellation is rejected, return the same item.
                    val accepted = currentEnabled && currentCanDismiss && currentDismiss()
                    if (accepted) delay(600)
                }
                restingOffset.animateTo(0f, spring(dampingRatio = .8f, stiffness = 550f),
                    initialVelocity = if (dismiss) 0f else velocity * .45f)
            } finally {
                travel = 0f
                breakaway = 0
                settling = false
            }
        }
    }

    LaunchedEffect(enabled) { if (!enabled) finishDrag(cancelled = true) }
    LaunchedEffect(canDismiss) {
        if (!canDismiss) {
            catchUp?.cancel()
            breakaway = 0
        }
    }

    Box(modifier.graphicsLayer {
        shape = LauncherLayout.RowShape
        // The translated foreground and dismiss backdrop share a stationary rounded
        // boundary. Otherwise an unclipped cover hits the parent's rectangular edge.
        // Wallpaper-native media can still draw its shadow outside the row at rest.
        clip = clipContent || dragging || settling
    }
        .onSizeChanged { width = it.width.coerceAtLeast(1) }
        // This surface never translates: movement and velocity stay in finger coordinates.
        // Horizontal detection leaves vertical scrolling, clicks and long presses to children.
        .pointerInput(enabled, width, maxThreshold, resistance) {
            detectHorizontalDragGestures(
                onDragStart = {
                    if (enabled && !settling) {
                        catchUp?.cancel()
                        tracker.resetTracking()
                        travel = 0f
                        breakaway = 0
                        dragging = true
                    }
                },
                onDragEnd = { finishDrag(cancelled = false) },
                onDragCancel = { finishDrag(cancelled = true) },
            ) { change, delta ->
                // Also consume non-clearable swipes, so they cannot turn into a message tap.
                change.consume()
                if (dragging && enabled) {
                    tracker.addPosition(change.uptimeMillis, change.position)
                    lastMovementTime = change.uptimeMillis
                    travel = (travel + delta).coerceIn(-width.toFloat(), width.toFloat())
                    val next = dismissBreakawayDirection(breakaway, travel, threshold, currentCanDismiss)
                    if (next != breakaway) {
                        catchUp?.cancel()
                        if (next == 0) breakaway = 0 else {
                            val lag = travel - resistedDismissOffset(travel, threshold, resistance)
                            catchUp = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                gap.snapTo(lag)
                                breakaway = next
                                // Animate only the lag, never the live finger position. Once caught
                                // up, every movement is 1:1, with no trailing animation per frame.
                                gap.animateTo(0f, spring(dampingRatio = 1f, stiffness = 1800f))
                            }
                        }
                    }
                }
            }
        }
        .semantics {
            if (enabled && canDismiss) customActions = listOf(CustomAccessibilityAction(label) {
                currentEnabled && currentCanDismiss && currentDismiss()
            })
        },
    ) {
        val offset = displayedOffset()
        if (canDismiss && abs(offset) > .5f) {
            Row(Modifier.matchParentSize().background(backgroundColor, LauncherLayout.RowShape).padding(horizontal = 16.dp),
                horizontalArrangement = if (offset > 0f) Arrangement.Absolute.Left else Arrangement.Absolute.Right,
                verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = contentColor, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(12.dp))
                Icon(painterResource(R.drawable.ms_close), null, Modifier.size(24.dp), tint = contentColor)
            }
        }
        Box(Modifier.fillMaxWidth().graphicsLayer {
            translationX = offset
            alpha = (1f - abs(offset) / width * .85f).coerceIn(0f, 1f)
        }) { content() }
    }
}
