package com.rainingtrace.feature.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rainingtrace.core.ui.AudioNoteChip
import com.rainingtrace.core.ui.LocalImage
import com.rainingtrace.core.ui.label
import com.rainingtrace.domain.memory.MemoryNode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 日记页的两个页签：记忆是"我拍到的/写下的"，轨迹是"我走过的"。 */
enum class JournalTab(val label: String) {
    MEMORY("记忆"),
    TRACK("轨迹"),
}

/**
 * 日记：记忆即收藏（GDD 支柱 §06），轨迹即足迹（GDD §05）。
 *
 * - 记忆页签：按日期分组（今天 / 昨天 / 具体日期），每条可「在地图查看」；
 * - 轨迹页签：月历标出有轨迹的日子，选中那天看摘要并可「在地图查看」。
 */
@Composable
fun JournalScreen(
    viewModel: JournalViewModel,
    onViewOnMap: (MemoryNode) -> Unit,
    onViewTrackDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val days by viewModel.days.collectAsStateWithLifecycle()
    val trackDays by viewModel.trackDays.collectAsStateWithLifecycle()
    val selectedTrackDay by viewModel.selectedTrackDay.collectAsStateWithLifecycle()
    val playingRef by viewModel.playingRef.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(JournalTab.MEMORY) }

    // 切到轨迹页签时重查一次，刚走完的路立刻能看到。
    LaunchedEffect(tab) {
        if (tab == JournalTab.TRACK) viewModel.refreshTrackDays()
    }

    Column(modifier = modifier.fillMaxSize()) {
        JournalTabRow(tab = tab, onSelect = { tab = it })
        when (tab) {
            JournalTab.MEMORY -> MemoryTimeline(
                days = days,
                playingRef = playingRef,
                onViewOnMap = onViewOnMap,
                onToggleAudio = viewModel::onToggleAudio,
            )

            JournalTab.TRACK -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            ) {
                TrackCalendar(
                    days = trackDays,
                    selectedDay = selectedTrackDay,
                    onSelectDay = viewModel::selectTrackDay,
                    onViewOnMap = onViewTrackDay,
                )
            }
        }
    }
}

@Composable
private fun JournalTabRow(tab: JournalTab, onSelect: (JournalTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        JournalTab.entries.forEach { entry ->
            val selected = entry == tab
            Surface(
                color = if (selected) {
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(percent = 50),
                modifier = Modifier.clickable { onSelect(entry) },
            ) {
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSecondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun MemoryTimeline(
    days: List<JournalDay>,
    playingRef: String?,
    onViewOnMap: (MemoryNode) -> Unit,
    onToggleAudio: (String) -> Unit,
) {
    if (days.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("还没有记忆", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "去湖边拍一张，或写点什么吧。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        days.forEach { day ->
            item(key = "day-${day.date}") {
                DayHeader(label = day.label, count = day.memories.size)
            }
            items(day.memories, key = { it.id }) { memory ->
                MemoryCard(
                    memory = memory,
                    playingRef = playingRef,
                    onViewOnMap = onViewOnMap,
                    onToggleAudio = onToggleAudio,
                )
            }
        }
    }
}

@Composable
private fun DayHeader(label: String, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "$count 条",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MemoryCard(
    memory: MemoryNode,
    playingRef: String?,
    onViewOnMap: (MemoryNode) -> Unit,
    onToggleAudio: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = TIME_FORMATTER.format(
                        Instant.ofEpochMilli(memory.createdAtEpochMs).atZone(ZONE),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 当时的天气/季节：老记忆可能没有，就不显示。
                    memory.weather?.let { weather ->
                        Text(
                            text = weather.label(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Spacer(Modifier.size(6.dp))
                    }
                    memory.mood?.let {
                        Text(
                            text = it.label(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            if (memory.text.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(memory.text, style = MaterialTheme.typography.bodyMedium)
            }
            if (memory.mediaRefs.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    memory.mediaRefs.forEach { uri ->
                        LocalImage(
                            localUri = uri,
                            modifier = Modifier
                                .size(THUMB_DP)
                                .clip(RoundedCornerShape(10.dp)),
                        )
                    }
                }
            }
            memory.audioRef?.let { audioRef ->
                Spacer(Modifier.height(8.dp))
                AudioNoteChip(
                    audioRef = audioRef,
                    isPlaying = playingRef == audioRef,
                    onToggle = { onToggleAudio(audioRef) },
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "在地图查看",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { onViewOnMap(memory) }
                    .padding(vertical = 4.dp),
            )
        }
    }
}

private val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val THUMB_DP = 72.dp