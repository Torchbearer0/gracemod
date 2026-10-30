package com.galaxyrio.gracelauncher

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.data.AppRepository
import com.galaxyrio.gracelauncher.data.LauncherApp
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLaunchRoutingTest {
    // No real activities or profile operations are invoked by these tests.
    private class RecordingContext(val failure: Exception? = null) :
        ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
        val launches = mutableListOf<Pair<Intent, Bundle?>>()
        var profileServiceRequests = 0

        override fun startActivity(intent: Intent, options: Bundle?) {
            launches += intent to options
            failure?.let { throw it }
        }

        override fun getSystemService(name: String): Any? {
            if (name == Context.LAUNCHER_APPS_SERVICE) {
                profileServiceRequests++
                throw SecurityException("Profile unavailable in test")
            }
            return super.getSystemService(name)
        }
    }

    private val app = LauncherApp(ComponentName("example.app", "example.app.LauncherAlias"), "App", null)

    @Test fun currentUserUsesExactLauncherEntryAndPreservesAnimationInputs() {
        for (user in listOf(null, Process.myUserHandle())) {
            val context = RecordingContext()
            val bounds = Rect(10, 20, 60, 70)
            val options = Bundle().apply { putString("animation", "preserved") }
            assertTrue(AppRepository(context).launch(app.copy(user = user), bounds, options))
            val (intent, actualOptions) = context.launches.single()
            assertEquals(Intent.ACTION_MAIN, intent.action)
            assertEquals(setOf(Intent.CATEGORY_LAUNCHER), intent.categories)
            assertEquals(app.componentName, intent.component)
            assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED, intent.flags)
            assertEquals(bounds, intent.sourceBounds)
            assertSame(options, actualOptions)
            assertEquals(0, context.profileServiceRequests)
        }
    }

    @Test fun personalLaunchFailuresAreReportedWithoutAnotherLaunchAttempt() {
        for (failure in listOf(ActivityNotFoundException("Missing entry"), SecurityException("Denied"))) {
            val context = RecordingContext(failure)
            assertFalse(AppRepository(context).launch(app))
            assertEquals(1, context.launches.size)
            assertEquals(0, context.profileServiceRequests)
        }
    }

    @Test fun foreignUserUsesProfileServiceAndNeverFallsBackToPersonalApp() {
        val context = RecordingContext()
        val otherUser = listOf(UserHandle.getUserHandleForUid(0), UserHandle.getUserHandleForUid(100_000))
            .first { it != Process.myUserHandle() }
        assertFalse(AppRepository(context).launch(app.copy(user = otherUser)))
        assertEquals(1, context.profileServiceRequests)
        assertTrue(context.launches.isEmpty())
    }
}
