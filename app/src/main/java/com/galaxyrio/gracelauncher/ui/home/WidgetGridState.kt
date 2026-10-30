package com.galaxyrio.gracelauncher.ui.home

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.galaxyrio.gracelauncher.data.*
import kotlin.math.roundToInt

internal enum class WidgetResizeEdge { Left, Top, Right, Bottom }
internal data class WidgetResizeLimits(
    val minColumns: Int, val maxColumns: Int, val minRows: Int, val maxRows: Int,
    val horizontal: Boolean, val vertical: Boolean,
)

/** The pointer lives on the stationary viewport, never on a translating item. */
@Stable
internal class WidgetGridState(
    private val commit: (List<WidgetGridCell>, String?) -> Unit,
    private val onEditing: (Boolean) -> Unit,
) {
    var cells by mutableStateOf(emptyList<WidgetGridCell>()); private set
    var draggingKey by mutableStateOf<String?>(null); private set
    var selectedKey by mutableStateOf<String?>(null); private set
    var resizing by mutableStateOf(false); private set
    var scroll by mutableFloatStateOf(0f); private set
    var maxScroll by mutableFloatStateOf(0f); private set
    var dragPosition by mutableStateOf(Offset.Zero); private set
    private var columns = 4
    private var cellWidth = 1f
    private var cellHeight = 1f
    private var origin = Offset.Zero
    private var viewport = 0f
    private var bottomInset = 0f
    private var deleteBounds = Rect.Zero
    private var widgetKeys = emptySet<String>()
    private var pointerInCell = Offset.Zero
    private var before = emptyList<WidgetGridCell>()
    private var resizeDelta = Offset.Zero
    val busy get() = draggingKey != null || resizing
    val overDelete get() = draggingKey != null && deleteBounds.contains(pointerViewport)
    private val pointerViewport get() = dragPosition + pointerInCell + origin - Offset(0f, scroll)
    val scrolling = ScrollableState { delta ->
        val previous = scroll
        if (!busy) scroll = (scroll - delta).coerceIn(0f, maxScroll)
        previous - scroll
    }

    fun configure(columns: Int, width: Float, height: Float, origin: Offset, viewport: Float,
        bottom: Float, deleteBounds: Rect, widgetKeys: Set<String>) {
        this.columns = columns; cellWidth = width; cellHeight = height; this.origin = origin
        this.viewport = viewport; bottomInset = bottom; this.deleteBounds = deleteBounds; this.widgetKeys = widgetKeys
        updateScrollRange()
    }

    fun synchronize(value: List<WidgetGridCell>) {
        if (busy) return
        cells = value
        if (selectedKey != null && cells.none { it.key == selectedKey }) deselect()
        updateScrollRange()
    }

    fun cellAt(pointer: Offset): WidgetGridCell? {
        val local = pointer - origin + Offset(0f, scroll)
        return cells.firstOrNull {
            local.x >= it.column * cellWidth && local.x < (it.column + it.size.columns) * cellWidth &&
                local.y >= it.row * cellHeight && local.y < (it.row + it.size.rows) * cellHeight
        }
    }

    fun viewportPosition(cell: WidgetGridCell) = origin + Offset(cell.column * cellWidth, cell.row * cellHeight - scroll)

    fun startDrag(key: String, pointer: Offset) {
        if (busy) return
        val cell = cells.firstOrNull { it.key == key } ?: return
        before = cells; selectedKey = null
        pointerInCell = pointer - viewportPosition(cell)
        dragPosition = Offset(cell.column * cellWidth, cell.row * cellHeight)
        draggingKey = key
        onEditing(true)
        updateScrollRange()
    }

    fun drag(delta: Offset) {
        val key = draggingKey ?: return
        dragPosition += delta
        if (!overDelete) {
            val original = before.firstOrNull { it.key == key } ?: return
            val next = original.copy(
                column = (dragPosition.x / cellWidth).roundToInt().coerceIn(0, columns - original.size.columns),
                row = (dragPosition.y / cellHeight).roundToInt().coerceAtLeast(0),
            )
            if (cells.firstOrNull { it.key == key } != next) cells = moveWidgetGrid(before, next, columns)
        }
        updateScrollRange()
    }

    /** Leave space below the current viewport while held, so dragging can grow the canvas indefinitely. */
    fun autoScroll(edge: Float, maxStep: Float) {
        if (draggingKey == null || overDelete) return
        val y = pointerViewport.y
        val factor = when {
            y < origin.y + edge -> -((origin.y + edge - y) / edge).coerceIn(0f, 1f)
            y > viewport - bottomInset - edge -> ((y - viewport + bottomInset + edge) / edge).coerceIn(0f, 1f)
            else -> 0f
        }
        val previous = scroll
        scroll = (scroll + factor * maxStep).coerceIn(0f, maxScroll)
        if (scroll != previous) drag(Offset(0f, scroll - previous))
    }

    fun finishDrag(cancelled: Boolean) {
        val key = draggingKey ?: return
        val removed = if (!cancelled && overDelete) key else null
        draggingKey = null
        cells = if (cancelled) before else compactWidgetRows(
            if (removed != null) before.filterNot { it.key == removed } else cells,
        )
        selectedKey = key.takeIf { !cancelled && removed == null && it in widgetKeys }
        if (!cancelled) commit(cells, removed)
        updateScrollRange()
        revealSelection()
        onEditing(selectedKey != null)
    }

    fun selectWidget(key: String) {
        if (key in widgetKeys) { selectedKey = key; onEditing(true) }
    }

    fun startResize(key: String) {
        if (busy || selectedKey != key) return
        before = cells; resizeDelta = Offset.Zero; resizing = true
    }

    fun resize(edge: WidgetResizeEdge, delta: Offset, limits: WidgetResizeLimits) {
        if (!resizing) return
        resizeDelta += delta
        val original = before.firstOrNull { it.key == selectedKey } ?: return
        val dx = (resizeDelta.x / cellWidth).roundToInt()
        val dy = (resizeDelta.y / cellHeight).roundToInt()
        var left = original.column; var top = original.row
        var width = original.size.columns; var height = original.size.rows
        when (edge) {
            WidgetResizeEdge.Left -> {
                val right = original.column + original.size.columns
                width = (width - dx).coerceIn(limits.minColumns.coerceAtMost(right), limits.maxColumns.coerceAtMost(right))
                left = right - width
            }
            WidgetResizeEdge.Right -> width = (width + dx).coerceIn(
                limits.minColumns.coerceAtMost(columns - left), limits.maxColumns.coerceAtMost(columns - left))
            WidgetResizeEdge.Top -> {
                val bottom = original.row + original.size.rows
                height = (height - dy).coerceIn(limits.minRows.coerceAtMost(bottom), limits.maxRows.coerceAtMost(bottom))
                top = bottom - height
            }
            WidgetResizeEdge.Bottom -> height = (height + dy).coerceIn(limits.minRows, limits.maxRows)
        }
        val next = original.copy(column = left, row = top, size = WidgetGridSize(width, height))
        cells = moveWidgetGrid(before, next, columns)
        updateScrollRange()
    }

    fun finishResize(cancelled: Boolean) {
        if (!resizing) return
        resizing = false
        cells = if (cancelled) before else compactWidgetRows(cells)
        if (!cancelled) commit(cells, null)
        updateScrollRange()
    }

    fun deselect() { selectedKey = null; onEditing(false) }

    fun cancelInteraction() {
        if (busy) cells = before
        draggingKey = null; resizing = false
        deselect(); updateScrollRange()
    }

    fun remove(key: String) {
        if (busy) return
        cells = compactWidgetRows(cells.filterNot { it.key == key })
        if (selectedKey == key) deselect()
        commit(cells, key); updateScrollRange()
    }

    fun moveBy(key: String, rows: Int) {
        if (busy) return
        val cell = cells.firstOrNull { it.key == key } ?: return
        cells = compactWidgetRows(moveWidgetGrid(cells, cell.copy(row = (cell.row + rows).coerceAtLeast(0)), columns))
        commit(cells, null); updateScrollRange()
    }

    private fun updateScrollRange() {
        val rows = cells.maxOfOrNull { it.row + it.size.rows } ?: 0
        val end = origin.y + rows * cellHeight + bottomInset + cellHeight * .3f
        maxScroll = (end - viewport).coerceAtLeast(0f)
        if (draggingKey != null) maxScroll = maxOf(maxScroll, scroll + cellHeight * 2f)
        scroll = scroll.coerceIn(0f, maxScroll)
    }

    private fun revealSelection() {
        val cell = cells.firstOrNull { it.key == selectedKey } ?: return
        val bottom = origin.y + (cell.row + cell.size.rows) * cellHeight - scroll
        if (bottom > viewport - bottomInset) scroll = (scroll + bottom - viewport + bottomInset).coerceIn(0f, maxScroll)
    }
}
