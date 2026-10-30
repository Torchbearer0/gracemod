package com.galaxyrio.gracelauncher.ui

import android.Manifest
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.net.Uri
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.os.Process
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.galaxyrio.gracelauncher.data.AppRepository
import com.galaxyrio.gracelauncher.data.LauncherAppCache
import com.galaxyrio.gracelauncher.data.CalendarRepository
import com.galaxyrio.gracelauncher.data.CatalogRefreshState
import com.galaxyrio.gracelauncher.data.FavoritesStore
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.LauncherAppOrder
import com.galaxyrio.gracelauncher.data.LauncherDatabase
import com.galaxyrio.gracelauncher.data.LauncherFolder
import com.galaxyrio.gracelauncher.data.FolderPlacement
import com.galaxyrio.gracelauncher.data.LauncherPreferences
import com.galaxyrio.gracelauncher.data.LauncherSettings
import com.galaxyrio.gracelauncher.data.ClockStyle
import com.galaxyrio.gracelauncher.data.LauncherSettingsRepository
import com.galaxyrio.gracelauncher.data.LauncherShortcut
import com.galaxyrio.gracelauncher.data.ScheduleEvent
import com.galaxyrio.gracelauncher.data.ShortcutRepository
import com.galaxyrio.gracelauncher.data.ShortcutResult
import com.galaxyrio.gracelauncher.data.WallpaperTextMode
import com.galaxyrio.gracelauncher.data.icons.IconPackInfo
import com.galaxyrio.gracelauncher.data.icons.IconPackRepository
import com.galaxyrio.gracelauncher.data.icons.IconPackStatus
import com.galaxyrio.gracelauncher.data.icons.ItemIconStore
import com.galaxyrio.gracelauncher.data.icons.GraceButtonIcon
import com.galaxyrio.gracelauncher.data.icons.isGraceButton
import com.galaxyrio.gracelauncher.data.ItemIcon
import com.galaxyrio.gracelauncher.data.IconDesign
import com.galaxyrio.gracelauncher.data.launcherThemeSeed
import com.galaxyrio.gracelauncher.data.PopupItem
import com.galaxyrio.gracelauncher.data.LauncherItemsRepository
import com.galaxyrio.gracelauncher.data.LauncherItemsSnapshot
import com.galaxyrio.gracelauncher.data.PrivateSpaceFolderId
import com.galaxyrio.gracelauncher.data.RecentlyInstalledFolderId
import com.galaxyrio.gracelauncher.data.PrivateSpaceFolderKey
import com.galaxyrio.gracelauncher.data.PrivateSpaceDefaultName
import com.galaxyrio.gracelauncher.data.PrivateSpaceDisplay
import com.galaxyrio.gracelauncher.data.WorkProfile
import com.galaxyrio.gracelauncher.data.WorkProfileFolderId
import com.galaxyrio.gracelauncher.data.WorkProfileFolderKey
import com.galaxyrio.gracelauncher.data.WorkProfileDefaultName
import com.galaxyrio.gracelauncher.data.PrivateAppCache
import com.galaxyrio.gracelauncher.platform.PrivateSpaceController
import com.galaxyrio.gracelauncher.platform.PrivateSpaceState
import com.galaxyrio.gracelauncher.platform.DefaultHome
import com.galaxyrio.gracelauncher.platform.BreezyWeatherUpdates
import com.galaxyrio.gracelauncher.data.media.MediaCommand
import com.galaxyrio.gracelauncher.data.media.MediaSessionRepository
import com.galaxyrio.gracelauncher.data.media.MediaSnapshot
import com.galaxyrio.gracelauncher.data.media.NowPlaying
import com.galaxyrio.gracelauncher.data.notifications.AppNotification
import com.galaxyrio.gracelauncher.data.notifications.appNotifications
import com.galaxyrio.gracelauncher.data.weather.BreezyWeatherRepository
import com.galaxyrio.gracelauncher.data.weather.WeatherState
import com.galaxyrio.gracelauncher.data.weather.WeatherStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

enum class ScheduleStatus {
    PermissionRequired,
    Loading,
    Ready,
    Error,
}

data class LauncherUiState(
    val apps: List<LauncherApp> = emptyList(),
    val favoriteKeys: Set<String> = emptySet(),
    val isLoadingApps: Boolean = true,
    val appLoadFailed: Boolean = false,
    val events: List<ScheduleEvent> = emptyList(),
    val scheduleStatus: ScheduleStatus = ScheduleStatus.PermissionRequired,
    val hasShortcutAccess: Boolean = false,
    val categories: Map<String, String> = emptyMap(),
    val textMode: WallpaperTextMode = WallpaperTextMode.Auto,
    val themedIcons: Boolean = false,
    val settings: LauncherSettings = LauncherSettings(),
    /** Manual hides only; list/search consumers must use effectiveHiddenAppKeys. */
    val hiddenAppKeys: Set<String> = emptySet(),
    val folders: List<LauncherFolder> = emptyList(),
    val isLoadingSettings: Boolean = false,
    val settingsLoadFailed: Boolean = false,
    val settingsSaveFailed: Boolean = false,
    val iconPacks: List<IconPackInfo> = emptyList(),
    val isLoadingIconPacks: Boolean = false,
    val iconPacksLoadFailed: Boolean = false,
    val iconPackStatus: IconPackStatus = IconPackStatus.System,
    // Unknown until the first system query, avoiding a banner flash for an existing default.
    val isDefaultHome: Boolean? = null,
    val media: MediaSnapshot = MediaSnapshot(),
    val notifications: Map<String, List<AppNotification>> = emptyMap(),
    val usage: com.galaxyrio.gracelauncher.data.UsageSnapshot = com.galaxyrio.gracelauncher.data.UsageSnapshot(),
    val favoriteOrder: List<String> = emptyList(),
    val weather: WeatherState = WeatherState(),
    val shortcutApps: List<LauncherApp> = emptyList(),
    val folderApps: List<LauncherApp> = emptyList(),
    val graceButtonApp: LauncherApp? = null,
    val popups: Map<String, List<PopupItem>> = emptyMap(),
    val itemIcons: Map<String, ItemIcon> = emptyMap(),
    val itemRevision: Int = 0,
    val privateSpace: PrivateSpaceState = PrivateSpaceState(),
    val privateApps: List<LauncherApp> = emptyList(),
    val privateAppsLoading: Boolean = false,
    val privateAppsFailed: Boolean = false,
    val privateFolderApp: LauncherApp? = null,
    val workProfiles: List<WorkProfile> = emptyList(),
    val workFolderApp: LauncherApp? = null,
    val shownShortcutKeys: Set<String> = emptySet(),
) {
    val folderHiddenAppKeys: Set<String> = com.galaxyrio.gracelauncher.data.hiddenAppsFromFolders(folders)
    val effectiveHiddenAppKeys: Set<String> = hiddenAppKeys + folderHiddenAppKeys

    fun findItem(key: String): LauncherApp? = apps.firstOrNull { it.key == key }
        ?: shortcutApps.firstOrNull { it.key == key && (!it.isPrivateSpace || privateContentVisible) }
            ?.let { if (it.isPrivateSpace) it.copy(showPrivateIndicator = settings.privateSpace.showIndicator) else it }
        ?: privateSpaceApps.firstOrNull { it.key == key }
        ?: privateFolder.takeIf { it.key == key }?.let(::folderItem)
        ?: workFolder.takeIf { it.key == key }?.let(::folderItem)
        ?: folders.firstOrNull { it.key == key }?.let(::folderItem)
        ?: folderApps.firstOrNull { it.key == key && it.folderId == RecentlyInstalledFolderId }
            ?.let { folderItem(recentlyInstalledFolder(it.originalLabel)) }
        ?: graceButtonApp?.takeIf { it.key == key }

    val privateFolder: LauncherFolder get() = settings.privateSpace.folder(privateSpaceApps)
    val workProfileApps: List<LauncherApp> get() = settings.workProfile.orderedApps(apps.filter { it.isWorkProfile && it.shortcut == null })
    val workFolder: LauncherFolder get() = settings.workProfile.folder(workProfileApps, WorkProfileFolderId)
    // Locking/pausing a profile does not remove its pinned folder; only its contents are protected.
    val canFavoritePrivateSpace: Boolean get() = !isLoadingSettings && !settingsLoadFailed &&
        settings.privateSpace.enabled && privateSpace.supported && privateSpace.user != null
    val canFavoriteWorkProfile: Boolean get() = !isLoadingSettings && !settingsLoadFailed &&
        settings.workProfile.enabled && workProfiles.isNotEmpty()
    val privateContentVisible: Boolean get() = !isLoadingSettings && !settingsLoadFailed && settings.privateSpace.enabled &&
        privateSpace.user != null && (settings.privateSpace.exposesApps || (privateSpace.accessible && !privateSpace.locked))
    val privateSpaceApps: List<LauncherApp> get() = if (privateContentVisible)
        settings.privateSpace.orderedApps(privateApps).map { it.copy(showPrivateIndicator = settings.privateSpace.showIndicator) } else emptyList()
    private val publicPrivateShortcuts: List<LauncherApp> get() = if (settings.privateSpace.exposesApps && privateContentVisible)
        shortcutApps.filter { it.isPrivateSpace && it.key in shownShortcutKeys }.map { it.copy(showPrivateIndicator = settings.privateSpace.showIndicator) } else emptyList()
    /** Public surfaces include private apps only after the user opts out of launcher protection. */
    val allApps: List<LauncherApp> get() = (apps + (if (settings.privateSpace.exposesApps) privateSpaceApps else emptyList()) + publicPrivateShortcuts)
        .distinctBy(LauncherApp::key).sortedWith(LauncherAppOrder)

    // Hidden apps remain available in folders. Profile-protected apps and saved
    // shortcuts are not installations in this personal-profile collection.
    val recentlyInstalledApps: List<LauncherApp> get() = apps.asSequence()
        .filter { it.shortcut == null && it.folderId == null && !it.isPrivateSpace && !it.isLauncherSettings }
        .sortedWith(compareByDescending<LauncherApp> { it.firstInstallTime }.then(LauncherAppOrder))
        .distinctBy { it.userSerial to it.packageName }.take(8).toList()

    fun recentlyInstalledFolder(name: String) = LauncherFolder(
        RecentlyInstalledFolderId, settings.listAppearance.recentlyInstalledName ?: name,
        recentlyInstalledApps.map(LauncherApp::key), FolderPlacement.AppList,
    )

    fun folderItem(folder: LauncherFolder): LauncherApp = ((if (folder.id == PrivateSpaceFolderId) privateFolderApp
        else if (folder.id == WorkProfileFolderId) workFolderApp
        else folderApps.firstOrNull { it.folderId == folder.id })
        ?: folder.asApp()).copy(label = folder.name, originalLabel = folder.name)

    fun popupItems(owner: LauncherApp, defaults: List<LauncherApp>): List<PopupItem> {
        if (owner.folderId == RecentlyInstalledFolderId) return recentlyInstalledApps.map { PopupItem(it.key) }
        if (owner.folderId == WorkProfileFolderId) return workProfileApps.map { PopupItem(it.key) }
        val items = popups[owner.key].takeUnless { owner.folderId == PrivateSpaceFolderId }
            ?: (if (owner.folderId == PrivateSpaceFolderId) privateSpaceApps.map { it.key }
                else if (owner.folderId == null) defaults.map { it.key }
                else folders.firstOrNull { it.id == owner.folderId }?.appKeys.orEmpty()).distinct().map { PopupItem(it) }
        return if (owner.folderId != PrivateSpaceFolderId && !settings.privateSpace.exposesApps)
            items.filterNot { it.key.startsWith("profile:") && findItem(it.key)?.isWorkProfile != true } else items
    }

    fun ownNotificationsFor(app: LauncherApp, expanded: Boolean): List<AppNotification> =
        if (app.shortcut != null || app.user != null || app.folderId != null) emptyList()
        else notifications[app.packageName].orEmpty().filter { settings.shortcutsFolders.showsNotification(it.silent, expanded) }

    /** An outer row previews its own notifications and those of the apps shown inside it. */
    fun notificationsFor(app: LauncherApp, expanded: Boolean): List<AppNotification> {
        if (app.shortcut != null || app.user != null || notifications.isEmpty()) return emptyList()
        return buildList {
            addAll(ownNotificationsFor(app, expanded))
            // Use the same contents/privacy filtering as the panel. Shortcuts and widget
            // providers do not stand for app rows; nested pop-ups are not expanded here.
            popupItems(app, emptyList()).filter { it.widget == null }.forEach { entry ->
                findItem(entry.key)?.let { addAll(ownNotificationsFor(it, expanded)) }
            }
        }.distinctBy { it.key }.sortedByDescending { it.postedAt }
    }

    fun notificationPreviewsFor(app: LauncherApp): List<AppNotification> =
        settings.shortcutsFolders.previewNotifications(notificationsFor(app, expanded = false))

    /** Resolve the exact first configured item, without skipping an unavailable item. */
    fun firstPopupApp(owner: LauncherApp, defaults: List<LauncherApp>): LauncherApp? {
        val first = popupItems(owner, defaults).firstOrNull() ?: return null
        val widget = first.widget
        if (widget != null) {
            val packageName = widget.widgetProvider?.let(android.content.ComponentName::unflattenFromString)?.packageName ?: return null
            // Hosted widgets currently belong to the personal profile. Never launch a
            // same-package work/private app or a saved shortcut in its place.
            return apps.firstOrNull { it.packageName == packageName && it.user == null && it.shortcut == null && it.folderId == null }
        }
        return findItem(first.key) ?: defaults.firstOrNull { it.key == first.key }
    }
    val homeMedia: NowPlaying?
        get() = media.nowPlaying.takeIf {
            settings.mediaPlayer && media.hasAccess && !isLoadingSettings && !settingsLoadFailed
        }

    /** Hide Apps affects only the alphabetical app list, not other launcher surfaces. */
    val appListApps: List<LauncherApp>
        get() = if (isLoadingSettings || settingsLoadFailed) emptyList()
        else (if (settings.privateSpace.display == PrivateSpaceDisplay.NormalApp) allApps else apps + publicPrivateShortcuts)
            .filterNot { it.key in effectiveHiddenAppKeys }
            .filterNot { it.isWorkProfile && it.shortcut == null && settings.workProfile.enabled &&
                settings.workProfile.display != PrivateSpaceDisplay.NormalApp }

    val favoriteApps: List<LauncherApp>
        get() {
            if (isLoadingSettings || settingsLoadFailed) return emptyList()
            val favorites = (allApps + shortcutApps.filter { !it.isPrivateSpace || (settings.privateSpace.exposesApps && privateContentVisible) })
                .filter { it.key in favoriteKeys }.associateBy(LauncherApp::key)
            return (favoriteOrder + favorites.keys).distinct().mapNotNull(favorites::get)
        }

    /** User folders use placement; built-in profile folders use stable favorite keys. */
    val favoriteFolders: List<LauncherFolder>
        get() {
            if (isLoadingSettings || settingsLoadFailed) return emptyList()
            return buildList {
                addAll(folders.filter { it.placement.inFavorites })
                if (canFavoritePrivateSpace && PrivateSpaceFolderKey in favoriteKeys) add(privateFolder)
                if (canFavoriteWorkProfile && WorkProfileFolderKey in favoriteKeys) add(workFolder)
            }
        }

    /** Every kind of favorite shares the same persisted ordering. */
    val favoriteItems: List<LauncherApp>
        get() {
            if (isLoadingSettings || settingsLoadFailed) return emptyList()
            val items = (favoriteApps + favoriteFolders.map(::folderItem))
                .associateBy(LauncherApp::key)
            // Existing folders keep their old position at the end until reordered.
            return (favoriteOrder + items.keys).distinct().mapNotNull(items::get)
        }

    val homeItems: List<PopupItem> get() = com.galaxyrio.gracelauncher.data.orderedHomeItems(
        settings, favoriteItems.map(LauncherApp::key), popups[com.galaxyrio.gracelauncher.data.HomeContentKey].orEmpty())
}

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val iconPacks = IconPackRepository(application)
    private val appRepository = AppRepository(application, iconPacks)
    private val appCache = LauncherAppCache(application)
    private var startupPreviewPending = true
    private val privateAppCache = PrivateAppCache(application)
    private var privateRawApps: List<LauncherApp> = emptyList()
    private var privateSavedApps: List<LauncherApp> = emptyList()
    private var privateRawSerial: Long = -1
    val privateSpaceController = PrivateSpaceController.get(application)
    private val calendarRepository = CalendarRepository(application)
    private val favoritesStore = FavoritesStore(application)
    private val preferences = LauncherPreferences(application)
    private val shortcutRepository = ShortcutRepository(application)
    private val settingsRepository = LauncherSettingsRepository(LauncherDatabase.getInstance(application))
    private val itemsRepository = LauncherItemsRepository(LauncherDatabase.getInstance(application))
    private val itemIcons = ItemIconStore(application, iconPacks)
    private data class StyledShortcuts(val inputs: List<Any?>, val result: ShortcutResult)
    private val styledShortcuts = android.util.LruCache<String, StyledShortcuts>(24)
    private var itemsSnapshot = LauncherItemsSnapshot()
    private var itemsReady = false
    private val mediaRepository = MediaSessionRepository(application)
    private val weatherRepository = BreezyWeatherRepository(application)
    private val settingsWriteMutex = Mutex()

    private val _uiState = MutableStateFlow(LauncherUiState(
        categories = preferences.categories(),
        textMode = preferences.textMode,
        themedIcons = preferences.themedIcons,
        graceButtonApp = GraceButtonIcon.item(application),
        isLoadingSettings = true,
    ))
    val uiState = _uiState.asStateFlow()

    private val _returnHomeRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val returnHomeRequests = _returnHomeRequests.asSharedFlow()

    private var appLoadJob: Job? = null
    private data class CatalogInputs(val configuration: Configuration, val day: LocalDate,
        val renames: Map<String, String>, val themedIcons: Boolean, val shortcutAccess: Boolean)
    private val catalogRefresh = CatalogRefreshState<CatalogInputs>()
    private var privateAppLoadJob: Job? = null
    private var scheduleLoadJob: Job? = null
    private var weatherLoadJob: Job? = null
    private val usageRepository = com.galaxyrio.gracelauncher.data.UsageRepository.get(application)
    private var usageLoadJob: Job? = null
    private var lastWeatherRefresh = 0L
    private val dateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { refreshApps() }
    }
    private val launcherApps = application.getSystemService(LauncherApps::class.java)
    private val packageChanges = object : LauncherApps.Callback() {
        private fun changed(user: UserHandle, packages: Collection<String>) {
            appRepository.invalidateApps(user, packages)
            if (user == Process.myUserHandle() && BreezyWeatherRepository.PACKAGE_NAME in packages) refreshWeather()
            refreshApps()
        }
        override fun onPackageAdded(packageName: String, user: UserHandle) = changed(user, listOf(packageName))
        override fun onPackageRemoved(packageName: String, user: UserHandle) = changed(user, listOf(packageName))
        override fun onPackageChanged(packageName: String, user: UserHandle) = changed(user, listOf(packageName))
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
            if (replacing) changed(user, packageNames.toList()) else refreshApps()
        }
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
            if (replacing) changed(user, packageNames.toList()) else refreshApps()
        }
        override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) = refreshApps()
        override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) = refreshApps()
        override fun onShortcutsChanged(packageName: String, shortcuts: MutableList<android.content.pm.ShortcutInfo>, user: UserHandle) {
            if (itemsSnapshot.shortcuts.any { it.packageName == packageName }) refreshApps()
        }
    }

    init {
        launcherApps.registerCallback(packageChanges)
        viewModelScope.launch {
            privateSpaceController.state.collect { state ->
                val previous = _uiState.value.privateSpace
                val sameProfile = previous.user == state.user && previous.serial == state.serial
                _uiState.update { it.copy(privateSpace = state,
                    privateApps = if (sameProfile) it.privateApps else emptyList(),
                    shortcutApps = if (sameProfile) it.shortcutApps else it.shortcutApps.filterNot(LauncherApp::isPrivateSpace)) }
                if (!sameProfile || previous.accessible != state.accessible ||
                    previous.locked != state.locked || previous.revision != state.revision) refreshPrivateApps()
            }
        }
        viewModelScope.launch {
            itemsRepository.snapshots.catch { error ->
                if (error is CancellationException) throw error
                Log.e("LauncherViewModel", "Unable to read launcher items", error)
                _uiState.update { it.copy(settingsLoadFailed = true) }
            }.collect { snapshot ->
                val reload = !itemsReady || snapshot.icons != itemsSnapshot.icons || snapshot.shortcuts != itemsSnapshot.shortcuts
                itemsReady = true
                itemsSnapshot = snapshot
                _uiState.update { it.copy(popups = snapshot.popups, itemIcons = snapshot.icons,
                    shownShortcutKeys = snapshot.shortcuts.filter { saved -> saved.showInAppList }.map { saved -> saved.itemKey }.toSet(),
                    itemRevision = it.itemRevision + 1) }
                if (reload) refreshApps()
            }
        }
        viewModelScope.launch {
            BreezyWeatherUpdates.events.collectLatest {
                // Refresh after the final signal, so a forecast update following
                // a current-conditions update is never lost to a leading-edge throttle.
                delay(500)
                refreshWeather()
            }
        }
        viewModelScope.launch {
            appNotifications.state.collect { notifications -> _uiState.update { it.copy(notifications = notifications) } }
        }
        viewModelScope.launch {
            mediaRepository.state.collect { media -> _uiState.update { it.copy(media = media) } }
        }
        ContextCompat.registerReceiver(application, dateReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_WALLPAPER_CHANGED)
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
            addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
            addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
            addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        viewModelScope.launch {
            settingsRepository.snapshots
                .catch { error ->
                    if (error is CancellationException) throw error
                    Log.e("LauncherViewModel", "Unable to read launcher settings", error)
                    _uiState.update { it.copy(isLoadingSettings = false, settingsLoadFailed = true) }
                    mediaRepository.setEnabled(false)
                    refreshWeather()
                }
                .collect { snapshot ->
                    val previous = _uiState.value
                    privateSpaceController.configure(snapshot.settings.privateSpace)
                    _uiState.update {
                        it.copy(
                            settings = snapshot.settings,
                            apps = it.apps.map { app -> if (app.isWorkProfile) app.copy(showWorkIndicator = snapshot.settings.workProfile.showIndicator) else app },
                            shortcutApps = it.shortcutApps.map { app -> if (app.isWorkProfile) app.copy(showWorkIndicator = snapshot.settings.workProfile.showIndicator) else app },
                            themedIcons = snapshot.settings.iconDesign?.design?.withThemeDefaults(IconDesign.defaults(preferences.themedIcons))?.themeIcons
                                ?: preferences.themedIcons,
                            hiddenAppKeys = snapshot.hiddenAppKeys,
                            folders = snapshot.folders,
                            isLoadingSettings = false,
                            settingsLoadFailed = false,
                        )
                    }
                    if (previous.isLoadingSettings ||
                        previous.settings.calendarAgenda != snapshot.settings.calendarAgenda
                    ) refreshSchedule()
                    if (previous.isLoadingSettings || previous.settings.mediaPlayer != snapshot.settings.mediaPlayer) {
                        mediaRepository.setEnabled(snapshot.settings.mediaPlayer)
                    }
                    if (previous.isLoadingSettings || previous.settings.enabledIconPackPackages != snapshot.settings.enabledIconPackPackages ||
                        previous.settings.iconDesign != snapshot.settings.iconDesign ||
                        previous.settings.darkMode != snapshot.settings.darkMode ||
                        previous.settings.useDynamicColors != snapshot.settings.useDynamicColors ||
                        previous.settings.themeColor != snapshot.settings.themeColor ||
                        previous.settings.paletteStyle != snapshot.settings.paletteStyle ||
                        previous.folders != snapshot.folders) refreshApps()
                    if (previous.settings.privateSpace != snapshot.settings.privateSpace) refreshPrivateApps()
                    if (previous.isLoadingSettings || previous.settings.digitalWellbeing != snapshot.settings.digitalWellbeing) {
                        usageLoadJob?.cancel()
                        usageLoadJob = null
                        refreshUsage()
                    }
                    if (previous.isLoadingSettings || previous.settings.weatherEnabled != snapshot.settings.weatherEnabled ||
                        previous.settings.weatherLocationId != snapshot.settings.weatherLocationId
                    ) refreshWeather()
                    if (previous.isLoadingSettings) {
                        val favorites = _uiState.value.favoriteApps
                        viewModelScope.launch { shortcutRepository.prefetch(favorites) }
                    }
                }
        }
        refreshApps()
    }

    private fun catalogInputs() = CatalogInputs(Configuration(getApplication<Application>().resources.configuration),
        LocalDate.now(), preferences.renames(), preferences.themedIcons, shortcutRepository.hasAccess())

    /** Explicit changes/retries invalidate the catalog; ordinary resumes only check its inputs. */
    fun refreshApps() {
        // Explicit package/pack refreshes can change artwork without changing its saved identity.
        styledShortcuts.evictAll()
        catalogRefresh.invalidate()
        refreshOnResume()
    }

    fun refreshOnResume() {
        refreshUsage()
        mediaRepository.refresh()
        refreshWeather(force = false)
        val isDefaultHome = DefaultHome.isDefault(getApplication())
        privateSpaceController.refresh()
        if (_uiState.value.privateAppsFailed) refreshPrivateApps()
        val favorites = _uiState.value.takeUnless { it.isLoadingApps || it.appLoadFailed }
            ?.let { favoritesStore.favoritesFor(it.apps) }
        _uiState.update { it.copy(isDefaultHome = isDefaultHome, hasShortcutAccess = shortcutRepository.hasAccess(),
            categories = preferences.categories(), textMode = preferences.textMode,
            favoriteKeys = favorites?.toSet() ?: it.favoriteKeys, favoriteOrder = favorites ?: it.favoriteOrder) }
        // Wait for the stored pack choice: do not flash system icons on cold start.
        if (!itemsReady || _uiState.value.isLoadingSettings || _uiState.value.settingsLoadFailed) return
        if (appLoadJob?.isActive == true || catalogRefresh.pending(catalogInputs()) == null) return
        appLoadJob = viewModelScope.launch {
            // Resumes share this job. A change during IO queues one pass for the latest inputs.
            while (true) {
                val request = catalogRefresh.start(catalogInputs()) ?: break
                val iconSettings = _uiState.value.settings
                val snapshot = itemsSnapshot
                val inputs = request.inputs
                val context = getApplication<Application>()
                // Non-visual settings no longer rebuild the catalog, so they must
                // not invalidate its startup preview after the process restarts.
                val cacheKey = LauncherAppCache.key(listOf(
                    iconSettings.enabledIconPackPackages, iconSettings.iconDesign, iconSettings.darkMode,
                    iconSettings.useDynamicColors, iconSettings.themeColor, iconSettings.paletteStyle,
                    snapshot.icons.toSortedMap(), snapshot.shortcuts.sortedBy { it.itemKey },
                    _uiState.value.folders, inputs.renames.toSortedMap(), inputs.themedIcons,
                    inputs.configuration, inputs.day,
                    launcherThemeSeed(context.resources, true, iconSettings.themeColor),
                    context.applicationInfo.sourceDir, inputs.shortcutAccess,
                ).joinToString("\n"))
                var incomplete = false
                refreshPrivateApps()
                _uiState.update {
                    it.copy(isLoadingApps = true, appLoadFailed = false, isLoadingIconPacks = true, hasShortcutAccess = shortcutRepository.hasAccess())
                }
                if (startupPreviewPending) {
                    startupPreviewPending = false
                    val preview = try { appCache.load(cacheKey, appRepository.launchableComponents()) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { null }
                    if (!catalogRefresh.isCurrent(request, catalogInputs())) continue
                    if (preview != null) {
                        val apps = preview.filter { it.folderId == null && !it.isGraceButton &&
                            (it.shortcut == null || it.key in _uiState.value.shownShortcutKeys) }
                        val favorites = favoritesStore.favoritesFor(apps)
                        _uiState.update { it.copy(apps = apps,
                            shortcutApps = preview.filter { app -> app.shortcut != null } + it.shortcutApps.filter(LauncherApp::isPrivateSpace),
                            folderApps = preview.filter { app -> app.folderId != null },
                            graceButtonApp = preview.firstOrNull { app -> app.isGraceButton } ?: it.graceButtonApp,
                            favoriteKeys = favorites.toSet(), favoriteOrder = favorites) }
                    }
                }
                try {
                    val installed = iconPacks.installedPacks()
                    _uiState.update { it.copy(iconPacks = installed, isLoadingIconPacks = false, iconPacksLoadFailed = false) }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    incomplete = true
                    _uiState.update { it.copy(isLoadingIconPacks = false, iconPacksLoadFailed = true) }
                }
                val selectedPacks = iconSettings.enabledIconPackPackages
                val availablePacks = selectedPacks.mapNotNull { iconPacks.load(it) }
                val status = when {
                    selectedPacks.isEmpty() -> IconPackStatus.System
                    availablePacks.isNotEmpty() -> IconPackStatus.Ready
                    else -> IconPackStatus.Unavailable
                }
                try {
                    val workProfiles = try { appRepository.workProfiles() } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        incomplete = true
                        _uiState.value.workProfiles
                    }
                    val apps = appRepository.loadApps(selectedPacks) + workProfiles.flatMap { profile ->
                        try { appRepository.loadWorkApps(profile, selectedPacks) } catch (error: Exception) {
                            if (error is CancellationException) throw error
                            incomplete = true
                            emptyList()
                        }
                    }
                    val renames = inputs.renames
                    suspend fun decorate(app: LauncherApp): LauncherApp = itemIcons.applyDesign(app, snapshot.icons[app.key], iconSettings.iconDesign, iconSettings, inputs.themedIcons)
                        .copy(label = renames[app.key] ?: app.originalLabel, showWorkIndicator = iconSettings.workProfile.showIndicator)
                    val shortcuts = snapshot.shortcuts.mapNotNull { saved ->
                        // Package names alone cannot distinguish personal and work installations.
                        val owners = apps.filter { app -> if (app.userSerial == null) !saved.itemKey.startsWith("profile:")
                            else saved.itemKey.startsWith("profile:${app.userSerial}:shortcut:") }
                        val owner = owners.firstOrNull { it.componentName.flattenToString() == saved.activity }
                            ?: owners.firstOrNull { it.packageName == saved.packageName } ?: return@mapNotNull null
                        val current = shortcutRepository.shortcutsFor(owner).shortcuts.firstOrNull { it.id == saved.shortcutId }
                            ?: LauncherShortcut(saved.shortcutId, saved.packageName, saved.label, owner.icon, owner.componentName)
                        decorate(current.asApp(owner))
                    }
                    val shown = snapshot.shortcuts.filter { it.showInAppList }.map { it.itemKey }.toSet()
                    val displayedApps = (apps.map { decorate(it) } + shortcuts.filter { it.key in shown })
                        .sortedWith(LauncherAppOrder)
                    // Keep this virtual folder out of the editable folder table, but
                    // render its saved icon through the same item-icon pipeline.
                    val recentFolder = LauncherFolder(RecentlyInstalledFolderId,
                        getApplication<Application>().getString(com.galaxyrio.gracelauncher.R.string.recently_installed),
                        emptyList(), FolderPlacement.AppList)
                    val folderApps = (_uiState.value.folders + recentFolder).map { folder ->
                        decorate(folder.asApp())
                    }
                    val privateFolderApp = decorate(_uiState.value.privateFolder.asApp())
                    val workFolderApp = decorate(_uiState.value.workFolder.asApp())
                    val graceButtonApp = itemIcons.apply(GraceButtonIcon.item(getApplication()),
                        GraceButtonIcon.choice(snapshot.icons[GraceButtonIcon.key]), iconSettings)
                    // Do not publish a theme/package revision superseded while IO was running.
                    if (!catalogRefresh.isCurrent(request, catalogInputs())) continue
                    val favorites = favoritesStore.favoritesFor(displayedApps)
                    _uiState.update {
                        it.copy(
                            apps = displayedApps,
                            shortcutApps = shortcuts + it.shortcutApps.filter(LauncherApp::isPrivateSpace),
                            folderApps = folderApps,
                            privateFolderApp = privateFolderApp,
                            workFolderApp = workFolderApp,
                            graceButtonApp = graceButtonApp,
                            workProfiles = workProfiles,
                            favoriteKeys = favorites.toSet(),
                            favoriteOrder = favorites,
                            // Settings also has an independent Activity/ViewModel.
                            // Refresh legacy preferences when returning to HOME.
                            categories = preferences.categories(),
                            textMode = preferences.textMode,
                            themedIcons = iconSettings.iconDesign?.design?.withThemeDefaults(IconDesign.defaults(preferences.themedIcons))?.themeIcons
                                ?: preferences.themedIcons,
                            iconPackStatus = status,
                            isLoadingApps = false,
                        )
                    }
                    // Retry incomplete sections on the next resume, without spinning here.
                    if (incomplete) return@launch
                    catalogRefresh.complete(request, request.inputs)
                    // Publish first; PNG encoding and disk I/O run on Dispatchers.IO.
                    appCache.save(cacheKey, displayedApps + shortcuts + folderApps + graceButtonApp)
                    shortcutRepository.prefetch(_uiState.value.favoriteApps)
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    if (catalogRefresh.isCurrent(request, catalogInputs())) {
                        _uiState.update { it.copy(isLoadingApps = false, appLoadFailed = true) }
                        return@launch // Leave this revision pending so the next resume/retry can load it.
                    }
                }
            }
        }
    }

    private fun refreshPrivateApps() {
        privateAppLoadJob?.cancel()
        val state = _uiState.value
        val snapshot = itemsSnapshot
        val profile = state.privateSpace
        val user = profile.user
        if (user == null || !state.settings.privateSpace.enabled || !itemsReady || state.isLoadingSettings || state.settingsLoadFailed ||
            (state.settings.privateSpace.protectsApps && (!profile.accessible || profile.locked))) {
            _uiState.update { it.copy(privateAppsLoading = false, privateAppsFailed = false) }
            return
        }
        privateAppLoadJob = viewModelScope.launch {
            _uiState.update { it.copy(privateAppsLoading = true, privateAppsFailed = false) }
            try {
                val settings = state.settings
                if (privateRawSerial != profile.serial) {
                    privateRawApps = privateAppCache.load(user, profile.serial)
                    privateSavedApps = privateRawApps
                    privateRawSerial = profile.serial
                }
                // This query never disables quiet mode or opens a credential UI.
                // Some systems suppress locked profiles; retain the last known inventory then.
                try {
                    val queried = appRepository.loadPrivateApps(user, profile.serial, emptyList())
                    if (queried.isNotEmpty() || !profile.locked) {
                        val previous = privateRawApps.associateBy(LauncherApp::key)
                        privateRawApps = queried.map { app ->
                            if (app.icon != null) app else app.copy(icon = previous[app.key]?.icon,
                                monochromeIcon = previous[app.key]?.monochromeIcon,
                                profileIconSource = previous[app.key]?.profileIconSource,
                                originalIconColor = previous[app.key]?.originalIconColor)
                        }
                        // Opening/unlocking or changing a theme isn't a change to
                        // source artwork. Avoid recompressing every PNG and writing it again.
                        try {
                            if (privateRawApps != privateSavedApps) {
                                privateAppCache.save(profile.serial, privateRawApps)
                                privateSavedApps = privateRawApps
                            }
                        }
                        catch (error: Exception) {
                            if (error is CancellationException) throw error
                            Log.w("LauncherViewModel", "Unable to cache private app inventory", error)
                        }
                    }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    if (privateRawApps.isEmpty()) throw error
                }
                val packed = appRepository.applyIconPacks(privateRawApps, settings.enabledIconPackPackages)
                val apps = packed.map { app ->
                    itemIcons.applyDesign(app, snapshot.icons[app.key], settings.iconDesign, settings, preferences.themedIcons)
                }
                val savedShortcuts = snapshot.shortcuts.filter { it.itemKey.startsWith("profile:${profile.serial}:shortcut:") }
                    .mapNotNull { saved ->
                        val owner = packed.firstOrNull { it.componentName.flattenToString() == saved.activity }
                            ?: packed.firstOrNull { it.packageName == saved.packageName } ?: return@mapNotNull null
                        val current = if (!profile.locked) shortcutRepository.shortcutsFor(owner).shortcuts.firstOrNull { it.id == saved.shortcutId } else null
                        val shortcut = current ?: LauncherShortcut(saved.shortcutId, saved.packageName, saved.label, owner.icon,
                            owner.componentName, user = user, userSerial = profile.serial, isPrivateSpace = true)
                        val app = shortcut.asApp(owner)
                        itemIcons.applyDesign(app, snapshot.icons[app.key], settings.iconDesign, settings, preferences.themedIcons)
                    }
                val renames = preferences.renames()
                // Cache visibility is determined by settings; never publish another profile's inventory.
                if (privateSpaceController.state.value.user == user && privateSpaceController.state.value.serial == profile.serial &&
                    _uiState.value.settings.privateSpace.enabled) {
                    _uiState.update { it.copy(privateApps = apps.map { app -> app.copy(label = renames[app.key] ?: app.originalLabel) },
                        shortcutApps = it.shortcutApps.filterNot(LauncherApp::isPrivateSpace) + savedShortcuts.map { app -> app.copy(label = renames[app.key] ?: app.originalLabel) },
                        privateAppsLoading = false) }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.update { it.copy(privateAppsLoading = false, privateAppsFailed = true) }
            }
        }
    }

    /** Only reads Breezy's local provider; never initiates a network weather request. */
    fun refreshWeather(force: Boolean = true) {
        val state = _uiState.value
        if (state.isLoadingSettings || state.settingsLoadFailed || !state.settings.weatherEnabled) {
            weatherLoadJob?.cancel()
            lastWeatherRefresh = 0L
            _uiState.update { it.copy(weather = WeatherState()) }
            return
        }
        if (!force && (weatherLoadJob?.isActive == true ||
                (state.weather.status == WeatherStatus.Ready &&
                    SystemClock.elapsedRealtime() - lastWeatherRefresh < 15 * 60_000L))) return
        weatherLoadJob?.cancel()
        val locationId = state.settings.weatherLocationId
        weatherLoadJob = viewModelScope.launch {
            val previous = _uiState.value.weather
            _uiState.update { it.copy(weather = previous.copy(status = WeatherStatus.Loading,
                snapshot = previous.snapshot.takeIf { previous.selectedLocationId == locationId || locationId == null })) }
            val weather = weatherRepository.load(locationId)
            lastWeatherRefresh = SystemClock.elapsedRealtime()
            _uiState.update { it.copy(weather = weather) }
        }
    }

    fun refreshSchedule() {
        if (_uiState.value.isLoadingSettings || _uiState.value.settingsLoadFailed ||
            !_uiState.value.settings.calendarAgenda
        ) {
            scheduleLoadJob?.cancel()
            _uiState.update { it.copy(events = emptyList(), scheduleStatus = ScheduleStatus.Ready) }
            return
        }
        val application = getApplication<Application>()
        val hasPermission = ContextCompat.checkSelfPermission(
            application,
            Manifest.permission.READ_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            scheduleLoadJob?.cancel()
            _uiState.update {
                it.copy(events = emptyList(), scheduleStatus = ScheduleStatus.PermissionRequired)
            }
            return
        }

        scheduleLoadJob?.cancel()
        scheduleLoadJob = viewModelScope.launch {
            _uiState.update { it.copy(scheduleStatus = ScheduleStatus.Loading) }
            runCatching { calendarRepository.upcomingEvents() }
                .onSuccess { events ->
                    _uiState.update { it.copy(events = events, scheduleStatus = ScheduleStatus.Ready) }
                }
                .onFailure {
                    if (it is CancellationException) throw it
                    _uiState.update { it.copy(events = emptyList(), scheduleStatus = ScheduleStatus.Error) }
                }
        }
    }

    fun launch(app: LauncherApp, bounds: Rect? = null, options: Bundle? = null, fromSearch: Boolean = false): Boolean {
        if (app.folderId != null) return false
        if (app.isPrivateSpace && !privateSpaceController.prepareAppLaunch(app.user, app.key)) return false
        if (!app.isPrivateSpace) privateSpaceController.close()
        val launched = app.shortcut?.let { shortcutRepository.launch(it, bounds, options) } ?: appRepository.launch(app, bounds, options)
        if (app.isPrivateSpace) privateSpaceController.finishAppLaunch(launched)
        if (launched && fromSearch && (!app.isPrivateSpace || _uiState.value.settings.privateSpace.exposesApps)) {
            updateSettings { it.copy(search = it.search.recordApp(app.key)) }
        }
        return launched
    }

    fun preparePrivateAppLaunch(app: LauncherApp): Boolean = privateSpaceController.prepareAppLaunch(app.user, app.key)

    private fun shortcutAppearanceInputs(app: LauncherApp, result: ShortcutResult): List<Any?> {
        val settings = _uiState.value.settings
        val resources = getApplication<Application>().resources
        return listOf(result, app, settings.iconDesign, settings.enabledIconPackPackages,
            settings.darkMode, settings.useDynamicColors, settings.themeColor, settings.paletteStyle,
            result.shortcuts.map { itemsSnapshot.icons[it.key] }, preferences.themedIcons,
            Configuration(resources.configuration), LocalDate.now(), launcherThemeSeed(resources, true, settings.themeColor))
    }

    suspend fun loadShortcuts(app: LauncherApp): ShortcutResult {
        val source = shortcutRepository.shortcutsFor(app)
        val inputs = shortcutAppearanceInputs(app, source)
        styledShortcuts[app.key]?.takeIf { it.inputs == inputs }?.let { return it.result }
        val settings = _uiState.value.settings
        val icons = itemsSnapshot.icons
        val result = source.copy(styledApps = source.shortcuts.associate { shortcut ->
            val item = shortcut.asApp(app)
            item.key to itemIcons.applyDesign(item, icons[item.key], settings.iconDesign, settings, preferences.themedIcons)
        })
        // A settings change or provider invalidation during IO must not become the next warm result.
        if (inputs == shortcutAppearanceInputs(app, source) && shortcutRepository.peek(app) == source) {
            styledShortcuts.put(app.key, StyledShortcuts(inputs, result))
        }
        return result
    }

    fun cachedShortcuts(app: LauncherApp): ShortcutResult? {
        val source = shortcutRepository.peek(app) ?: return null
        return styledShortcuts[app.key]?.takeIf { it.inputs == shortcutAppearanceInputs(app, source) }?.result ?: source
    }

    fun prepareShortcuts(app: LauncherApp) {
        viewModelScope.launch { loadShortcuts(app) }
    }

    fun preparePrivateShortcutLaunch(shortcut: LauncherShortcut): Boolean =
        privateSpaceController.prepareAppLaunch(shortcut.user, shortcut.key)

    fun launchShortcut(shortcut: LauncherShortcut, bounds: Rect? = null, options: Bundle? = null): Boolean {
        if (shortcut.isPrivateSpace && !preparePrivateShortcutLaunch(shortcut)) return false
        if (!shortcut.isPrivateSpace) privateSpaceController.close()
        val launched = shortcutRepository.launch(shortcut, bounds, options)
        if (shortcut.isPrivateSpace) privateSpaceController.finishAppLaunch(launched)
        return launched
    }

    override fun onCleared() {
        launcherApps.unregisterCallback(packageChanges)
        getApplication<Application>().unregisterReceiver(dateReceiver)
        shortcutRepository.close()
        mediaRepository.close()
    }

    fun controlMedia(sessionId: String, command: MediaCommand): Boolean = mediaRepository.command(sessionId, command)
    fun dismissMedia(sessionId: String, revision: Long): Boolean {
        // Recheck live playback after the exit animation, not only the gesture's starting state.
        if (_uiState.value.settings.mediaKeepWhilePlaying && mediaRepository.state.value.nowPlaying?.playing == true) return false
        return mediaRepository.dismiss(sessionId, revision)
    }
    fun dismissNotification(key: String, revision: Long): Boolean = appNotifications.dismiss(key, revision)
    fun openNotification(key: String, revision: Long): Boolean = appNotifications.open(getApplication(), key, revision)

    fun renameApp(app: LauncherApp, label: String) {
        if (app.folderId == RecentlyInstalledFolderId) {
            updateSettings { it.copy(listAppearance = it.listAppearance.copy(
                recentlyInstalledName = label.trim().takeIf(String::isNotEmpty))) }
            return
        }
        if (app.folderId == WorkProfileFolderId) {
            updateSettings { it.copy(workProfile = it.workProfile.copy(name = label.trim().ifBlank { WorkProfileDefaultName })) }
            return
        }
        if (app.folderId == PrivateSpaceFolderId) {
            updateSettings { it.copy(privateSpace = it.privateSpace.copy(name = label.trim().ifBlank { PrivateSpaceDefaultName })) }
            return
        }
        if (app.folderId != null) {
            updateFolder(app.folderId, label, null)
            return
        }
        preferences.rename(app.key, label)
        if (app.shortcut != null) persistSettings { itemsRepository.rememberShortcut(app) }
        _uiState.update { state ->
            state.copy(apps = state.apps.map {
                if (it.key == app.key) it.copy(label = label.trim().ifBlank { it.originalLabel }) else it
            }.sortedWith(LauncherAppOrder), shortcutApps = state.shortcutApps.map {
                if (it.key == app.key) it.copy(label = label.trim().ifBlank { it.originalLabel }) else it
            }, privateApps = state.privateApps.map {
                if (it.key == app.key) it.copy(label = label.trim().ifBlank { it.originalLabel }) else it
            }, itemRevision = state.itemRevision + 1)
        }
    }

    fun reorderPrivateApps(keys: List<String>) = updateSettings {
        it.copy(privateSpace = it.privateSpace.copy(appOrder = keys.distinct()))
    }

    fun reorderWorkApps(keys: List<String>) = updateSettings {
        it.copy(workProfile = it.workProfile.copy(appOrder = keys.distinct()))
    }

    suspend fun resetWorkProfileAppearance(): Boolean {
        if (!setItemIcon(_uiState.value.workFolder.asApp(), null)) return false
        updateSettings { it.copy(workProfile = it.workProfile.copy(name = WorkProfileDefaultName)) }
        return true
    }

    suspend fun resetPrivateSpaceAppearance(): Boolean {
        if (!setItemIcon(_uiState.value.privateFolder.asApp(), null)) return false
        updateSettings { it.copy(privateSpace = it.privateSpace.copy(name = PrivateSpaceDefaultName)) }
        return true
    }

    suspend fun setItemIcon(app: LauncherApp, choice: ItemIcon?): Boolean = settingsWriteMutex.withLock {
        try {
            val previous = itemsSnapshot.icons[app.key]
            itemsRepository.rememberShortcut(app)
            itemsRepository.saveIcon(app.key, choice)
            if (previous?.kind == "image" && (choice?.kind != "image" || previous.source != choice.source) &&
                !itemsRepository.isImageReferenced(previous.source)) itemIcons.deleteImage(previous)
            true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            false
        }
    }

    suspend fun importItemIcon(app: LauncherApp, uri: Uri): Boolean {
        var imported: ItemIcon? = null
        return try {
            imported = itemIcons.importImage(uri)
            // The imported image is a fresh ItemIcon, keep the display override.
            setItemIcon(app, imported.copy(display = itemsSnapshot.icons[app.key]?.display))
                .also { if (!it) itemIcons.deleteImage(imported) }
        } catch (error: Exception) {
            itemIcons.deleteImage(imported)
            if (error is CancellationException) throw error
            false
        }
    }

    fun showShortcutInAppList(app: LauncherApp, show: Boolean) = persistSettings {
        app.shortcut?.let { shortcutRepository.pin(listOf(it)) }
        itemsRepository.rememberShortcut(app, show)
    }

    private suspend fun rememberPopupItems(apps: List<LauncherApp>) {
        apps.filter { it.shortcut != null }.forEach { itemsRepository.rememberShortcut(it) }
        shortcutRepository.pin(apps.mapNotNull { it.shortcut })
    }

    suspend fun rememberShortcut(app: LauncherApp): Boolean = settingsWriteMutex.withLock {
        try {
            rememberPopupItems(listOf(app))
            _uiState.update { state -> state.copy(shortcutApps = state.shortcutApps.filterNot { it.key == app.key } + app) }
            true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            _uiState.update { it.copy(settingsSaveFailed = true) }
            false
        }
    }

    fun updatePopup(owner: LauncherApp, defaults: List<LauncherApp>, transform: (List<PopupItem>) -> List<PopupItem>) = persistSettings {
        rememberPopupItems(defaults + listOf(owner))
        val removed = itemsRepository.updatePopup(owner.key, defaults.map { PopupItem(it.key) }, transform)
        val host = com.galaxyrio.gracelauncher.platform.HomeWidgetHost(getApplication())
        removed.forEach { runCatching { host.deleteAppWidgetId(it) } }
    }

    suspend fun ensurePopup(owner: LauncherApp, defaults: List<LauncherApp>): Boolean = settingsWriteMutex.withLock {
        try {
            rememberPopupItems(defaults + listOf(owner))
            itemsRepository.updatePopup(owner.key, defaults.map { PopupItem(it.key) }) { it }
            true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            false
        }
    }

    fun categorize(app: LauncherApp, category: String?) {
        preferences.categorize(app.key, category)
        _uiState.update { it.copy(categories = preferences.categories()) }
    }

    fun setTextMode(mode: WallpaperTextMode) {
        preferences.textMode(mode)
        _uiState.update { it.copy(textMode = mode) }
    }

    fun setThemedIcons(enabled: Boolean) {
        preferences.themedIcons(enabled)
        _uiState.update { it.copy(themedIcons = enabled) }
    }

    fun refreshUsage(forceRanking: Boolean = false) {
        val state = _uiState.value
        if (state.isLoadingSettings || state.settingsLoadFailed) return
        val settings = state.settings.digitalWellbeing
        val access = usageRepository.hasAccess()
        if (!settings.enabled || !access) {
            usageLoadJob?.cancel()
            _uiState.update { it.copy(usage = com.galaxyrio.gracelauncher.data.UsageSnapshot(hasAccess = access)) }
        } else if (usageLoadJob?.isActive == true && !forceRanking) return
        if (forceRanking) usageLoadJob?.cancel()
        if (settings.enabled && access) _uiState.update { it.copy(usage = it.usage.copy(hasAccess = true, loading = true)) }
        usageLoadJob = viewModelScope.launch {
            val result = usageRepository.refresh(settings, forceRanking)
            if (_uiState.value.settings.digitalWellbeing == settings) _uiState.update { it.copy(usage = result) }
        }
    }

    fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) = persistSettings {
        settingsRepository.mutateSettings(transform)
    }

    suspend fun applyClockStyle(style: ClockStyle): Boolean = settingsWriteMutex.withLock {
        try {
            // A manager opened from an unsaved clock editor can delete its draft font.
            val available = com.galaxyrio.gracelauncher.data.ClockFontStore(getApplication()).list().map { it.id }.toSet()
            val missing = (listOf(style.singleLine, style.twoLines) + style.presets.values).mapNotNull { it.fontId }
                .filter { com.galaxyrio.gracelauncher.data.ClockFontStore.displayName(it) != null && it !in available }.toSet()
            settingsRepository.mutateSettings { it.copy(clockStyle = style.withoutFonts(missing)) }
            _uiState.update { it.copy(settingsSaveFailed = false) }
            true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.e("LauncherViewModel", "Unable to save clock style", error)
            _uiState.update { it.copy(settingsSaveFailed = true) }
            false
        }
    }

    suspend fun deleteFonts(ids: Set<String>): Boolean = settingsWriteMutex.withLock {
        try {
            val store = com.galaxyrio.gracelauncher.data.ClockFontStore(getApplication())
            val imported = store.list().map { it.id }.toSet().intersect(ids)
            // Commit reference cleanup before removing private files, so failed saves are safe.
            settingsRepository.mutateSettings { current -> current.copy(
                fontLibrary = current.fontLibrary.without(imported),
                appFontId = current.appFontId.takeUnless { it in imported },
                listAppearance = current.listAppearance.copy(fontId = current.listAppearance.fontId.takeUnless { it in imported },
                    alphabet = current.listAppearance.alphabet.let { it.copy(fontId = it.fontId.takeUnless { id -> id in imported }) }),
                clockStyle = current.clockStyle.withoutFonts(imported),
            ) }
            store.delete(imported)
            _uiState.update { it.copy(settingsSaveFailed = false) }
            true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.e("LauncherViewModel", "Unable to delete fonts", error)
            _uiState.update { it.copy(settingsSaveFailed = true) }
            false
        }
    }

    suspend fun applyIconDesign(choice: ItemIcon): Boolean = settingsWriteMutex.withLock {
        try {
            var previous: ItemIcon? = null
            // The bulk design carries the shared icon-display settings inside its
            // JSON, the same field per-app overrides use.
            settingsRepository.mutateSettings {
                previous = it.iconDesign
                it.copy(iconDesign = choice)
            }
            if (previous?.kind == "image" && (choice.kind != "image" || previous?.source != choice.source) &&
                !itemsRepository.isImageReferenced(previous!!.source)) itemIcons.deleteImage(previous)
            _uiState.update { it.copy(settingsSaveFailed = false) }
            true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.e("LauncherViewModel", "Unable to save icon design", error)
            _uiState.update { it.copy(settingsSaveFailed = true) }
            false
        }
    }

    suspend fun deleteIconDesigns(keys: Set<String>): Boolean = settingsWriteMutex.withLock {
        try {
            val previous = itemsRepository.resetIcons(keys)
            previous.filter { it.kind == "image" }.distinctBy { it.source }.forEach {
                if (!itemsRepository.isImageReferenced(it.source)) itemIcons.deleteImage(it)
            }
            _uiState.update { it.copy(settingsSaveFailed = false) }
            true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.e("LauncherViewModel", "Unable to delete icon designs", error)
            _uiState.update { it.copy(settingsSaveFailed = true) }
            false
        }
    }

    fun removeHomeWidget() = persistSettings {
        var removedId = -1
        settingsRepository.mutateSettings { current ->
            removedId = current.homeLayout.widgetId
            current.copy(homeLayout = current.homeLayout.withoutWidget())
        }
        // Deallocate only after Room commits; a storage failure must leave the widget intact.
        if (removedId >= 0) runCatching {
            com.galaxyrio.gracelauncher.platform.HomeWidgetHost(getApplication()).deleteAppWidgetId(removedId)
        }
    }

    fun setHiddenApps(keys: Set<String>) {
        val snapshot = keys.toSet()
        persistSettings { settingsRepository.setHiddenApps(snapshot) }
    }

    fun hideApp(app: LauncherApp) = persistSettings { settingsRepository.hideApp(app.key) }

    fun setFolderAutoHide(id: String, enabled: Boolean) = persistSettings {
        settingsRepository.updateFolder(id, autoHideApps = enabled)
    }

    fun saveFolder(folder: LauncherFolder) {
        val snapshot = folder.copy(appKeys = folder.appKeys.toList())
        persistSettings {
            rememberPopupItems(snapshot.appKeys.mapNotNull(_uiState.value::findItem))
            settingsRepository.saveFolder(snapshot)
        }
    }

    fun updateFolder(id: String, name: String?, placement: FolderPlacement?, appListAtBottom: Boolean? = null) = persistSettings {
        settingsRepository.updateFolder(id, name, placement, appListAtBottom)
    }

    suspend fun setFolderFavorites(selection: Map<String, Boolean>): Boolean = settingsWriteMutex.withLock {
        try {
            settingsRepository.setFolderFavorites(selection)
            _uiState.update { it.copy(settingsSaveFailed = false) }
            true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.e("LauncherViewModel", "Unable to save favorite folders", error)
            _uiState.update { it.copy(settingsSaveFailed = true) }
            false
        }
    }

    fun deleteFolder(id: String) = persistSettings {
        val previous = itemsSnapshot.icons["folder:$id"]
        val removed = settingsRepository.deleteFolder(id)
        val host = com.galaxyrio.gracelauncher.platform.HomeWidgetHost(getApplication())
        removed.forEach { runCatching { host.deleteAppWidgetId(it) } }
        if (previous?.kind == "image" && !itemsRepository.isImageReferenced(previous.source)) itemIcons.deleteImage(previous)
    }

    /** Room emissions update the UI only after the operation has committed. */
    private fun persistSettings(write: suspend () -> Unit) {
        viewModelScope.launch {
            settingsWriteMutex.withLock {
                try {
                    write()
                    _uiState.update { it.copy(settingsSaveFailed = false) }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    Log.e("LauncherViewModel", "Unable to save launcher settings", error)
                    _uiState.update { it.copy(settingsSaveFailed = true) }
                }
            }
        }
    }

    fun toggleFavorite(app: LauncherApp): Boolean {
        val state = _uiState.value
        val profileAvailable = when (app.folderId) {
            PrivateSpaceFolderId -> state.canFavoritePrivateSpace
            WorkProfileFolderId -> state.canFavoriteWorkProfile
            else -> null
        }
        if (profileAvailable == false) return app.key in state.favoriteKeys
        if (app.folderId != null && profileAvailable == null) {
            val folder = _uiState.value.folders.firstOrNull { it.id == app.folderId } ?: return false
            val favorite = !folder.placement.inFavorites
            persistSettings { settingsRepository.setFolderFavorites(mapOf(folder.id to favorite)) }
            return favorite
        }
        if (app.shortcut != null && app.key !in _uiState.value.favoriteKeys) {
            viewModelScope.launch {
                if (rememberShortcut(app)) {
                    val updated = favoritesStore.toggle(app.key, currentFavoriteOrder())
                    _uiState.update { it.copy(favoriteKeys = updated.toSet(), favoriteOrder = updated) }
                }
            }
            return true
        }
        val updated = favoritesStore.toggle(app.key, currentFavoriteOrder())
        _uiState.update { it.copy(favoriteKeys = updated.toSet(), favoriteOrder = updated) }
        if (app.folderId == null && app.key in updated) prepareShortcuts(app)
        return app.key in updated
    }

    fun reorderFavorites(keys: List<String>) {
        val updated = favoritesStore.reorder(keys, currentFavoriteOrder())
        _uiState.update { it.copy(favoriteOrder = updated) }
        val visibleOrder = _uiState.value.homeItems.map { it.key }
        persistSettings {
            settingsRepository.mutateSettings { current -> current.copy(homeLayout = current.homeLayout.copy(
                homeOrder = com.galaxyrio.gracelauncher.data.mergeHomeOrder(keys, current.homeLayout.homeOrder ?: visibleOrder))) }
        }
    }

    private fun currentFavoriteOrder(): List<String> = _uiState.value.let { state ->
        (state.favoriteOrder + state.favoriteItems.map(LauncherApp::key)).distinct()
    }

    fun requestReturnHome() {
        _returnHomeRequests.tryEmit(Unit)
    }
}
