package com.galaxyrio.gracelauncher.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.SearchNames
import com.galaxyrio.gracelauncher.data.searchNames
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@Composable
internal fun rememberAppSearchNames(
    apps: List<LauncherApp>,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
): Map<String, SearchNames>? {
    // A new app list gets fresh state immediately; a previous index must never
    // be used with new keys or renamed labels while its replacement is building.
    val names = remember(apps, dispatcher) { mutableStateOf<Map<String, SearchNames>?>(null) }
    LaunchedEffect(names) {
        names.value = withContext(dispatcher) {
            apps.associate { app ->
                ensureActive()
                app.key to app.searchNames()
            }
        }
    }
    return names.value
}
