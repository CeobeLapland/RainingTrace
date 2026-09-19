package com.rainingtrace.core.ui

import android.media.MediaMetadataRetriever
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.rainingtrace.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 记忆里的语音条：点按播放/停止，并显示时长（从文件元数据读取）。
 * 日记与地图聚焦卡共用。
 */
@Composable
fun AudioNoteChip(
    audioRef: String,
    isPlaying: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val durationMs by produceState(initialValue = 0L, audioRef) {
        value = withContext(Dispatchers.IO) {
            runCatching { readDurationMs(audioRef) }.getOrDefault(0L)
        }
    }

    Surface(
        color = if (isPlaying) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(percent = 50),
        modifier = modifier.clickable(onClick = onToggle),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painter = painterResource(
                    if (isPlaying) R.drawable.ic_stop else R.drawable.ic_play,
                ),
                contentDescription = if (isPlaying) "停止" else "播放",
                tint = if (isPlaying) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = if (durationMs > 0) {
                    "语音 ${formatDuration(durationMs)}"
                } else {
                    "语音"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (isPlaying) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

private fun readDurationMs(localUri: String): Long {
    val file = File(localUri.removePrefix("file://"))
    if (!file.exists()) return 0L
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: 0L
    } finally {
        runCatching { retriever.release() }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}
