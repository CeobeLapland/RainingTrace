package com.rainingtrace.domain.settings

/**
 * LLM 对话配置（OpenAI 兼容接口）。
 *
 * 只存本机（DataStore 私有目录），不上传。**未配置或关闭时，NPC 自动回退模板**，
 * 所以"没填 key"永远不等于"游戏坏了"。
 */
data class LlmSettings(
    val enabled: Boolean = false,
    /** 例如 `https://api.openai.com/v1` / `https://open.bigmodel.cn/api/paas/v4` / `http://192.168.1.2:11434/v1`。 */
    val baseUrl: String = DEFAULT_BASE_URL,
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
    /** 0.0–2.0；太高会跑人设，默认 0.8。 */
    val temperature: Double = 0.8,
) {
    /** 三项都填了才算可用（本地 Ollama 可以把 apiKey 填任意非空占位）。 */
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_MODEL = "gpt-4o-mini"
    }
}
