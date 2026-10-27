package com.operit.usagetimeline.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.operit.usagetimeline.data.AppPalette
import com.operit.usagetimeline.data.DayUsage
import com.operit.usagetimeline.data.UsageSession
import com.operit.usagetimeline.ui.AppIcon
import com.operit.usagetimeline.ui.DateBar
import com.operit.usagetimeline.ui.EmptyHint
import com.operit.usagetimeline.ui.HourBarChart
import com.operit.usagetimeline.ui.PermissionCard
import com.operit.usagetimeline.ui.RowDivider
import com.operit.usagetimeline.ui.SearchField
import com.operit.usagetimeline.ui.UiState
import com.operit.usagetimeline.ui.UsageViewModel
import com.operit.usagetimeline.ui.openUsageAccessSettings
import com.operit.usagetimeline.util.Fmt
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun TimelineScreen(
    vm: UsageViewModel,
    state: UiState,
    snackbar: (String) -> Unit,
    onOpenApp: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    var showDatePicker by remember { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    // false = 时间正序（从早到晚），true = 倒序（从晚到早，默认更符合「最近在用」的直觉）
    var descending by rememberSaveable { mutableStateOf(true) }
    val day = state.day

    // 搜索过滤 + 排序后的会话列表
    val visibleSessions = remember(day, query, descending) {
        val sessions = day?.sessions ?: emptyList()
        val labels = day?.apps?.associate { it.packageName to it.label } ?: emptyMap()
        val filtered = if (query.isBlank()) {
            sessions
        } else {
            val q = query.trim().lowercase()
            sessions.filter { s ->
                val label = labels[s.packageName] ?: s.packageName
                label.lowercase().contains(q) || s.packageName.lowercase().contains(q)
            }
        }
        filtered.sortedBy { it.start }.let { if (descending) it.asReversed() else it }
    }

    // 按小时分组（在 Composable 上下文中预先算好，LazyListScope 里不能调用 remember）
    val groupedByHour = remember(visibleSessions, day, descending) {
        val dayStart = day?.dayStart ?: 0L
        val map = visibleSessions.groupBy {
            ((it.start - dayStart) / 3600_000L).toInt().coerceIn(0, 23)
        }
        val keys = map.keys.toList().let { if (descending) it.sortedDescending() else it.sorted() }
        keys.map { it to (map[it] ?: emptyList()) }
    }

    // 滚动位置显式持久化：从详情页/设置页返回时不会跳回顶部
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    Column(modifier = Modifier.fillMaxSize()) {
        SmallTopAppBar(
            title = "时间线",
            subtitle = when {
                day == null -> ""
                query.isNotBlank() -> "匹配 ${visibleSessions.size} 条"
                else -> "${day.apps.size} 个应用 · ${day.sessionCount} 次记录"
            },
            navigationIcon = {
                IconButton(onClick = { showDatePicker = true }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Months,
                        contentDescription = "选择日期",
                        modifier = Modifier.size(22.dp),
                        tint = MiuixTheme.colorScheme.onSurface,
                    )
                }
            },
            actions = {
                IconButton(onClick = { searchOpen = !searchOpen }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Search,
                        contentDescription = "搜索应用",
                        modifier = Modifier.size(22.dp),
                        tint = if (searchOpen) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.onSurface
                        },
                    )
                }
                IconButton(onClick = { descending = !descending }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Sort,
                        contentDescription = if (descending) "当前倒序，点击切换正序" else "当前正序，点击切换倒序",
                        modifier = Modifier.size(22.dp),
                        tint = MiuixTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Settings,
                        contentDescription = "设置",
                        modifier = Modifier.size(22.dp),
                        tint = MiuixTheme.colorScheme.onSurface,
                    )
                }
            },
        )

        if (!state.hasPermission) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    PermissionCard(
                        onRequest = { openUsageAccessSettings(context) },
                        onOpenSettings = { openUsageAccessSettings(context) },
                    )
                }
                item {
                    EmptyHint(
                        title = "授权后即可查看时间线",
                        summary = "读取系统「使用情况访问」数据，授权后返回即可生效",
                    )
                }
            }
            return@Column
        }

        PullToRefresh(
            isRefreshing = state.refreshing,
            onRefresh = { vm.pullRefresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DateBar(
                    date = state.date,
                    today = state.today,
                    onPrevious = { vm.previousDay() },
                    onNext = { vm.nextDay() },
                    onPickDate = { showDatePicker = true },
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            if (searchOpen) {
                item {
                    SearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "搜索应用名称",
                        autoFocus = true,
                    )
                }
            }

            if (state.loading && day == null) {
                item { LoadingCard() }
                return@LazyColumn
            }

            val d = day ?: return@LazyColumn

            if (query.isBlank()) {
                item { DayStrip(day = d) }
            }

            if (visibleSessions.isEmpty()) {
                item {
                    EmptyHint(
                        title = if (query.isNotBlank()) "没有匹配的应用记录" else "这一天没有使用记录",
                        summary = if (query.isNotBlank()) "换个关键词试试" else "试试切换到其他日期",
                    )
                }
            }

            for ((hour, sessions) in groupedByHour) {
                item(key = "h_$hour") {
                    HourHeader(
                        hour = hour,
                        totalMs = sessions.sumOf { it.duration },
                    )
                }
                item(key = "c_$hour") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp),
                    ) {
                        val maxDuration = sessions.maxOfOrNull { it.duration } ?: 1L
                        sessions.forEachIndexed { index, session ->
                            SessionRow(
                                session = session,
                                label = d.appOf(session.packageName)?.label ?: session.packageName,
                                maxDuration = maxDuration,
                                onClick = { onOpenApp(session.packageName) },
                            )
                            if (index != sessions.lastIndex) {
                                RowDivider(modifier = Modifier.padding(start = 16.dp))
                            }
                        }
                    }
                }
            }

            if (query.isBlank()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    HourBarChartCard(hourly = d.hourly)
                }
            }
        }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            initial = state.date,
            maxDate = state.today,
            onDismiss = { showDatePicker = false },
            onConfirm = { date ->
                showDatePicker = false
                vm.selectDate(date)
            },
            snackbar = snackbar,
        )
    }
}

@Composable
private fun LoadingCard() {
    Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(30.dp)) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = "正在读取使用记录…",
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

/** 24 小时色带：横向展示一天里每个应用的活跃区间。 */
@Composable
private fun DayStrip(day: DayUsage) {
    val background = MiuixTheme.colorScheme.surfaceContainerHighest
    val labelColor = MiuixTheme.colorScheme.onSurfaceVariantSummary

    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = "全天分布",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(10.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp),
        ) {
            drawRoundRect(
                color = background,
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
            )
            val span = (day.dayEnd - day.dayStart).toFloat().coerceAtLeast(1f)
            for (s in day.sessions) {
                val left = ((s.start - day.dayStart) / span) * size.width
                val right = ((s.end - day.dayStart) / span) * size.width
                val w = (right - left).coerceAtLeast(1.6f)
                drawRoundRect(
                    color = AppPalette.colorFor(s.packageName),
                    topLeft = Offset(left, 0f),
                    size = Size(w, size.height),
                    cornerRadius = CornerRadius(2.5f.dp.toPx(), 2.5f.dp.toPx()),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("00", "06", "12", "18", "24").forEach { h ->
                Text(text = "${h}:00", fontSize = 9.sp, color = labelColor)
            }
        }
    }
}

@Composable
private fun HourHeader(hour: Int, totalMs: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = Fmt.hourLabel(hour),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
        Text(
            text = Fmt.durationShort(totalMs),
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
    }
}

@Composable
private fun SessionRow(
    session: UsageSession,
    label: String,
    maxDuration: Long,
    onClick: () -> Unit,
) {
    val color = AppPalette.colorFor(session.packageName)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = Fmt.clock(session.start),
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.width(42.dp),
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(26.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        Spacer(Modifier.width(10.dp))
        AppIcon(packageName = session.packageName, size = 32)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 14.sp,
                maxLines = 1,
                color = MiuixTheme.colorScheme.onSurface,
            )
            val ratio = (session.duration.toFloat() / maxDuration.coerceAtLeast(1L).toFloat())
                .coerceIn(0.04f, 1f)
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color.copy(alpha = 0.55f)),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = Fmt.duration(session.duration),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = "至 ${Fmt.clock(session.end)}",
                fontSize = 10.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun HourBarChartCard(hourly: LongArray) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = "每小时使用时长",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        HourBarChart(hourly = hourly)
    }
}