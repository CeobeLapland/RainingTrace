package com.rainingtrace.feature.messages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rainingtrace.R
import com.rainingtrace.core.common.AppContainer

/**
 * 聊天子页面：从消息列表进入，自带返回顶栏（子路由不显示主外壳底栏）。
 *
 * npcId 走导航参数而不是"一次性请求对象"：这是标准的主从导航，
 * 用请求对象反而要额外处理 consume 时机，而且进程被回收后参数也恢复不了。
 */
@Composable
fun ChatRoute(
    container: AppContainer,
    npcId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onBack() }

    val chatViewModel: ChatViewModel = viewModel(key = npcId) {
        ChatViewModel(
            npcId = npcId,
            npcRepository = container.npcRepository,
            npcPresence = container.npcPresence,
            messageRepository = container.npcMessageRepository,
            stateRepository = container.npcStateRepository,
            sendNpcMessage = container.sendNpcMessage,
            worldState = container.worldStateProvider,
            settings = container.settingsRepository,
        )
    }
    val uiState by chatViewModel.uiState.collectAsStateWithLifecycle()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(48.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_back),
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = uiState.profile?.name ?: "消息",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            ChatScreen(viewModel = chatViewModel, modifier = Modifier.weight(1f))
        }
    }
}
