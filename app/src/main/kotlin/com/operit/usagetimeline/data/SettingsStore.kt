package com.operit.usagetimeline.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** 简单的偏好存储（SharedPreferences 包装 + Compose 可观察状态）。 */
class SettingsStore(context: Context) {

    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** 最短记录阈值（秒），低于该值的切换不算一次使用。 */
    var minSessionSeconds by mutableStateOf(sp.getInt(KEY_MIN_SESSION, 5).coerceIn(0, 60))
        private set

    /** 时间线隐藏系统应用 */
    var hideSystemApps by mutableStateOf(sp.getBoolean(KEY_HIDE_SYSTEM, true))
        private set

    /** 使用随机主题色（Monet 壁纸取色） */
    var dynamicColor by mutableStateOf(sp.getBoolean(KEY_DYNAMIC, true))
        private set

    /** 深色模式：0=跟随系统 1=浅色 2=深色 */
    var themeMode by mutableStateOf(sp.getInt(KEY_THEME_MODE, 0).coerceIn(0, 2))
        private set

    /** 时间线是否按「时间段」合并相邻记录展示 */
    var groupTimeline by mutableStateOf(sp.getBoolean(KEY_GROUP, false))
        private set

    fun updateMinSessionSeconds(value: Int) {
        val v = value.coerceIn(0, 60)
        minSessionSeconds = v
        sp.edit().putInt(KEY_MIN_SESSION, v).apply()
    }

    fun updateHideSystemApps(value: Boolean) {
        hideSystemApps = value
        sp.edit().putBoolean(KEY_HIDE_SYSTEM, value).apply()
    }

    fun updateDynamicColor(value: Boolean) {
        dynamicColor = value
        sp.edit().putBoolean(KEY_DYNAMIC, value).apply()
    }

    fun updateThemeMode(value: Int) {
        themeMode = value.coerceIn(0, 2)
        sp.edit().putInt(KEY_THEME_MODE, themeMode).apply()
    }

    fun updateGroupTimeline(value: Boolean) {
        groupTimeline = value
        sp.edit().putBoolean(KEY_GROUP, value).apply()
    }

    private companion object {
        const val KEY_MIN_SESSION = "min_session_seconds"
        const val KEY_HIDE_SYSTEM = "hide_system_apps"
        const val KEY_DYNAMIC = "dynamic_color"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_GROUP = "group_timeline"
    }
}

/** 每个应用固定分配一个颜色，让时间线条块的色带稳定可辨。 */
object AppPalette {
    private val colors = listOf(
        Color(0xFF3482FF),
        Color(0xFF29B36B),
        Color(0xFFFF8A28),
        Color(0xFFB25BFF),
        Color(0xFFFF5B7A),
        Color(0xFF18B8C9),
        Color(0xFFEEB400),
        Color(0xFF7A82FF),
        Color(0xFF3FBF9A),
        Color(0xFFE45FBD),
    )

    fun colorFor(key: String): Color {
        var h = 0
        for (c in key) h = h * 31 + c.code
        return colors[Math.floorMod(h, colors.size)]
    }
}
