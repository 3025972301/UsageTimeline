package com.operit.usagetimeline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.operit.usagetimeline.ui.MainScreen
import com.operit.usagetimeline.ui.UsageViewModel
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { AppRoot() }
    }
}

@Composable
private fun AppRoot() {
    val vm: UsageViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val settings = vm.settings

    val mode = when (settings.themeMode) {
        1 -> ColorSchemeMode.Light
        2 -> ColorSchemeMode.Dark
        else -> if (settings.dynamicColor) ColorSchemeMode.MonetSystem else ColorSchemeMode.System
    }

    val controller = remember(mode) {
        ThemeController(
            colorSchemeMode = mode,
            lightColors = lightColorScheme(),
            darkColors = darkColorScheme(),
        )
    }

    MiuixTheme(controller = controller) {
        // 每次回到前台都重新检查权限并刷新数据：
        // 用户从「使用情况访问」设置页返回后，无需重启即可看到数据。
        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(lifecycleOwner, vm) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                vm.autoRefresh()
            }
        }
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) vm.onResumeTick()
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        // 状态栏/导航栏图标颜色跟随应用主题（避免浅色主题配深色图标看不见）
        val activity = LocalActivity.current
        val darkTheme = when (settings.themeMode) {
            1 -> false
            2 -> true
            else -> isSystemInDarkTheme()
        }
        LaunchedEffect(darkTheme, activity) {
            val window = activity?.window ?: return@LaunchedEffect
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }

        // 顶层提供不透明背景：详情页/设置页等全屏页面不会透出黑色窗口背景
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MiuixTheme.colorScheme.surface,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                MainScreen(vm = vm, state = state)
            }
        }
    }
}
