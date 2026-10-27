package com.operit.usagetimeline.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.operit.usagetimeline.data.DayUsage
import com.operit.usagetimeline.data.SettingsStore
import com.operit.usagetimeline.data.UsageRepository
import com.operit.usagetimeline.data.UsageSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class UiState(
    val date: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val loading: Boolean = true,
    val hasPermission: Boolean = false,
    val day: DayUsage? = null,
    val error: String? = null,
    val refreshing: Boolean = false,
)

class UsageViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = UsageRepository(app)
    val settings = SettingsStore(app)
    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()
    private var loadJob: Job? = null
    private var generation = 0L

    init { refresh() }

    fun refresh() = load(_state.value.date)

    fun selectDate(date: LocalDate) {
        if (date != _state.value.date && !date.isAfter(LocalDate.now())) load(date)
    }

    fun previousDay() = selectDate(_state.value.date.minusDays(1))
    fun nextDay() = selectDate(_state.value.date.plusDays(1))
    fun goToday() = selectDate(LocalDate.now())

    private fun load(date: LocalDate, refreshEvents: Boolean = true) {
        loadJob?.cancel()
        val request = ++generation
        val keepDay = _state.value.day.takeIf { date == _state.value.date }
        _state.value = _state.value.copy(
            date = date, today = LocalDate.now(), day = keepDay,
            loading = keepDay == null, refreshing = keepDay != null, error = null,
        )
        loadJob = viewModelScope.launch {
            try {
                val permitted = repo.hasUsageAccess()
                currentCoroutineContext().ensureActive()
                if (!permitted) {
                    repo.clearCache()
                    _state.value = _state.value.copy(hasPermission = false, day = null, loading = false, refreshing = false)
                    return@launch
                }
                _state.value = _state.value.copy(hasPermission = true)
                val day = repo.loadDay(date, settings.minSessionSeconds * 1000L, settings.hideSystemApps, refreshEvents)
                currentCoroutineContext().ensureActive()
                if (request == generation) {
                    _state.value = _state.value.copy(day = day, loading = false, refreshing = false)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (request == generation) {
                    _state.value = _state.value.copy(
                        loading = false, refreshing = false,
                        error = e.message ?: "读取使用记录失败，请重试",
                    )
                }
            }
        }
    }

    fun pullRefresh() = refresh()
    fun reloadWithSettings() = load(_state.value.date, refreshEvents = false)

    fun onResumeTick() {
        val s = _state.value
        val date = if (s.date == s.today) LocalDate.now() else s.date
        load(date)
    }

    suspend fun loadAppHistory(pkg: String, days: Int, endDate: LocalDate): List<UsageSession> {
        check(repo.hasUsageAccess()) { "使用情况访问权限已关闭" }
        val all = ArrayList<UsageSession>()
        for (i in 0 until days) {
            currentCoroutineContext().ensureActive()
            all.addAll(repo.loadDay(endDate.minusDays(i.toLong()), settings.minSessionSeconds * 1000L, false)
                .sessions.filter { it.packageName == pkg })
        }
        return all.sortedByDescending { it.start }
    }

    fun packageLabel(pkg: String): String = repo.labelOf(pkg)

    /** Runs inside repeatOnLifecycle; no timer survives the foreground UI. */
    suspend fun autoRefresh() {
        while (true) {
            delay(60_000)
            val s = _state.value
            if (s.hasPermission && s.date == s.today && loadJob?.isActive != true) load(LocalDate.now())
        }
    }
}
