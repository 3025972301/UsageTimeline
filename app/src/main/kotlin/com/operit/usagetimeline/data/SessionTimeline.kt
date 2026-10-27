package com.operit.usagetimeline.data

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

internal data class TimelineEvent(val time: Long, val type: Int, val packageName: String?)

/** Reconstruct foreground sessions before applying display filters. */
internal fun buildSessions(
    events: List<TimelineEvent>,
    start: Long,
    end: Long,
    minSessionMs: Long,
): List<UsageSession> {
    val raw = mutableListOf<UsageSession>()
    var pkg: String? = null
    var opened = 0L
    var paused: Long? = null
    var count = 0
    fun close(at: Long) {
        val current = pkg ?: return
        val from = maxOf(opened, start)
        val to = minOf(paused ?: at, end)
        if (to > from) raw.add(UsageSession(current, from, to))
        pkg = null
        paused = null
        count = 0
    }
    for (event in events.sortedBy { it.time }) {
        if (event.time >= end) break
        when (event.type) {
            1 -> {
                val next = event.packageName ?: continue
                // A resumed app after its final pause starts a new session, not idle time.
                if (pkg != next || paused != null) {
                    close(event.time)
                    pkg = next
                    opened = event.time
                }
                count++
            }
            2 -> if (pkg == event.packageName && count > 0) {
                count--
                if (count == 0) paused = event.time
            }
            16, 17, 26 -> close(event.time)
        }
    }
    close(end)
    val merged = mutableListOf<UsageSession>()
    for (session in raw) {
        val last = merged.lastOrNull()
        if (last != null && last.packageName == session.packageName &&
            session.start - last.end in 0L..2000L
        ) {
            merged[merged.lastIndex] = last.copy(end = session.end)
        } else {
            merged.add(session)
        }
    }
    return merged.filter { it.duration >= minSessionMs }
}

/** Local clock hours can be skipped or repeated during daylight-saving transitions. */
fun hourlyUsage(sessions: List<UsageSession>, zone: ZoneId = ZoneId.systemDefault()): LongArray {
    val result = LongArray(24)
    for (session in sessions) {
        var cursor = session.start
        while (cursor < session.end) {
            val local = Instant.ofEpochMilli(cursor).atZone(zone)
            val nextHour = local.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli()
            val boundary = minOf(session.end, nextHour)
            result[local.hour] += boundary - cursor
            cursor = boundary
        }
    }
    return result
}
