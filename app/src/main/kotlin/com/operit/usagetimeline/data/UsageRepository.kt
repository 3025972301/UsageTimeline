package com.operit.usagetimeline.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/**
 * 通过 UsageStatsManager 的事件流还原“时间线”使用记录。
 *
 * 事件配对策略（针对 Activity 切换的真实行为）：
 * - ACTIVITY_RESUMED(1)：同一个包内多 Activity 叠加时用引用计数，不切断会话；不同包则收尾上一个会话。
 * - ACTIVITY_PAUSED(2)：计数减到 0 才认为应用离开前台。
 * - 息屏 / 锁屏 / 关机：强制收尾当前会话，避免把息屏时间算成使用时间。
 */
class UsageRepository(private val context: Context) {

    private val usm: UsageStatsManager? =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    private val pm: PackageManager = context.packageManager

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName,
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName,
                )
            }
        } catch (t: Throwable) {
            return false
        }
        if (mode == AppOpsManager.MODE_ALLOWED) return true
        if (mode == AppOpsManager.MODE_ERRORED || mode == AppOpsManager.MODE_IGNORED) return false
        // MODE_DEFAULT：少数 ROM 不返回 ALLOWED，用实际查询兜底判断
        return probeUsageData()
    }

    /** 兜底：尝试查询最近 24 小时的使用数据，有返回即视为已授权。 */
    private fun probeUsageData(): Boolean = try {
        val now = System.currentTimeMillis()
        val stats = usm?.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - 24L * 3600_000L,
            now,
        )
        !stats.isNullOrEmpty()
    } catch (t: Throwable) {
        false
    }

    suspend fun loadDay(
        date: LocalDate,
        minSessionMs: Long,
        hideSystemApps: Boolean,
    ): DayUsage = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        val queryEnd = minOf(dayEnd, now + 1000L)
        if (queryEnd <= dayStart) return@withContext DayUsage.empty(dayStart, dayEnd)

        // 往前多看 6 小时，用于还原「跨零点仍在运行」的会话
        val queryStart = maxOf(dayStart - 6 * 3600_000L, now - 8L * 24 * 3600_000L)

        val events = ArrayList<Ev>()
        var screenOn = 0
        var unlock = 0
        try {
            val iterator = usm?.queryEvents(queryStart, queryEnd) ?: return@withContext DayUsage.empty(dayStart, dayEnd)
            val e = UsageEvents.Event()
            while (iterator.hasNextEvent()) {
                iterator.getNextEvent(e)
                val type = e.eventType
                if (type == TYPE_SCREEN_INTERACTIVE) {
                    if (e.timeStamp in dayStart until dayEnd) screenOn++
                    events.add(Ev(e.timeStamp, type, null))
                    continue
                }
                if (type == TYPE_KEYGUARD_HIDDEN) {
                    if (e.timeStamp in dayStart until dayEnd) unlock++
                    events.add(Ev(e.timeStamp, type, null))
                    continue
                }
                val pkg = e.packageName
                events.add(Ev(e.timeStamp, type, pkg))
            }
        } catch (t: Throwable) {
            return@withContext DayUsage.empty(dayStart, dayEnd)
        }

        events.sortBy { it.time }

        val raw = ArrayList<UsageSession>()
        var cur: MutableSession? = null

        fun emit(pkg: String, start: Long, end: Long) {
            if (end > start) raw.add(UsageSession(pkg, start, end))
        }

        fun closeCurrent(at: Long) {
            val s = cur ?: return
            val end = if (s.end >= 0) s.end else at
            emit(s.packageName, s.start, end)
            cur = null
        }

        for (ev in events) {
            if (ev.time >= queryEnd) break
            when (ev.type) {
                TYPE_ACTIVITY_RESUMED -> {
                    val pkg = ev.pkg ?: continue
                    val s = cur
                    if (s != null && s.packageName == pkg) {
                        s.count++
                        s.end = -1
                    } else {
                        closeCurrent(ev.time)
                        cur = MutableSession(pkg, ev.time)
                    }
                }

                TYPE_ACTIVITY_PAUSED -> {
                    val pkg = ev.pkg ?: continue
                    val s = cur
                    if (s != null && s.packageName == pkg) {
                        s.count--
                        if (s.count <= 0) s.end = ev.time
                    }
                }

                TYPE_SCREEN_NON_INTERACTIVE, TYPE_KEYGUARD_SHOWN, TYPE_DEVICE_SHUTDOWN -> {
                    closeCurrent(ev.time)
                }
            }
        }
        closeCurrent(minOf(now, dayEnd))

        // 裁剪到当天范围 + 过滤过短的会话 + 合并相邻同应用碎片
        val sessions = ArrayList<UsageSession>(raw.size)
        for (s in raw) {
            val start = maxOf(s.start, dayStart)
            val end = minOf(s.end, dayEnd)
            if (end <= start) continue
            if (end - start < minSessionMs) continue
            sessions.add(UsageSession(s.packageName, start, end))
        }

        val merged = ArrayList<UsageSession>(sessions.size)
        for (s in sessions) {
            val last = merged.lastOrNull()
            if (last != null && last.packageName == s.packageName && s.start - last.end <= 2000L) {
                merged[merged.size - 1] = last.copy(end = maxOf(last.end, s.end))
            } else {
                merged.add(s)
            }
        }

        var filtered = merged
        if (hideSystemApps) {
            val systemCache = HashMap<String, Boolean>()
            filtered = ArrayList<UsageSession>(merged.size)
            for (s in merged) {
                val isSystem = systemCache.getOrPut(s.packageName) { checkSystemApp(s.packageName) }
                if (!isSystem) filtered.add(s)
            }
        }

        val byPkg = LinkedHashMap<String, MutableList<UsageSession>>()
        for (s in filtered) byPkg.getOrPut(s.packageName) { ArrayList() }.add(s)

        val apps = byPkg.map { (pkg, list) ->
            AppUsage(
                packageName = pkg,
                label = labelOf(pkg),
                totalMs = list.sumOf { it.duration },
                sessions = list,
            )
        }.sortedByDescending { it.totalMs }

        val hourly = LongArray(24)
        for (s in filtered) {
            var t = s.start
            while (t < s.end) {
                val hour = ((t - dayStart) / 3600_000L).toInt().coerceIn(0, 23)
                val hourEnd = dayStart + (hour + 1) * 3600_000L
                val chunkEnd = minOf(s.end, hourEnd)
                hourly[hour] += chunkEnd - t
                t = chunkEnd
            }
        }

        DayUsage(
            dayStart = dayStart,
            dayEnd = dayEnd,
            sessions = filtered,
            apps = apps,
            totalMs = filtered.sumOf { it.duration },
            hourly = hourly,
            screenOnCount = screenOn,
            unlockCount = unlock,
        )
    }

    fun labelOf(packageName: String): String = try {
        val info = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(info).toString()
    } catch (t: Throwable) {
        packageName
    }

    private fun checkSystemApp(packageName: String): Boolean = try {
        val info = pm.getApplicationInfo(packageName, 0)
        (info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
    } catch (t: Throwable) {
        false
    }

    private data class Ev(val time: Long, val type: Int, val pkg: String?)

    private class MutableSession(val packageName: String, val start: Long) {
        var end: Long = -1
        var count: Int = 1
    }

    private companion object {
        const val TYPE_ACTIVITY_RESUMED = 1 // UsageEvents.Event.ACTIVITY_RESUMED / MOVE_TO_FOREGROUND
        const val TYPE_ACTIVITY_PAUSED = 2 // UsageEvents.Event.ACTIVITY_PAUSED / MOVE_TO_BACKGROUND
        const val TYPE_SCREEN_INTERACTIVE = 15
        const val TYPE_SCREEN_NON_INTERACTIVE = 16
        const val TYPE_KEYGUARD_SHOWN = 17
        const val TYPE_KEYGUARD_HIDDEN = 18
        const val TYPE_DEVICE_SHUTDOWN = 26
    }
}
