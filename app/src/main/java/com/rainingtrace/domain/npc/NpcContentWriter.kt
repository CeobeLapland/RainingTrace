package com.rainingtrace.domain.npc

/**
 * NPC 编辑结果的保存（内容工作台）。
 *
 * 只保存**被编辑的那一位**：内容是"内置 assets + 覆盖层按 id 合并"，
 * 所以覆盖层里写一条就够，不必导出整份 JSON。
 */
interface NpcContentWriter {

    /** 保存一位 NPC 到覆盖层并重载内容。 */
    suspend fun save(profile: NpcProfile): NpcSaveResult

    /** 丢弃这一位的覆盖层改动，回到内置内容。 */
    suspend fun resetToBuiltIn(npcId: String): NpcSaveResult

    /**
     * 覆盖层里有哪些 NPC id。
     *
     * 列表用它标"已覆盖"：覆盖层优先级高于内置 assets，以后更新内置内容时，
     * 这些 id 会继续用覆盖层的旧版本——必须让人看得见。
     */
    suspend fun overriddenIds(): Set<String>
}

sealed interface NpcSaveResult {
    object Saved : NpcSaveResult
    data class Invalid(val issues: List<NpcEditIssue>) : NpcSaveResult
    data class Failed(val message: String) : NpcSaveResult
}

/** 编辑时的一条校验问题；[field] 用于在表单里定位（也可以只展示成清单）。 */
data class NpcEditIssue(
    val field: String,
    val message: String,
)

/**
 * 编辑中的 NPC 草稿：**不做任何 require**。
 *
 * 表单必须允许"中途非法"（例如取消勾选一个话题后，favoriteTopic 暂时不在 topics 里），
 * 而 [NpcProfile] 的 init 会直接抛异常。所以编辑用这份无校验状态，
 * 保存前再 [toProfile] 转回领域对象。
 */
data class NpcEditDraft(
    val id: String,
    val name: String,
    val oneLiner: String,
    val role: String,
    val traits: Set<NpcTrait>,
    val topics: Set<NpcTopic>,
    val favoriteTopic: NpcTopic?,
    val backstory: List<String>,
    val giftLiked: List<String>,
    val giftDisliked: List<String>,
    val homePlaceId: String?,
    val enabled: Boolean,
    val personaPrompt: String?,
    val schedule: List<NpcScheduleEntry>,
    val scheduleVariants: List<NpcScheduleVariant>,
) {
    /** 转回领域对象；会顺手清掉"半成品"导致的非法组合（favorite 不在 topics）。 */
    fun toProfile(): NpcProfile? = runCatching {
        NpcProfile(
            id = id,
            name = name,
            oneLiner = oneLiner,
            schedule = schedule,
            scheduleVariants = scheduleVariants,
            role = role,
            traits = traits,
            topics = topics,
            // 填了 favorite 但没填 topics 是半成品常态：保存时清掉这个字段，不阻止保存。
            favoriteTopic = favoriteTopic?.takeIf { it in topics },
            backstory = backstory,
            giftPreferences = GiftPreferences(liked = giftLiked, disliked = giftDisliked),
            homePlaceId = homePlaceId,
            enabled = enabled,
            personaPrompt = personaPrompt,
        )
    }.getOrNull()

    companion object {
        fun from(profile: NpcProfile): NpcEditDraft = NpcEditDraft(
            id = profile.id,
            name = profile.name,
            oneLiner = profile.oneLiner,
            role = profile.role,
            traits = profile.traits,
            topics = profile.topics,
            favoriteTopic = profile.favoriteTopic,
            backstory = profile.backstory,
            giftLiked = profile.giftPreferences.liked,
            giftDisliked = profile.giftPreferences.disliked,
            homePlaceId = profile.homePlaceId,
            enabled = profile.enabled,
            personaPrompt = profile.personaPrompt,
            schedule = profile.schedule,
            scheduleVariants = profile.scheduleVariants,
        )
    }
}

/**
 * 实时校验（纯函数）：编辑时就能看见问题，而不是保存后才发现"静默失效"。
 *
 * 与 `ContentValidator` 同口径，但**不丢弃内容**——这里只报告，保存前由用户改。
 */
fun validateNpcDraft(
    draft: NpcEditDraft,
    placeIds: Set<String>,
    resourceIds: Set<String>,
): List<NpcEditIssue> = buildList {
    // name / oneLiner 为空**不算问题**：名字没想好时 UI 会用 id 显示。
    // 这里只提示"会静默失效"的引用问题，不拦保存。

    val favorite = draft.favoriteTopic
    if (favorite != null && favorite !in draft.topics) {
        add(NpcEditIssue("favoriteTopic", "最喜欢的话题「${favorite.label}」不在话题里（保存时会自动清空）"))
    }

    draft.schedule.forEachIndexed { index, entry ->
        if (entry.placeId !in placeIds) {
            add(
                NpcEditIssue(
                    "schedule[$index]",
                    "第 ${index + 1} 条作息的地点「${entry.placeId}」不存在（这条不会生效）",
                ),
            )
        }
        if (entry.startMinute !in 0 until 24 * 60) {
            add(NpcEditIssue("schedule[$index]", "第 ${index + 1} 条作息的时间越界（需 0–1439）"))
        }
        if (entry.travelMinutes < 0) {
            add(NpcEditIssue("schedule[$index]", "第 ${index + 1} 条作息的步行时间不能为负"))
        }
    }

    val home = draft.homePlaceId
    if (home != null && home !in placeIds) {
        add(NpcEditIssue("homePlaceId", "家的地点「$home」不存在"))
    }

    (draft.giftLiked + draft.giftDisliked)
        .filterNot { it in resourceIds }
        .distinct()
        .forEach { id ->
            add(NpcEditIssue("giftPreferences", "礼物引用的资源「$id」不存在"))
        }

    draft.scheduleVariants.forEach { variant ->
        variant.schedule.forEach { entry ->
            if (entry.placeId !in placeIds) {
                add(
                    NpcEditIssue(
                        "scheduleVariants.${variant.id}",
                        "变体「${variant.label}」引用了不存在的地点「${entry.placeId}」",
                    ),
                )
            }
        }
    }
}
