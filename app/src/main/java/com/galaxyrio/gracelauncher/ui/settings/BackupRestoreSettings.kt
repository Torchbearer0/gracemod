package com.galaxyrio.gracelauncher.ui.settings

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Process
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.data.LauncherDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Composable
internal fun BackupRestoreSettings(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) { runCatching { writeBackup(context, uri) } }
            busy = false
            Toast.makeText(context, if (result.isSuccess) "Backup created" else "Backup failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
        }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val valid = withContext(Dispatchers.IO) { runCatching { validateBackup(context, uri) }.getOrDefault(false) }
            busy = false
            if (valid) pendingRestore = uri
            else Toast.makeText(context, "This backup is invalid or incomplete", Toast.LENGTH_LONG).show()
        }
    }

    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Backup & Restore", style = MaterialTheme.typography.headlineSmall)
        Text("Create one ZIP file containing the launcher database (settings, favourites, folders, home arrangement and widget configuration) and a copy of your current system wallpaper.")
        Button(onClick = { createBackup.launch("GraceLauncher-backup.zip") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "Working…" else "Create backup")
        }
        Button(onClick = { openBackup.launch(arrayOf("application/zip", "application/octet-stream")) }, enabled = !busy,
            modifier = Modifier.fillMaxWidth()) { Text("Restore backup") }
        Text("Restore replaces the launcher's current saved data. Android may require hosted widgets to be added again because widget provider IDs can change. Wallpaper restoration is attempted using Android's wallpaper permission.",
            style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onBack) { Text("Back") }
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Replace current launcher data?") },
            text = { Text("This will replace your current launcher settings, favourites, home layout, folders and widget configuration. The backup wallpaper will also be restored if present. This cannot be undone.") },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    pendingRestore = null
                    scope.launch {
                        busy = true
                        val result = withContext(Dispatchers.IO) { runCatching { restoreBackup(context, uri) } }
                        busy = false
                        if (result.isFailure) Toast.makeText(context, "Restore failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }) { Text("Replace and restore") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } },
        )
    }
}

private fun writeBackup(context: Context, uri: Uri) {
    val database = LauncherDatabase.getInstance(context)
    database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { }
    val databaseFile = context.getDatabasePath("grace_launcher.db")
    require(databaseFile.isFile) { "Launcher database not found" }
    context.contentResolver.openOutputStream(uri)?.use { output ->
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("launcher.db")); databaseFile.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            // Legacy preferences still hold a few compatibility settings. Keep them
            // alongside the Room database so the archive is a complete app-state snapshot.
            File(context.applicationInfo.dataDir, "shared_prefs").listFiles()?.forEach { preferenceFile ->
                if (preferenceFile.isFile && !preferenceFile.name.contains("..")) {
                    zip.putNextEntry(ZipEntry("shared_prefs/${preferenceFile.name}"))
                    preferenceFile.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            val wallpaper = currentWallpaperBitmap(context)
            if (wallpaper != null) {
                zip.putNextEntry(ZipEntry("wallpaper.png"))
                wallpaper.compress(Bitmap.CompressFormat.PNG, 100, zip)
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("backup-info.txt")); zip.write("GraceLauncher backup v1".toByteArray()); zip.closeEntry()
        }
    } ?: error("Could not open destination")
}

private fun validateBackup(context: Context, uri: Uri): Boolean {
    var validDb = false
    context.contentResolver.openInputStream(uri)?.use { input ->
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "launcher.db") {
                    val header = ByteArray(16)
                    var read = 0
                    while (read < header.size) {
                        val count = zip.read(header, read, header.size - read)
                        if (count <= 0) break
                        read += count
                    }
                    validDb = read == 16 && header.copyOfRange(0, 15).toString(Charsets.US_ASCII) == "SQLite format 3"
                }
                zip.closeEntry()
            }
        }
    }
    return validDb
}

private fun restoreBackup(context: Context, uri: Uri) {
    val tempDb = File(context.cacheDir, "restore-launcher.db")
    var wallpaper: Bitmap? = null
    context.contentResolver.openInputStream(uri)?.use { input ->
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when (entry.name) {
                    "launcher.db" -> tempDb.outputStream().use { zip.copyTo(it) }
                    "wallpaper.png" -> wallpaper = BitmapFactoryCompat.decode(zip)
                    else -> if (entry.name.startsWith("shared_prefs/") && !entry.name.contains("..")) {
                        val name = entry.name.removePrefix("shared_prefs/")
                        if (name.isNotBlank() && !name.contains('/')) {
                            val target = File(context.applicationInfo.dataDir, "shared_prefs/$name")
                            target.parentFile?.mkdirs()
                            target.outputStream().use { zip.copyTo(it) }
                        }
                    }
                }
                zip.closeEntry()
            }
        }
    } ?: error("Could not read backup")
    require(tempDb.isFile && tempDb.length() > 16L) { "Backup database missing" }
    LauncherDatabase.closeInstanceForRestore()
    val databaseFile = context.getDatabasePath("grace_launcher.db")
    databaseFile.parentFile?.mkdirs()
    File(databaseFile.path + "-wal").delete()
    File(databaseFile.path + "-shm").delete()
    tempDb.copyTo(databaseFile, overwrite = true)
    tempDb.delete()
    wallpaper?.let { bitmap ->
        runCatching { WallpaperManager.getInstance(context).setBitmap(bitmap) }
    }
    Process.killProcess(Process.myPid())
}

private object BitmapFactoryCompat {
    fun decode(input: java.io.InputStream): Bitmap? = android.graphics.BitmapFactory.decodeStream(input)
}

private fun currentWallpaperBitmap(context: Context): Bitmap? = runCatching {
    val drawable = WallpaperManager.getInstance(context).drawable ?: return null
    val width = drawable.intrinsicWidth.coerceAtLeast(1)
    val height = drawable.intrinsicHeight.coerceAtLeast(1)
    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
    }
}.getOrNull()
