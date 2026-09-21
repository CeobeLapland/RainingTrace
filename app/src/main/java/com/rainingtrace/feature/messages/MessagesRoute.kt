package com.rainingtrace.feature.messages

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rainingtrace.core.common.AppContainer

/**
 * 消息 Tab 的入口：创建 VM 后交给 [MessagesScreen]。
 * 一级 Tab 不显示返回栏（外壳顶/底栏由 shell 统一提供）。
 */
@Composable
fun MessagesRoute(
    container: AppContainer,
    onOpenChat: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val messagesViewModel: MessagesViewModel = viewModel {
        MessagesViewModel(
            npcRepository = container.npcRepository,
            npcPresence = container.npcPresence,
            messageRepository = container.npcMessageRepository,
            worldState = container.worldStateProvider,
        )
    }
    MessagesScreen(
        viewModel = messagesViewModel,
        onOpenChat = onOpenChat,
        modifier = modifier,
    )
}
