package com.galaxyrio.gracelauncher.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.delay

@Composable
internal fun rememberHomeTime(clock: Clock = Clock.systemUTC()): State<Instant> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(initialValue = clock.instant(), lifecycle, clock) {
        // Coroutine delays use uptime, which stops during deep sleep. Restart on
        // resume so the clock, date and agenda don't wait for an old delay to end.
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val now = clock.instant()
                value = now
                delay(60_000 - now.toEpochMilli() % 60_000)
            }
        }
    }
}
