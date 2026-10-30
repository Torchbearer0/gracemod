package com.galaxyrio.gracelauncher.platform

/** Give widget interactions more time before moving; retain longer accessibility delays. */
internal fun widgetLongPressTimeout(systemTimeoutMillis: Long): Long = systemTimeoutMillis.coerceAtLeast(800L)
