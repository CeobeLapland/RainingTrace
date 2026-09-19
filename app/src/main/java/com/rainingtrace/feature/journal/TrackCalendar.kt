package com.rainingtrace.feature.journal

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.rainingtrace.domain.track.TrackDay
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 轨迹日历（GDD §05 足迹：可开关、按日期筛选、可生成日历）。
 *
 * 有轨迹的日子用实心圆标出，点一天看当天摘要，可「在地图查看」那段轨迹。
 * 时段/距离这类聚合量按需在那一天加载，长期使用也不会一次读全部轨迹点。
 */
@Composable
fun TrackCalendar(
    days: List<TrackDay>,
    selectedDay: TrackDaySummary?,
    onSelectDay: (LocalDate) -> Unit,
    onViewOnMap: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (days.isEmpty()) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("还没有轨迹", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "开着地图走一段，或在设置里打开「足迹记录」。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val daysByDate = remember(days) { days.associateBy { it.date } }
    var monthOverride by remember { mutableStateOf<YearMonth?>(null) }
    val defaultMonth = selectedDay?.date?.let(YearMonth::from)
        ?: days.first().date.let(YearMonth::from)
    val month = monthOverride ?: defaultMonth

    Column(modifier = modifier.fillMaxWidth()) {
        MonthHeader(
            month = month,
            canGoNext = month.isBefore(YearMonth.now()),
            onPrev = { monthOverride = month.minusMonths(1) },
            onNext = { monthOverride = month.plusMonths(1) },
        )
        Spacer(Modifier.height(6.dp))
        WeekdayHeader()
        MonthGrid(
            month = month,
            marked = daysByDate.keys,
            selected = selectedDay?.date,
            onClick = onSelectDay,
        )
        Spacer(Modifier.height(12.dp))
        selectedDay?.let { day ->
            TrackDaySummaryCard(day = day, onViewOnMap = onViewOnMap)
        }
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    canGoNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        MonthArrow(text = "‹", enabled = true, onClick = onPrev)
        Text(
            text = MONTH_FORMAT.format(month),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        MonthArrow(text = "›", enabled = canGoNext, onClick = onNext)
    }
}

@Composable
private fun MonthArrow(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            color = if (enabled) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            },
        )
    }
}

@Composable
private fun WeekdayHeader() {
    Row(modifier = Modifier.fillMaxWidth()) {
        WEEKDAYS.forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    marked: Set<LocalDate>,
    selected: LocalDate?,
    onClick: (LocalDate) -> Unit,
) {
    // 周一为一周之首：空白格数 = 1 号的星期序号 - 1。
    val leadingBlanks = month.atDay(1).dayOfWeek.value - 1
    val cells: List<LocalDate?> = buildList {
        repeat(leadingBlanks) { add(null) }
        for (day in 1..month.lengthOfMonth()) add(month.atDay(day))
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        cells.chunked(DAYS_PER_WEEK).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    DayCell(
                        date = date,
                        marked = date != null && date in marked,
                        selected = date != null && date == selected,
                        onClick = onClick,
                        modifier = Modifier.weight(1f),
                    )
                }
                // 末行补齐，避免最后一行的格子被拉伸。
                repeat(DAYS_PER_WEEK - week.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    marked: Boolean,
    selected: Boolean,
    onClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(3.dp)
            .height(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (date != null) {
            val background = when {
                selected -> MaterialTheme.colorScheme.secondary
                marked -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else -> MaterialTheme.colorScheme.surface
            }
            val labelColor = when {
                selected -> MaterialTheme.colorScheme.onSecondary
                marked -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(color = background)
                    .clickable(enabled = marked) { onClick(date) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = labelColor,
                )
            }
        }
    }
}

@Composable
private fun TrackDaySummaryCard(
    day: TrackDaySummary,
    onViewOnMap: (LocalDate) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = DAY_TITLE.format(day.date),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${distanceLabel(day.lengthMeters)} · " +
                    "${day.startLabel}–${day.endLabel} · ${day.pointCount} 个点",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { onViewOnMap(day.date) }) {
                    Text("在地图查看")
                }
            }
        }
    }
}

private fun distanceLabel(meters: Double): String = if (meters < 1000) {
    "${meters.toInt()} m"
} else {
    String.format(Locale.SIMPLIFIED_CHINESE, "%.2f km", meters / 1000)
}

private const val DAYS_PER_WEEK = 7
private val WEEKDAYS = listOf("一", "二", "三", "四", "五", "六", "日")
private val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy 年 M 月")
private val DAY_TITLE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.SIMPLIFIED_CHINESE)