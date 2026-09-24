package com.rainingtrace.data.repository

import com.rainingtrace.domain.npc.NpcProfile
import com.rainingtrace.domain.npc.NpcRepository

/**
 * 内存实现：NPC 全部来自构造参数。
 *
 * 内容本体住在 `assets/content/npcs.json`（+ 私有目录覆盖层）。
 * 原来这里用 `FakePlaceRepository.XXX.id` 常量做编译期绑定，防止作息里的
 * placeId 写错被静默剔除；外置之后这份保证由 `ContentValidator.validNpcs`
 * （运行时诊断）与 `ShippedContentTest`（构建前）接住。
 */
class FakeNpcRepository(
    private val npcs: List<NpcProfile>,
) : NpcRepository {

    private val byId: Map<String, NpcProfile> = npcs.associateBy { it.id }

    override suspend fun all(): List<NpcProfile> = npcs

    override suspend fun byId(id: String): NpcProfile? = byId[id]
}