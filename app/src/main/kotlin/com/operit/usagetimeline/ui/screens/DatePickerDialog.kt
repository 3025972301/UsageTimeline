package com.operit.usagetimeline.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.operit.usagetimeline.util.Fmt
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalDate
import java.time.YearMonth

/**
 * 自绘的月历选择器（不依赖 Material 组件，保持 Miuix 观感）。
 */
@Composable
fun DatePickerDialog(
    initial: LocalDate,
    maxDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
    snackbar: (String) -> Unit = {},
) {
    var month by remember { mutableStateOf(YearMonth.from(initial)) }
    var selected by remember { mutableStateOf(initial) }
    val today = LocalDate.now()

    OverlayDialog(
        show = true,
        title = "选择日期",
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 月份切换
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = { month = month.minusMonths(1) },
                    enabled = month.isAfter(YearMonth.from(today).minusMonths(24)),
                ) {
                    Icon(
                        imageVector = MiuixIcons.Regular.ChevronBackward,
                        contentDescription = "上个月",
                        modifier = Modifier.size(20.dp),
                        tint = MiuixTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = "${month.year}年${month.monthValue}月",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                IconButton(
                    onClick = { month = month.plusMonths(1) },
                    enabled = month.isBefore(YearMonth.from(today)),
                ) {
                    Icon(
                        imageVector = MiuixIcons.Regular.ChevronForward,
                        contentDescription = "下个月",
                        modifier = Modifier.size(20.dp),
                        tint = if (month.isBefore(YearMonth.from(today))) {
                            MiuixTheme.colorScheme.onSurface
                        } else {
                            MiuixTheme.colorScheme.disabledOnSurface
                        },
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // 星期表头
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { w ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = w,
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            val firstDay = month.atDay(1)
            val leading = firstDay.dayOfWeek.value - 1 // 周一为 0
            val days = month.lengthOfMonth()
            val cells = ArrayList<LocalDate?>()
            repeat(leading) { cells.add(null) }
            for (d in 1..days) cells.add(month.atDay(d))
            while (cells.size % 7 != 0) cells.add(null)

            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier
                    .fillMaxWidth()
                    .height((cells.size / 7 * 42).dp),
                userScrollEnabled = false,
            ) {
                items(cells) { date ->
                    Box(
                        modifier = Modifier
                            .height(42.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (date != null) {
                            val isFuture = date.isAfter(maxDate)
                            val isSelected = date == selected
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MiuixTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                                    )
                                    .clickable(enabled = !isFuture) { selected = date },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "${date.dayOfMonth}",
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                    color = when {
                                        isSelected -> MiuixTheme.colorScheme.onPrimary
                                        isFuture -> MiuixTheme.colorScheme.disabledOnSurface
                                        date == today -> MiuixTheme.colorScheme.primary
                                        else -> MiuixTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    text = "今天",
                    onClick = {
                        selected = today
                        month = YearMonth.from(today)
                    },
                    minWidth = 0.dp,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { onConfirm(selected) },
                    minWidth = 0.dp,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("查看 ${Fmt.dayLabel(selected, today)}", fontSize = 14.sp)
                }
            }
        }
    }
}