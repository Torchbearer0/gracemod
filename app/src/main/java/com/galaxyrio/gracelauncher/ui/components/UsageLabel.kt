package com.galaxyrio.gracelauncher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.UsageDisplay
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import java.text.NumberFormat

@Composable
internal fun favoriteUsageLabel(app: LauncherApp, state: LauncherUiState): String? {
    val settings = state.settings.digitalWellbeing
    val usage = state.usage
    if (!settings.enabled || !settings.showUsage || !usage.hasAccess || usage.updatedAt == 0L ||
        app.user != null || app.folderId != null || app.shortcut != null || app.isLauncherSettings) return null
    fun value(range: UsageDisplay): Long = when (range) {
        UsageDisplay.Day, UsageDisplay.Percentage -> usage.day[app.packageName]
        UsageDisplay.Week -> usage.week[app.packageName]
        UsageDisplay.Month -> usage.month[app.packageName]
    } ?: 0
    val percentageFormat = NumberFormat.getPercentInstance().apply { maximumFractionDigits = 1 }
    val values = UsageDisplay.entries.filter { it in settings.display }.associateWith { range ->
        val duration = value(range)
        if (range == UsageDisplay.Percentage) {
            percentageFormat.format(
                if (usage.totalDay > 0) duration.toDouble() / usage.totalDay else 0.0)
        } else when {
            duration in 1 until 60_000 -> stringResource(R.string.usage_less_minute)
            duration < 3_600_000 -> stringResource(R.string.usage_minutes, duration / 60_000)
            else -> stringResource(R.string.usage_hours, duration / 3_600_000, duration / 60_000 % 60)
        }
    }
    val zeroValues = if (settings.hideZeroValues) values.keys.filter { range ->
        // Hide a percentage rounded to 0% too; nonzero sub-minute durations remain <1m.
        if (range == UsageDisplay.Percentage) values[range] == percentageFormat.format(0)
        else value(range) == 0L
    }.toSet() else emptySet()
    return formatUsageValues(values, zeroValues)
}

internal fun formatUsageValues(values: Map<UsageDisplay, String>, hiddenValues: Set<UsageDisplay> = emptySet()): String? {
    val visible = values.filterKeys { it !in hiddenValues }
    val period = UsageDisplay.entries.firstOrNull { it != UsageDisplay.Percentage && it in visible }
    val duration = period?.let { visible.getValue(it).replace(" m", "m").replace(" h", "h") }
    return listOfNotNull(duration, visible[UsageDisplay.Percentage]).takeIf { it.isNotEmpty() }?.joinToString(" ")
}
