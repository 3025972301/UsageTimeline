package com.operit.usagetimeline.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.CancellationException
import com.operit.usagetimeline.data.hourlyUsage
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.operit.usagetimeline.data.AppPalette
import com.operit.usagetimeline.data.UsageSession
import com.operit.usagetimeline.ui.AppIcon
import com.operit.usagetimeline.ui.EmptyHint
import com.operit.usagetimeline.ui.GroupTitle
import com.operit.usagetimeline.ui.HourBarChart
import com.operit.usagetimeline.ui.StatCell
import com.operit.usagetimeline.ui.UiState
import com.operit.usagetimeline.ui.UsageViewModel
import com.operit.usagetimeline.ui.isAppInstalled
import com.operit.usagetimeline.ui.launchApp
import com.operit.usagetimeline.ui.openAppInfo
import com.operit.usagetimeline.util.Fmt
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 单个应用的完整使用记录页。 */
@Composable
fun AppDetailScreen(
    vm: UsageViewModel,
    state: UiState,
    packageName: String,
    snackbar: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val day = state.day
    val app = day?.appOf(packageName)
    val label = app?.label ?: vm.packageLabel(packageName)
    val color = AppPalette.colorFor(packageName)
    val installed = remember(packageName) { isAppInstalled(context, packageName) }

    // 0 = 当天；1 = 近 7 天
    var tabIndex by rememberSaveable(packageName) { mutableStateOf(0) }
    var history by remember(packageName) { mutableStateOf<List<UsageSession>>(emptyList()) }
    var historyLoaded by remember(packageName) { mutableStateOf(false) }
    var historyError by remember(packageName) { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf(0) }

    LaunchedEffect(packageName, tabIndex, state.date, state.day, state.hasPermission, vm.settings.minSessionSeconds, retry) {
        if (tabIndex == 1) {
            historyLoaded = false
            historyError = null
            history = emptyList()
            try {
                history = vm.loadAppHistory(packageName, days = 7, endDate = state.date)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                historyError = e.message ?: "读取记录失败"
            } finally {
                historyLoaded = true
            }
        }
    }

    val sessions = if (tabIndex == 0) {
        app?.sessions?.sortedBy { it.start } ?: emptyList()
    } else {
        history
    }
    val totalMs = sessions.sumOf { it.duration }

    // 当天各小时分布
    val hourly = remember(app) { hourlyUsage(app?.sessions.orEmpty()) }
    val reversed = remember(sessions) { sessions.sortedByDescending { it.start } }
    val maxDuration = remember(sessions) { sessions.maxOfOrNull { it.duration } ?: 1L }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MiuixTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SmallTopAppBar(
                title = label,
                subtitle = packageName,
                color = MiuixTheme.colorScheme.surface,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Back,
                            contentDescription = "返回",
                            modifier = Modifier.size(22.dp),
                            tint = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                },
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(18.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(packageName = packageName, size = 54)
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = label,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = if (tabIndex == 0) {
                                        Fmt.dayLabel(state.date, state.today) + " 使用记录"
                                    } else {
                                        "截至 ${state.date} 的 7 天记录"
                                    },
                                    fontSize = 11.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            StatCell(
                                value = Fmt.durationShort(totalMs),
                                label = "总时长",
                                valueColor = color,
                            )
                            StatCell(value = "${sessions.size}", label = "打开次数")
                            StatCell(
                                value = if (sessions.isNotEmpty()) Fmt.durationShort(totalMs / sessions.size) else "—",
                                label = "平均每次",
                            )
                            StatCell(
                                value = if (sessions.isNotEmpty()) Fmt.clock(sessions.first().start) else "—",
                                label = if (tabIndex == 0) "首次打开" else "最近一次",
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                enabled = installed,
                                onClick = {
                                    if (!launchApp(context, packageName)) {
                                        snackbar("无法启动该应用")
                                    }
                                },
                                minWidth = 0.dp,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(if (installed) "打开应用" else "应用未安装", fontSize = 14.sp)
                            }
                            TextButton(
                                text = "应用信息",
                                onClick = { openAppInfo(context, packageName) },
                                minWidth = 0.dp,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                item {
                    TabRow(
                        tabs = listOf("当天", "近 7 天"),
                        selectedTabIndex = tabIndex,
                        onTabSelected = { tabIndex = it },
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }

                if (tabIndex == 0) {
                    item { GroupTitle("当日时段分布") }
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                        ) {
                            HourBarChart(hourly = hourly, barColor = color)
                        }
                    }
                }

                item {
                    GroupTitle("完整使用记录（${sessions.size} 条）")
                }

                if (tabIndex == 1 && !historyLoaded) {
                    item {
                        EmptyHint(title = "正在加载…", summary = "读取近 7 天记录")
                    }
                } else if (tabIndex == 1 && historyError != null) {
                    item {
                        EmptyHint(title = "读取失败", summary = historyError!!)
                        TextButton(text = "重试", onClick = { retry++ })
                    }
                } else if (sessions.isEmpty()) {
                    item {
                        EmptyHint(
                            title = "没有该应用的使用记录",
                            summary = "切换时间范围可查看其他记录",
                        )
                    }
                } else {
                    itemsIndexed(reversed, key = { _, s -> "${s.packageName}:${s.start}" }) { index, session ->
                        Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                            DetailSessionRow(
                                session = session,
                                maxDuration = maxDuration,
                                color = color,
                                index = index,
                                showDate = tabIndex == 1,
                            )
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "说明：记录来自系统使用情况事件（Activity 前后台切换），息屏或锁屏时结束计时。",
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailSessionRow(
    session: UsageSession,
    maxDuration: Long,
    color: Color,
    index: Int,
    showDate: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${index + 1}",
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.width(18.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showDate) {
                    Text(
                        text = "${Fmt.localDate(session.start).monthValue}/${Fmt.localDate(session.start).dayOfMonth} ",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Text(
                    text = Fmt.clockSeconds(session.start),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = "  →  ${Fmt.clockSeconds(session.end)}",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(Modifier.height(5.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(
                        (session.duration.toFloat() / maxDuration.coerceAtLeast(1L).toFloat())
                            .coerceIn(0.03f, 1f),
                    )
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color.copy(alpha = 0.6f)),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = Fmt.duration(session.duration),
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}
