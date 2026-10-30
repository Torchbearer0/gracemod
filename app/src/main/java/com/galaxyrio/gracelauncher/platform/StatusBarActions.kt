package com.galaxyrio.gracelauncher.platform

import android.content.Context
import com.galaxyrio.gracelauncher.data.GraceButtonAction

/** Opt-in, best-effort non-SDK APIs. No hidden-API exemptions or elevated permissions. */
internal object StatusBarActions {
    // Resolve each method only on first use, not while scrolling or rendering settings.
    private val managerClass by lazy { runCatching { Class.forName("android.app.StatusBarManager") }.getOrNull() }
    private val notifications by lazy { runCatching { managerClass?.getMethod("expandNotificationsPanel") }.getOrNull() }
    // Use the no-argument overload; the String overload has stricter target-SDK restrictions.
    private val quickSettings by lazy { runCatching { managerClass?.getMethod("expandSettingsPanel") }.getOrNull() }

    fun expand(context: Context, action: GraceButtonAction): Boolean = runCatching {
        val method = when (action) {
            GraceButtonAction.Notifications -> notifications
            GraceButtonAction.QuickSettings -> quickSettings
            else -> null
        } ?: return false
        val service = context.getSystemService("statusbar") ?: return false
        method.invoke(service)
        // This confirms dispatch only. OEMs may silently ignore the request;
        // the user decides whether to keep the Lab setting enabled.
        true
    }.getOrDefault(false)
}
