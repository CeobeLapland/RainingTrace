package com.rainingtrace.feature.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rainingtrace.domain.npc.NpcMessage
import com.rainingtrace.domain.npc.NpcMessageRepository
import com.rainingtrace.domain.npc.NpcPresence
import com.rainingtrace.domain.npc.NpcPresenceUseCase
import com.rainingtrace.domain.npc.NpcProfile
import com.rainingtrace.domain.npc.NpcRepository
import com.rainingtrace.domain.world.WorldStateProvider
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 会话列表的一行。档案、此刻状态、最后一条消息都在 VM 里拼好，
 * UI 不做业务计算（与 InventoryScreen 同一口径）。
 */
data class ConversationRow(
    val profile: NpcProfile,
    val presence: NpcPresence?,
    val lastMessage: NpcMessage?,
    val unreadCount: Int,
)

data class MessagesUiState(
    val rows: List<ConversationRow> = emptyList(),
    /** 今天（来自世界状态，UI 不用自己取时间）。 */
    val today: LocalDate = LocalDate.MIN,
    val loading: Boolean = true,
)

/**
 * 消息 Tab：会话列表。
 *
 * 列表里每个人都会显示**此刻在「X」· 在做什么**——这是"他们有自己的生活"
 * 最直接的证据，也是不用聊天就能感受到的那部分。
 */
class MessagesViewModel(
    private val npcRepository: NpcRepository,
    private val npcPresence: NpcPresenceUseCase,
    messageRepository: NpcMessageRepository,
    worldState: WorldStateProvider,
) : ViewModel() {

    /** NPC 档案是只读配置，取一次就够。 */
    private val profiles = MutableStateFlow<List<NpcProfile>>(emptyList())

    init {
        viewModelScope.launch { profiles.value = npcRepository.all() }
    }

    val uiState: StateFlow<MessagesUiState> = combine(
        profiles,
        messageRepository.observeConversations(),
        worldState.state,
    ) { profiles, conversations, world ->
        if (profiles.isEmpty()) return@combine MessagesUiState()
        val byNpc = conversations.associateBy { it.npcId }
        val presences = npcPresence.presencesAt(world).associateBy { it.npcId }
        MessagesUiState(
            rows = profiles
                .map { profile ->
                    ConversationRow(
                        profile = profile,
                        presence = presences[profile.id],
                        lastMessage = byNpc[profile.id]?.lastMessage,
                        unreadCount = byNpc[profile.id]?.unreadCount ?: 0,
                    )
                }
                // 聊过的按最近说话排前面，没聊过的按名字排后面。
                .sortedWith(
                    compareByDescending<ConversationRow> {
                        it.lastMessage?.createdAtEpochMs ?: Long.MIN_VALUE
                    }.thenBy { it.profile.name },
                ),
            loading = false,
            today = world.localDate,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MessagesUiState())
}
