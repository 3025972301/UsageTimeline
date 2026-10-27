package com.operit.usagetimeline.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

class UsageRepository(private val context: Context) {
    private val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    private val pm = context.packageManager
    private val labels = ConcurrentHashMap<String, String>()
    private val systemApps = ConcurrentHashMap<String, Boolean>()
    private val queryMutex = Mutex()
    private val cache = EventHistoryCache()

    suspend fun hasUsageAccess(): Boolean = withContext(Dispatchers.IO) {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return@withContext false
        @Suppress("DEPRECATION")
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        when (mode) {
            AppOpsManager.MODE_ALLOWED -> true
            AppOpsManager.MODE_DEFAULT -> !usm?.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                System.currentTimeMillis() - 24 * 3600_000L,
                System.currentTimeMillis(),
            ).isNullOrEmpty()
            else -> false
        }
    }

    suspend fun clearCache() = queryMutex.withLock {
        cache.clear()
        labels.clear()
        systemApps.clear()
    }

    suspend fun loadDay(date: LocalDate, minSessionMs: Long, hideSystemApps: Boolean, refreshEvents: Boolean = true): DayUsage =
        withContext(Dispatchers.IO) {
            queryMutex.withLock {
                val zone = ZoneId.systemDefault()
                val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
                val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val now = System.currentTimeMillis()
                val queryEnd = minOf(dayEnd, now)
                if (queryEnd <= dayStart) return@withLock DayUsage.empty(dayStart, dayEnd)
                val snapshot = cache.load(dayStart, dayEnd, now, refreshEvents) { queryStart, end ->
                    val events = ArrayList<TimelineEvent>()
                    val iterator = usm?.queryEvents(queryStart, end)
                        ?: error("系统暂时无法提供使用记录，请解锁设备后重试")
                    val event = UsageEvents.Event()
                    val coroutine = currentCoroutineContext()
                    while (iterator.hasNextEvent()) {
                        coroutine.ensureActive()
                        iterator.getNextEvent(event)
                        if (event.eventType in EVENT_TYPES) {
                            events.add(TimelineEvent(event.timeStamp, event.eventType, event.packageName))
                        }
                    }
                    events
                }
                currentCoroutineContext().ensureActive()
                val sessions = snapshot.sessions.filter {
                    it.duration >= minSessionMs && (!hideSystemApps || !isSystemApp(it.packageName))
                }
                val apps = sessions.groupBy { it.packageName }.map { (pkg, records) ->
                    AppUsage(pkg, labelOf(pkg), records.sumOf { it.duration }, records)
                }.sortedByDescending { it.totalMs }
                DayUsage(
                    dayStart, dayEnd, sessions, apps, sessions.sumOf { it.duration },
                    hourlyUsage(sessions, zone),
                    snapshot.events.count { it.type == 15 && it.time >= dayStart },
                    snapshot.events.count { it.type == 18 && it.time >= dayStart },
                )
            }
        }

    fun labelOf(packageName: String): String = labels.getOrPut(packageName) {
        try {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun isSystemApp(packageName: String): Boolean = systemApps.getOrPut(packageName) {
        try {
            pm.getApplicationInfo(packageName, 0).flags and
                (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
            false
        }
    }

    private companion object {
        val EVENT_TYPES = setOf(1, 2, 15, 16, 17, 18, 26)
    }
}
