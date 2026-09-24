package com.rainingtrace.feature.warehouse

import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rainingtrace.domain.inventory.ResourceCategory

/**
 * 仓库页：家的存储。每一行「取出到背包」搬的是**整叠**。
 *
 * 轻量到不值得做数量选择器：仓库本来就是临时堆放处，玩家想要一半时
 * 取出来再从背包存回去就是了。
 */
@Composable
fun WarehouseScreen(
    viewModel: WarehouseViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "存了 ${uiState.entries.size} 种东西",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
        )

        if (uiState.entries.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("仓库还空着", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "在背包里把用不上的东西存进来吧。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(uiState.entries, key = { it.definition.id }) { entry ->
                WarehouseCard(
                    entry = entry,
                    onTake = {
                        viewModel.takeAllToBackpack(entry.definition.id, entry.quantity)
                    },
                )
            }
        }
    }
}

@Composable
private fun WarehouseCard(entry: WarehouseEntry, onTake: () -> Unit) {
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
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = categoryGlyph(entry.definition.category),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.definition.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "× ${entry.quantity}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onTake) {
                Text("取出到背包")
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