package com.galaxyrio.gracelauncher.data

import androidx.room.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import org.json.JSONArray
import org.json.JSONObject

/**
 * Shared Icon designer data, also written by the desktop's per-item editor.
 * Overrides precede the enabled pack order. Resource names survive pack updates.
 */
data class ItemIcon(val kind: String, val source: String = "", val name: String = "", val design: IconDesign? = null,
    /** Per-item icon-display override, null inherits the shared setting. */
    val display: IconDisplaySettings? = null) {
    /** Used by launcher rendering and editor previews alike; never mutate the saved patch. */
    fun resolve(shared: IconDesign, sharedDisplay: IconDisplaySettings, isShortcut: Boolean = false): ItemIcon {
        val defaults = shared.itemDefaults(isShortcut)
        val displayDefaults = if (shared.appliesTo(isShortcut)) sharedDisplay else IconDisplaySettings()
        return copy(
            design = (design?.withThemeDefaults(defaults) ?: defaults).copy(iconSize = shared.iconSize),
            display = display?.resolve(displayDefaults) ?: displayDefaults.takeUnless { shared.appliesTo(isShortcut) },
        )
    }

    fun overridesFrom(shared: IconDesign, sharedDisplay: IconDisplaySettings, isShortcut: Boolean = false): ItemIcon = copy(
        design = design?.overridesFrom(shared.itemDefaults(isShortcut)),
        display = display?.overridesFrom(if (shared.appliesTo(isShortcut)) sharedDisplay else IconDisplaySettings()),
    )

    fun encode(): String = JSONObject().put("kind", kind).put("source", source).put("name", name)
        .apply {
            design?.let { put("design", it.json()) }
            display?.let { put("display", it.json()) }
        }.toString()
    companion object {
        val System = ItemIcon("system")
        val Theme = ItemIcon("theme")
        fun decode(json: String): ItemIcon? = runCatching {
            val value = JSONObject(json)
            ItemIcon(value.getString("kind"), value.optString("source"), value.optString("name"),
                IconDesign.decode(value.optJSONObject("design")),
                value.optJSONObject("display")?.let { IconDisplaySettings.decode(it.toString()) })
                .takeIf { it.kind in setOf("system", "theme", "pack", "image", "symbol") }
        }.getOrNull()
    }
}

/** Items reference the same app/shortcut identity everywhere; widgets own a separate host ID. */
data class PopupItem(val key: String, val widget: HomeLayout? = null,
    /** Used only by Widget screen. Other mixed-content surfaces remain ordered lists. */
    val grid: WidgetGridPlacement? = null) {
    companion object {
        fun encode(items: List<PopupItem>): String = JSONArray().apply {
            items.distinctBy { it.key }.forEach { item -> put(JSONObject().apply {
                put("key", item.key)
                item.widget?.let { put("widget", JSONObject(it.encode())) }
                item.grid?.let { put("grid", it.json()) }
            }) }
        }.toString()
        fun decode(json: String): List<PopupItem> = runCatching {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { index ->
                val entry = array.getJSONObject(index)
                val key = entry.optString("key").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                PopupItem(key, entry.optJSONObject("widget")?.let { HomeLayout.decode(it.toString()) },
                    WidgetGridPlacement.decode(entry.optJSONObject("grid")))
            }.distinctBy { it.key }
        }.getOrDefault(emptyList())
    }
}

@Entity(tableName = "item_icons")
data class ItemIconEntity(@PrimaryKey val itemKey: String, val iconJson: String)

@Entity(tableName = "saved_shortcuts")
data class SavedShortcutEntity(
    @PrimaryKey val itemKey: String, val packageName: String, val shortcutId: String,
    val activity: String, val label: String, val showInAppList: Boolean = false,
)

@Entity(tableName = "app_popups")
data class AppPopupEntity(@PrimaryKey val ownerKey: String, val itemsJson: String)

@Dao
abstract class LauncherItemsDao {
    @Query("SELECT * FROM item_icons") abstract fun icons(): kotlinx.coroutines.flow.Flow<List<ItemIconEntity>>
    @Query("SELECT * FROM item_icons") abstract suspend fun readIcons(): List<ItemIconEntity>
    @Query("SELECT * FROM saved_shortcuts") abstract fun shortcuts(): kotlinx.coroutines.flow.Flow<List<SavedShortcutEntity>>
    @Query("SELECT * FROM app_popups") abstract fun popups(): kotlinx.coroutines.flow.Flow<List<AppPopupEntity>>
    @Query("SELECT * FROM app_popups") abstract suspend fun readPopups(): List<AppPopupEntity>
    @Query("SELECT * FROM app_popups WHERE ownerKey = :key") abstract suspend fun popup(key: String): AppPopupEntity?
    @Query("SELECT * FROM saved_shortcuts WHERE itemKey = :key") abstract suspend fun shortcut(key: String): SavedShortcutEntity?
    @Upsert abstract suspend fun saveIcon(value: ItemIconEntity)
    @Query("DELETE FROM item_icons WHERE itemKey = :key") abstract suspend fun resetIcon(key: String)
    @Query("DELETE FROM item_icons WHERE itemKey IN (:keys)") abstract suspend fun resetIcons(keys: List<String>)
    @Upsert abstract suspend fun saveShortcut(value: SavedShortcutEntity)
    @Upsert abstract suspend fun savePopup(value: AppPopupEntity)
    @Query("DELETE FROM app_popups WHERE ownerKey = :key") abstract suspend fun deletePopup(key: String)
}

data class LauncherItemsSnapshot(
    val icons: Map<String, ItemIcon> = emptyMap(),
    val shortcuts: List<SavedShortcutEntity> = emptyList(),
    val popups: Map<String, List<PopupItem>> = emptyMap(),
)

class LauncherItemsRepository(private val database: LauncherDatabase) {
    private val dao = database.itemsDao()
    val snapshots = combine(dao.icons(), dao.shortcuts(), dao.popups()) { icons, shortcuts, popups ->
        LauncherItemsSnapshot(
            icons.mapNotNull { row -> ItemIcon.decode(row.iconJson)?.let { row.itemKey to it } }.toMap(),
            shortcuts, popups.associate { it.ownerKey to PopupItem.decode(it.itemsJson) },
        )
    }.distinctUntilChanged()

    suspend fun saveIcon(key: String, icon: ItemIcon?) = database.withTransaction {
        // Built-in groups have stable icon identities but deliberately have no folder row.
        when (key) {
            PrivateSpaceFolderKey, WorkProfileFolderKey, RecentlyInstalledFolderKey -> Unit
            else -> requireExistingFolder(key)
        }
        if (icon == null) dao.resetIcon(key) else dao.saveIcon(ItemIconEntity(key, icon.encode()))
    }

    private suspend fun requireExistingFolder(key: String) {
        if (key.startsWith("folder:") && key != PrivateSpaceFolderKey) check(database.settingsDao().folder(key.removePrefix("folder:")) != null) {
            "Folder no longer exists"
        }
    }

    /** Full icon reset — intentionally clears display overrides too. */
    suspend fun resetIcons(keys: Set<String>): List<ItemIcon> = database.withTransaction {
        val previous = dao.readIcons().filter { it.itemKey in keys }.mapNotNull { ItemIcon.decode(it.iconJson) }
        keys.toList().chunked(900).forEach { dao.resetIcons(it) }
        previous
    }

    /** A special design can inherit the bulk image source; retain it until its final reference is gone. */
    suspend fun isImageReferenced(name: String): Boolean {
        fun ItemIcon?.matches() = this?.kind == "image" && source == name
        return dao.readIcons().any { ItemIcon.decode(it.iconJson).matches() } ||
            database.settingsDao().readSettings()?.iconDesignJson?.let(ItemIcon::decode).matches()
    }

    suspend fun rememberShortcut(app: LauncherApp, showInAppList: Boolean? = null) {
        val shortcut = app.shortcut ?: return
        database.withTransaction {
            val previous = dao.shortcut(app.key)
            dao.saveShortcut(SavedShortcutEntity(app.key, shortcut.packageName, shortcut.id,
                app.componentName.flattenToString(), app.originalLabel,
                showInAppList ?: previous?.showInAppList ?: false))
        }
    }

    /** Atomic edits also preserve widgets appended by the separate Android setup Activity. */
    suspend fun updatePopup(key: String, defaults: List<PopupItem>, transform: (List<PopupItem>) -> List<PopupItem>): List<Int> =
        database.withTransaction {
            requireExistingFolder(key)
            if (key == HomeContentKey) LauncherSettingsRepository(database).migrateLegacyHomeWidget()
            val previous = dao.popup(key)?.let { PopupItem.decode(it.itemsJson) } ?: defaults
            val stack = key.takeIf { it.startsWith("folder:") }?.removePrefix("folder:")
                ?.let { database.settingsDao().folder(it) }?.isAppStack == true
            val next = transform(previous).distinctBy { it.key }.filter {
                (!stack || (it.widget == null && !it.key.startsWith("folder:"))) &&
                    (key == WidgetScreenKey || (it.key != PrivateSpaceFolderKey && it.key != WorkProfileFolderKey))
            }
            dao.savePopup(AppPopupEntity(key, PopupItem.encode(next)))
            val retained = next.mapNotNull { it.widget?.widgetId }.toSet()
            previous.mapNotNull { it.widget?.widgetId }.filterNot { it in retained }
        }
}
