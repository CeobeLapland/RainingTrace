package com.rainingtrace.domain.npc

/**
 * 真实 LLM 的插槽（N4）。
 *
 * domain 只认这个接口；换厂商 = 换一个实现（OpenAI / 通义 / 本地 llama.cpp 都行）。
 * 返回 null 表示"这次没拿到文本"（网络失败 / 超时 / 内容被拒），
 * 调用方（[LlmNarrativeService]）会回退到模板叙事——绝不假装听懂。
 */
interface LlmClient {
    suspend fun complete(request: LlmRequest): String?
}

/** 一次补全请求：system 是人格+状态+记忆，turns 是最近对话。 */
data class LlmRequest(
    val system: String,
    val turns: List<LlmTurn>,
)

data class LlmTurn(
    val role: String,
    val content: String,
)
