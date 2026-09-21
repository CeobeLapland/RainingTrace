package com.rainingtrace.domain.npc

/**
 * 中文关键词表。规则解析的全部"知识"都在这里，方便将来换成 AI 时对照。
 *
 * 注意顺序：**具体的要排在笼统的前面**（"明天下午"先于"明天"），
 * 因为匹配取第一个命中。
 */
internal object NpcKeywordTable {

    val TOPICS: Map<NpcTopic, List<String>> = mapOf(
        NpcTopic.BOOKS to listOf("书", "看书", "读", "自习", "复习", "考试", "作业", "论文", "图书馆"),
        NpcTopic.ART to listOf("画", "写生", "素描", "颜料", "颜色"),
        NpcTopic.RUNNING to listOf("跑步", "跑", "运动", "锻炼", "操场"),
        NpcTopic.FOOD to listOf("吃", "饭", "食堂", "菜", "面", "夜宵", "早饭", "早餐"),
        NpcTopic.WEATHER to listOf("天气", "下雨", "雨", "晴", "雪", "雾", "风", "冷", "热", "阴天"),
        NpcTopic.NIGHT to listOf("夜", "晚上", "半夜", "熬夜", "星星", "月亮"),
        NpcTopic.PLANTS to listOf("花", "树", "草", "果子", "菌", "叶子", "苔", "松果"),
        NpcTopic.SELF to listOf("你是谁", "名字", "住在", "做什么的", "忙什么", "哪里人", "哪儿人"),
    )

    val TIMES: List<Pair<String, TimeHint>> = listOf(
        "明天下午" to TimeHint.TOMORROW_AFTERNOON,
        "明天早上" to TimeHint.TOMORROW_MORNING,
        "明天晚上" to TimeHint.TOMORROW_EVENING,
        // 光说"明天"不知道上下午，按最常说的下午算；回复里会带上"明天下午"这个词，
        // 所以玩家能立刻看出我们是怎么理解的，不会误会成承诺。
        "明天" to TimeHint.TOMORROW_AFTERNOON,
        "今天晚上" to TimeHint.TONIGHT,
        "今晚" to TimeHint.TONIGHT,
        "待会儿" to TimeHint.LATER_TODAY,
        "待会" to TimeHint.LATER_TODAY,
        "过会儿" to TimeHint.LATER_TODAY,
        "等会" to TimeHint.LATER_TODAY,
        "现在" to TimeHint.NOW,
    )

    /** "想见面"的说法。片 1 不会答应，只会软拒绝 + 陈述作息。 */
    val MEET: List<String> = listOf("等我", "等一", "见面", "见一面", "一起", "来找我", "陪我", "碰头")

    /** "在问作息"的说法。 */
    val SCHEDULE_QUESTIONS: List<String> =
        listOf("在哪", "去哪里", "有空", "有没空", "忙不忙", "干嘛", "在做什么", "干什么")

    val QUESTION_MARKERS: List<String> = listOf("？", "?", "吗", "呢", "在不在", "有没有", "是不是")
}

/**
 * 规则版解析：关键词命中 + 地点名/别名匹配。
 *
 * 刻意保持"笨"：宁可返回 `confidence = 0` 走"没听懂"分支，也不要猜错——
 * 猜错的代价是 NPC 说出与事实不符的话，那比听不懂糟得多。
 */
class RuleBasedNpcMessageParser : NpcMessageParser {

    override suspend fun parse(text: String, context: ParseContext): ParsedPlayerMessage {
        val normalized = text.trim()
        if (normalized.isEmpty()) return ParsedPlayerMessage(raw = text)

        val topics = NpcKeywordTable.TOPICS
            .filterValues { keywords -> keywords.any { it in normalized } }
            .keys

        val place = matchPlace(normalized, context)

        val parsed = ParsedPlayerMessage(
            raw = text,
            topics = topics,
            mentionedPlaceId = place?.id,
            mentionedPlaceName = place?.name,
            timeHint = NpcKeywordTable.TIMES.firstOrNull { it.first in normalized }?.second,
            wantsToMeet = NpcKeywordTable.MEET.any { it in normalized },
            asksAboutSchedule = NpcKeywordTable.SCHEDULE_QUESTIONS.any { it in normalized },
            isQuestion = NpcKeywordTable.QUESTION_MARKERS.any { it in normalized },
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
