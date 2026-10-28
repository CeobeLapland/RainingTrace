package com.rainingtrace.feature.editor

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rainingtrace.R
import com.rainingtrace.core.common.AppContainer
import com.rainingtrace.domain.inventory.ResourceDefinition
import com.rainingtrace.domain.map.Place
import com.rainingtrace.domain.npc.NpcEditDraft
import com.rainingtrace.domain.npc.NpcEditIssue
import com.rainingtrace.domain.npc.NpcProfile
import com.rainingtrace.domain.npc.NpcScheduleEntry
import com.rainingtrace.domain.npc.NpcTopic
import com.rainingtrace.domain.npc.NpcTrait

/**
 * 内容工作台（NPC）：开发者模式里的可视化编辑。
 *
 * 只改**人物**：基本信息、性格、话题、礼物、人设提示词、作息。
 * 所有关联 id（地点 / 资源 / 话题 / 性格）都是**下拉选择**，不手打；
 * 编辑时实时校验，避免"存进去却静默不生效"。
 */
@Composable
fun NpcEditorRoute(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val viewModel: NpcEditorViewModel = viewModel {
        NpcEditorViewModel(
            npcRepository = container.npcRepository,
            placeRepository = container.placeRepository,
            contentPanel = container.contentStore,
            writer = container.npcContentWriter,
        )
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    BackHandler {
        if (uiState.editing != null) viewModel.backToList() else onBack()
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.consumeMessage()
        }
    }

    if (uiState.editing == null) {
        NpcListPage(
            uiState = uiState,
            onQueryChange = viewModel::onQueryChange,
            onSelect = viewModel::select,
            onBack = onBack,
        )
    } else {
        NpcEditPage(
            draft = uiState.editing!!,
            places = uiState.placeOptions,
            resources = uiState.resourceOptions,
            issues = uiState.issues,
            onEdit = viewModel::edit,
            onSave = viewModel::save,
            onReset = viewModel::resetToBuiltIn,
            onBack = viewModel::backToList,
        )
    }
}

// ---------------------------------------------------------------------------
// 列表页
// ---------------------------------------------------------------------------

@Composable
private fun NpcListPage(
    uiState: NpcEditorUiState,
    onQueryChange: (String) -> Unit,
    onSelect: (NpcProfile) -> Unit,
    onBack: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column {
            EditorTopBar(title = "NPC 工作台", onBack = onBack)
            Text(
                text = "改动只影响你这一份（写入覆盖层），随时可以重置回内置内容。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            OutlinedTextField(
                value = uiState.query,
                onValueChange = onQueryChange,
                label = { Text("搜索名字 / id / 身份") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Text(
                text = "共 ${uiState.filteredNpcs.size} 位",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(uiState.filteredNpcs, key = { it.id }) { npc ->
                    NpcRow(
                        npc = npc,
                        overridden = npc.id in uiState.overriddenIds,
                        onClick = { onSelect(npc) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NpcRow(npc: NpcProfile, overridden: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(npc.name.ifBlank { npc.id }, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                if (overridden) {
                    Text(
                        text = "已覆盖",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (!npc.enabled) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "未启用",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (npc.scheduleVariants.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "变体 ${npc.scheduleVariants.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            Text(
                text = npc.role.ifBlank { npc.id },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (npc.oneLiner.isNotBlank()) {
                Text(
                    text = npc.oneLiner,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 编辑页
// ---------------------------------------------------------------------------

@Composable
private fun NpcEditPage(
    draft: NpcEditDraft,
    places: List<Place>,
    resources: List<ResourceDefinition>,
    issues: List<NpcEditIssue>,
    onEdit: ((NpcEditDraft) -> NpcEditDraft) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    var showLikedPicker by remember { mutableStateOf(false) }
    var showDislikedPicker by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column {
            EditorTopBar(
                title = draft.name.ifBlank { draft.id },
                onBack = onBack,
                action = {
                    TextButton(onClick = onSave) { Text("保存") }
                },
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (issues.isNotEmpty()) {
                    IssueCard(issues)
                }

                Text("基本信息", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { v -> onEdit { it.copy(name = v) } },
                    label = { Text("名字") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.role,
                    onValueChange = { v -> onEdit { it.copy(role = v) } },
                    label = { Text("身份（一句话）") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.oneLiner,
                    onValueChange = { v -> onEdit { it.copy(oneLiner = v) } },
                    label = { Text("一句话人设（路边第一次撞见时显示）") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("按作息出现在地图上", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = draft.enabled,
                        onCheckedChange = { v -> onEdit { it.copy(enabled = v) } },
                    )
                }

                Text("性格", style = MaterialTheme.typography.labelLarge)
                ChipGroup(
                    options = NpcTrait.entries,
                    labelOf = { it.label },
                    selected = draft.traits,
                    onToggle = { trait ->
                        onEdit {
                            it.copy(
                                traits = if (trait in it.traits) it.traits - trait else it.traits + trait,
                            )
                        }
                    },
                )

                Text("愿意聊的话题", style = MaterialTheme.typography.labelLarge)
                ChipGroup(
                    options = NpcTopic.entries,
                    labelOf = { it.label },
                    selected = draft.topics,
                    onToggle = { topic ->
                        onEdit {
                            val next = if (topic in it.topics) it.topics - topic else it.topics + topic
                            // 取消话题时，若 favorite 正好是它，一并清掉（避免保存时非法）。
                            val nextFavorite = it.favoriteTopic?.takeIf { f -> f in next }
                            it.copy(topics = next, favoriteTopic = nextFavorite)
                        }
                    },
                )

                Text("最喜欢的话题", style = MaterialTheme.typography.labelLarge)
                DropdownField(
                    value = draft.favoriteTopic?.label ?: "（未选择）",
                    options = draft.topics.toList().sortedBy { it.label },
                    labelOf = { it.label },
                    onPick = { topic -> onEdit { it.copy(favoriteTopic = topic) } },
                    onClear = { onEdit { it.copy(favoriteTopic = null) } },
                )

                Text("背景（一行一条）", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = draft.backstory.joinToString("\n"),
                    onValueChange = { v ->
                        onEdit { it.copy(backstory = v.lines().filter { line -> line.isNotBlank() }) }
                    },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("人设提示词（LLM 用，留空 = 自动组装）", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = draft.personaPrompt.orEmpty(),
                    onValueChange = { v -> onEdit { it.copy(personaPrompt = v.takeIf { it.isNotBlank() }) } },
                    minLines = 3,
                    placeholder = { Text("留空就用身份/性格/背景自动拼") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("礼物偏好", style = MaterialTheme.typography.labelLarge)
                ResourcePickerRow(
                    label = "喜欢（${draft.giftLiked.size}）",
                    ids = draft.giftLiked,
                    resources = resources,
                    onClick = { showLikedPicker = true },
                )
                ResourcePickerRow(
                    label = "不喜欢（${draft.giftDisliked.size}）",
                    ids = draft.giftDisliked,
                    resources = resources,
                    onClick = { showDislikedPicker = true },
                )

                Text("家（可选）", style = MaterialTheme.typography.labelLarge)
                DropdownField(
                    value = places.firstOrNull { it.id == draft.homePlaceId }?.name ?: "（未设置）",
                    options = places,
                    labelOf = { it.name },
                    onPick = { place -> onEdit { it.copy(homePlaceId = place.id) } },
                    onClear = { onEdit { it.copy(homePlaceId = null) } },
                )

                Text("作息（到达时刻 → 地点 → 在做什么）", style = MaterialTheme.typography.labelLarge)
                draft.schedule.forEachIndexed { index, entry ->
                    ScheduleRow(
                        entry = entry,
                        places = places,
                        onChange = { next ->
                            onEdit {
                                it.copy(schedule = it.schedule.toMutableList().also { list -> list[index] = next })
                            }
                        },
                        onDelete = {
                            onEdit { it.copy(schedule = it.schedule.filterIndexed { i, _ -> i != index }) }
                        },
                    )
                }
                OutlinedButton(
                    onClick = {
                        onEdit {
                            val last = it.schedule.maxByOrNull { entry -> entry.startMinute }
                            val nextMinute = ((last?.startMinute ?: 480) + 120).coerceAtMost(23 * 60 + 55)
                            it.copy(
                                schedule = it.schedule + NpcScheduleEntry(
                                    startMinute = nextMinute,
                                    placeId = places.firstOrNull()?.id.orEmpty(),
                                    travelMinutes = 15,
                                    activity = "",
                                ),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("＋ 加一条作息") }

                if (draft.scheduleVariants.isNotEmpty()) {
                    Text("作息变体（只读）", style = MaterialTheme.typography.labelLarge)
                    draft.scheduleVariants.forEach { variant ->
                        Text(
                            text = "「${variant.label}」${variant.schedule.size} 段" +
                                if (variant.schedule.isEmpty()) "（这段时间不在）" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("重置为内置内容") }
                Spacer(Modifier.height(24.dp))
            }
        }

        if (showLikedPicker) {
            ResourcePickerDialog(
                title = "选择他喜欢的",
                resources = resources,
                selectedIds = draft.giftLiked.toSet(),
                onConfirm = { ids -> onEdit { it.copy(giftLiked = ids) } },
                onDismiss = { showLikedPicker = false },
            )
        }
        if (showDislikedPicker) {
            ResourcePickerDialog(
                title = "选择他不喜欢的",
                resources = resources,
                selectedIds = draft.giftDisliked.toSet(),
                onConfirm = { ids -> onEdit { it.copy(giftDisliked = ids) } },
                onDismiss = { showDislikedPicker = false },
            )
        }
    }
}

@Composable
private fun IssueCard(issues: List<NpcEditIssue>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "提示（${issues.size} 处，不影响保存）",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            issues.forEach {
                Text(
                    text = "· ${it.message}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun <T> ChipGroup(
    options: List<T>,
    labelOf: (T) -> String,
    selected: Set<T>,
    onToggle: (T) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option in selected,
                onClick = { onToggle(option) },
                label = { Text(labelOf(option)) },
            )
        }
    }
}

/** 单选下拉（带"清除"），用于地点 / 最喜欢话题。 */
@Composable
private fun <T> DropdownField(
    value: String,
    options: List<T>,
    labelOf: (T) -> String,
    onPick: (T) -> Unit,
    onClear: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(value, modifier = Modifier.weight(1f))
            Text("▾")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("（清除）") },
                onClick = {
                    expanded = false
                    onClear()
                },
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(labelOf(option)) },
                    onClick = {
                        expanded = false
                        onPick(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun ScheduleRow(
    entry: NpcScheduleEntry,
    places: List<Place>,
    onChange: (NpcScheduleEntry) -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MinuteOfDayDropdown(
                    value = entry.startMinute,
                    onPick = { onChange(entry.copy(startMinute = it)) },
                )
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    var expanded by remember { mutableStateOf(false) }
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = places.firstOrNull { it.id == entry.placeId }?.name ?: "选择地点",
                            modifier = Modifier.weight(1f),
                        )
                        Text("▾")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        places.forEach { place ->
                            DropdownMenuItem(
                                text = { Text(place.name) },
                                onClick = {
                                    expanded = false
                                    onChange(entry.copy(placeId = place.id))
                                },
                            )
                        }
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = "删除这条作息",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            OutlinedTextField(
                value = entry.activity,
                onValueChange = { onChange(entry.copy(activity = it)) },
                label = { Text("在做什么") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("步行时间", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = entry.travelMinutes.toString(),
                    onValueChange = { v ->
                        v.filter { it.isDigit() }.toIntOrNull()?.let { minutes ->
                            onChange(entry.copy(travelMinutes = minutes))
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(110.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("分钟（从上一处走过来）", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** 24 小时 × 5 分钟步进的时间下拉；非 5 倍数的旧值也会保留，不丢数据。 */
@Composable
private fun MinuteOfDayDropdown(value: Int, onPick: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val minutes = (0..55 step 5).toList().let { if (value % 5 != 0) it + value else it }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text("%02d:%02d".format(value / 60, value % 60))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            minutes.forEach { minute ->
                DropdownMenuItem(
                    text = { Text("%02d:%02d".format(minute / 60, minute % 60)) },
                    onClick = {
                        expanded = false
                        onPick(minute)
                    },
                )
            }
        }
    }
}

@Composable
private fun ResourcePickerRow(
    label: String,
    ids: List<String>,
    resources: List<ResourceDefinition>,
    onClick: () -> Unit,
) {
    val names = ids.mapNotNull { id -> resources.firstOrNull { it.id == id }?.name }
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (names.isEmpty()) "$label：未选择" else "$label：${names.joinToString("、")}",
            modifier = Modifier.weight(1f),
        )
        Text("选")
    }
}

@Composable
private fun ResourcePickerDialog(
    title: String,
    resources: List<ResourceDefinition>,
    selectedIds: Set<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(selectedIds) }
    val filtered = remember(query, resources) {
        if (query.isBlank()) {
            resources
        } else {
            resources.filter {
                it.name.contains(query, ignoreCase = true) || it.id.contains(query, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$title（已选 ${selected.size}）") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("搜索资源名 / id") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(filtered, key = { it.id }) { resource ->
                        val checked = resource.id in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (checked) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                )
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = resource.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = {
                                    selected = if (checked) selected - resource.id else selected + resource.id
                                },
                            ) { Text(if (checked) "已选" else "选") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected.toList()) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun EditorTopBar(
    title: String,
    onBack: () -> Unit,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = "返回",
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}
