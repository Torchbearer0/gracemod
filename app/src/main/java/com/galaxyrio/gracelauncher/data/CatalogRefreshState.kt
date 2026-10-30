package com.galaxyrio.gracelauncher.data

/** Tracks successful loads; invalidations arriving during a load must survive its completion. */
internal class CatalogRefreshState<S> {
    data class Request<S>(val inputs: S, val revision: Long)

    private var revision = 0L
    private var loaded: Request<S>? = null

    fun invalidate() { revision++ }

    fun pending(inputs: S): Request<S>? = Request(inputs, revision).takeUnless { it == loaded }

    fun start(inputs: S): Request<S>? = pending(inputs)?.also {
        // Once rebuilding, even a return to the old inputs needs a successful completion.
        loaded = null
    }

    fun isCurrent(request: Request<S>, inputs: S): Boolean = request == Request(inputs, revision)

    fun complete(request: Request<S>, inputs: S): Boolean {
        if (!isCurrent(request, inputs)) return false
        loaded = request
        return true
    }
}
