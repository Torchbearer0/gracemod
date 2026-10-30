@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.galaxyrio.gracelauncher.ui.widgets

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.pm.LauncherApps
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.ui.settings.LocalSettingsBottomInset
import com.galaxyrio.gracelauncher.data.AppRepository
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.icons.IconPackRepository
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.AppIcon
import com.galaxyrio.gracelauncher.ui.components.LauncherSearchBar
import com.galaxyrio.gracelauncher.ui.settings.SettingsScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.os.Build
import com.galaxyrio.gracelauncher.data.EnglishLabelReader
import com.galaxyrio.gracelauncher.data.LauncherLabelOrder
import com.galaxyrio.gracelauncher.data.SearchNames
import com.galaxyrio.gracelauncher.data.SearchQuery

private data class WidgetEntry(val info: AppWidgetProviderInfo, val label: String, val englishLabel: String?) {
    val searchNames = SearchNames(label, englishLabel)
    val key get() = info.provider.flattenToString() + ":" + info.profile
}

private data class WidgetAppGroup(
    val key: String, val packageName: String, val label: String,
    val app: LauncherApp, val widgets: List<WidgetEntry>, val englishLabel: String?,
) {
    val searchNames = SearchNames(label, englishLabel, app.label, app.originalLabel, app.englishLabel)
}

@Composable
internal fun WidgetPicker(providers: List<AppWidgetProviderInfo>?, busy: Boolean, uiState: LauncherUiState, onBack: () -> Unit, onSelect: (AppWidgetProviderInfo) -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val queryState = rememberTextFieldState()
    val query = queryState.text.toString()
    var expandedApps by rememberSaveable { mutableStateOf(listOf<String>()) }
    val groups by produceState<List<WidgetAppGroup>?>(null, providers, configuration, uiState.allApps, uiState.settings.enabledIconPackPackages) {
        value = providers?.let { withContext(Dispatchers.IO) { loadWidgetGroups(context, it, uiState) } }
    }
    val filtered = remember(groups, query) {
        val term = SearchQuery(query, fuzzy = false)
        groups.orEmpty().mapNotNull { app ->
            if (app.searchNames.matches(term) || app.packageName.contains(term.literal, true)) app
            else app.widgets.filter { it.searchNames.matches(term) }.takeIf { it.isNotEmpty() }?.let { app.copy(widgets = it) }
        }
    }
    val toggle: (String) -> Unit = { key -> expandedApps = if (key in expandedApps) expandedApps - key else expandedApps + key }
    SettingsScaffold(stringResource(R.string.widget_choose), "widget_picker", onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            LauncherSearchBar(
                queryState, stringResource(R.string.widget_search), "widget_search_query",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), enabled = !busy && groups != null,
            )
            if (groups == null || busy) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                WidgetGroups(filtered, expandedApps, true, toggle, onSelect, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun WidgetGroups(
    groups: List<WidgetAppGroup>, expandedApps: List<String>, enabled: Boolean,
    onToggle: (String) -> Unit, onSelect: (AppWidgetProviderInfo) -> Unit, modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(bottom = 24.dp + LocalSettingsBottomInset.current)) {
        if (groups.isEmpty()) item {
            Text(stringResource(R.string.widget_none), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        groups.forEach { app ->
            val expanded = app.key in expandedApps
            item(key = "app:${app.key}", contentType = "app") {
                val angle by animateFloatAsState(if (expanded) 180f else 0f, MaterialTheme.motionScheme.fastEffectsSpec(), label = "widgetAppArrow")
                val expansion = stringResource(if (expanded) R.string.widget_group_expanded else R.string.widget_group_collapsed)
                ListItem(
                    onClick = { onToggle(app.key) }, enabled = enabled,
                    modifier = Modifier.animateItem().testTag("widget_app:${app.key}").semantics { stateDescription = expansion },
                    leadingContent = { AppIcon(app.app, size = 44.dp) },
                    content = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(pluralStringResource(R.plurals.widget_count, app.widgets.size, app.widgets.size)) },
                    trailingContent = { Icon(painterResource(R.drawable.ms_expand_more), null, Modifier.rotate(angle)) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                )
            }
            if (expanded) items(app.widgets, key = { "preview:${it.key}" }, contentType = { "preview" }) { entry ->
                OutlinedCard(
                    onClick = { onSelect(entry.info) }, enabled = enabled,
                    modifier = Modifier.animateItem().padding(horizontal = 16.dp, vertical = 6.dp)
                        .fillMaxWidth().testTag("widget_preview:${entry.key}"),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        WidgetPreview(entry.info, app.app.icon, Modifier.fillMaxWidth().height(184.dp))
                        Text(entry.label, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
internal fun WidgetAppIcon(icon: ImageBitmap?, modifier: Modifier = Modifier.size(44.dp)) {
    if (icon != null) Image(icon, contentDescription = null, modifier = modifier)
    else Icon(painterResource(R.drawable.ms_apps), null, modifier, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

private suspend fun loadWidgetGroups(context: Context, providers: List<AppWidgetProviderInfo>, uiState: LauncherUiState): List<WidgetAppGroup> {
    val pm = context.packageManager
    val launcherApps = context.getSystemService(LauncherApps::class.java)
    val iconSize = (48 * context.resources.displayMetrics.density).toInt().coerceIn(48, 192)
    val englishLabels = EnglishLabelReader(context)
    val repository = AppRepository(context, IconPackRepository(context))
    return providers.groupBy { it.provider.packageName to it.profile }.map { (identity, widgets) ->
        val (packageName, profile) = identity
        val appInfo = runCatching { launcherApps.getApplicationInfo(packageName, 0, profile) }.getOrNull()
        val label = runCatching { appInfo?.loadLabel(pm)?.toString() }.getOrNull() ?: packageName
        val badgedLabel = pm.getUserBadgedLabel(label, profile).toString()
        val icon = runCatching {
            val drawable = appInfo?.loadIcon(pm) ?: widgets.first().loadIcon(context, context.resources.displayMetrics.densityDpi)
            pm.getUserBadgedIcon(drawable, profile).toBitmap(iconSize, iconSize).asImageBitmap()
        }.getOrNull()
        val entries = widgets.map { info ->
            val englishLabel = if (Build.VERSION.SDK_INT >= 31) englishLabels.label(info.activityInfo) else {
                runCatching { pm.getReceiverInfo(info.provider, 0) }.getOrNull()
                    ?.takeIf { it.applicationInfo.uid == appInfo?.uid }?.let(englishLabels::label)
            }
            WidgetEntry(info, runCatching { info.loadLabel(pm) }.getOrDefault(label), englishLabel)
        }.sortedWith(compareBy(LauncherLabelOrder) { it.label })
        val app = uiState.allApps.firstOrNull {
            it.shortcut == null && it.packageName == packageName && (it.user ?: android.os.Process.myUserHandle()) == profile
        } ?: repository.applyIconPacks(listOf(LauncherApp(widgets.first().provider, badgedLabel, icon)),
            uiState.settings.enabledIconPackPackages).single()
        WidgetAppGroup("$packageName:$profile", packageName, badgedLabel, app, entries, appInfo?.let(englishLabels::label))
    }.sortedWith(compareBy(LauncherLabelOrder) { it.label })
}
