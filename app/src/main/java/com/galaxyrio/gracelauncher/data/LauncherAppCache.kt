package com.galaxyrio.gracelauncher.data

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import android.util.Base64
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.galaxyrio.gracelauncher.data.icons.isGraceButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

/** Rendered personal-profile rows survive process death without re-querying icon providers.
 * Read only after settings/items load, so hidden apps and profile protection still apply.
 * This disposable file is excluded from backup and is never a source of launcher settings.
 */
internal class LauncherAppCache(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "launcher-apps.json"))

    suspend fun load(key: String, installed: Set<ComponentName>): List<LauncherApp>? = withContext(Dispatchers.IO) {
        mutex.withLock {
            bestEffort(null) {
                if (!file.baseFile.exists()) return@bestEffort null
                val root = JSONObject(file.readFully().toString(Charsets.UTF_8))
                if (root.getInt("version") != 3 || root.getString("key") != key) return@bestEffort null
                val coroutine = currentCoroutineContext()
                val entries = root.getJSONArray("apps")
                List(entries.length()) { index ->
                    coroutine.ensureActive()
                    decode(entries.getJSONObject(index))
                }.filter { it.folderId != null || it.isGraceButton || it.componentName in installed }
            }
        }
    }

    suspend fun save(key: String, apps: List<LauncherApp>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            bestEffort(Unit) {
                val coroutine = currentCoroutineContext()
                val entries = JSONArray()
                apps.filter(::personal).distinctBy(LauncherApp::key).forEach { app ->
                    coroutine.ensureActive()
                    entries.put(encode(app))
                }
                val root = JSONObject().put("version", 3).put("key", key).put("apps", entries)
                val bytes = root.toString().toByteArray(Charsets.UTF_8)
                coroutine.ensureActive()
                val stream = file.startWrite()
                try {
                    stream.write(bytes)
                    coroutine.ensureActive()
                    file.finishWrite(stream)
                } catch (error: Exception) {
                    file.failWrite(stream)
                    throw error
                }
            }
        }
    }

    private fun personal(app: LauncherApp) = app.user == null && app.userSerial == null &&
        !app.isPrivateSpace && !app.isWorkProfile && app.shortcut?.user == null && app.shortcut?.userSerial == null &&
        app.shortcut?.isPrivateSpace != true

    private fun encode(app: LauncherApp) = JSONObject().apply {
        put("component", app.componentName.flattenToString())
        put("label", app.label); put("originalLabel", app.originalLabel)
        put("icon", encodeIcon(app.icon)); put("monochrome", encodeIcon(app.monochromeIcon))
        put("scale", app.monochromeScale); put("pack", app.iconPackPackage); put("themePack", app.themeIconPackPackage)
        put("system", app.isSystemApp); put("adaptive", app.isAdaptiveIcon)
        put("installedAt", app.firstInstallTime); put("settings", app.isLauncherSettings)
        put("originalColor", app.originalIconColor); put("englishLabel", app.englishLabel)
        put("folder", app.folderId); put("stack", app.isAppStack)
        app.iconDisplay?.let { put("display", it.json()) }
        app.shortcut?.let { shortcut -> put("shortcut", JSONObject().apply {
            put("id", shortcut.id); put("package", shortcut.packageName); put("label", shortcut.label)
            put("activity", shortcut.activity?.flattenToString()); put("icon", encodeIcon(shortcut.icon))
        }) }
    }

    private fun decode(entry: JSONObject): LauncherApp {
        fun optional(name: String) = entry.optString(name).takeIf(String::isNotEmpty)
        val component = requireNotNull(ComponentName.unflattenFromString(entry.getString("component")))
        val shortcut = entry.optJSONObject("shortcut")?.let {
            LauncherShortcut(it.getString("id"), it.getString("package"), it.getString("label"),
                decodeIcon(it.optString("icon")), it.optString("activity").takeIf(String::isNotEmpty)
                    ?.let(ComponentName::unflattenFromString))
        }
        return LauncherApp(component, entry.getString("label"), decodeIcon(entry.optString("icon")),
            monochromeIcon = decodeIcon(entry.optString("monochrome")), originalLabel = entry.getString("originalLabel"),
            monochromeScale = entry.getDouble("scale").toFloat(), iconPackPackage = optional("pack"),
            themeIconPackPackage = optional("themePack"), shortcut = shortcut,
            isSystemApp = entry.getBoolean("system"), isAdaptiveIcon = entry.getBoolean("adaptive"),
            firstInstallTime = entry.getLong("installedAt"), isLauncherSettings = entry.getBoolean("settings"),
            originalIconColor = if (entry.has("originalColor")) entry.getInt("originalColor") else null,
            englishLabel = optional("englishLabel"), folderId = optional("folder"), isAppStack = entry.getBoolean("stack"),
            iconDisplay = entry.optJSONObject("display")?.let { IconDisplaySettings.decode(it.toString()) })
    }

    private fun encodeIcon(icon: ImageBitmap?): String = icon?.let {
        ByteArrayOutputStream().use { output ->
            check(it.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output))
            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        }
    }.orEmpty()

    private fun decodeIcon(encoded: String): ImageBitmap? = if (encoded.isEmpty()) null else {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)).asImageBitmap()
    }

    private inline fun <T> bestEffort(fallback: T, action: () -> T): T = try { action() }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) {
        Log.w("LauncherAppCache", "Unable to use startup preview; live apps will still load", error)
        fallback
    }

    companion object {
        private val mutex = Mutex()
        fun key(inputs: String): String = MessageDigest.getInstance("SHA-256")
            .digest(inputs.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
