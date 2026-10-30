package com.galaxyrio.gracelauncher.platform

import android.app.ActivityOptions
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import com.galaxyrio.gracelauncher.data.HomeContentKey
import com.galaxyrio.gracelauncher.data.WidgetScreenKey
import kotlin.math.ceil
import kotlin.math.floor

internal fun widgetConfigurationOptions(): Bundle = ActivityOptions.makeBasic().apply {
    // A user-visible setup flow lends its launch privilege to the provider's IntentSender.
    if (Build.VERSION.SDK_INT >= 36) {
        setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE)
    } else if (Build.VERSION.SDK_INT >= 34) {
        @Suppress("DEPRECATION")
        setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
    }
}.toBundle()

internal fun AppWidgetProviderInfo.widthBounds(padding: Float, unit: Float, capacity: Int, minimum: Int = 1) =
    widgetAxisBounds(minWidth, minResizeWidth, if (Build.VERSION.SDK_INT >= 31) maxResizeWidth else 0,
        resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0, padding, unit, capacity, minimum)

internal fun AppWidgetProviderInfo.heightBounds(padding: Float, unit: Float, capacity: Int, minimum: Int = 1) =
    widgetAxisBounds(minHeight, minResizeHeight, if (Build.VERSION.SDK_INT >= 31) maxResizeHeight else 0,
        resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0, padding, unit, capacity, minimum)

/** Content sizes, excluding the host padding, as required by AppWidgetManager. */
internal fun widgetSizeOptions(widthDp: Float, heightDp: Float): Bundle {
    val width = widthDp.coerceAtLeast(1f)
    val height = heightDp.coerceAtLeast(1f)
    return Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, floor(width).toInt())
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, floor(height).toInt())
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, ceil(width).toInt())
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, ceil(height).toInt())
        if (Build.VERSION.SDK_INT >= 31) putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,
            arrayListOf(SizeF(width, height)))
    }
}

/** Supply a usable size before configuration. The host replaces it with measured sizes on layout. */
internal fun initialWidgetOptions(context: Context, info: AppWidgetProviderInfo,
    owner: String? = null, columns: Int = 4, homeSidePadding: Int = 44): Bundle {
    val density = context.resources.displayMetrics.density
    val screenWidth = context.resources.configuration.screenWidthDp.toFloat().coerceAtLeast(1f)
    val padding = AppWidgetHostView.getDefaultPaddingForWidget(context, info.provider, null)
    val horizontal = (padding.left + padding.right) / density
    val vertical = (padding.top + padding.bottom) / density
    if (owner == WidgetScreenKey) {
        val count = columns.coerceIn(2, 8)
        val cellWidth = ((screenWidth - 32f) / count).coerceAtLeast(1f)
        val cellHeight = cellWidth.coerceIn(88f, 112f)
        val width = info.widthBounds((horizontal + 8f) * density, cellWidth * density, count)
        val height = info.heightBounds((vertical + 8f) * density, cellHeight * density, 20)
        val target = if (Build.VERSION.SDK_INT >= 31)
            widgetTargetSize(width, height, info.targetCellWidth, info.targetCellHeight)
        else width.initial to height.initial
        return widgetSizeOptions(target.first * cellWidth - 8f - horizontal,
            target.second * cellHeight - 8f - vertical)
    }
    val available = (screenWidth - if (owner == null || owner == HomeContentKey)
        (homeSidePadding.coerceIn(8, 80) * 2 + 20f) else 64f).coerceAtLeast(1f)
    val width = info.widthBounds(horizontal * density, density, available.toInt().coerceAtLeast(1))
    val height = info.heightBounds(vertical * density, density, 1600, 48)
    return widgetSizeOptions(width.max - horizontal, height.initial - vertical)
}
