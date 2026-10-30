package com.galaxyrio.gracelauncher.data.media

import android.app.Notification
import android.app.NotificationManager
import android.media.session.MediaSession
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.annotation.MainThread
import com.galaxyrio.gracelauncher.data.notifications.appNotifications
import com.galaxyrio.gracelauncher.data.notifications.AppNotificationRanking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Keep only media-notification identity, not notification text, artwork or actions. */
internal data class MediaNotificationRef(val key: String, val packageName: String, val token: MediaSession.Token)

internal data class MediaNotificationSnapshot(
    val connected: Boolean = false,
    val notifications: List<MediaNotificationRef> = emptyList(),
) {
    fun contains(packageName: String, token: MediaSession.Token): Boolean = connected &&
        notifications.any { it.packageName == packageName && it.token == token }
}

@MainThread
internal class MediaNotificationStore {
    private val references = linkedMapOf<String, MediaNotificationRef>()
    private val _state = MutableStateFlow(MediaNotificationSnapshot())
    val state = _state.asStateFlow()

    fun connected(notifications: List<StatusBarNotification>) {
        references.clear()
        notifications.mapNotNull(::mediaNotificationRef).forEach { references[it.key] = it }
        publish()
    }

    fun posted(notification: StatusBarNotification) {
        if (!_state.value.connected) return
        val media = mediaNotificationRef(notification)
        // A notification can be updated in-place from MediaStyle to an ordinary notification.
        val changed = if (media == null) references.remove(notification.key) != null
            else references.put(media.key, media) != media
        if (changed) publish()
    }

    fun removed(key: String) {
        // Removal callbacks may carry a light notification with no media extras.
        if (references.remove(key) != null) publish()
    }

    fun disconnected() {
        references.clear()
        _state.value = MediaNotificationSnapshot()
    }

    private fun publish() {
        _state.value = MediaNotificationSnapshot(connected = true, notifications = references.values.toList())
    }
}

internal val mediaNotifications = MediaNotificationStore()

/** One system binding feeds app messages and media-notification visibility independently. */
class MediaNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        // Bootstrap only after binding, so existing paused notifications survive a launcher restart.
        try {
            val notifications = activeNotifications.orEmpty().toList()
            mediaNotifications.connected(notifications)
            appNotifications.connected(notifications, appRanking(currentRanking)) { key -> runCatching { cancelNotification(key) }.isSuccess }
        } catch (_: RuntimeException) {
            mediaNotifications.disconnected()
            appNotifications.disconnected()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        mediaNotifications.posted(sbn)
        // Publish content and its ranking together, without a transient normal/silent flash.
        appNotifications.posted(sbn, appRanking(rankingMap))
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        mediaNotifications.removed(sbn.key)
        appNotifications.removed(sbn.key)
    }
    override fun onNotificationRankingUpdate(rankingMap: RankingMap) { appNotifications.ranking(appRanking(rankingMap)) }
    private fun appRanking(rankingMap: RankingMap?): (String) -> AppNotificationRanking = { key ->
        val ranking = Ranking()
        if (rankingMap == null || !rankingMap.getRanking(key, ranking)) AppNotificationRanking()
        else AppNotificationRanking(
            visible = !ranking.isSuspended && ranking.importance != NotificationManager.IMPORTANCE_NONE,
            // Use the system's per-notification importance, not sound/vibration or DND:
            // muting the phone does not turn ordinary messages into silent channels.
            silent = ranking.importance in NotificationManager.IMPORTANCE_MIN..NotificationManager.IMPORTANCE_LOW,
        )
    }
    override fun onListenerDisconnected() { mediaNotifications.disconnected(); appNotifications.disconnected() }
    override fun onDestroy() {
        mediaNotifications.disconnected()
        appNotifications.disconnected()
        super.onDestroy()
    }
}

@Suppress("DEPRECATION")
private fun mediaNotificationRef(sbn: StatusBarNotification): MediaNotificationRef? = runCatching {
    val extras = sbn.notification.extras ?: return null
    val token = if (Build.VERSION.SDK_INT >= 33) {
        extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
    } else {
        extras.getParcelable(Notification.EXTRA_MEDIA_SESSION) as? MediaSession.Token
    } ?: return null
    MediaNotificationRef(sbn.key, sbn.packageName, token)
}.getOrNull()
