package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.*
import com.galaxyrio.gracelauncher.ui.components.formatUsageValues
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class DigitalWellbeingTest {
    @Test fun usageValuesUseOnePeriodAndAnIndependentPercentage() {
        val all = mapOf(UsageDisplay.Day to "1 m", UsageDisplay.Week to "2 m",
            UsageDisplay.Month to "1 h 3 m", UsageDisplay.Percentage to "50%")
        assertEquals("1m 50%", formatUsageValues(all))
        for (period in listOf(UsageDisplay.Day, UsageDisplay.Week, UsageDisplay.Month)) {
            assertEquals("1m 50%", formatUsageValues(mapOf(period to "1 m", UsageDisplay.Percentage to "50%")))
            assertEquals("1m", formatUsageValues(mapOf(period to "1 m")))
        }
        assertEquals("1m", formatUsageValues(all.filterKeys { it == UsageDisplay.Day || it == UsageDisplay.Week }))
        assertEquals("50%", formatUsageValues(mapOf(UsageDisplay.Percentage to "50%")))
        assertNull(formatUsageValues(emptyMap()))
    }
    @Test fun oldMultiplePeriodsKeepFirstPeriodWithoutChangingPercentage() {
        assertEquals(setOf(UsageDisplay.Day, UsageDisplay.Percentage), singleUsageDisplay(UsageDisplay.entries.toSet()))
        assertEquals(setOf(UsageDisplay.Week), singleUsageDisplay(setOf(UsageDisplay.Month, UsageDisplay.Week)))
        assertEquals(setOf(UsageDisplay.Percentage), singleUsageDisplay(setOf(UsageDisplay.Percentage)))
        assertTrue(singleUsageDisplay(emptySet()).isEmpty())
    }
    private val zone = ZoneId.of("Asia/Shanghai")
    @Test fun zeroValuesHideIndependentlyAndEmptyUsageHasNoSeparator() {
        val values = mapOf(UsageDisplay.Week to "0 m", UsageDisplay.Percentage to "0%")
        assertNull(formatUsageValues(values, values.keys))
        assertEquals("0m 0%", formatUsageValues(values))
        assertEquals("50%", formatUsageValues(values + (UsageDisplay.Percentage to "50%"), setOf(UsageDisplay.Week)))
        assertEquals("1m", formatUsageValues(values + (UsageDisplay.Week to "1 m"), setOf(UsageDisplay.Percentage)))
        assertEquals("<1m", formatUsageValues(mapOf(UsageDisplay.Day to "<1 m")))
    }
    private fun time(value: String) = Instant.parse(value).toEpochMilli()

    @Test fun disabledByDefaultWithDayDisplayAndDailyRefresh() {
        val settings = DigitalWellbeingSettings()
        assertFalse(settings.enabled)
        assertTrue(settings.hideZeroValues)
        assertEquals(setOf(UsageDisplay.Day), settings.display)
        assertEquals(UsageRefresh.Daily, settings.refresh)
    }
    @Test fun dailyRefreshUsesLocalDateNotElapsedTwentyFourHours() {
        val before = time("2026-10-22T15:59:00Z")
        assertFalse(usageRefreshDue(before, before + 30_000, UsageRefresh.Daily, zone))
        assertTrue(usageRefreshDue(before, before + 60_000, UsageRefresh.Daily, zone))
    }
    @Test fun weeklyRefreshOccursOnMondayAndNeverKeepsItsSnapshot() {
        val sunday = time("2026-10-25T15:59:00Z")
        assertFalse(usageRefreshDue(sunday, sunday + 30_000, UsageRefresh.Weekly, zone))
        assertTrue(usageRefreshDue(sunday, sunday + 60_000, UsageRefresh.Weekly, zone))
        assertFalse(usageRefreshDue(sunday, sunday + 90L * 86_400_000, UsageRefresh.Never, zone))
        assertFalse(usageRefreshDue(sunday, sunday - 90L * 86_400_000, UsageRefresh.Never, zone))
        assertTrue(usageRefreshDue(0, sunday, UsageRefresh.Never, zone))
    }
    @Test fun midnightSessionsAreClippedAndUnfinishedSessionsEndAtQueryTime() {
        val usage = ForegroundUsage(100, 200)
        usage.resume("one", "Activity", 50)
        usage.pause("one", "Activity", 120)
        usage.resume("two", "Activity", 180)
        assertEquals(mapOf("one" to 20L, "two" to 20L), usage.finish())
    }
    @Test fun overlappingActivitiesWithinOneAppDoNotDoubleCount() {
        val usage = ForegroundUsage(0, 200)
        usage.resume("app", "one", 10)
        usage.resume("app", "one", 20)
        usage.resume("app", "two", 30)
        usage.pause("app", "one", 40)
        usage.pause("app", "two", 50)
        assertEquals(40L, usage.finish()["app"])
    }
    @Test fun screenOffOrShutdownStopsAllActiveSessions() {
        val usage = ForegroundUsage(0, 200)
        usage.resume("one", "Activity", 10)
        usage.resume("two", "Activity", 20)
        usage.stopAll(30)
        usage.pause("one", "Activity", 100)
        assertEquals(mapOf("one" to 20L, "two" to 10L), usage.finish())
    }
    @Test fun appOpeningsCountPackageTransitionsNotInternalActivityChanges() {
        val counter = AppOpenCounter("launcher", LocalDate.parse("2026-10-18"), zone)
        val start = time("2026-10-23T00:00:00Z")
        counter.resume("app", start)
        counter.resume("app", start + 1) // Another Activity or duplicate resume.
        counter.resume("launcher", start + 2)
        counter.resume("app", start + 3)
        counter.resume("other", start + 4)
        counter.resume("app", start + 5)
        assertEquals(mapOf("app" to 3L, "other" to 1L), counter.totals())
    }
    @Test fun appOpeningHistorySurvivesBatchesWithoutCountingContinuationTwice() {
        val initial = mapOf("2026-10-23" to mapOf("app" to 4L))
        val counter = AppOpenCounter("launcher", LocalDate.parse("2026-10-18"), zone, initial, "app")
        counter.resume("app", time("2026-10-23T00:00:00Z"))
        assertEquals(4L, counter.totals()["app"])
        counter.screenOff()
        counter.resume("app", time("2026-10-23T01:00:00Z"))
        assertEquals(5L, counter.totals()["app"])
        assertEquals(4L, initial.getValue("2026-10-23")["app"])
    }
    @Test fun openingCountsExpireOutsideSevenLocalDaysAndWarmupDoesNotCount() {
        val counter = AppOpenCounter("launcher", LocalDate.parse("2026-10-18"), zone,
            mapOf("2026-10-17" to mapOf("old" to 100L), "2026-10-18" to mapOf("app" to 2L)))
        counter.resume("carry", time("2026-10-17T15:59:00Z"))
        counter.resume("carry", time("2026-10-17T16:01:00Z"))
        counter.resume("app", time("2026-10-17T16:02:00Z"))
        assertEquals(mapOf("app" to 3L), counter.totals())
        assertEquals(3L, counter.days.getValue("2026-10-18")["app"])
    }
}
