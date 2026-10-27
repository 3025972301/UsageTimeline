package com.operit.usagetimeline.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Report
import com.operit.usagetimeline.ui.screens.AppDetailScreen
import com.operit.usagetimeline.ui.screens.AppsScreen
import com.operit.usagetimeline.ui.screens.SettingsScreen
import com.operit.usagetimeline.ui.screens.StatsScreen
import com.operit.usagetimeline.ui.screens.TimelineScreen

private enum class Tab {
    Timeline,
    Apps,
    Stats,
}

@Composable
fun MainScreen(vm: UsageViewModel, state: UiState) {
    var currentTab by rememberSaveable { mutableStateOf(Tab.Timeline.name) }
    var detailPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    val tab = Tab.valueOf(currentTab)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.hasPermission) {
        if (!state.hasPermission) detailPackage = null
    }

    // 关键：保存每个 Tab 的界面状态（LazyList 滚动位置、搜索词等），
    // 从详情页/设置页返回时不会重建列表、不会回到顶部。
    val tabStateHolder = rememberSaveableStateHolder()

    val toast: (String) -> Unit = { msg ->
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }

    BackHandler(enabled = detailPackage != null || showSettings) {
        if (detailPackage != null) detailPackage = null else showSettings = false
    }

    Scaffold(
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
        bottomBar = {
            if (detailPackage == null && !showSettings) {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == Tab.Timeline,
                        onClick = { currentTab = Tab.Timeline.name },
                        icon = MiuixIcons.Regular.ListView,
                        label = "时间线",
                    )
                    NavigationBarItem(
                        selected = tab == Tab.Apps,
                        onClick = { currentTab = Tab.Apps.name },
                        icon = MiuixIcons.Regular.GridView,
                        label = "应用",
                    )
                    NavigationBarItem(
                        selected = tab == Tab.Stats,
                        onClick = { currentTab = Tab.Stats.name },
                        icon = MiuixIcons.Regular.Report,
                        label = "统计",
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (detailPackage == null && !showSettings) padding.calculateBottomPadding() else 0.dp),
        ) {
            // 详情页 / 设置页用覆盖层方式呈现（不销毁下层 Tab 内容，返回后滚动位置完整保留）
            if (state.loading && !state.hasPermission) {
                Box(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp)) {
                    EmptyHint(title = "正在读取使用记录…", summary = "")
                }
            } else if (state.error != null && state.day == null) {
                Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp)) {
                    EmptyHint(title = "读取失败", summary = state.error)
                    TextButton(text = "重试", onClick = { vm.refresh() })
                }
            } else AnimatedContent(
                targetState = Tab.valueOf(currentTab),
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(110)) },
                label = "tab",
            ) { target ->
                tabStateHolder.SaveableStateProvider(target.name) {
                    when (target) {
                        Tab.Timeline -> TimelineScreen(
                            vm = vm,
                            state = state,
                            snackbar = toast,
                            onOpenApp = { pkg -> detailPackage = pkg },
                            onOpenSettings = { showSettings = true },
                        )

                        Tab.Apps -> AppsScreen(
                            vm = vm,
                            state = state,
                            snackbar = toast,
                            onOpenApp = { pkg -> detailPackage = pkg },
                            onOpenSettings = { showSettings = true },
                        )

                        Tab.Stats -> StatsScreen(
                            vm = vm,
                            state = state,
                            onOpenApp = { pkg -> detailPackage = pkg },
                            onOpenSettings = { showSettings = true },
                        )
                    }
                }
            }

            if (state.error != null && state.day != null) {
                LaunchedEffect(state.error) { toast("刷新失败，已保留上次记录：${state.error}") }
            }

            if (detailPackage != null) {
                // 覆盖层：抵消触摸，避免点击/滚动穿透到下层列表
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) { detectTapGestures { } },
                ) {
                    AppDetailScreen(
                        vm = vm,
                        state = state,
                        packageName = detailPackage!!,
                        snackbar = toast,
                        onBack = { detailPackage = null },
                    )
                }
            }

            if (showSettings) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) { detectTapGestures { } },
                ) {
                    SettingsScreen(
                        vm = vm,
                        state = state,
                        onBack = { showSettings = false },
                    )
                }
            }
        }
    }
}
