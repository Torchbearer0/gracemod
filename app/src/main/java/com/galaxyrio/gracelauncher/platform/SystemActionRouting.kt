package com.galaxyrio.gracelauncher.platform

import com.galaxyrio.gracelauncher.data.GraceButtonAction

/** Select exactly one backend. A failed/no-op experimental call must never trigger a fallback. */
internal fun performGlobalSystemAction(
    action: GraceButtonAction,
    preferNonAccessibility: Boolean,
    accessibility: (GraceButtonAction) -> Boolean,
    statusBar: (GraceButtonAction) -> Boolean,
): Boolean = when {
    action.usesStatusBarApi(preferNonAccessibility) -> statusBar(action)
    action.requiresAccessibility -> accessibility(action)
    else -> false
}
