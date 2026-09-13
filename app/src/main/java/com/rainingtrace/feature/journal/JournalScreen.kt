package com.rainingtrace.feature.journal

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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rainingtrace.domain.memory.MemoryNode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 日记时间线：记忆即收藏（GDD 支柱）。
 * MVP 显示文字/心情/时间/地点格；照片缩略图 P1 补。
 */
@Composable
fun JournalScreen(
    viewModel: JournalViewModel,
    modifier: Modifier = Modifier,
) {
    val memories by viewModel.memories.collectAsStateWithLifecycle()

    if (memories.isEmpty()) {
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
        items(memories, key = { it.id }) { memory ->
            MemoryCard(memory)
        }
    }
}

@Composable
private fun MemoryCard(memory: MemoryNode, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    memory.mediaRefs.take(3).forEach { uri ->
                        LocalImageThumbnail(localUri = uri)
                    }
                }
            }
        }
    }
}

private val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
private val TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("M月d日 HH:mm")

private fun com.rainingtrace.domain.memory.Mood.label(): String = when (this) {
    com.rainingtrace.domain.memory.Mood.CALM -> "平静"
    com.rainingtrace.domain.memory.Mood.HAPPY -> "开心"
    com.rainingtrace.domain.memory.Mood.CURIOUS -> "好奇"
    com.rainingtrace.domain.memory.Mood.LONELY -> "孤独"
    com.rainingtrace.domain.memory.Mood.EXCITED -> "兴奋"
    com.rainingtrace.domain.memory.Mood.MELANCHOLY -> "低落"
}
