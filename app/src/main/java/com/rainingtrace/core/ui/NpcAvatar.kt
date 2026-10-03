package com.rainingtrace.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rainingtrace.core.ui.theme.NpcAvatarPalette
import com.rainingtrace.domain.art.ArtPaths

/**
 * NPC 头像：优先用美术图（`art/npc/<npcId>.png`），缺图回退成"色块 + 姓氏字形"。
 *
 * 回退色由 `npcId` 稳定映射，同一个人到哪都是同一个色。
 */
@Composable
fun NpcAvatar(
    npcId: String,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    ArtIcon(
        path = ArtPaths.npcAvatar(npcId),
        contentDescription = null,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 4)),
        fallback = { NpcAvatarFallback(npcId = npcId, name = name, size = size) },
    )
}

@Composable
private fun NpcAvatarFallback(npcId: String, name: String, size: Dp) {
    val color = NpcAvatarPalette[npcId.hashCode().mod(NpcAvatarPalette.size)]
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 4))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.take(1),
            color = Color.White,
            fontSize = (size.value * 0.4f).sp,
            fontWeight = FontWeight.Medium,
        )
    }
}