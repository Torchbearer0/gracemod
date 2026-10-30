package com.galaxyrio.gracelauncher.ui.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import com.galaxyrio.gracelauncher.platform.widgetLongPressTimeout

/** Match the native host for unavailable widgets and already-selected widget bodies. */
@Composable
internal fun WidgetLongPressConfiguration(content: @Composable () -> Unit) {
    val system = LocalViewConfiguration.current
    val configuration = remember(system) {
        object : ViewConfiguration by system {
            override val longPressTimeoutMillis = widgetLongPressTimeout(system.longPressTimeoutMillis)
        }
    }
    CompositionLocalProvider(LocalViewConfiguration provides configuration, content = content)
}
