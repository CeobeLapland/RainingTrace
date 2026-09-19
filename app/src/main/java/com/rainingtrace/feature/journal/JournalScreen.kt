package com.rainingtrace.feature.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 日记时间线：记忆即收藏（GDD 支柱）。
 * 按日期分组展示（今天 / 昨天 / 具体日期），每条记忆可「在地图查看」。
 */
@Composable
fun JournalScreen(
    viewModel: JournalViewModel,
    onViewOnMap: (MemoryNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val days by viewModel.days.collectAsStateWithLifecycle()
    val playingRef by viewModel.playingRef.collectAsStateWithLifecycle()

    if (days.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize(),
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
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
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
                    onToggleAudio = viewModel::onToggleAudio,
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
                memory.mood?.let {
                    Text(
                        text = it.label(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
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
