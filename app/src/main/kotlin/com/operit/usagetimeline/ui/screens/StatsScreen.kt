package com.operit.usagetimeline.ui.screens

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.operit.usagetimeline.data.AppPalette
import com.operit.usagetimeline.ui.AppIcon
import com.operit.usagetimeline.ui.DateBar
import com.operit.usagetimeline.ui.EmptyHint
import com.operit.usagetimeline.ui.GroupTitle
import com.operit.usagetimeline.ui.HourBarChart
import com.operit.usagetimeline.ui.PermissionCard
import com.operit.usagetimeline.ui.RowDivider
import com.operit.usagetimeline.ui.StatCell
import com.operit.usagetimeline.ui.SummaryCard
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
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun StatsScreen(
    vm: UsageViewModel,
    state: UiState,
    onOpenApp: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    var showDatePicker by remember { mutableStateOf(false) }
    val day = state.day

    // 滚动位置显式持久化：从详情页/设置页返回时不会跳回顶部
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    Column(modifier = Modifier.fillMaxSize()) {
        SmallTopAppBar(
            title = "统计",
            subtitle = "当天使用概览",
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

            val d = day
            if (d == null) {
                item {
                    EmptyHint(title = "正在读取…", summary = "请稍候")
                }
                return@LazyColumn
            }

            item {
                SummaryCard(
                    totalMs = d.totalMs,
                    sessionCount = d.sessionCount,
                    appCount = d.apps.size,
                    screenOnCount = d.screenOnCount,
                )
            }

            item {
                GroupTitle("每小时使用时长")
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                ) {
                    HourBarChart(hourly = d.hourly)
                }
            }

            item {
                GroupTitle("使用分布")
            }
            item {
                Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                    StatRow("使用最多的应用", d.apps.firstOrNull()?.label ?: "无")
                    RowDivider(modifier = Modifier.padding(horizontal = 16.dp), startPadding = 0)
                    StatRow("打开最多次数", d.apps.maxByOrNull { it.count }?.let { "${it.label} · ${it.count} 次" } ?: "无")
                    RowDivider(modifier = Modifier.padding(horizontal = 16.dp), startPadding = 0)
                    StatRow("解锁次数", "${d.unlockCount} 次")
                    RowDivider(modifier = Modifier.padding(horizontal = 16.dp), startPadding = 0)
                    StatRow("平均每次使用", if (d.sessionCount > 0) Fmt.duration(d.totalMs / d.sessionCount) else "无")
                    RowDivider(modifier = Modifier.padding(horizontal = 16.dp), startPadding = 0)
                    StatRow("最活跃时段", mostActiveHour(d.hourly))
                }
            }

            if (d.apps.isNotEmpty()) {
                item {
                    GroupTitle("应用排行")
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                        d.apps.take(8).forEachIndexed { index, app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenApp(app.packageName) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (index < 3) {
                                        MiuixTheme.colorScheme.primary
                                    } else {
                                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    },
                                    modifier = Modifier.width(20.dp),
                                )
                                AppIcon(packageName = app.packageName, size = 34)
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.label,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        color = MiuixTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "${app.count} 次",
                                        fontSize = 10.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                                Text(
                                    text = Fmt.durationShort(app.totalMs),
                                    fontSize = 13.sp,
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                            if (index != d.apps.take(8).lastIndex) {
                                RowDivider()
                            }
                        }
                    }
                }
            }

            item {
                GroupTitle("使用时长占比")
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                ) {
                    if (d.totalMs <= 0) {
                        Text(
                            text = "暂无数据",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    } else {
                        // 横向堆叠占比条
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp)),
                        ) {
                            d.apps.take(6).forEach { app ->
                                Box(
                                    modifier = Modifier
                                        .weight(app.totalMs.toFloat().coerceAtLeast(1f))
                                        .fillMaxSize()
                                        .background(AppPalette.colorFor(app.packageName)),
                                )
                            }
                            val rest = d.totalMs - d.apps.take(6).sumOf { it.totalMs }
                            if (rest > 0) {
                                Box(
                                    modifier = Modifier
                                        .weight(rest.toFloat())
                                        .fillMaxSize()
                                        .background(MiuixTheme.colorScheme.surfaceContainerHigh),
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        d.apps.take(6).forEach { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(AppPalette.colorFor(app.packageName)),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = app.label,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f),
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "${(app.totalMs * 100.0 / d.totalMs).toInt()}%",
                                    fontSize = 11.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StatCell(value = "${d.screenOnCount}", label = "亮屏")
                    StatCell(value = "${d.unlockCount}", label = "解锁")
                    StatCell(value = "${d.sessionCount}", label = "记录数")
                    StatCell(value = "${d.apps.size}", label = "应用数")
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
        )
    }
}

@Composable
private fun StatRow(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

private fun mostActiveHour(hourly: LongArray): String {
    var bestHour = -1
    var best = 0L
    hourly.forEachIndexed { hour, value ->
        if (value > best) {
            best = value
            bestHour = hour
        }
    }
    return if (bestHour < 0) "无" else "${Fmt.hourLabel(bestHour)} · ${Fmt.durationShort(best)}"
}