package com.galaxyrio.gracelauncher.platform

import kotlin.math.ceil

/** Provider sizes are pixels. Normalize before converting to dp or grid cells. */
internal data class WidgetAxisBounds(val initial: Int, val min: Int, val max: Int) {
    val canResize: Boolean get() = min < max
}

internal fun widgetAxisBounds(
    defaultSize: Int, minResize: Int, maxResize: Int, resizable: Boolean,
    padding: Float, unit: Float, capacity: Int, minimum: Int = 1,
): WidgetAxisBounds {
    require(unit > 0 && capacity >= minimum && minimum > 0)
    fun cells(pixels: Int) = ceil((pixels.coerceAtLeast(0) + padding) / unit).toInt()
        .coerceIn(minimum, capacity)
    val initial = cells(defaultSize)
    if (!resizable) return WidgetAxisBounds(initial, initial, initial)
    // Invalid metadata must not prevent resizing (AppWidgetProviderInfo contract).
    val min = cells(minResize.takeIf { it > 0 && it <= defaultSize } ?: defaultSize)
    val max = if (maxResize > 0 && maxResize >= defaultSize) cells(maxResize) else capacity
    return WidgetAxisBounds(initial, min.coerceAtMost(initial), max.coerceAtLeast(initial))
}

internal fun widgetTargetSize(width: WidgetAxisBounds, height: WidgetAxisBounds,
    targetWidth: Int, targetHeight: Int): Pair<Int, Int> =
    if (targetWidth in width.min..width.max && targetHeight in height.min..height.max)
        targetWidth to targetHeight else width.initial to height.initial
