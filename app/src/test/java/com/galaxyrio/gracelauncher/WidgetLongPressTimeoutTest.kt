package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.platform.widgetLongPressTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetLongPressTimeoutTest {
    @Test fun shortSystemDelayIsExtended() = assertEquals(800L, widgetLongPressTimeout(400L))
    @Test fun longerAccessibilityDelayIsPreserved() = assertEquals(1500L, widgetLongPressTimeout(1500L))
    @Test fun nestedWidgetConfigurationDoesNotExtendTwice() =
        assertEquals(800L, widgetLongPressTimeout(widgetLongPressTimeout(500L)))
}
