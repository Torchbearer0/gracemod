package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.icons.IconSnapshotCache
import org.junit.Assert.*
import org.junit.Test

class IconSnapshotCacheTest {
    @Test fun reopeningAnUnchangedProfileReusesTheSameResult() {
        val cache = IconSnapshotCache<Int, Any>()
        val result = Any()
        cache.put("profile:10:app", 1, result)
        repeat(20) { assertSame(result, cache.get("profile:10:app", 1)) }
    }

    @Test fun changedRevisionsDoNotReuseOldResults() {
        val cache = IconSnapshotCache<Int, Any>()
        cache.put("profile:10:app", 1, Any())
        assertNull(cache.get("profile:10:app", 2))
    }

    @Test fun changingOneIconDoesNotInvalidateOtherProfilesOrApps() {
        val cache = IconSnapshotCache<Int, Any>()
        val other = Any()
        cache.put("profile:10:app", 1, Any())
        cache.put("profile:11:app", 1, other)
        cache.put("profile:10:app", 2, Any())
        assertNull(cache.get("profile:10:app", 1))
        assertSame(other, cache.get("profile:11:app", 1))
        assertNull(cache.get("personal:app", 1))
    }

    @Test fun onlyTheLatestAppearanceIsRetainedPerItem() {
        val cache = IconSnapshotCache<Int, Any>(2)
        val other = Any()
        val latest = Any()
        cache.put("other", 1, other)
        repeat(20) { cache.put("app", it, latest) }
        assertNull(cache.get("app", 18))
        assertSame(latest, cache.get("app", 19))
        assertSame(other, cache.get("other", 1))
    }

    @Test fun entryCountAndBitmapBudgetAreBounded() {
        for ((capacity, budget) in listOf(2 to Int.MAX_VALUE, 100 to 10)) {
            val cache = IconSnapshotCache<Int, ByteArray>(capacity = capacity, maxBytes = budget, sizeOf = { it.size })
            val keys = listOf("a", "b", "c")
            keys.forEach { cache.put(it, 1, ByteArray(4)) }
            assertNotNull("capacity=$capacity, budget=$budget", cache.get("c", 1))
            assertEquals("capacity=$capacity, budget=$budget", 2, keys.count { cache.get(it, 1) != null })
        }
    }

    @Test fun replacingAnIconReleasesItsOldBudgetAndOversizedImagesAreNotRetained() {
        val cache = IconSnapshotCache<Int, ByteArray>(maxBytes = 10, sizeOf = { it.size })
        cache.put("a", 1, ByteArray(8))
        cache.put("a", 2, ByteArray(2))
        cache.put("b", 1, ByteArray(8))
        assertNotNull(cache.get("a", 2))
        assertNotNull(cache.get("b", 1))
        cache.put("huge", 1, ByteArray(11))
        assertNull(cache.get("huge", 1))
        assertNotNull(cache.get("a", 2))
        assertNotNull(cache.get("b", 1))
    }

    @Test fun fullCatalogScansReuseIconsEvenWhenTheCatalogExceedsEitherLimit() {
        val iconBytes = 144 * 144 * 4
        for ((apps, budget) in listOf(200 to 16 * 1024 * 1024, 203 to 16 * 1024 * 1024, 257 to Int.MAX_VALUE)) {
            val cache = IconSnapshotCache<Int, Int>(maxBytes = budget, sizeOf = { iconBytes })
            val capacity = minOf(256, budget / iconBytes)
            repeat(5) { pass ->
                var hits = 0
                repeat(apps) { key ->
                    if (cache.get("app:$key", 1) != null) hits++ else cache.put("app:$key", 1, key)
                }
                if (pass > 0) {
                    if (apps <= capacity) assertEquals(apps, hits)
                    else assertTrue("$apps apps, pass $pass: only $hits hits", hits >= capacity - 1)
                }
            }
        }
    }
}
