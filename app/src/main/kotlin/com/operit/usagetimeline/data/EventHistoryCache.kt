package com.operit.usagetimeline.data

internal data class EventSnapshot(val until: Long, val fetchedAt: Long, val events: List<TimelineEvent>, val sessions: List<UsageSession>)

/** Access is serialized by the repository. Keep raw events so filters never need a system query. */
internal class EventHistoryCache {
    private val snapshots = LinkedHashMap<Pair<Long, Long>, EventSnapshot>(8, 0.75f, true)

    fun clear() = snapshots.clear()

    suspend fun load(
        dayStart: Long,
        dayEnd: Long,
        now: Long,
        refresh: Boolean,
        query: suspend (Long, Long) -> List<TimelineEvent>,
    ): EventSnapshot {
        val until = minOf(dayEnd, now)
        val key = dayStart to dayEnd
        val previous = snapshots[key]?.takeIf { it.until <= until }
        // Recheck historical tails periodically: some ROMs deliver events after midnight.
        if (previous != null && (!refresh || (previous.until == dayEnd && now - previous.fetchedAt < 300_000L))) {
            return previous
        }
        val from = if (previous == null) dayStart - 6 * 3600_000L
            else maxOf(dayStart - 6 * 3600_000L, previous.until - 60_000L)
        val fresh = query(from, until).filter { it.time in from until until }
        val events = previous?.events.orEmpty().filter { it.time < from } + fresh
        val snapshot = EventSnapshot(until, now, events, buildSessions(events, dayStart, until, 0))
        snapshots[key] = snapshot
        while (snapshots.size > 8) snapshots.remove(snapshots.keys.first())
        return snapshot
    }
}
