package com.galaxyrio.gracelauncher.data

internal const val AppStackInset = 8f
private const val AppStackColumnGap = 12f
internal const val AppStackRowGap = 8f

/** Shared by the inline surface and widget-grid footprint, in density-independent units. */
internal fun appStackColumns(width: Float, iconSize: Float): Int =
    ((width - AppStackInset * 2 + AppStackColumnGap) / (iconSize + AppStackColumnGap)).toInt().coerceAtLeast(2)

internal fun appStackHeight(rows: Int, iconSize: Float): Float =
    AppStackInset * 2 + iconSize + (rows.coerceAtLeast(1) - 1) * (iconSize + AppStackRowGap)

/** Reserve the last slot for the toggle only when a stack overflows one row. */
internal fun appStackRows(count: Int, columns: Int, expanded: Boolean): Int {
    val width = columns.coerceAtLeast(2)
    return if (!expanded || count <= width) 1 else (count + width) / width
}

/** Full-width stacks insert temporary rows; never persist these expansion-only coordinates. */
internal fun expandWidgetStacks(cells: List<WidgetGridCell>, rows: Map<String, Int>): List<WidgetGridCell> = cells.map { cell ->
    val shift = cells.filter { it.row < cell.row }.sumOf { ((rows[it.key] ?: 1) - 1).coerceAtLeast(0) }
    cell.copy(row = cell.row + shift, size = rows[cell.key]?.let { cell.size.copy(rows = it.coerceAtLeast(1)) } ?: cell.size)
}

internal fun collapseWidgetStacks(cells: List<WidgetGridCell>, stackKeys: Set<String>): List<WidgetGridCell> = cells.map { cell ->
    val shift = cells.filter { it.key in stackKeys && it.row < cell.row }.sumOf { (it.size.rows - 1).coerceAtLeast(0) }
    cell.copy(row = (cell.row - shift).coerceAtLeast(0),
        size = if (cell.key in stackKeys) cell.size.copy(rows = 1) else cell.size)
}
