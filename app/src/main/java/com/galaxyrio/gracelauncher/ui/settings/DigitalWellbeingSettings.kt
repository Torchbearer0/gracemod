package com.galaxyrio.gracelauncher.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.DigitalWellbeingSettings
import com.galaxyrio.gracelauncher.data.UsageDisplay
import com.galaxyrio.gracelauncher.data.UsageRefresh
import com.galaxyrio.gracelauncher.data.singleUsageDisplay
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.LauncherIcon
import com.galaxyrio.gracelauncher.ui.components.LauncherSymbol
import java.text.DateFormat
import java.util.Date

@Composable
internal fun DigitalWellbeingSettingsScreen(state: LauncherUiState, actions: LauncherActions, onBack: () -> Unit) {
    val settings = state.settings.digitalWellbeing
    val display = singleUsageDisplay(settings.display)
    val canEdit = LocalSettingsStorageState.current.canEdit
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    fun update(change: (DigitalWellbeingSettings) -> DigitalWellbeingSettings) = actions.updateSettings {
        it.copy(digitalWellbeing = change(it.digitalWellbeing))
    }
    SettingsScaffold(stringResource(R.string.digital_wellbeing), "digital_wellbeing", onBack) { padding ->
        SettingsList(padding) {
            item {
                SettingsFeatureBanner(stringResource(R.string.usage_enable), settings.enabled, "usage_enabled") { enabled ->
                    update { it.copy(enabled = enabled) }
                    if (enabled && !state.usage.hasAccess) dialog = "access"
                }
            }
            if (settings.enabled && !state.usage.hasAccess) item {
                SettingsActionItem(stringResource(R.string.usage_access), stringResource(R.string.usage_access_required),
                    0, 1, "usage_access", leading = { LauncherIcon(LauncherSymbol.Hourglass) }) { dialog = "access" }
                Spacer(Modifier.height(12.dp))
            }
            item {
                UsageOption(stringResource(R.string.usage_show_favorites),
                    UsageDisplay.entries.filter { it in display }.map { usageDisplayLabel(it) }.joinToString(" · ")
                        .ifEmpty { stringResource(R.string.usage_none) }, settings.showUsage,
                    settings.enabled, 0, "usage_show_favorites", onClick = { dialog = "display" }) { value ->
                    update { it.copy(showUsage = value) }
                }
            }
            item {
                UsageOption(stringResource(R.string.usage_sort), usageRefreshLabel(settings.refresh), settings.sortApps,
                    settings.enabled, 1, "usage_sort", onClick = { dialog = "sort" }) { value ->
                    update { it.copy(sortApps = value) }
                }
            }
            if (state.usage.failed) item {
                Text(stringResource(R.string.usage_failed), Modifier.padding(4.dp), color = MaterialTheme.colorScheme.error)
            }
        }
    }
    when (dialog) {
        "access" -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text(stringResource(R.string.usage_access)) },
            text = { Text(stringResource(R.string.usage_access_explanation)) },
            confirmButton = { TextButton(onClick = { dialog = null; actions.requestUsageAccess() }, Modifier.testTag("usage_grant")) {
                Text(stringResource(R.string.media_open_settings))
            } }, dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.settings_cancel)) } })
        "display", "sort" -> AlertDialog(onDismissRequest = { dialog = null },
            title = { Text(stringResource(if (dialog == "display") R.string.usage_show_favorites else R.string.usage_sort)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (dialog == "display") {
                        val period = display.firstOrNull { it != UsageDisplay.Percentage }
                        Column(Modifier.selectableGroup()) {
                            listOf(null, UsageDisplay.Day, UsageDisplay.Week, UsageDisplay.Month).forEach { option ->
                                val selected = period == option
                                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .testTag("usage_display:${option?.name ?: "None"}")
                                    .selectable(selected, enabled = canEdit, role = Role.RadioButton, onClick = {
                                        update { it.copy(display = it.display.filter { value -> value == UsageDisplay.Percentage }.toSet() + listOfNotNull(option)) }
                                    }), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected, null, enabled = canEdit)
                                    Text(if (option == null) stringResource(R.string.usage_none) else usageDisplayLabel(option),
                                        Modifier.padding(start = 12.dp))
                                }
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        val percentage = UsageDisplay.Percentage in display
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("usage_display:Percentage")
                            .toggleable(percentage, enabled = canEdit, role = Role.Switch) { enabled ->
                                update { it.copy(display = if (enabled) singleUsageDisplay(it.display) + UsageDisplay.Percentage
                                    else singleUsageDisplay(it.display) - UsageDisplay.Percentage) }
                            }, verticalAlignment = Alignment.CenterVertically) {
                            Text(usageDisplayLabel(UsageDisplay.Percentage), Modifier.weight(1f).padding(end = 12.dp))
                            Switch(percentage, null, enabled = canEdit)
                        }
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("usage_hide_zero")
                            .toggleable(settings.hideZeroValues, enabled = canEdit, role = Role.Switch) { enabled ->
                                update { it.copy(hideZeroValues = enabled) }
                            }, verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.usage_hide_zero), Modifier.weight(1f).padding(end = 12.dp))
                            Switch(settings.hideZeroValues, null, enabled = canEdit)
                        }
                    } else {
                        Text(stringResource(R.string.usage_refresh_frequency), style = MaterialTheme.typography.titleSmall)
                        UsageRefresh.entries.forEach { option ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("usage_frequency:${option.name}")
                                .selectable(settings.refresh == option, enabled = canEdit, role = Role.RadioButton,
                                    onClick = { update { it.copy(refresh = option) } }), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(settings.refresh == option, null, enabled = canEdit)
                                Text(usageRefreshLabel(option), Modifier.padding(start = 12.dp))
                            }
                        }
                        Button(onClick = actions.refreshUsageRanking,
                            enabled = canEdit && settings.enabled && state.usage.hasAccess && !state.usage.loading,
                            modifier = Modifier.padding(top = 8.dp).testTag("usage_refresh_now")) {
                            Text(stringResource(if (state.usage.loading) R.string.usage_refreshing else R.string.usage_refresh_now))
                        }
                        if (state.usage.rankedAt > 0) Text(stringResource(R.string.usage_last_refresh,
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(state.usage.rankedAt))),
                            style = MaterialTheme.typography.bodySmall)
                        if (state.usage.failed) Text(stringResource(R.string.usage_failed), color = MaterialTheme.colorScheme.error)
                    }
                }
            }, confirmButton = { TextButton(onClick = { dialog = null }, Modifier.testTag("usage_details_done")) {
                Text(stringResource(R.string.done))
            } })
    }
}

@Composable
private fun UsageOption(title: String, summary: String, checked: Boolean, enabled: Boolean, index: Int,
    tag: String, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    SettingsActionItem(title, summary, index, 2, tag, enabled = enabled, onClick = onClick, trailing = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VerticalDivider(Modifier.height(32.dp))
            Switch(checked, onToggle, enabled = enabled && LocalSettingsStorageState.current.canEdit,
                modifier = Modifier.padding(start = 16.dp).testTag("${tag}_switch").semantics { contentDescription = title })
        }
    })
}

@Composable
private fun usageDisplayLabel(value: UsageDisplay): String = stringResource(when (value) {
    UsageDisplay.Day -> R.string.usage_day
    UsageDisplay.Week -> R.string.usage_week
    UsageDisplay.Month -> R.string.usage_month
    UsageDisplay.Percentage -> R.string.usage_percentage
})

@Composable
private fun usageRefreshLabel(value: UsageRefresh): String = stringResource(when (value) {
    UsageRefresh.Daily -> R.string.usage_daily
    UsageRefresh.Weekly -> R.string.usage_weekly
    UsageRefresh.Never -> R.string.usage_never
})
