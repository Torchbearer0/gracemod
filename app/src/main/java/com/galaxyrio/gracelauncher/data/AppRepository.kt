package com.galaxyrio.gracelauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.LauncherApps
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import android.graphics.drawable.Drawable
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.galaxyrio.gracelauncher.SettingsActivity
import com.galaxyrio.gracelauncher.data.icons.IconPackRepository
import com.galaxyrio.gracelauncher.data.icons.IconDominantColor
import com.galaxyrio.gracelauncher.data.icons.IconSnapshotCache
import com.galaxyrio.gracelauncher.data.icons.ProfileIconSource
import com.galaxyrio.gracelauncher.data.icons.renderIcon
import com.galaxyrio.gracelauncher.data.icons.PackIcon
import com.galaxyrio.gracelauncher.data.icons.iconBitmapBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.time.LocalDate

data class WorkProfile(val user: UserHandle, val serial: Long)

class AppRepository(private val context: Context, private val iconPacks: IconPackRepository = IconPackRepository(context)) {
    private val packageManager = context.packageManager
    private data class PersonalSourceSignature(val configuration: Configuration, val day: LocalDate,
        val label: String, val installedAt: Long, val updatedAt: Long, val version: Long,
        val apkPaths: List<String>, val flags: Int, val iconResource: Int, val revision: Long)
    private val personalSources = IconSnapshotCache<PersonalSourceSignature, LauncherApp>(
        maxBytes = 32 * 1024 * 1024, sizeOf = { iconBitmapBytes(it.icon, it.monochromeIcon) })
    private data class ProfileSourceSignature(val configuration: Configuration, val day: LocalDate,
        val label: String, val installedAt: Long, val apkPaths: List<String>, val flags: Int, val revision: Long)
    private val profileSources = IconSnapshotCache<ProfileSourceSignature, LauncherApp>()
    private data class PackSignature(val source: Any?,
        val packs: List<Any>, val day: LocalDate)
    private val packedIcons = IconSnapshotCache<PackSignature, Pair<String, PackIcon>>(maxBytes = 8 * 1024 * 1024,
        sizeOf = { iconBitmapBytes(it.second.bitmap, it.second.monochrome) })
    private val packageRevisions = mutableMapOf<Pair<UserHandle, String>, Long>()

    /** Package changes invalidate only that installation's artwork, including the personal profile. */
    fun invalidateApps(user: UserHandle, packages: Collection<String>) = synchronized(packageRevisions) {
        packages.forEach { name ->
            val key = user to name
            packageRevisions[key] = (packageRevisions[key] ?: 0L) + 1
        }
    }

    suspend fun loadApps(iconPackPackage: String?): List<LauncherApp> = loadApps(listOfNotNull(iconPackPackage))

    /** Cheap startup validation; no labels, artwork, icon packs or foreign resources are loaded. */
    internal suspend fun launchableComponents(): Set<ComponentName> = withContext(Dispatchers.IO) {
        queryLauncherActivities().map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }.toSet()
    }

    private fun queryLauncherActivities(): List<android.content.pm.ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
    }

    suspend fun loadApps(iconPackPackages: List<String> = emptyList()): List<LauncherApp> = withContext(Dispatchers.IO) {
        val coroutine = currentCoroutineContext()
        val configuration = Configuration(context.resources.configuration)
        val day = LocalDate.now()
        val resolved = queryLauncherActivities()

        val englishLabels = EnglishLabelReader(context)
        val packages = mutableMapOf<String, PackageInfo?>()
        val apps = resolved.asSequence()
            .mapNotNull { resolveInfo ->
                coroutine.ensureActive()
                val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                // Expose only our settings entry, never the launcher/HOME activity.
                if (activityInfo.packageName == context.packageName &&
                    activityInfo.name != SettingsActivity::class.java.name) return@mapNotNull null
                val component = ComponentName(activityInfo.packageName, activityInfo.name)
                val label = resolveInfo.loadLabel(packageManager)
                    .toString()
                    .trim()
                    .ifBlank { activityInfo.name.substringAfterLast('.') }
                val packageInfo = packages.getOrPut(activityInfo.packageName) {
                    @Suppress("DEPRECATION")
                    runCatching { packageManager.getPackageInfo(activityInfo.packageName, 0) }.getOrNull()
                }
                val info = activityInfo.applicationInfo
                val revision = synchronized(packageRevisions) {
                    packageRevisions[Process.myUserHandle() to activityInfo.packageName] ?: 0L
                }
                val signature = PersonalSourceSignature(configuration, day, label, packageInfo?.firstInstallTime ?: 0,
                    packageInfo?.lastUpdateTime ?: 0, packageInfo?.longVersionCode ?: 0,
                    listOfNotNull(info.sourceDir) + info.splitSourceDirs.orEmpty(), info.flags, resolveInfo.iconResource, revision)
                personalSources.get(component.flattenToString(), signature) ?: run {
                    val drawable = runCatching { resolveInfo.loadIcon(packageManager) }.getOrNull()
                    makeApp(component, label, drawable, info.flags).copy(
                        firstInstallTime = signature.installedAt, isLauncherSettings = activityInfo.packageName == context.packageName,
                        englishLabel = englishLabels.label(resolveInfo), iconSourceRevision = signature).also { app ->
                        coroutine.ensureActive()
                        if (app.icon != null) personalSources.put(app.key, signature, app)
                    }
                }
            }
            .distinctBy(LauncherApp::key)
            .sortedWith(LauncherAppOrder)
            .toList()
        applyIconPacks(apps, iconPackPackages)
    }

    fun workProfiles(): List<WorkProfile> {
        val launcher = context.getSystemService(LauncherApps::class.java)
        val users = context.getSystemService(UserManager::class.java)
        return launcher.profiles.filter { user ->
            user != Process.myUserHandle() && (Build.VERSION.SDK_INT < 35 ||
                launcher.getLauncherUserInfo(user)?.userType == UserManager.USER_TYPE_PROFILE_MANAGED)
        }.mapNotNull { user -> users.getSerialNumberForUser(user).takeIf { it >= 0 }?.let { WorkProfile(user, it) } }
    }

    suspend fun loadWorkApps(profile: WorkProfile, iconPackPackages: List<String>): List<LauncherApp> {
        val users = context.getSystemService(UserManager::class.java)
        return if (users.isQuietModeEnabled(profile.user) || !users.isUserUnlocked(profile.user)) emptyList()
        else loadProfileApps(profile.user, profile.serial, iconPackPackages, work = true)
    }

    suspend fun loadPrivateApps(user: UserHandle, serial: Long, iconPackPackages: List<String>): List<LauncherApp> =
        loadProfileApps(user, serial, iconPackPackages, work = false)

    private suspend fun loadProfileApps(user: UserHandle, serial: Long, iconPackPackages: List<String>, work: Boolean): List<LauncherApp> = withContext(Dispatchers.IO) {
        val coroutine = currentCoroutineContext()
        val configuration = Configuration(context.resources.configuration)
        val day = LocalDate.now()
        val englishLabels = EnglishLabelReader(context)
        val apps = context.getSystemService(LauncherApps::class.java).getActivityList(null, user).map { activity ->
            coroutine.ensureActive()
            val component = activity.componentName
            val label = activity.label.toString().trim().ifBlank { component.shortClassName }
            val info = activity.applicationInfo
            val revision = synchronized(packageRevisions) { packageRevisions[user to component.packageName] ?: 0L }
            val signature = ProfileSourceSignature(configuration, day, label, activity.firstInstallTime,
                listOfNotNull(info.sourceDir) + info.splitSourceDirs.orEmpty(), info.flags, revision)
            val key = "profile:$serial:${component.flattenToString()}"
            profileSources.get(key, signature) ?: run {
                val drawable = runCatching { activity.getIcon(0) }.getOrNull()
                val source = runCatching { drawable?.let(ProfileIconSource::fromDrawable) }.getOrNull()
                makeApp(component, label, drawable, info.flags)
                    .let { app -> app.copy(user = user, userSerial = serial, isPrivateSpace = !work, isWorkProfile = work,
                        firstInstallTime = activity.firstInstallTime, profileIconSource = source, englishLabel = englishLabels.label(activity),
                        // Share the bounded source bitmap instead of retaining a second raw copy.
                        icon = source?.layers()?.original?.asImageBitmap() ?: app.icon) }
                    .also { app ->
                        coroutine.ensureActive()
                        // A transient profile permission/availability failure must be retried.
                        if (app.icon != null) profileSources.put(key, signature, app)
                    }
            }
        }.distinctBy(LauncherApp::key).sortedWith(LauncherAppOrder)
        applyIconPacks(apps, iconPackPackages)
    }

    suspend fun applyIconPacks(apps: List<LauncherApp>, iconPackPackages: List<String>): List<LauncherApp> = withContext(Dispatchers.IO) {
        val packs = normalizeIconPackOrder(iconPackPackages).mapNotNull { iconPacks.load(it) }
        if (packs.isEmpty()) return@withContext apps
        apps.map { app ->
            val signature = PackSignature(app.iconSourceRevision ?: app.profileIconSource?.cacheKey ?: app.icon,
                packs.map { it.cacheKey }, LocalDate.now())
            val (packageName, icon) = packedIcons.get(app.key, signature) ?: run {
                val match = firstMatchingPackIcon(packs) { it.iconFor(app.componentName, null) } ?: return@map app
                (match.first.packageName to match.second).also { packedIcons.put(app.key, signature, it) }
            }
            app.copy(icon = icon.bitmap, monochromeIcon = icon.monochrome, monochromeScale = icon.monochromeScale,
                iconPackPackage = packageName, themeIconPackPackage = packageName)
        }
    }

    private fun makeApp(component: ComponentName, label: String, drawable: Drawable?, flags: Int): LauncherApp {
        // Sample the provider's original drawable on IO, before considering packs.
        // Only the color survives; previews/drags never reload or resample icons.
        val originalColor = runCatching { drawable?.let {
            val sample = renderIcon(it, 48)
            try { IconDominantColor.of(sample) } finally { sample.recycle() }
        } }.getOrNull()
        val icon = runCatching { drawable?.toBitmap(144, 144)?.asImageBitmap() }.getOrNull()
        val monochrome = if (Build.VERSION.SDK_INT >= 33) {
            runCatching {
                (drawable as? AdaptiveIconDrawable)?.monochrome?.toBitmap(144, 144)?.let { bitmap ->
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    bitmap.takeIf { pixels.count { pixel -> pixel ushr 24 > 32 } >= pixels.size / 100 }?.asImageBitmap()
                }
            }.getOrNull()
        } else null
        return LauncherApp(componentName = component, label = label, icon = icon, monochromeIcon = monochrome,
            isSystemApp = flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
            isAdaptiveIcon = drawable is AdaptiveIconDrawable, originalIconColor = originalColor)
    }

    fun launch(
        app: LauncherApp,
        sourceBounds: Rect? = null,
        options: Bundle? = null,
    ): Boolean = runCatching {
        val user = app.user ?: Process.myUserHandle()
        if (user == Process.myUserHandle()) {
            // Match Launcher3/Lawnchair: current-user entry activities may perform
            // OEM authentication or forwarding before opening the actual app.
            val intent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(app.componentName)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                .also { it.sourceBounds = sourceBounds }
            context.startActivity(intent, options)
        } else {
            // Never fall back to the personal installation of a cross-profile app.
            context.getSystemService(LauncherApps::class.java).startMainActivity(
                app.componentName, user, sourceBounds, options,
            )
        }
    }.onFailure { error ->
        Log.e("AppRepository", "Unable to launch ${app.componentName} for ${app.user ?: Process.myUserHandle()}", error)
    }.isSuccess
}
