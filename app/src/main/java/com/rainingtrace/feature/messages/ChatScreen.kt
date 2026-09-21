package com.rainingtrace.feature.messages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rainingtrace.domain.npc.NpcMessage
import com.rainingtrace.domain.npc.RelationshipStage
import java.time.LocalDate
import kotlinx.coroutines.delay

/**
 * 聊天页正文：顶部一行"此刻在做什么" + 消息气泡 + 输入区。
 *
 * 副标题那一行很重要——它让"他是个活人"这件事在聊天页也成立，
 * 而不是一进会话就只剩下一堆气泡。
 */
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val sending by viewModel.sending.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PresenceHeader(uiState)

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (uiState.messages.isEmpty()) {
                    item {
                        EmptyChat(
                            name = uiState.profile?.name.orEmpty(),
                            stage = uiState.stage,
                        )
                    }
                }
                items(uiState.messages, key = { it.id }) { message ->
                    MessageBubble(message = message, today = uiState.today)
                }
            }

            InputBar(
                draft = draft,
                sending = sending,
                onTextChanged = viewModel::onTextChanged,
                onSend = viewModel::onSend,
            )
        }

        toast?.let { message ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            LaunchedEffect(message) {
                delay(2500)
                viewModel.consumeToast()
            }
        }
    }
}

@Composable
private fun PresenceHeader(uiState: ChatUiState) {
    val presence = uiState.presence
    val where = when {
        presence == null -> uiState.profile?.role.orEmpty()
        presence.walking -> "正在往「${presence.placeName}」走"
        presence.activity.isBlank() -> "此刻在「${presence.placeName}」"
        else -> "此刻在「${presence.placeName}」· ${presence.activity}"
    }
    val stage = if (uiState.showAffection) {
        "${uiState.stage.label} · 好感 ${uiState.affection}"
    } else {
        uiState.stage.label
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 4.dp),
    ) {
        if (where.isNotBlank()) {
            Text(
                text = where,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Text(
            text = stage,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MessageBubble(message: NpcMessage, today: LocalDate) {
    val fromNpc = message.fromNpc
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromNpc) Arrangement.Start else Arrangement.End,
    ) {
        Column(horizontalAlignment = if (fromNpc) Alignment.Start else Alignment.End) {
            Surface(
                color = if (fromNpc) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.widthIn(max = 280.dp),
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (fromNpc) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            Text(
                text = timeLabel(message.createdAtEpochMs, today),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
            )
        }
    }
}

@Composable
private fun EmptyChat(name: String, stage: RelationshipStage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (name.isBlank()) "你们还没说过话。" else "你还没跟${name}说过话。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (stage == RelationshipStage.STRANGER) {
                "在地图上走到他身边，或者先打个招呼。"
            } else {
                "他记得你。"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun InputBar(
    draft: String,
    sending: Boolean,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = onTextChanged,
                placeholder = {
                    Text("说点什么", style = MaterialTheme.typography.bodyMedium)
                },
                minLines = 1,
                maxLines = 4,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onSend,
                enabled = draft.isNotBlank() && !sending,
            ) {
                Text(if (sending) "…" else "发送")
            }
        }
    }
}
