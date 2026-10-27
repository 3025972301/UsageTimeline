package com.operit.usagetimeline.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.operit.usagetimeline.data.AppPalette
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Clear
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 应用图标缓存（避免每次重组都访问 PackageManager）。 */
object AppIconCache {
    private val cache = LruCache<String, Bitmap>(160)

    fun load(context: Context, packageName: String): Bitmap? {
        cache.get(packageName)?.let { return it }
        return try {
            val pm = context.packageManager
            val drawable = pm.getApplicationIcon(packageName)
            val bmp = drawable.toBitmap(144, 144, Bitmap.Config.ARGB_8888)
            cache.put(packageName, bmp)
            bmp
        } catch (t: Throwable) {
            null
        }
    }
}

@Composable
fun AppIcon(packageName: String, size: Int = 40, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(packageName) { AppIconCache.load(context, packageName) }
    val shape = RoundedCornerShape((size * 0.28f).dp)
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier
                .size(size.dp)
                .clip(shape),
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(
            modifier = modifier
                .size(size.dp)
                .clip(shape)
                .background(AppPalette.colorFor(packageName).copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = packageName.take(1).uppercase(),
                fontSize = (size * 0.42f).sp,
                fontWeight = FontWeight.Medium,
                color = AppPalette.colorFor(packageName),
            )
        }
    }
}

@Composable
fun GroupTitle(text: String, modifier: Modifier = Modifier) {
    SmallTitle(text = text, modifier = modifier)
}

/** 顶部数值卡片：大数字 + 说明。 */
@Composable
fun StatCell(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MiuixTheme.colorScheme.onSurface,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

/** 空状态提示。 */
@Composable
fun EmptyHint(
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = summary,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

/** 权限引导卡片。 */
@Composable
fun PermissionCard(
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth(), insideMargin = PaddingValues(18.dp)) {
        Text(
            text = "需要「使用情况访问」权限",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "开启后即可读取本机应用的使用记录，用于生成时间线与统计。数据仅保存在系统本地，应用不会上传任何信息。",
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            top.yukonga.miuix.kmp.basic.Button(
                onClick = onRequest,
                minWidth = 0.dp,
                modifier = Modifier.weight(1f),
            ) {
                Text("去授权", fontSize = 14.sp)
            }
            top.yukonga.miuix.kmp.basic.TextButton(
                text = "应用设置",
                onClick = onOpenSettings,
                minWidth = 0.dp,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 设置项：标题 + 说明 + 可选尾部操作 / 底部扩展内容。 */
@Composable
fun BasicSettingRow(
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    end: (@Composable () -> Unit)? = null,
    bottom: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = summary,
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            if (end != null) {
                Spacer(Modifier.width(10.dp))
                end()
            }
        }
        if (bottom != null) {
            Spacer(Modifier.height(8.dp))
            bottom()
        }
    }
}

/** 搜索输入框（Miuix 风格，基于 BasicTextField 自绘）。 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            // 等待布局完成后再请求焦点，否则会抛异常
            try {
                withFrameNanos { }
                focusRequester.requestFocus()
                keyboard?.show()
            } catch (t: Throwable) {
                // ignore
            }
        }
    }
    val bg = MiuixTheme.colorScheme.secondaryContainer
    val textColor = MiuixTheme.colorScheme.onSurface
    val hintColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = MiuixIcons.Regular.Search,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
            tint = hintColor,
        )
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    fontSize = 14.sp,
                    color = hintColor,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MiuixTheme.textStyles.main.copy(color = textColor, fontSize = 14.sp),
                cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        }
        if (value.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(hintColor.copy(alpha = 0.18f))
                    .clickable { onValueChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MiuixIcons.Regular.Clear,
                    contentDescription = "清除",
                    modifier = Modifier.size(12.dp),
                    tint = hintColor,
                )
            }
        }
    }
}

fun openUsageAccessSettings(context: Context) {
    val candidates = listOf(
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}")),
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )
    for (intent in candidates) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            return
        } catch (t: Throwable) {
            // try next
        }
    }
}

fun openAppInfo(context: Context, packageName: String) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (t: Throwable) {
        // ignore
    }
}

fun launchApp(context: Context, packageName: String): Boolean = try {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    if (intent == null) {
        false
    } else {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }
} catch (t: Throwable) {
    false
}

fun isAppInstalled(context: Context, packageName: String): Boolean = try {
    context.packageManager.getApplicationInfo(packageName, 0)
    true
} catch (e: PackageManager.NameNotFoundException) {
    false
}