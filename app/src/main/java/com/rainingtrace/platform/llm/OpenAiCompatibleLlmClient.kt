package com.rainingtrace.platform.llm

import com.rainingtrace.domain.npc.LlmClient
import com.rainingtrace.domain.npc.LlmRequest
import com.rainingtrace.domain.settings.AppSettingsRepository
import com.rainingtrace.domain.settings.LlmSettings
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * OpenAI 兼容的 LLM 客户端（`POST {baseUrl}/chat/completions`）。
 *
 * 兼容范围：OpenAI / DeepSeek / 智谱 / 通义（兼容模式）/ Ollama 等一切
 * "Bearer + messages + choices[].message.content" 的接口。
 *
 * 三条原则：
 * 1. **每次请求现读设置**：设置页改完立刻生效，不用重启；
 * 2. **失败一律返回 null**（网络/超时/非 2xx/JSON 变形），由 `LlmNarrativeService`
 *    回退模板——绝不假装听懂；
 * 3. **超时给够**（30s）：LLM 首 token 慢是常态，别用天气接口的 8s 去卡它。
 */
class OpenAiCompatibleLlmClient(
    private val client: HttpClient,
    private val settings: AppSettingsRepository,
) : LlmClient {

    override suspend fun complete(request: LlmRequest): String? = runCatching {
        val config = settings.currentLlm()
        if (!config.enabled || !config.isConfigured) return null

        val response = client.post(endpoint(config.baseUrl)) {
            header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
            contentType(ContentType.Application.Json)
            setBody(buildRequestBody(config, request))
        }
        if (response.status.value !in 200..299) return null
        parseCompletion(response.bodyAsText())
    }.getOrNull()

    companion object {

        /** 容错：用户可能填了带 `/chat/completions` 的完整地址，也可能只填到 `/v1`。 */
        fun endpoint(baseUrl: String): String {
            val trimmed = baseUrl.trim().trimEnd('/')
            return if (trimmed.endsWith("/chat/completions")) trimmed else "$trimmed/chat/completions"
        }

        /** 请求体（纯函数，可单测）：`messages` 第一条是 system，其余按时间顺序。 */
        fun buildRequestBody(config: LlmSettings, request: LlmRequest): String {
            val messages = buildJsonArray {
                add(
                    buildJsonObject {
                        put("role", "system")
                        put("content", request.system)
                    },
                )
                request.turns.forEach { turn ->
                    add(
                        buildJsonObject {
                            put("role", turn.role)
                            put("content", turn.content)
                        },
                    )
                }
            }
            val body = buildJsonObject {
                put("model", config.model)
                put("messages", messages)
                put("temperature", config.temperature)
                // 回复要短：人设要求"1–3 句"，给 200 token 足够，也省费用。
                put("max_tokens", MAX_TOKENS)
            }
            return Json.encodeToString(JsonObject.serializer(), body)
        }

        /** 解析 `choices[0].message.content`；变形/空内容一律 null。 */
        fun parseCompletion(body: String): String? = runCatching {
            val root = Json.parseToJsonElement(body).jsonObject
            val choices: JsonArray? = root["choices"]?.jsonArray
            val content = choices
                ?.firstOrNull()
                ?.jsonObject
                ?.get("message")
                ?.jsonObject
                ?.get("content")
                ?.jsonPrimitive
                ?.contentOrNull
            content?.trim()?.takeIf { it.isNotEmpty() }
        }.getOrNull()

        /** 给回复留的 token 上限。 */
        const val MAX_TOKENS = 220
    }
}
