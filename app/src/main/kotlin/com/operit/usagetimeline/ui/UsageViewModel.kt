package com.operit.usagetimeline.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.operit.usagetimeline.data.DayUsage
import com.operit.usagetimeline.data.SettingsStore
import com.operit.usagetimeline.data.UsageRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.operit.usagetimeline.data.UsageSession
import kotlinx.coroutines.launch
import java.time.LocalDate

data class UiState(
    val date: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val loading: Boolean = true,
    val hasPermission: Boolean = false,
    val day: DayUsage? = null,
    val error: String? = null,
    /** 下拉刷新中（显示顶部指示器，不清空列表） */
    val refreshing: Boolean = false,
)

class UsageViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = UsageRepository(app)
    val settings = SettingsStore(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        load(_state.value.date, showLoading = true)
    }

    fun selectDate(date: LocalDate) {
        if (date == _state.value.date) return
        load(date, showLoading = true)
    }

    fun previousDay() = selectDate(_state.value.date.minusDays(1))

    fun nextDay() {
        val next = _state.value.date.plusDays(1)
        if (next.isAfter(LocalDate.now())) return
        selectDate(next)
    }

    fun goToday() = selectDate(LocalDate.now())

    private fun load(date: LocalDate, showLoading: Boolean) {
        loadJob?.cancel()
        val hasPermission = repo.hasUsageAccess()
        _state.value = _state.value.copy(
            date = date,
            today = LocalDate.now(),
            hasPermission = hasPermission,
            loading = showLoading,
            error = null,
        )
        if (!hasPermission) {
            _state.value = _state.value.copy(loading = false, day = null, refreshing = false)
            return
        }
        loadJob = viewModelScope.launch {
            val result = runCatching {
                repo.loadDay(
                    date = date,
                    minSessionMs = settings.minSessionSeconds * 1000L,
                    hideSystemApps = settings.hideSystemApps,
                )
            }
            result.onSuccess { day ->
                _state.value = _state.value.copy(loading = false, day = day, error = null, refreshing = false)
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "读取使用记录失败",
                    refreshing = false,
                )
            }
        }
    }

    /** 下拉刷新：保留当前列表内容，仅显示顶部刷新指示器。 */
    fun pullRefresh() {
        if (!_state.value.hasPermission) {
            _state.value = _state.value.copy(refreshing = false, loading = false)
            return
        }
        _state.value = _state.value.copy(refreshing = true)
        load(_state.value.date, showLoading = false)
    }

    /** 重新按新的过滤设置加载（不显示 loading 骨架）。 */
    fun reloadWithSettings() {
        if (_state.value.hasPermission) load(_state.value.date, showLoading = false)
    }

    /** 回到前台时调用：重新检查权限、跨天检测并重载数据。 */
    fun onResumeTick() {
        val today = LocalDate.now()
        val hasPermission = repo.hasUsageAccess()
        // 权限刚被授予：立即全量加载，无需重启应用
        if (hasPermission && !_state.value.hasPermission) {
            load(_state.value.date, showLoading = true)
            return
        }
        // 权限被撤销
        if (!hasPermission && _state.value.hasPermission) {
            _state.value = _state.value.copy(hasPermission = false, day = null, loading = false)
            return
        }
        if (today != _state.value.today) {
            val wasToday = _state.value.date == _state.value.today
            _state.value = _state.value.copy(today = today)
            if (wasToday) load(today, showLoading = false) else load(_state.value.date, showLoading = false)
            return
        }
        if (hasPermission) load(_state.value.date, showLoading = false)
    }

    fun hasUsageAccessNow(): Boolean = repo.hasUsageAccess()

    /** 加载某应用最近 days 天的全部会话（详情页“完整记录”用）。 */
    fun loadAppHistory(pkg: String, days: Int, onResult: (List<UsageSession>) -> Unit) {
        viewModelScope.launch {
            val end = LocalDate.now().plusDays(1)
            val result = runCatching {
                val all = ArrayList<UsageSession>()
                for (i in 0 until days) {
                    val d = end.minusDays(i.toLong() + 1)
                    val usage = repo.loadDay(
                        date = d,
                        minSessionMs = settings.minSessionSeconds * 1000L,
                        hideSystemApps = false,
                    )
                    all.addAll(usage.sessions.filter { it.packageName == pkg })
                }
                all.sortedByDescending { it.start }
            }
            onResult(result.getOrDefault(emptyList()))
        }
    }

    fun packageLabel(pkg: String): String = repo.labelOf(pkg)

    /** 每 60 秒刷新一次当天数据（当天页面保持“实时”）。 */
    fun startAutoRefresh() {
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                val s = _state.value
                if (s.hasPermission && s.date == LocalDate.now()) {
                    load(s.date, showLoading = false)
                }
            }
        }
    }
}