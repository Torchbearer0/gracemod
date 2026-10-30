package com.galaxyrio.gracelauncher.ui.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.material3.Text
import androidx.compose.material3.AssistChip
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.WidgetSetupActivity
import com.galaxyrio.gracelauncher.data.HomeLayout

/** Always expose settings; some providers have a configure Activity but omit feature flags. */
@Composable
internal fun WidgetConfigurationButton(layout: HomeLayout, onBeforeOpen: () -> Unit) {
    val context = LocalContext.current
    AssistChip(onClick = {
        // Look up at click time so provider updates do not leave a stale settings target.
        val info = runCatching { AppWidgetManager.getInstance(context).getAppWidgetInfo(layout.widgetId) }
            .getOrElse {
                Log.e("GraceWidgetSetup", "settings lookup failed id=${layout.widgetId}", it)
                Toast.makeText(context, R.string.widget_configuration_launch_error, Toast.LENGTH_LONG).show()
                return@AssistChip
            }
        if (info?.configure == null) {
            Toast.makeText(context, R.string.widget_no_configuration, Toast.LENGTH_SHORT).show()
            return@AssistChip
        }
        onBeforeOpen()
        runCatching {
            context.startActivity(Intent(context, WidgetSetupActivity::class.java)
                .putExtra(WidgetSetupActivity.EXTRA_CONFIGURE_ID, layout.widgetId))
        }.onFailure {
            Log.e("GraceWidgetSetup", "reconfigure launch failed id=${layout.widgetId} provider=${info.provider}", it)
            Toast.makeText(context, R.string.widget_configuration_launch_error, Toast.LENGTH_LONG).show()
        }
    }, label = { Text(stringResource(R.string.widget_settings_button), maxLines = 1) },
        modifier = Modifier.testTag("widget_settings"))
}
