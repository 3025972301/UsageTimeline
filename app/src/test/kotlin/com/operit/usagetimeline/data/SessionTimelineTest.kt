package com.operit.usagetimeline.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SessionTimelineTest {
    private fun event(time: Long, type: Int, pkg: String? = "a") = TimelineEvent(time, type, pkg)

    @Test fun reopeningDoesNotCountIdleTime() {
        val sessions = buildSessions(listOf(event(1000, 1), event(5000, 2), event(15000, 1), event(20000, 2)), 0, 30000, 0)
        assertEquals(listOf(UsageSession("a", 1000, 5000), UsageSession("a", 15000, 20000)), sessions)
    }

    @Test fun multipleActivitiesKeepSessionUntilFinalPause() {
        val sessions = buildSessions(listOf(event(1000, 1), event(2000, 1), event(3000, 2), event(4000, 2)), 0, 10000, 0)
        assertEquals(listOf(UsageSession("a", 1000, 4000)), sessions)
    }

    @Test fun screenOffStopsAnUnpairedSession() {
        for (type in listOf(16, 17, 26)) {
            assertEquals(listOf(UsageSession("a", 1000, 5000)),
                buildSessions(listOf(event(1000, 1), event(5000, type, null)), 0, 20000, 0))
        }
    }

    @Test fun shortFragmentsMergeBeforeThreshold() {
        assertEquals(listOf(UsageSession("a", 1000, 6000)), buildSessions(
            listOf(event(1000, 1), event(3000, 2), event(4000, 1), event(6000, 2)), 0, 10000, 5000))
    }

    @Test fun appSwitchesAreNotMergedThroughFilteredApps() {
        assertEquals(2, buildSessions(listOf(event(0, 1), event(10000, 1, "b"), event(11000, 1)), 0, 20000, 5000).size)
    }

    @Test fun clipsCrossMidnightAndRunningSessions() {
        assertEquals(listOf(UsageSession("a", 5000, 10000)), buildSessions(listOf(event(0, 1)), 5000, 10000, 0))
    }

    @Test fun ignoresUnmatchedPausesAndSortsEvents() {
        assertEquals(listOf(UsageSession("a", 1000, 5000)), buildSessions(
            listOf(event(5000, 2), event(0, 2), event(1000, 1), event(6000, 2)), 0, 10000, 0))
    }

    @Test(timeout = 2000) fun fallBackDayHasTwentyFiveHoursWithoutLooping() {
        val zone = ZoneId.of("America/New_York")
        val day = LocalDate.of(2026, 11, 1)
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val hours = hourlyUsage(listOf(UsageSession("a", start, end)), zone)
        assertEquals(25 * 3600_000L, hours.sum())
        assertEquals(2 * 3600_000L, hours[1])
    }

    @Test fun springForwardDaySkipsMissingHour() {
        val zone = ZoneId.of("America/New_York")
        val day = LocalDate.of(2026, 3, 8)
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val hours = hourlyUsage(listOf(UsageSession("a", start, end)), zone)
        assertEquals(23 * 3600_000L, hours.sum())
        assertEquals(0L, hours[2])
    }

    @Test(timeout = 2000) fun handlesLargeEventHistory() {
        val events = (0 until 10000).flatMap { i ->
            val start = i * 10000L
            listOf(event(start, 1), event(start + 5000, 2))
        }
        val sessions = buildSessions(events, 0, 100000000L, 0)
        assertEquals(10000, sessions.size)
        assertEquals(50000000L, sessions.sumOf { it.duration })
    }
}
