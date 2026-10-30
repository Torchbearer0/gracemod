package com.galaxyrio.gracelauncher.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.ScheduleStatus

@Composable
internal fun CalendarSettings(uiState: LauncherUiState, actions: LauncherActions, onBack: () -> Unit, onAppearance: () -> Unit = {}, onChooseTapAction: () -> Unit = {}) {
    SettingsScaffold(stringResource(R.string.widget_calendar), "settings_calendar", onBack,
        actions = { SettingsAppearanceAction(onAppearance) }) { padding ->
        SettingsList(padding) {
            item {
                SettingsFeatureBanner(stringResource(R.string.calendar_enable), uiState.settings.calendarAgenda, "calendar_agenda") { enabled ->
                    actions.updateSettings { it.copy(calendarAgenda = enabled) }
                }
            }
            item {
                SettingsActionItem("Tap action", uiState.settings.calendarAppKey?.let { key -> uiState.allApps.firstOrNull { it.key == key }?.label }
                    ?: "Open Grace calendar agenda", 0, 1, "calendar_tap_action", onClick = onChooseTapAction)
            }
            item {
                SettingsToggleItem(stringResource(R.string.settings_show_battery), null, uiState.settings.showBatteryPercentage,
                    0, 1, "show_battery") { value -> actions.updateSettings { it.copy(showBatteryPercentage = value) } }
            }
            if (uiState.scheduleStatus == ScheduleStatus.PermissionRequired) item {
                SettingsHeading(stringResource(R.string.widget_calendar_access))
                SettingsActionItem(stringResource(R.string.connect_calendar), stringResource(R.string.calendar_permission_description),
                    0, 1, "calendar_access", onClick = actions.requestCalendarAccess)
            }
        }
    }
}
