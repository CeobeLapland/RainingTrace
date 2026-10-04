package com.rainingtrace.domain.npc

/**
 * 单次 LLM 请求的上下文预算（第三道闸，第一道是记忆封顶、第二道是最近消息回看）。
 *
 * 中文按"1 字 ≈ 1 token"保守估；超预算就丢掉优先级最低的部分，保证**上下文永远有界**。
 */
object PromptBudget {

    /** 上下文总预算（字符）。约 1200–1800 token，留足给 model 输出的余量。 */
    const val MAX_CHARS = 2400

    /**
     * 依次塞 persona → 记忆 → 最近消息，预算耗尽即停。
     *
     * - persona 是人格，永不丢，最多被截尾；
     * - 记忆行调用方已按重要性降序排好，超了就丢尾部（最低优先级的先丢）；
     * - 最近消息**新的优先**（对话连续性比旧消息重要），内部保持时间升序。
     */
    fun trimToBudget(
        persona: String,
        memoryLines: List<String>,
        recentMessages: List<String>,
        maxChars: Int = MAX_CHARS,
    ): TrimmedPrompt {
        var budget = maxChars

        val keptPersona = persona.take(budget)
        budget -= keptPersona.length

        val keptMemory = mutableListOf<String>()
        for (line in memoryLines) {
            val cost = line.length + 4
            if (budget - cost < 0) break
            keptMemory += line
            budget -= cost
        }

        val newestFirst = mutableListOf<String>()
        for (msg in recentMessages.asReversed()) {
            val cost = msg.length + 6
            if (budget - cost < 0) break
            newestFirst += msg
            budget -= cost
        }

        return TrimmedPrompt(keptPersona, keptMemory, newestFirst.asReversed())
    }
}

/** 裁剪后的三段；顺序 = 拼接顺序（persona → 记忆 → 最近消息）。 */
data class TrimmedPrompt(
    val persona: String,
    val memory: List<String>,
    val recent: List<String>,
) {
    /** 给 token 计数/调试用的总量。 */
    val totalChars: Int
        get() = persona.length + memory.sumOf { it.length + 4 } + recent.sumOf { it.length + 6 }
}
