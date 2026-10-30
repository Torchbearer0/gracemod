package com.galaxyrio.gracelauncher.data

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.PackageItemInfo
import android.content.pm.ResolveInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Process
import android.os.UserHandle
import java.util.Locale

/**
 * One reader per inventory load: resource objects are reused within the load, never across package
 * updates. Read installed resources only, without loading foreign code or mutating shared Resources.
 * English may be absent (including a missing language split); Android's normal fallback is valid.
 */
internal class EnglishLabelReader(private val context: Context) {
    private val configuration = Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) }
    private val resources = mutableMapOf<Pair<Int, String>, Resources?>()

    fun label(info: ApplicationInfo): String? = read(info, info)

    fun label(info: ActivityInfo): String? =
        read(info, info.applicationInfo) ?: label(info.applicationInfo)

    fun label(info: ResolveInfo): String? {
        info.nonLocalizedLabel?.toString()?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
        val activity = info.activityInfo ?: return null
        if (info.labelRes != 0) {
            val owner = if (info.resolvePackageName == null) activity.applicationInfo else {
                runCatching { context.packageManager.getApplicationInfo(info.resolvePackageName, 0) }.getOrNull()
            }
            owner?.let { text(it, info.labelRes) }?.let { return it }
        }
        return label(activity)
    }

    fun label(info: LauncherActivityInfo): String? =
        if (Build.VERSION.SDK_INT >= 31) label(info.activityInfo)
        // Older public APIs do not expose the profile activity's label resource.
        else null

    private fun read(info: PackageItemInfo, app: ApplicationInfo): String? =
        info.nonLocalizedLabel?.toString()?.trim()?.takeIf(String::isNotEmpty)
            ?: info.labelRes.takeIf { it != 0 }?.let { text(app, it) }

    private fun text(app: ApplicationInfo, id: Int): String? = runCatching {
        val key = app.uid to app.packageName
        if (!resources.containsKey(key)) resources[key] = runCatching {
            if (Build.VERSION.SDK_INT >= 31) {
                context.packageManager.getResourcesForApplication(app, configuration)
            } else {
                // Never accidentally read another user's installation on older Android versions.
                check(UserHandle.getUserHandleForUid(app.uid) == Process.myUserHandle())
                context.createPackageContext(app.packageName, 0)
                    .createConfigurationContext(configuration).resources
            }
        }.getOrNull()
        resources[key]?.getText(id)?.toString()?.trim()?.takeIf(String::isNotEmpty)
    }.getOrNull()
}
