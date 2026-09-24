package com.rainingtrace.feature.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rainingtrace.R
import com.rainingtrace.core.time.WORLD_ZONE
import com.rainingtrace.core.ui.describe
import com.rainingtrace.core.ui.label
import com.rainingtrace.domain.inventory.Rarity
import com.rainingtrace.domain.inventory.ResourceCategory
import com.rainingtrace.domain.inventory.ResourceDefinition
import com.rainingtrace.domain.inventory.ResourceSource
import java.time.Instant
import java.time.format.DateTimeFormatter

/** 收藏页的两个页签：背包是"我现在有什么"，图鉴是"我见过什么"。 */
enum class CollectionTab(val label: String) {
    BAG("背包"),
    CODEX("图鉴"),
}

/**
 * 收藏页：同一入口下的两个页签。
 *
 * 拆开的理由不是审美——两者寿命不同：[BagUiState] 会随消耗归零，
 * [CodexUiState] 只增不减。页签状态留在 Compose（同 `JournalScreen` 的口径）。
 */
@Composable
fun InventoryScreen(
    viewModel: InventoryViewModel,
    onOpenCraft: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(CollectionTab.BAG) }

    // 图鉴的真相是足迹事件，切过去时刷一次就够（不做实时订阅）。
    LaunchedEffect(tab) {
        if (tab == CollectionTab.CODEX) viewModel.refreshCodex()
    }

    Column(modifier = modifier.fillMaxSize()) {
        CollectionTabRow(tab = tab, onSelect = { tab = it })
        when (tab) {
            CollectionTab.BAG -> BagPane(viewModel = viewModel, onOpenCraft = onOpenCraft)
            CollectionTab.CODEX -> CodexPane(viewModel)
        }
    }
}

@Composable
private fun CollectionTabRow(tab: CollectionTab, onSelect: (CollectionTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CollectionTab.entries.forEach { entry ->
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

// ---- 背包 ----

@Composable
private fun BagPane(viewModel: InventoryViewModel, onOpenCraft: () -> Unit) {
    val uiState by viewModel.bagUiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        CraftEntry(onOpenCraft = onOpenCraft)
        CapacityLine(uiState)

        if (uiState.entries.isEmpty()) {
            EmptyHint("背包还是空的", "去地图上的地点看看、采点什么吧。")
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(uiState.entries, key = { it.definition.id }) { entry ->
                ItemCard(
                    definition = entry.definition,
                    trailing = {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "× ${entry.quantity} / ${entry.definition.stackLimit}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            // 存的是整叠：想要一半就从仓库取回来，不做数量选择器。
                            TextButton(
                                onClick = {
                                    viewModel.storeToWarehouse(entry.definition.id, entry.quantity)
                                },
                            ) {
                                Text("存入仓库")
                            }
                        }
                    },
                )
            }
        }
    }
}

/** 制作入口：背包页签往里走的一道门（材料够了才有得做）。 */
@Composable
private fun CraftEntry(onOpenCraft: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clickable(onClick = onOpenCraft)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = "制作",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "把背包里的材料做成别的东西",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * 软容量：只说清"用了多少"，满了也只提示。**不阻止采集**——
 * 这是刻意的（见 `InventoryCapacity`），所以文案要让玩家安心。
 */
@Composable
private fun CapacityLine(uiState: BagUiState) {
    val fill = uiState.fill
    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)) {
        Text(
            text = "已放 ${fill.usedKinds} / ${fill.softLimit} 种",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (fill.isOver) {
            Text(
                text = "有点满了，建议把用不上的存进仓库。",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

// ---- 图鉴 ----

@Composable
private fun CodexPane(viewModel: InventoryViewModel) {
    val uiState by viewModel.codexUiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "已发现 ${uiState.discoveredKinds} / ${uiState.totalKinds}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(uiState.entries, key = { it.definition.id }) { entry ->
                CodexCard(entry)
            }
        }
    }
}

@Composable
private fun CodexCard(entry: CodexEntry) {
    val tint = if (entry.discovered) {
        Color.Unspecified
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryBadge(definition = entry.definition, dimmed = !entry.discovered)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.definition.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (entry.discovered) {
                        MaterialTheme.colorScheme.onBackground
                    } else {
                        tint
                    },
                )
                Text(
                    text = "${categoryLabel(entry.definition.category)} · " +
                        rarityLabel(entry.definition.rarity),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = entry.definition.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (entry.discovered) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = discoveredLine(entry),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    sourceLine(entry)?.let { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (entry.discovered) "已发现" else "未发现",
                style = MaterialTheme.typography.labelMedium,
                color = if (entry.discovered) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                },
            )
        }
    }
}

private fun discoveredLine(entry: CodexEntry): String = buildString {
    append("获得 ${entry.timesAcquired} 次")
    entry.firstAtEpochMs?.let {
        append(" · 首次 ")
        append(FIRST_SEEN_FORMAT.format(Instant.ofEpochMilli(it).atZone(WORLD_ZONE)))
    }
}

/** 来源那行：采得到就说地点与条件，只能做出来就说"加工"。 */
private fun sourceLine(entry: CodexEntry): String? {
    if (entry.sources.isNotEmpty()) {
        return "来源：" + entry.sources.joinToString("；") { sourceLabel(it) }
    }
    return if (entry.crafted) "来源：加工" else null
}

private fun sourceLabel(source: ResourceSource): String {
    val place = source.placeType?.label() ?: "任意地点"
    val conditions = source.conditions.joinToString("·") { it.describe() }
    return if (conditions.isEmpty()) place else "$place · $conditions"
}

// ---- 共用 ----

/** 背包条目卡；[trailing] 放右侧的数量/操作。 */
@Composable
private fun ItemCard(
    definition: ResourceDefinition,
    trailing: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryBadge(definition = definition, dimmed = false)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = definition.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "${categoryLabel(definition.category)} · ${rarityLabel(definition.rarity)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = definition.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

@Composable
private fun CategoryBadge(definition: ResourceDefinition, dimmed: Boolean) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(
                color = if (dimmed) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
                },
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = categoryGlyph(definition.category),
            style = MaterialTheme.typography.titleLarge,
            color = if (dimmed) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            } else {
                Color.Unspecified
            },
        )
    }
}

@Composable
private fun EmptyHint(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium)
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