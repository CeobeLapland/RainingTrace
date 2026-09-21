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

/**
 * NPC 头像：色块 + 姓氏字形。
 *
 * 还没有立绘资源，先和 `PlaceThumb` 用同一种占位做法；将来有图只需改这一个组件。
 * 颜色由 `npcId` 稳定映射，同一个人到哪都是同一个色。
 */
@Composable
fun NpcAvatar(
    npcId: String,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val color = NpcAvatarPalette[npcId.hashCode().mod(NpcAvatarPalette.size)]
    Box(
        modifier = modifier
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
