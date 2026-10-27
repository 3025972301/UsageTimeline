package com.operit.usagetimeline.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.operit.usagetimeline.util.Fmt
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalDate

/** 日期切换条：前一天 / 日期（可点开日历） / 后一天。 */
@Composable
fun DateBar(
    date: LocalDate,
    today: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPickDate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = MiuixIcons.Regular.ChevronBackward,
                contentDescription = "前一天",
                modifier = Modifier.size(22.dp),
                tint = MiuixTheme.colorScheme.onSurface,
            )
        }
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { onPickDate() }
                .padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = Fmt.dayLabel(date, today),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = "${date.year}/${date.monthValue}/${date.dayOfMonth} ${Fmt.weekday(date)}",
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        IconButton(
            onClick = onNext,
            enabled = date.isBefore(today),
        ) {
            Icon(
                imageVector = MiuixIcons.Regular.ChevronForward,
                contentDescription = "后一天",
                modifier = Modifier.size(22.dp),
                tint = if (date.isBefore(today)) {
                    MiuixTheme.colorScheme.onSurface
                } else {
                    MiuixTheme.colorScheme.disabledOnSurface
                },
            )
        }
    }
}

/** 24 小时使用时长柱状图。 */
@Composable
fun HourBarChart(
    hourly: LongArray,
    modifier: Modifier = Modifier,
    barColor: Color = MiuixTheme.colorScheme.primary,
) {
    val maxValue = hourly.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val trackColor = MiuixTheme.colorScheme.surfaceContainerHighest
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
        ) {
            val count = 24
            val gap = 2.dp.toPx()
            val barWidth = (size.width - gap * (count - 1)) / count
            for (i in 0 until count) {
                val ratio = (hourly.getOrElse(i) { 0L }.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
                val x = i * (barWidth + gap)
                val corner = CornerRadius(barWidth / 2f, barWidth / 2f)
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth, size.height),
                    cornerRadius = corner,
                )
                if (ratio > 0f) {
                    val h = (size.height * ratio).coerceAtLeast(barWidth)
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, size.height - h),
                        size = Size(barWidth, h),
                        cornerRadius = corner,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("0", "6", "12", "18", "24").forEach { label ->
                Text(
                    text = if (label == "24") "24时" else "${label}时",
                    fontSize = 10.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

/** 概览卡片：总时长 + 关键指标。 */
@Composable
fun SummaryCard(
    totalMs: Long,
    sessionCount: Int,
    appCount: Int,
    screenOnCount: Int,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth(), insideMargin = PaddingValues(18.dp)) {
        Text(
            text = Fmt.duration(totalMs),
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Text(
            text = "屏幕使用总时长",
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StatCell(value = "$appCount", label = "应用")
            StatCell(value = "$sessionCount", label = "打开次数")
            StatCell(value = "$screenOnCount", label = "亮屏次数")
            StatCell(value = Fmt.durationShort(totalMs / 24), label = "平均每小时")
        }
    }
}

/** 通用列表行：图标 + 标题/副标题 + 尾部内容。 */
@Composable
fun AppRow(
    packageName: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(packageName = packageName, size = 38)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                maxLines = 1,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                maxLines = 1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** 用于在卡片内分隔列表项。 */
@Composable
fun RowDivider(modifier: Modifier = Modifier, startPadding: Int = 66) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = startPadding.dp)
            .height(0.6.dp)
            .background(MiuixTheme.colorScheme.dividerLine),
    )
}