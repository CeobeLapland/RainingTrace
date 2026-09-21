package com.rainingtrace.domain.npc

/**
 * NPC 愿意聊的话题。
 *
 * 每个值都要能从玩家消息里的中文关键词命中（见 `RuleBasedNpcMessageParser`），
 * 并且都要有对应的接话台词——否则话题匹配上了却无话可说。
 */
enum class NpcTopic(val label: String) {
    BOOKS("书与阅读"),
    ART("画画"),
    RUNNING("跑步"),
    FOOD("吃与食堂"),
    WEATHER("天气"),
    NIGHT("夜里的事"),
    PLANTS("草木果子"),

    /** 关于他自己：作息、住在哪、在做什么。 */
    SELF("关于他自己"),
}
