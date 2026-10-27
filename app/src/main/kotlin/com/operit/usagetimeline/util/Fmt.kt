package com.operit.usagetimeline.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Fmt {

    private val hhmm = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    private val hhmmss = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault())

    fun clock(ts: Long): String =
        hhmm.format(Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()))

    fun clockSeconds(ts: Long): String =
        hhmmss.format(Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()))

    fun localDate(ts: Long): LocalDate =
        Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()

    /** 1 小时 23 分 / 23 分 / 45 秒 */
    fun duration(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return when {
            h > 0 -> "${h}小时${m}分"
            m > 0 -> "${m}分${if (s > 0) "${s}秒" else ""}"
            else -> "${s}秒"
        }
    }

    /** 紧凑写法：1h23m / 23m / 45s */
    fun durationShort(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return when {
            h > 0 -> "${h}h${m}m"
            m > 0 -> "${m}m"
            s >= 1 -> "${s}s"
            else -> "<1s"
        }
    }

    /** 23:05 - 23:41 */
    fun range(start: Long, end: Long): String = "${clock(start)} - ${clock(end)}"

    fun dayLabel(date: LocalDate, today: LocalDate): String = when {
        date == today -> "今天"
        date == today.minusDays(1) -> "昨天"
        date == today.plusDays(1) -> "明天"
        date.year == today.year -> "${date.monthValue}月${date.dayOfMonth}日"
        else -> "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
    }

    fun weekday(date: LocalDate): String = when (date.dayOfWeek.value) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        else -> "周日"
    }

    fun hourLabel(hour: Int): String = String.format(Locale.US, "%02d:00", hour)
}