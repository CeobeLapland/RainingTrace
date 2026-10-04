package com.rainingtrace.domain.npc

/**
 * 人物设定（LLM system prompt 的"人格块"）。
 *
 * [personaFor] 优先用档案里的手写 `personaPrompt`（可配置）；
 * 没写就用 [defaultPersona] 从结构化字段自动组装——默认即有内容，不会因为
 * 某个 NPC 没手写就变成"没有性格"。
 */
object NpcPersonaPrompt {

    /**
     * 手写覆盖优先；否则自动组装。
     * 组装只用档案里的真实字段，不让 LLM 凭空想象设定。
     */
    fun personaFor(profile: NpcProfile): String =
        profile.personaPrompt?.takeIf { it.isNotBlank() } ?: defaultPersona(profile)

    fun defaultPersona(profile: NpcProfile): String = buildString {
        append("你是${profile.name}。")
        if (profile.role.isNotBlank()) append(profile.role.trimEnd('。')).append('。')
        if (profile.oneLiner.isNotBlank()) append("认识你的人会说：").append(profile.oneLiner).append('。')
        if (profile.traits.isNotEmpty()) {
            append("性格：").append(profile.traits.joinToString("、") { it.label }).append('。')
        }
        profile.favoriteTopic?.let {
            append("你最喜欢聊").append(it.label).append('。')
        }
        if (profile.giftPreferences.liked.isNotEmpty()) {
            append("你收到这些会高兴：").append(profile.giftPreferences.liked.joinToString("、")).append('。')
        }
        if (profile.giftPreferences.disliked.isNotEmpty()) {
            append("这些会勾起不好的事：").append(profile.giftPreferences.disliked.joinToString("、")).append('。')
        }
        if (profile.backstory.isNotEmpty()) {
            append("关于你的往事：").append(profile.backstory.joinToString("；")).append('。')
        }
    }
}
