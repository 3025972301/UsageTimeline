package com.operit.usagetimeline.data

/** 一次前台使用记录（从打开到离开）。 */
data class UsageSession(
    val packageName: String,
    val start: Long,
    val end: Long,
) {
    val duration: Long get() = (end - start).coerceAtLeast(0L)
}

/** 单个应用在一天内的汇总。 */
data class AppUsage(
    val packageName: String,
    val label: String,
    val totalMs: Long,
    val sessions: List<UsageSession>,
) {
    val count: Int get() = sessions.size
    val firstOpen: Long get() = sessions.firstOrNull()?.start ?: 0L
    val lastClose: Long get() = sessions.lastOrNull()?.end ?: 0L
}

/** 一整天（或所选日期）的统计结果。 */
data class DayUsage(
    val dayStart: Long,
    val dayEnd: Long,
    val sessions: List<UsageSession>,
    val apps: List<AppUsage>,
    val totalMs: Long,
    val hourly: LongArray,
    /** 点亮屏幕次数 */
    val screenOnCount: Int,
    /** 解锁次数 */
    val unlockCount: Int,
) {
    val sessionCount: Int get() = sessions.size

    fun appOf(packageName: String): AppUsage? = apps.firstOrNull { it.packageName == packageName }

    companion object {
        fun empty(dayStart: Long, dayEnd: Long) = DayUsage(
            dayStart = dayStart,
            dayEnd = dayEnd,
            sessions = emptyList(),
            apps = emptyList(),
            totalMs = 0L,
            hourly = LongArray(24),
            screenOnCount = 0,
            unlockCount = 0,
        )
    }
}
