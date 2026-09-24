package com.rainingtrace.data.content

import com.rainingtrace.domain.content.ContentReport
import com.rainingtrace.domain.npc.NpcTopic
import com.rainingtrace.domain.world.WeatherKind
import com.rainingtrace.domain.world.WorldCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内置内容的守门人。
 *
 * 内容外置之后，"作息里 placeId 写错 → 编译期报错"这条保证没有了：写错只会让
 * 那个人整天不出现，是静默的。所以必须有一处**响亮**的检查，这个测试就是它
 * （比运行时诊断更早发现问题，且不会等到真机试玩才发现）。
 *
 * 每外置一类内容，这里就多加一段断言。
 */
class ShippedContentTest {

    private fun readAsset(name: String): String {
        val stream = javaClass.classLoader?.getResourceAsStream("content/$name")
        assertNotNull("内置内容缺失：content/$name（检查 app/src/main/assets 与 sourceSets 配置）", stream)
        return stream!!.readBytes().toString(Charsets.UTF_8)
    }

    private fun places(report: ContentReport) =
        decodePlaceEntries(readAsset("places.json"), "places.json", report)

    @Test
    fun `内置地点没有错误诊断`() {
        val report = ContentReport()
        val decoded = places(report)
        assertEquals("内置地点有错误：${report.items}", 0, report.errorCount)
        assertTrue("内置地点数不足：${decoded.entries.size}", decoded.entries.size >= 9)
    }

    @Test
    fun `内置地点 id 唯一且必要字段齐全`() {
        val decoded = places(ContentReport())
        val ids = decoded.entries.map { it.id }
        assertEquals("地点 id 有重复：$ids", ids.size, ids.toSet().size)
        decoded.entries.forEach { place ->
            assertTrue("地点 id 为空", place.id.isNotBlank())
            assertTrue("地点 ${place.id} 没有名字", place.name.isNotBlank())
            assertTrue("地点 ${place.id} 没有可执行动作", place.actions.isNotEmpty())
        }
    }

    @Test
    fun `内置资源没有错误诊断且数量不缩水`() {
        val report = ContentReport()
        val decoded = decodeResourceEntries(readAsset("resources.json"), "resources.json", report)

        assertEquals("内置资源有错误：${report.items}", 0, report.errorCount)
        assertTrue("内置资源数不足：${decoded.entries.size}", decoded.entries.size >= 18)
        val ids = decoded.entries.map { it.id }
        assertEquals("资源 id 有重复：$ids", ids.size, ids.toSet().size)
    }

    @Test
    fun `内置产出规则没有错误诊断且数量不缩水`() {
        val report = ContentReport()
        val decoded = decodeYieldRuleEntries(readAsset("yield_rules.json"), "yield_rules.json", report)

        assertEquals("内置产出规则有错误：${report.items}", 0, report.errorCount)
        assertTrue("内置产出规则数不足：${decoded.entries.size}", decoded.entries.size >= 21)
    }

    /**
     * 这条原来由编译期常量（`InMemoryResourceCatalog.RAIN_MOSS.id`）保证，
     * 现在改成对真实 JSON 做同样的引用完整性断言。
     */
    @Test
    fun `每条产出规则引用的资源都存在`() {
        val resourceIds = decodeResourceEntries(
            readAsset("resources.json"),
            "resources.json",
            ContentReport(),
        ).entries.map { it.id }.toSet()

        val missing = decodeYieldRuleEntries(
            readAsset("yield_rules.json"),
            "yield_rules.json",
            ContentReport(),
        ).entries
            .map { it.resourceId }
            .filterNot { it in resourceIds }
            .toSet()

        assertTrue("yield_rules.json 引用了不存在的资源：$missing", missing.isEmpty())
    }

    /** 条件里引用的地点类型/天气/季节都必须能被解析出来（否则规则会静默失效）。 */
    @Test
    fun `内置产出规则的条件都能解析`() {
        val rules = decodeYieldRuleEntries(
            readAsset("yield_rules.json"),
            "yield_rules.json",
            ContentReport(),
        ).entries

        assertTrue("有规则解析后没有任何条件（说明条件被丢掉了）", rules.any { it.conditions.isNotEmpty() })

        // 雨天简写 RAINY 必须真的展开成"所有会下雨的天气"，
        // 只认其中一种的话，雨天内容会有一半场景永远不出现。
        val rainyLake = rules.first { it.id == "rule.observe.rainy_lake" }
        val weather = rainyLake.conditions.single() as WorldCondition.WeatherIn
        assertEquals(WeatherKind.RAINY, weather.kinds)
    }

    // ---- NPC ----

    private fun npcs(report: ContentReport) =
        decodeNpcEntries(readAsset("npcs.json"), "npcs.json", report)

    @Test
    fun `内置 NPC 没有错误诊断且数量不缩水`() {
        val report = ContentReport()
        val decoded = npcs(report)

        assertEquals("内置 NPC 有错误：${report.items}", 0, report.errorCount)
        assertTrue("内置 NPC 数不足：${decoded.entries.size}", decoded.entries.size >= 5)
        val ids = decoded.entries.map { it.id }
        assertEquals("NPC id 有重复：$ids", ids.size, ids.toSet().size)
    }

    /**
     * 这条原来靠"作息里写 `FakePlaceRepository.XXX.id` 常量"在编译期保证。
     * 现在内容全是字符串，所以必须有人替编译器盯着——否则写错只会让那个人
     * 整天不出现，而地图上完全看不出哪里不对。
     */
    @Test
    fun `每条作息的 placeId 都指向真实存在的地点`() {
        val placeIds = places(ContentReport()).entries.map { it.id }.toSet()
        val dangling = npcs(ContentReport()).entries
            .flatMap { npc -> npc.schedule.map { npc.id to it.placeId } }
            .filterNot { (_, placeId) -> placeId in placeIds }

        assertTrue("npcs.json 的作息指向不存在的地点：$dangling", dangling.isEmpty())
    }

    @Test
    fun `每位 NPC 都有作息且 favoriteTopic 在 topics 里`() {
        npcs(ContentReport()).entries.forEach { npc ->
            assertTrue("${npc.id} 没有任何作息", npc.schedule.isNotEmpty())
            val favorite = npc.favoriteTopic
            if (favorite != null) {
                assertTrue("${npc.id} 的 favoriteTopic 不在 topics 里", favorite in npc.topics)
            }
        }
    }

    @Test
    fun `内置主动消息规则没有错误诊断且引用的 NPC 都存在`() {
        val report = ContentReport()
        val rules = decodeNpcProactiveRuleEntries(
            readAsset("npc_proactive_rules.json"),
            "npc_proactive_rules.json",
            report,
        ).entries

        assertEquals("内置主动规则有错误：${report.items}", 0, report.errorCount)
        assertTrue("内置主动规则数不足：${rules.size}", rules.size >= 10)

        val npcIds = npcs(ContentReport()).entries.map { it.id }.toSet()
        val dangling = rules.map { it.npcId }.filterNot { it in npcIds }.toSet()
        assertTrue("npc_proactive_rules.json 引用了不存在的 NPC：$dangling", dangling.isEmpty())
    }

    // ---- 台词与别名 ----

    private fun lines(report: ContentReport) =
        decodeLineEntries(readAsset("npc_lines.json"), "npc_lines.json", report)

    @Test
    fun `内置台词没有错误诊断`() {
        val report = ContentReport()
        val decoded = lines(report)

        assertEquals("内置台词有错误：${report.items}", 0, report.errorCount)
        assertTrue("台词 key 数不足：${decoded.entries.size}", decoded.entries.size >= 30)
    }

    /**
     * 变体太少的代价是"同一句在几轮里反复出现"，那是最容易露馅的地方，
     * 所以把它写成硬约束（原始设计约束是每部件 ≥4，这里守住下限 3）。
     */
    @Test
    fun `每个台词 key 至少 3 条变体`() {
        val thin = lines(ContentReport()).entries.filterValues { it.size < 3 }.map { it.key }

        assertTrue("这些 key 的台词变体太少（同一句会连着出现）：$thin", thin.isEmpty())
    }

    /**
     * `unparsed` 是结构性兜底（`TemplateNarrativeService.FALLBACK_LINES`）读的那个 key：
     * 内容里删掉它虽然不会崩，但会让人忘记它其实是必需的。
     */
    @Test
    fun `台词表保留了结构性 key`() {
        val table = lines(ContentReport()).entries

        assertTrue("缺少 \"unparsed\"（没听懂时的兜底台词）", "unparsed" in table)
        // 关系阶段的招呼：少一个阶段那种关系下就会退回 unparsed。
        listOf("greet.stranger", "greet.nodding", "greet.acquainted", "greet.friend")
            .forEach { assertTrue("缺少 \"$it\"", it in table) }
    }

    @Test
    fun `内置别名都指向真实存在的地点`() {
        val report = ContentReport()
        val aliases = decodeAliasEntries(
            readAsset("place_aliases.json"),
            "place_aliases.json",
            report,
        ).entries

        assertEquals("内置别名有错误：${report.items}", 0, report.errorCount)
        assertTrue("别名数不足：${aliases.size}", aliases.size >= 15)

        val placeIds = places(ContentReport()).entries.map { it.id }.toSet()
        val dangling = aliases.filterValues { it !in placeIds }
        assertTrue("别名指向不存在的地点：$dangling", dangling.isEmpty())
    }

    /**
     * 关键词表是"NPC 听得懂中文"的全部依据。它读空不会崩，但会让每一次对话
     * 都变成"没听懂"——那是最难排查的一种坏法，所以这里盯着下限。
     */
    @Test
    fun `内置关键词表能解析出话题与时间`() {
        val report = ContentReport()
        val rules = decodeNpcKeywords(
            readAsset("npc_keywords.json"),
            "npc_keywords.json",
            report,
        )

        assertNotNull("关键词表读不出来：${report.items}", rules)
        assertEquals("关键词表有错误：${report.items}", 0, report.errorCount)
        assertEquals("话题数不对", NpcTopic.entries.size, rules!!.topics.size)
        assertTrue("没有任何\"想见面\"的说法", rules.meet.isNotEmpty())
        assertTrue("没有时间词", rules.times.isNotEmpty())
        assertTrue("没有问句标记", rules.questionMarkers.isNotEmpty())

        // 顺序敏感：长词必须排在短词前面，否则"明天下午"会被"明天"截胡。
        val times = rules.times.map { it.first }
        val afternoon = times.indexOf("明天下午")
        val tomorrow = times.indexOf("明天")
        assertTrue("「明天下午」必须排在「明天」前面（现在是 $afternoon vs $tomorrow）", afternoon < tomorrow)
    }
}