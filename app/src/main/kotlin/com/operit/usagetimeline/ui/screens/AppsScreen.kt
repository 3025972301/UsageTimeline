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
import androidx.compose.foundation.lazy.items
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
import com.operit.usagetimeline.data.AppUsage
import com.operit.usagetimeline.ui.AppIcon
import com.operit.usagetimeline.ui.DateBar
import com.operit.usagetimeline.ui.EmptyHint
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
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AppsScreen(
    vm: UsageViewModel,
    state: UiState,
    snackbar: (String) -> Unit,
    onOpenApp: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    var sortIndex by remember { mutableStateOf(0) }
    var showDatePicker by remember { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val day = state.day

    val sortedApps: List<AppUsage> = remember(day, sortIndex, query) {
        val apps = day?.apps ?: emptyList()
        val filtered = if (query.isBlank()) {
            apps
        } else {
            val q = query.trim().lowercase()
            apps.filter {
                it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
        }
        when (sortIndex) {
            1 -> filtered.sortedByDescending { it.count }
            else -> filtered.sortedByDescending { it.totalMs }
        }
    }

    // 滚动位置显式持久化：从详情页/设置页返回时不会跳回顶部
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    Column(modifier = Modifier.fillMaxSize()) {
        SmallTopAppBar(
            title = "应用",
            subtitle = when {
                day == null -> ""
                query.isNotBlank() -> "匹配 ${sortedApps.size} 个应用"
                else -> "共 ${day.apps.size} 个应用"
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
                        tint = if (searchOpen) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
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

            item {
                val total = day?.totalMs ?: 0L
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "总计 ${Fmt.duration(total)}",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }

            item {
                TabRow(
                    tabs = listOf("按时长", "按次数"),
                    selectedTabIndex = sortIndex,
                    onTabSelected = { sortIndex = it },
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            if (sortedApps.isEmpty()) {
                item {
                    EmptyHint(
                        title = if (query.isNotBlank()) "没有匹配的应用" else "没有应用使用记录",
                        summary = if (query.isNotBlank()) "换个关键词试试" else "当天暂无数据，或已在设置中过滤",
                    )
                }
            }

            item {
                if (sortedApps.isNotEmpty()) {
                    val maxTotal = sortedApps.first().totalMs.coerceAtLeast(1L)
                    Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                        sortedApps.forEachIndexed { index, app ->
                            AppUsageRow(
                                app = app,
                                maxTotal = maxTotal,
                                totalMs = day?.totalMs ?: 0L,
                                onClick = { onOpenApp(app.packageName) },
                            )
                            if (index != sortedApps.lastIndex) {
                                RowDivider(modifier = Modifier.padding(start = 16.dp))
                            }
                        }
                    }
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
fun AppUsageRow(
    app: AppUsage,
    maxTotal: Long,
    totalMs: Long,
    onClick: () -> Unit,
) {
    val color = AppPalette.colorFor(app.packageName)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(packageName = app.packageName, size = 38)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    fontSize = 15.sp,
                    maxLines = 1,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${app.count} 次 · 首次 ${Fmt.clock(app.firstOpen)}",
                    fontSize = 11.sp,
                    maxLines = 1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = Fmt.durationShort(app.totalMs),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                val pct = if (totalMs > 0) {
                    (app.totalMs * 100.0 / totalMs).toInt()
                } else {
                    0
                }
                Text(
                    text = "$pct%",
                    fontSize = 10.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MiuixTheme.colorScheme.surfaceContainerHighest),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(
                        (app.totalMs.toFloat() / maxTotal.toFloat()).coerceIn(0.02f, 1f),
                    )
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}
