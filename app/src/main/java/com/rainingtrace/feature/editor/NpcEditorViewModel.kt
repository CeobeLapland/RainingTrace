package com.rainingtrace.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rainingtrace.domain.content.ContentPanel
import com.rainingtrace.domain.inventory.ResourceDefinition
import com.rainingtrace.domain.map.Place
import com.rainingtrace.domain.map.PlaceRepository
import com.rainingtrace.domain.npc.NpcContentWriter
import com.rainingtrace.domain.npc.NpcEditDraft
import com.rainingtrace.domain.npc.NpcEditIssue
import com.rainingtrace.domain.npc.NpcProfile
import com.rainingtrace.domain.npc.NpcRepository
import com.rainingtrace.domain.npc.NpcSaveResult
import com.rainingtrace.domain.npc.validateNpcDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NpcEditorUiState(
    val query: String = "",
    val npcs: List<NpcProfile> = emptyList(),
    val placeOptions: List<Place> = emptyList(),
    val resourceOptions: List<ResourceDefinition> = emptyList(),
    /** null = 列表页；非 null = 编辑这一位。 */
    val editing: NpcEditDraft? = null,
    /** 已写入覆盖层的 NPC id：列表里标出来，避免"内置更新了却没生效"的困惑。 */
    val overriddenIds: Set<String> = emptySet(),
    val issues: List<NpcEditIssue> = emptyList(),
    val message: String? = null,
) {
    val filteredNpcs: List<NpcProfile>
        get() = if (query.isBlank()) {
            npcs
        } else {
            npcs.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.id.contains(query, ignoreCase = true) ||
                    it.role.contains(query, ignoreCase = true)
            }
        }
}

/**
 * 内容工作台（NPC）：列表 → 编辑 → 校验 → 写覆盖层 → 重载。
 *
 * 编辑状态是 [NpcEditDraft]（**无 require**），所以表单可以处在"中途非法"的状态
 * （例如取消话题勾选）；保存时再 `toProfile()`。
 */
class NpcEditorViewModel(
    private val npcRepository: NpcRepository,
    private val placeRepository: PlaceRepository,
    private val contentPanel: ContentPanel,
    private val writer: NpcContentWriter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NpcEditorUiState())
    val uiState: StateFlow<NpcEditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { refreshOptions() }
    }

    private suspend fun refreshOptions() {
        val npcs = npcRepository.all().sortedBy { it.name }
        val places = placeRepository.all().sortedBy { it.name }
        val resources = contentPanel.index.value.resources.sortedBy { it.name }
        val overridden = writer.overriddenIds()
        _uiState.update {
            it.copy(
                npcs = npcs,
                placeOptions = places,
                resourceOptions = resources,
                overriddenIds = overridden,
            )
        }
    }

    fun onQueryChange(query: String) = _uiState.update { it.copy(query = query) }

    fun select(npc: NpcProfile) {
        val draft = NpcEditDraft.from(npc)
        _uiState.update {
            it.copy(editing = draft, issues = issuesFor(draft, it), message = null)
        }
    }

    /** 返回列表页。未保存的改动丢弃（保存是显式动作）。 */
    fun backToList() = _uiState.update { it.copy(editing = null, issues = emptyList(), message = null) }

    fun edit(transform: (NpcEditDraft) -> NpcEditDraft) {
        val current = _uiState.value.editing ?: return
        val next = transform(current)
        _uiState.update { it.copy(editing = next, issues = issuesFor(next, it)) }
    }

    fun save() {
        val draft = _uiState.value.editing ?: return
        // 校验只做提示，**不阻止保存**：内容可以半成品，空字段/悬空引用以后再填。
        // 唯一真正拦人的是"连领域对象都构造不出来"（例如 id 丢失）。
        val profile = draft.toProfile()
        if (profile == null) {
            _uiState.update { it.copy(message = "内容不完整到无法构造，检查 id 是否为空。") }
            return
        }
        viewModelScope.launch {
            when (val result = writer.save(profile)) {
                is NpcSaveResult.Saved -> {
                    refreshOptions()
                    _uiState.update {
                        it.copy(
                            npcs = npcsLatest(),
                            editing = draft,
                            issues = emptyList(),
                            message = "已写入覆盖层并重新读取内容，地图与档案页立刻生效。",
                        )
                    }
                }

                is NpcSaveResult.Invalid -> _uiState.update {
                    it.copy(issues = result.issues, message = "还有 ${result.issues.size} 处问题，改完再保存。")
                }

                is NpcSaveResult.Failed -> _uiState.update {
                    it.copy(message = "保存失败：${result.message}")
                }
            }
        }
    }

    /** 丢弃这一位的覆盖层改动，回到内置内容。 */
    fun resetToBuiltIn() {
        val current = _uiState.value.editing ?: return
        viewModelScope.launch {
            when (val result = writer.resetToBuiltIn(current.id)) {
                is NpcSaveResult.Saved -> {
                    refreshOptions()
                    _uiState.update {
                        it.copy(
                            npcs = npcsLatest(),
                            editing = null,
                            issues = emptyList(),
                            message = "已清除这一位的覆盖层改动，回到内置内容。",
                        )
                    }
                }

                is NpcSaveResult.Failed -> _uiState.update {
                    it.copy(message = "清除失败：${result.message}")
                }

                is NpcSaveResult.Invalid -> Unit
            }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    private suspend fun npcsLatest(): List<NpcProfile> =
        npcRepository.all().sortedBy { it.name }

    private fun issuesFor(draft: NpcEditDraft, state: NpcEditorUiState): List<NpcEditIssue> =
        validateNpcDraft(
            draft = draft,
            placeIds = state.placeOptions.map { it.id }.toSet(),
            resourceIds = state.resourceOptions.map { it.id }.toSet(),
        )
}
