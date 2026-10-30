package com.galaxyrio.gracelauncher.ui.settings

import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState

@Composable
internal fun WidgetScreenSettingsPage(uiState: LauncherUiState, actions: LauncherActions, onBack: () -> Unit, onEdit: () -> Unit) {
    val settings = uiState.settings.widgetScreen
    var columns by remember(settings.columns) { mutableFloatStateOf(settings.columns.toFloat()) }
    SettingsScaffold(stringResource(R.string.widget_screen_title), "widget_screen_settings", onBack) { padding ->
        SettingsList(padding) {
            item {
                SettingsFeatureBanner(stringResource(R.string.widget_screen_enable), settings.enabled, "widget_screen_enabled") { enabled ->
                    actions.updateSettings { it.copy(widgetScreen = it.widgetScreen.copy(enabled = enabled)) }
                }
            }
            item {
                SettingsToggleItem(stringResource(R.string.widget_screen_swipe),
                    if (settings.swipeToOpen) null else stringResource(R.string.widget_screen_swipe_disabled),
                    settings.swipeToOpen, 0, 4, "widget_screen_swipe") { enabled ->
                    actions.updateSettings { it.copy(widgetScreen = it.widgetScreen.copy(swipeToOpen = enabled)) }
                }
            }
            item {
                SettingsToggleItem(stringResource(R.string.favorites_lock_editing), null,
                    settings.editingLocked, 1, 4, "widget_screen_lock_editing") { locked ->
                    actions.updateSettings { it.copy(widgetScreen = it.widgetScreen.copy(editingLocked = locked)) }
                }
            }
            item {
                DesignerSegment(2, 4, "widget_screen_columns_item") {
                    DesignerSlider(stringResource(R.string.widget_screen_columns), columns, 4f, 2f..8f,
                        LocalSettingsStorageState.current.canEdit, "widget_screen_columns", suffix = "",
                        onFinished = { value ->
                            actions.updateSettings { it.copy(widgetScreen = it.widgetScreen.copy(columns = value.toInt().coerceIn(2, 8))) }
                        }) { columns = it }
                }
            }
            item {
                SettingsActionItem(stringResource(R.string.widget_screen_edit), null, 3, 4, "widget_screen_edit", onClick = onEdit)
            }
        }
    }
}
