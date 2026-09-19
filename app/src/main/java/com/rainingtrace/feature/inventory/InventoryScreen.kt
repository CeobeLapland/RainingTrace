package com.rainingtrace.feature.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rainingtrace.core.time.WORLD_ZONE
import com.rainingtrace.domain.inventory.Rarity
import com.rainingtrace.domain.inventory.ResourceCategory
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * 背包/图鉴：收藏总览。每个目录条目一张卡：
 * 已收集显示数量，未收集置灰；顶部给出收集进度，形成"再出门收集"的动机。
 */
@Composable
fun InventoryScreen(
    viewModel: InventoryViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "图鉴",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 20.dp, top = 8.dp),
        )
        Text(
            text = "已收集 ${uiState.ownedKinds} / ${uiState.totalKinds}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, top = 2.dp, bottom = 8.dp),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                end = 20.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(uiState.entries, key = { it.definition.id }) { entry ->
                CollectionCard(entry)
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun CollectionCard(entry: CollectionEntry) {
    val owned = entry.quantity > 0
    val tint = if (owned) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = if (owned) {
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = categoryGlyph(entry.definition.category),
                    style = MaterialTheme.typography.titleLarge,
                    color = tint,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.definition.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (owned) MaterialTheme.colorScheme.onBackground else tint,
                )
                Text(
                    text = "${categoryLabel(entry.definition.category)} · ${rarityLabel(entry.definition.rarity)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = entry.definition.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(10.dp))
            if (owned) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "× ${entry.quantity}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    entry.firstAcquiredAtEpochMs?.let { first ->
                        Text(
                            text = "首次 ${FIRST_SEEN_FORMAT.format(
                                Instant.ofEpochMilli(first).atZone(WORLD_ZONE),
                            )}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Text(
                    text = "未收集",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}

private fun categoryGlyph(category: ResourceCategory): String = when (category) {
    ResourceCategory.NATURE -> "叶"
    ResourceCategory.KNOWLEDGE -> "记"
    ResourceCategory.CULTURE -> "俗"
    ResourceCategory.MEMORY -> "忆"
    ResourceCategory.ANOMALY -> "异"
    ResourceCategory.CRAFT -> "工"
}

private fun categoryLabel(category: ResourceCategory): String = when (category) {
    ResourceCategory.NATURE -> "自然"
    ResourceCategory.KNOWLEDGE -> "知识"
    ResourceCategory.CULTURE -> "文化"
    ResourceCategory.MEMORY -> "记忆"
    ResourceCategory.ANOMALY -> "异常"
    ResourceCategory.CRAFT -> "制造"
}

private fun rarityLabel(rarity: Rarity): String = when (rarity) {
    Rarity.COMMON -> "常见"
    Rarity.UNCOMMON -> "少见"
    Rarity.RARE -> "稀有"
    Rarity.ANOMALY -> "异常"
}

private val FIRST_SEEN_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日")