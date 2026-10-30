package com.galaxyrio.gracelauncher.ui.components

import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sin

/** A saturating sine resistance, inspired by SystemUI SwipeHelper's constrained dragging. */
internal fun resistedDismissOffset(offset: Float, threshold: Float, resistance: Int = 75): Float {
    if (threshold <= 0f) return offset
    val strength = resistance.coerceIn(0, 90) / 100f
    val progress = (abs(offset) / threshold).coerceIn(0f, 1f)
    val baseline = sign(offset) * threshold * .45f * sin(progress * (Math.PI / 2)).toFloat()
    // Preserve the original feel at 55%; zero is exactly finger tracking, never acceleration.
    return if (strength <= .55f) {
        val mix = strength / .55f
        offset * (1f - mix) + baseline * mix
    } else baseline * ((1f - strength) / .45f)
}

/** Stay released on reversal within the same side; re-arm only after crossing the origin. */
internal fun dismissBreakawayDirection(previous: Int, offset: Float, threshold: Float, canDismiss: Boolean = true): Int {
    if (!canDismiss) return 0
    if (previous * offset > 0f) return previous
    return if (abs(offset) >= threshold && offset != 0f) sign(offset).toInt() else 0
}

/** A quick reversal must not let residual spring lag push the content past the origin. */
internal fun releasedDismissOffset(offset: Float, lag: Float): Float =
    offset - lag.coerceIn(minOf(0f, offset), maxOf(0f, offset))

internal fun shouldDismissDrag(offset: Float, threshold: Float, cancelled: Boolean, canDismiss: Boolean = true): Boolean =
    canDismiss && !cancelled && offset != 0f && abs(offset) >= threshold
