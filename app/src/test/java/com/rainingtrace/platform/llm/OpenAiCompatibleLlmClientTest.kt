package com.rainingtrace.platform.llm

import com.rainingtrace.domain.npc.LlmRequest
import com.rainingtrace.domain.npc.LlmTurn
import com.rainingtrace.domain.settings.LlmSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OpenAI 兼容客户端的**纯函数部分**：地址拼装、请求体、响应解析。
 *
 * 网络本身不做单测（数据层/平台层历来不测），所以把胶水压成纯函数，
 * 让"厂商格式差异"这类问题在构建前就能被发现。
 */
class OpenAiCompatibleLlmClientTest {

    private val config = LlmSettings(
        enabled = true,
        baseUrl = "https://api.openai.com/v1",
        apiKey = "sk-test",
        model = "gpt-4o-mini",
        temperature = 0.8,
    )

    @Test
    fun `baseUrl 只填到 v1 时自动补 chat completions`() {
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            OpenAiCompatibleLlmClient.endpoint("https://api.openai.com/v1"),
        )
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            OpenAiCompatibleLlmClient.endpoint("https://api.openai.com/v1/"),
        )
    }

    @Test
    fun `已经填了完整地址时不重复拼接`() {
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            OpenAiCompatibleLlmClient.endpoint("https://api.openai.com/v1/chat/completions"),
        )
    }

    @Test
    fun `请求体包含 system 与全部对话轮次`() {
        val request = LlmRequest(
            system = "你是熊二。",
            turns = listOf(
                LlmTurn("user", "熊二，你哥呢"),
                LlmTurn("assistant", "熊大啊，俺也不知道"),
            ),
        )

        val body = Json.parseToJsonElement(
            OpenAiCompatibleLlmClient.buildRequestBody(config, request),
        ).jsonObject

        assertEquals("gpt-4o-mini", body["model"]?.jsonPrimitive?.content)
        val messages = body["messages"]!!.jsonArray
        assertEquals(3, messages.size)
        assertEquals("system", messages[0].jsonObject["role"]?.jsonPrimitive?.content)
        assertEquals("你是熊二。", messages[0].jsonObject["content"]?.jsonPrimitive?.content)
        assertEquals("user", messages[1].jsonObject["role"]?.jsonPrimitive?.content)
        assertEquals("assistant", messages[2].jsonObject["role"]?.jsonPrimitive?.content)
        assertTrue(
            "必须限制回复长度（省钱、也符合人设要的短句）",
            (body["max_tokens"]?.jsonPrimitive?.content?.toInt() ?: 0) in 1..512,
        )
    }

    @Test
    fun `解析标准 choices 格式`() {
        val body = """
            {"choices":[{"message":{"role":"assistant","content":"  俺要吃蜂蜜  "}}]}
        """.trimIndent()

        assertEquals("俺要吃蜂蜜", OpenAiCompatibleLlmClient.parseCompletion(body))
    }

    @Test
    fun `空内容与异常 JSON 一律返回 null`() {
        assertNull(OpenAiCompatibleLlmClient.parseCompletion("""{"choices":[]}"""))
        assertNull(OpenAiCompatibleLlmClient.parseCompletion("""{"choices":[{"message":{"content":null}}]}"""))
        assertNull(OpenAiCompatibleLlmClient.parseCompletion("""{"choices":[{"message":{"content":"   "}}]}"""))
        assertNull(OpenAiCompatibleLlmClient.parseCompletion("""{"error":"invalid key"}"""))
        assertNull(OpenAiCompatibleLlmClient.parseCompletion("not json"))
    }
}
