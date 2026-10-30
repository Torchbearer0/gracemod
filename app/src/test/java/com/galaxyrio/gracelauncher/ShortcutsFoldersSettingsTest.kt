package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.NotificationDisplay
import com.galaxyrio.gracelauncher.data.ShortcutsFoldersSettings
import com.galaxyrio.gracelauncher.data.notifications.AppNotification
import org.junit.Assert.*
import org.junit.Test

class ShortcutsFoldersSettingsTest {
    @Test fun defaultsHideSilentAndShowOrdinaryNotifications() {
        val settings = ShortcutsFoldersSettings()
        assertTrue(settings.enabled)
        assertFalse(settings.swipeLeftToOpenFirst)
        assertFalse(settings.singleLineNotificationPreviews)
        assertFalse(settings.multipleAppNotificationPreviews)
        listOf(false, true).forEach { expanded ->
            assertFalse(settings.showsNotification(silent = true, expanded))
            assertTrue(settings.showsNotification(silent = false, expanded))
        }
    }

    @Test fun bothKindsHaveIndependentNormalExpandedAndHiddenModes() {
        NotificationDisplay.entries.forEach { silent ->
            NotificationDisplay.entries.forEach { normal ->
                val settings = ShortcutsFoldersSettings(silentNotifications = silent, normalNotifications = normal)
                listOf(false, true).forEach { expanded ->
                    assertEquals(silent == NotificationDisplay.Normal || (expanded && silent == NotificationDisplay.ExpandedOnly),
                        settings.showsNotification(silent = true, expanded))
                    assertEquals(normal == NotificationDisplay.Normal || (expanded && normal == NotificationDisplay.ExpandedOnly),
                        settings.showsNotification(silent = false, expanded))
                }
            }
        }
    }

    @Test fun disablingPopupsHidesPreviewsWithoutLosingTheirPreferences() {
        val enabled = ShortcutsFoldersSettings(swipeLeftToOpenFirst = true, silentNotifications = NotificationDisplay.Normal,
            normalNotifications = NotificationDisplay.ExpandedOnly, singleLineNotificationPreviews = true,
            multipleAppNotificationPreviews = true)
        val disabled = enabled.copy(enabled = false)
        listOf(false, true).forEach { silent ->
            listOf(false, true).forEach { expanded -> assertFalse(disabled.showsNotification(silent, expanded)) }
        }
        assertEquals(enabled, disabled.copy(enabled = true))
    }

    @Test fun defaultPreviewKeepsOnlyTheNewestNotification() {
        val newest = AppNotification("new", "one", "Title", "Body", 30, 1, true, true)
        val older = newest.copy(key = "old", packageName = "two", postedAt = 20)
        assertEquals(listOf(newest), ShortcutsFoldersSettings().previewNotifications(listOf(newest, older)))
    }

    @Test fun multiplePreviewsKeepTheLatestPerAppInTimeOrderWithoutChangingTheInput() {
        val newest = AppNotification("new", "one", "Title", "First\nSecond", 30, 1, true, true)
        val other = newest.copy(key = "other", packageName = "two", postedAt = 20)
        val older = newest.copy(key = "old", postedAt = 10)
        val notifications = listOf(newest, other, older, other)
        val settings = ShortcutsFoldersSettings(multipleAppNotificationPreviews = true)
        assertEquals(listOf(newest, other), settings.previewNotifications(notifications))
        assertEquals(listOf(newest, other, older, other), notifications)
        assertEquals("First\nSecond", settings.previewNotifications(notifications).first().text)
        assertEquals(listOf(newest), settings.previewNotifications(listOf(newest, older)))
    }

    @Test fun eitherPreviewModeHandlesNoNotifications() {
        listOf(false, true).forEach { multiple ->
            assertTrue(ShortcutsFoldersSettings(multipleAppNotificationPreviews = multiple).previewNotifications(emptyList()).isEmpty())
        }
    }
}
