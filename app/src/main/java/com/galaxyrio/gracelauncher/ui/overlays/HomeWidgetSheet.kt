package com.galaxyrio.gracelauncher.ui.overlays

import android.content.ComponentName
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.AppIcon
import com.galaxyrio.gracelauncher.ui.components.LauncherSymbol

@Composable
internal fun HomeWidgetSheet(uiState: LauncherUiState, actions: LauncherActions, onChange: (LauncherOverlay?) -> Unit, custom: Boolean) {
    val settings = uiState.settings
    val widget = settings.homeLayout
    val component = widget.widgetProvider?.let(ComponentName::unflattenFromString)
    val app = component?.let { provider ->
        uiState.apps.firstOrNull { it.packageName == provider.packageName }
            ?: LauncherApp(provider, widget.widgetLabel ?: provider.packageName, null)
    }
    fun navigate(page: String) { onChange(LauncherOverlay.SettingsDestination(page)) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
        .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 12.dp)) {
        Row(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            if (custom && app != null) { AppIcon(app, size = 34.dp); Spacer(Modifier.width(20.dp)) }
            Text(if (custom) widget.widgetLabel ?: stringResource(R.string.widget_home_menu) else stringResource(R.string.widget_home_menu),
                fontSize = 25.sp, fontWeight = FontWeight.Medium)
        }
        if (custom) {
            WidgetAction(LauncherSymbol.Home, stringResource(R.string.edit_homepage)) { onChange(LauncherOverlay.Homepage()) }
            if (app != null) WidgetAction(LauncherSymbol.Info, stringResource(R.string.app_info)) { onChange(null); actions.appInfo(app) }
            WidgetAction(LauncherSymbol.Resize, stringResource(R.string.widget_move_resize)) { actions.moveWidget() }
            WidgetAction(LauncherSymbol.Edit, stringResource(R.string.widget_configuration)) { onChange(null); actions.configureWidget() }
            WidgetAction(LauncherSymbol.Delete, stringResource(R.string.widget_remove)) { onChange(null); actions.removeWidget() }
        } else {
            WidgetAction(LauncherSymbol.Clock, "Clock tap action", arrow = true) { navigate("Clock") }
            WidgetAction(LauncherSymbol.Clock, stringResource(R.string.settings_clock_style), arrow = true) { navigate("ClockStyle") }
            WidgetToggle("Show clock", null, settings.clockEnabled) { enabled ->
                actions.updateSettings { it.copy(clockEnabled = enabled) }
            }
            WidgetAction(LauncherSymbol.Calendar, stringResource(R.string.widget_calendar),
                stringResource(R.string.settings_calendar_agenda_summary), arrow = true) { navigate("Calendar") }
            WidgetToggle("Show calendar", null, settings.calendarAgenda) { enabled ->
                actions.updateSettings { it.copy(calendarAgenda = enabled) }
                if (enabled) actions.requestCalendarAccess()
            }
            WidgetAction(LauncherSymbol.Weather, stringResource(R.string.settings_weather), arrow = true) { navigate("Weather") }
            WidgetAction(LauncherSymbol.Music, "Media player tap action", arrow = true) { navigate("MediaPlayer") }
            WidgetToggle(stringResource(R.string.settings_media_player), stringResource(R.string.media_player_summary), settings.mediaPlayer) { enabled ->
                actions.updateSettings { it.copy(mediaPlayer = enabled) }
                if (enabled && !uiState.media.hasAccess) actions.requestMediaAccess()
            }
            WidgetToggle(stringResource(R.string.settings_show_battery), null, settings.showBatteryPercentage) { enabled ->
                actions.updateSettings { it.copy(showBatteryPercentage = enabled) }
            }
            WidgetToggle("Show movable divider", "A draggable spacer in the home item order", settings.homeLayout.baselineEnabled) { enabled ->
                actions.updateSettings { it.copy(homeLayout = it.homeLayout.copy(baselineEnabled = enabled)) }
            }
            Text("Divider height: ${settings.homeLayout.baselineHeightDp} dp", Modifier.padding(start = 12.dp, top = 8.dp),
                style = MaterialTheme.typography.labelLarge)
            Slider(value = settings.homeLayout.baselineHeightDp.toFloat(), onValueChange = { value ->
                actions.updateSettings { it.copy(homeLayout = it.homeLayout.copy(baselineHeightDp = value.toInt())) }
            }, valueRange = 2f..240f)
            WidgetAction(LauncherSymbol.Resize, "Resize / rearrange home items") { onChange(null); actions.moveWidget() }
            WidgetAction(LauncherSymbol.Widgets, "Add another widget") { onChange(null); actions.addWidget() }
        }
        HorizontalDivider(Modifier.padding(horizontal = 12.dp).padding(top = 6.dp, bottom = 8.dp),
            thickness = 1.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        if (!custom) WidgetAction(LauncherSymbol.Home, stringResource(R.string.edit_homepage), arrow = true) { onChange(LauncherOverlay.Homepage()) }
        WidgetAction(LauncherSymbol.Settings, stringResource(R.string.grace_settings), arrow = !custom) { onChange(LauncherOverlay.Settings) }
    }
}

@Composable
private fun WidgetAction(symbol: LauncherSymbol, label: String, summary: String? = null, arrow: Boolean = false, onClick: () -> Unit) {
    PanelAction(symbol, label, iconColumnWidth = 34.dp, iconTextSpacing = 20.dp, summary = summary,
        trailing = if (arrow) ({ Icon(painterResource(R.drawable.ms_expand_more), null, Modifier.rotate(-90f).size(24.dp)) }) else null,
        onClick = onClick)
}

@Composable
private fun WidgetToggle(label: String, summary: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(16.dp))
        .toggleable(value = checked, role = Role.Switch, onValueChange = onChange).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 16.sp)
            if (summary != null) Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked, onCheckedChange = null)
    }
}
