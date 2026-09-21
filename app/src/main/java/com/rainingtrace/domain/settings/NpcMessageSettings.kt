package com.rainingtrace.domain.settings

/** 他们主动找你的频率。 */
enum class ProactiveLevel(val label: String, val dailyLimit: Int) {
    /** 一条都不发，你去找他们就好。 */
    QUIET("不打扰", 0),
    NORMAL("正常", 2),
    ACTIVE("活跃", 5),
    ;

    companion object {
        val DEFAULT = NORMAL

        fun fromKey(key: String): ProactiveLevel = entries.firstOrNull { it.name == key } ?: DEFAULT
    }
}

/**
 * 消息相关的玩家偏好。
 *
 * [showAffection] 关掉后只在关系变化时给一句提示，不显示数值——
 * 有人喜欢看数字，有人觉得破坏气氛。
 */
data class NpcMessageSettings(
    val proactiveLevel: ProactiveLevel = ProactiveLevel.DEFAULT,
    val showAffection: Boolean = true,
)
