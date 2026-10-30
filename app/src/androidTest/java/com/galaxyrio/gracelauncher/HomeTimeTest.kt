package com.galaxyrio.gracelauncher

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.galaxyrio.gracelauncher.ui.home.rememberHomeTime
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test

class HomeTimeTest {
    @get:Rule val compose = createComposeRule()

    private val clock = MutableClock(Instant.parse("2026-10-15T23:59:10Z"))
    private lateinit var owner: TestLifecycleOwner

    @Test fun pauseThenResumeRefreshesWithoutWaitingForThePendingMinuteTick() {
        showTime()
        assertTime("2026-10-15T23:59:10Z")

        // Some devices only pause HOME while the lock screen covers it.
        moveTo(Lifecycle.State.STARTED)
        compose.runOnIdle { clock.now = Instant.parse("2026-10-16T00:04:20Z") }
        assertTime("2026-10-15T23:59:10Z")
        moveTo(Lifecycle.State.RESUMED)
        assertTime("2026-10-16T00:04:20Z")

        // A second wake must restart again, including after a clock correction.
        moveTo(Lifecycle.State.STARTED)
        compose.runOnIdle { clock.now = Instant.parse("2026-10-16T00:01:25Z") }
        moveTo(Lifecycle.State.RESUMED)
        assertTime("2026-10-16T00:01:25Z")
    }

    @Test fun stoppedHomeRefreshesOnResumeAndKeepsMinuteTicksAligned() {
        showTime()
        moveTo(Lifecycle.State.CREATED)
        compose.runOnIdle { clock.now = Instant.parse("2026-10-16T00:04:59Z") }
        compose.mainClock.advanceTimeBy(120_000)
        assertTime("2026-10-15T23:59:10Z")
        moveTo(Lifecycle.State.STARTED)
        assertTime("2026-10-15T23:59:10Z")
        moveTo(Lifecycle.State.RESUMED)
        assertTime("2026-10-16T00:04:59Z")

        compose.runOnIdle { clock.now = Instant.parse("2026-10-16T00:05:00Z") }
        compose.mainClock.advanceTimeBy(1_200)
        assertTime("2026-10-16T00:05:00Z")
    }

    private fun showTime() {
        compose.runOnUiThread { owner = TestLifecycleOwner() }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                val now by rememberHomeTime(clock)
                Text(now.toString(), Modifier.testTag("time"))
            }
        }
    }

    private fun moveTo(state: Lifecycle.State) {
        compose.runOnIdle { owner.lifecycle.currentState = state }
    }

    private fun assertTime(expected: String) = compose.onNodeWithTag("time").assertTextEquals(expected)

    private class TestLifecycleOwner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
    }

    private class MutableClock(var now: Instant) : Clock() {
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)
    }
}
