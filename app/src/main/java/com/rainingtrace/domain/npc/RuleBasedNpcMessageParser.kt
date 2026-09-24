package com.rainingtrace.domain.npc

/**
 * 中文关键词表。规则解析的全部"知识"都在这里，所以它是**内容**：
 * 开发者模式改 `npc_keywords.json` 就生效，不用改代码。
 *
 * 注意顺序：**具体的要排在笼统的前面**（"明天下午"先于"明天"），
 * 因为匹配取第一个命中。
 */
data class NpcKeywordRules(
    val topics: Map<NpcTopic, List<String>>,
    val times: List<Pair<String, TimeHint>>,
    val meet: List<String>,
    val scheduleQuestions: List<String>,
    val questionMarkers: List<String>,
) {
    companion object {
        /**
         * 空表：内容本体住在 `assets/content/npc_keywords.json`。
         *
         * 空表意味着"什么也听不懂"（confidence = 0，走"没听懂"分支）——
         * 刻意不在这里留一份代码副本，否则它迟早和 JSON 漂移。
         * 内置内容一定有词，这由 `ShippedContentTest` 在构建前守住。
         */
        val EMPTY = NpcKeywordRules(
            topics = emptyMap(),
            times = emptyList(),
            meet = emptyList(),
            scheduleQuestions = emptyList(),
            questionMarkers = emptyList(),
        )
    }
}

/**
 * 规则版解析：关键词命中 + 地点名/别名匹配。
 *
 * 刻意保持"笨"：宁可返回 `confidence = 0` 走"没听懂"分支，也不要猜错——
 * 猜错的代价是 NPC 说出与事实不符的话，那比听不懂糟得多。
 */
class RuleBasedNpcMessageParser(
    /**
     * 关键词表**按需取**（不在构造时快照），这样改完 `npc_keywords.json`
     * 点「重新读取内容」后立刻生效。
     */
    private val rules: () -> NpcKeywordRules = { NpcKeywordRules.EMPTY },
) : NpcMessageParser {

    override suspend fun parse(text: String, context: ParseContext): ParsedPlayerMessage {
        val normalized = text.trim()
        if (normalized.isEmpty()) return ParsedPlayerMessage(raw = text)

        val table = rules()
        val topics = table.topics
            .filterValues { keywords -> keywords.any { it in normalized } }
            .keys

        val place = matchPlace(normalized, context)

        val parsed = ParsedPlayerMessage(
            raw = text,
            topics = topics,
            mentionedPlaceId = place?.id,
            mentionedPlaceName = place?.name,
            timeHint = table.times.firstOrNull { it.first in normalized }?.second,
            wantsToMeet = table.meet.any { it in normalized },
            asksAboutSchedule = table.scheduleQuestions.any { it in normalized },
            isQuestion = table.questionMarkers.any { it in normalized },
        )
        return if (parsed.understood) parsed.copy(confidence = 1.0) else parsed
    }

    private data class MatchedPlace(val id: String, val name: String)

    /**
     * 地点匹配：正式名 + 口语别名，**按关键词长度从长到短**试。
     * 不这样排的话"湖心花园"会先被"湖"或"花园"截胡，指到别的地方去。
     */
    private fun matchPlace(text: String, context: ParseContext): MatchedPlace? {
        val byId = context.places.associateBy { it.id }
        val candidates = context.places.map { it.name to it.id } +
            context.placeAliases.map { (alias, id) -> alias to id }
        val id = candidates
            .filter { (keyword, _) -> keyword.isNotBlank() }
            .sortedByDescending { (keyword, _) -> keyword.length }
            .firstOrNull { (keyword, _) -> keyword in text }
            ?.second
            ?: return null
        // 别名的正式名从地点表里取；取不到就退回 id，模板里也不会空着。
        return MatchedPlace(id = id, name = byId[id]?.name ?: id)
    }
}
