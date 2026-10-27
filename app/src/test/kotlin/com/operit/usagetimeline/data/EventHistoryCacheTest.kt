package com.operit.usagetimeline.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class EventHistoryCacheTest {
    private val end = 86_400_000L

    @Test fun refreshReadsOnlyOverlappingTailWithoutDuplicates() = runBlocking {
        val cache = EventHistoryCache()
        val events = listOf(TimelineEvent(1000, 1, "a"), TimelineEvent(110000, 2, "a"))
        val windows = mutableListOf<Pair<Long, Long>>()
        val query: suspend (Long, Long) -> List<TimelineEvent> = { from, to ->
            windows.add(from to to)
            events.filter { it.time in from until to }
        }
        cache.load(0, end, 100000, true, query)
        val refreshed = cache.load(0, end, 120000, true, query)
        assertEquals(40000L to 120000L, windows.last())
        assertEquals(events, refreshed.events)
        assertEquals(listOf(UsageSession("a", 1000, 110000)), refreshed.sessions)
    }

    @Test fun changingFiltersReusesSnapshotWithoutQuery() = runBlocking {
        val cache = EventHistoryCache()
        val initial = cache.load(0, end, 10000, true) { _, _ -> listOf(TimelineEvent(1000, 1, "a")) }
        val filtered = cache.load(0, end, 20000, false) { _, _ -> error("Unexpected system query") }
        assertSame(initial, filtered)
    }

    @Test fun completedDaysAreCachedButLateEventsAreRechecked() = runBlocking {
        val cache = EventHistoryCache()
        cache.load(0, end, end, true) { _, _ -> emptyList() }
        cache.load(0, end, end + 1000, true) { _, _ -> error("Unexpected system query") }
        var called = false
        cache.load(0, end, end + 300000, true) { from, to ->
            called = true
            assertEquals(end - 60000, from)
            assertEquals(end, to)
            emptyList()
        }
        assertTrue(called)
    }

    @Test fun clockRollbackRebuildsRatherThanKeepingFutureEvents() = runBlocking {
        val cache = EventHistoryCache()
        cache.load(0, end, 120000, true) { _, _ -> listOf(TimelineEvent(110000, 1, "a")) }
        val result = cache.load(0, end, 100000, true) { from, _ ->
            assertEquals(-6 * 3600_000L, from)
            emptyList()
        }
        assertTrue(result.events.isEmpty())
    }

    @Test fun cancelledRefreshDoesNotPoisonCache() = runBlocking {
        val cache = EventHistoryCache()
        val initial = cache.load(0, end, 100000, true) { _, _ -> emptyList() }
        try {
            cache.load(0, end, 120000, true) { _, _ -> throw CancellationException() }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertSame(initial, cache.load(0, end, 130000, false) { _, _ -> error("Unexpected query") })
    }

    @Test fun permissionRevocationClearsCachedData() = runBlocking {
        val cache = EventHistoryCache()
        cache.load(0, end, 100000, true) { _, _ -> listOf(TimelineEvent(1000, 1, "a")) }
        cache.clear()
        assertTrue(cache.load(0, end, 100000, false) { _, _ -> emptyList() }.events.isEmpty())
    }
}
