package com.operit.usagetimeline.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.operit.usagetimeline.ui.BasicSettingRow
import com.operit.usagetimeline.ui.GroupTitle
import com.operit.usagetimeline.ui.UiState
import com.operit.usagetimeline.ui.UsageViewModel
import com.operit.usagetimeline.ui.openAppInfo
import com.operit.usagetimeline.ui.openUsageAccessSettings
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(
    vm: UsageViewModel,
    state: UiState,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val settings = vm.settings

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MiuixTheme.colorScheme.surface,
    ) {
    Column(modifier = Modifier.fillMaxSize()) {
        SmallTopAppBar(
            title = "设置",
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
            item { GroupTitle("统计规则") }

            item {
                Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                    BasicSettingRow(
                        title = "最短记录时长",
                        summary = "低于该时长的切换不计入统计：${settings.minSessionSeconds} 秒",
                        bottom = {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Slider(
                                    value = settings.minSessionSeconds.toFloat(),
                                    onValueChange = { settings.updateMinSessionSeconds(it.toInt()) },
                                    valueRange = 0f..60f,
                                    steps = 59,
                                    onValueChangeFinished = { vm.reloadWithSettings() },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("0 秒", fontSize = 10.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                    Text("60 秒", fontSize = 10.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                }
                            }
                        },
                    )
                    BasicSettingRow(
                        title = "隐藏系统应用",
                        summary = "不统计桌面、系统设置等预装应用",
                        end = {
                            Switch(
                                checked = settings.hideSystemApps,
                                onCheckedChange = {
                                    settings.updateHideSystemApps(it)
                                    vm.reloadWithSettings()
                                },
                            )
                        },
                    )
                }
            }

            item { GroupTitle("外观") }

            item {
                Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                    BasicSettingRow(
                        title = "主题",
                        summary = "跟随系统 / 浅色 / 深色",
                        bottom = {
                            TabRow(
                                tabs = listOf("跟随系统", "浅色", "深色"),
                                selectedTabIndex = settings.themeMode,
                                onTabSelected = { settings.updateThemeMode(it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                            )
                        },
                    )
                    BasicSettingRow(
                        title = "壁纸取色",
                        summary = "使用壁纸主题色（Monet 动态取色）",
                        end = {
                            Switch(
                                checked = settings.dynamicColor,
                                onCheckedChange = { settings.updateDynamicColor(it) },
                            )
                        },
                    )
                }
            }

            item { GroupTitle("权限与数据") }

            item {
                Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                    BasicSettingRow(
                        title = "使用情况访问权限",
                        summary = if (state.hasPermission) "已授权" else "未授权，无法读取使用记录",
                        end = {
                            Text(
                                text = if (state.hasPermission) "已开启" else "去开启",
                                fontSize = 13.sp,
                                color = if (state.hasPermission) {
                                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                                } else {
                                    MiuixTheme.colorScheme.primary
                                },
                                modifier = Modifier.padding(end = 4.dp),
                            )
                        },
                        onClick = { openUsageAccessSettings(context) },
                    )
                    BasicSettingRow(
                        title = "刷新数据",
                        summary = "重新读取当前日期数据",
                        onClick = { vm.refresh() },
                        end = {
                            Text(
                                text = "刷新",
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 4.dp),
                            )
                        },
                    )
                }
            }

            item { GroupTitle("关于") }

            item {
                Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(18.dp)) {
                    Text(
                        text = "使用时间线",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "轻量的应用使用记录查看器，数据全部来自系统 UsageStats，仅在本地处理。界面基于 Miuix 设计组件。",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "应用信息",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .clickable { openAppInfo(context, context.packageName) },
                        )
                    }
                }
            }
        }
    }
    }
}
