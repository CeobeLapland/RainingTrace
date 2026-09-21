package com.rainingtrace.feature.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rainingtrace.domain.npc.NpcMessage
import com.rainingtrace.domain.npc.NpcMessageRepository
import com.rainingtrace.domain.npc.NpcPresence
import com.rainingtrace.domain.npc.NpcPresenceUseCase
import com.rainingtrace.domain.npc.NpcProfile
import com.rainingtrace.domain.npc.NpcRepository
import com.rainingtrace.domain.npc.NpcState
import com.rainingtrace.domain.npc.NpcStateRepository
import com.rainingtrace.domain.npc.RelationshipStage
import com.rainingtrace.domain.npc.SendMessageResult
import com.rainingtrace.domain.npc.SendNpcMessageUseCase
import com.rainingtrace.domain.settings.AppSettingsRepository
import com.rainingtrace.domain.world.WorldStateProvider
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatUiState(
    val profile: NpcProfile? = null,
    val messages: List<NpcMessage> = emptyList(),
    /** 他此刻在哪、在做什么；聊天页顶部那行副标题用它。 */
    val presence: NpcPresence? = null,
    val stage: RelationshipStage = RelationshipStage.STRANGER,
    val affection: Int = 0,
    val showAffection: Boolean = true,
    val today: LocalDate = LocalDate.MIN,
)

/**
 * 与单个 NPC 的聊天页。
 *
 * 草稿、发送中、提示各自一个流，和线程流分开——它们变化频繁，
 * 没必要每次都把整条消息列表重新组装一遍。
 */
class ChatViewModel(
    private val npcId: String,
    private val npcRepository: NpcRepository,
    private val npcPresence: NpcPresenceUseCase,
    private val messageRepository: NpcMessageRepository,
    stateRepository: NpcStateRepository,
    private val sendNpcMessage: SendNpcMessageUseCase,
    worldState: WorldStateProvider,
    settings: AppSettingsRepository,
) : ViewModel() {

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    private val profile = MutableStateFlow<NpcProfile?>(null)

    init {
        viewModelScope.launch { profile.value = npcRepository.byId(npcId) }
        // 进会话即视为已读（底栏红点跟着 Room Flow 自动消失）。
        viewModelScope.launch { messageRepository.markRead(npcId) }
    }

    val uiState: StateFlow<ChatUiState> = combine(
        profile,
        messageRepository.observeThread(npcId),
        stateRepository.observeStates(),
        worldState.state,
        settings.npcMessages,
    ) { profile, messages, states, world, messageSettings ->
        val state = states[npcId] ?: NpcState.initial(npcId)
        ChatUiState(
            profile = profile,
            messages = messages,
            presence = npcPresence.presenceOf(npcId, world),
            stage = state.stage,
            affection = state.affection,
            showAffection = messageSettings.showAffection,
            today = world.localDate,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    fun onTextChanged(text: String) {
        _draft.value = text
    }

    fun onSend() {
        val text = _draft.value.trim()
        if (text.isEmpty() || _sending.value) return
        _sending.value = true
        viewModelScope.launch {
            when (val result = sendNpcMessage(npcId, text)) {
                is SendMessageResult.Sent -> _draft.value = ""
                SendMessageResult.Empty -> Unit
                SendMessageResult.TooLong -> _toast.value = "太长了，说短一点"
                SendMessageResult.UnknownNpc -> _toast.value = "找不到这个人"
            }
            _sending.value = false
        }
    }

    fun consumeToast() {
        _toast.value = null
    }
}
