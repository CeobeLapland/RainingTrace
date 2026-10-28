package com.rainingtrace.data.content

import android.content.Context
import com.rainingtrace.domain.content.ContentReport
import com.rainingtrace.domain.npc.NpcContentWriter
import com.rainingtrace.domain.npc.NpcProfile
import com.rainingtrace.domain.npc.NpcSaveResult

/**
 * NPC 内容工作台的保存实现：**只写被编辑的那一位**到覆盖层。
 *
 * 覆盖层的语义是"内置 assets + 覆盖层按 id 合并"，所以：
 * - 保存一位 → 覆盖层里加/换一条，其它 NPC 完全不动；
 * - 重置 → 从覆盖层删掉这一条（覆盖层空了就删文件）；
 * - 写完调 `reload()`，地图/聊天/档案页立刻读到新内容，不用重启。
 *
 * 校验在写入前做（`validateNpcEdit`）：宁可拒绝保存，也不写入一份会静默失效的内容。
 */
class OverlayNpcContentWriter(
    private val context: Context,
    private val contentStore: ContentStore,
) : NpcContentWriter {

    override suspend fun save(profile: NpcProfile): NpcSaveResult {
        // 校验已经由编辑页在草稿阶段做完（NpcEditDraft + validateNpcDraft），
        // 这里只写文件；领域对象的 require 已经在 toProfile 时守住。
        return runCatching {
            val merged = (readOverlay().filterNot { it.id == profile.id } + profile)
                .sortedBy { it.id }
            writeOverlay(merged)
            contentStore.reload()
        }.fold(
            onSuccess = { NpcSaveResult.Saved },
            onFailure = { NpcSaveResult.Failed(it.message ?: "写入覆盖层失败") },
        )
    }

    override suspend fun resetToBuiltIn(npcId: String): NpcSaveResult = runCatching {
        val remaining = readOverlay().filterNot { it.id == npcId }
        writeOverlay(remaining)
        contentStore.reload()
    }.fold(
        onSuccess = { NpcSaveResult.Saved },
        onFailure = { NpcSaveResult.Failed(it.message ?: "清除覆盖层失败") },
    )

    override suspend fun overriddenIds(): Set<String> =
        runCatching { readOverlay().map { it.id }.toSet() }.getOrDefault(emptySet())

    private fun readOverlay(): List<NpcProfile> {
        val file = ContentStore.overlayFile(context, ContentStore.NPCS_FILE)
        if (!file.exists()) return emptyList()
        return decodeNpcEntries(
            text = file.readText(),
            file = "${ContentStore.NPCS_FILE}（覆盖）",
            report = ContentReport(),
        ).entries
    }

    private fun writeOverlay(profiles: List<NpcProfile>) {
        val file = ContentStore.overlayFile(context, ContentStore.NPCS_FILE)
        if (profiles.isEmpty()) {
            file.delete()
            return
        }
        file.parentFile?.mkdirs()
        file.writeText(encodeNpcOverlay(profiles))
    }
}
