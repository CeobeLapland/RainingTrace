package com.rainingtrace.data.content

import com.rainingtrace.domain.content.ContentReport
import com.rainingtrace.domain.inventory.ResourceDefinition
import com.rainingtrace.domain.map.Place
import com.rainingtrace.domain.npc.NpcKeywordRules
import com.rainingtrace.domain.npc.NpcProfile
import com.rainingtrace.domain.npc.NpcProactiveRule
import com.rainingtrace.domain.world.ResourceYieldRule

/**
 * 测试侧读取**真实的内置内容文件**（`src/main/assets/content/` 下的那些 .json）。
 *
 * 内容外置之后，代码里不再保留任何一份内容副本，所以测试要用内容必须从这里来。
 * 好处是：每个用到内容的测试都顺带验证了一遍"内置内容读得出来、且没有诊断"——
 * 这正是原来"编译期引用常量"提供的保证。
 */
object ShippedContent {

    private fun readAsset(name: String): String {
        val stream = javaClass.classLoader?.getResourceAsStream("content/$name")
            ?: error("内置内容缺失：content/$name（检查 app/src/main/assets 与 sourceSets 配置）")
        return stream.readBytes().toString(Charsets.UTF_8)
    }

    /** 读一份并**要求零诊断**：内置内容出问题应该在这里响亮地失败。 */
    private fun <T> load(name: String, decode: (String, String, ContentReport) -> T): T {
        val report = ContentReport()
        val decoded = decode(readAsset(name), name, report)
        check(report.items.isEmpty()) { "内置内容 $name 有诊断：${report.items}" }
        return decoded
    }

    val places: List<Place> by lazy { load("places.json", ::decodePlaceEntries).entries }

    val placeById: Map<String, Place> by lazy { places.associateBy { it.id } }

    val resources: List<ResourceDefinition> by lazy {
        load("resources.json", ::decodeResourceEntries).entries
    }

    val yieldRules: List<ResourceYieldRule> by lazy {
        load("yield_rules.json", ::decodeYieldRuleEntries).entries
    }

    val npcs: List<NpcProfile> by lazy { load("npcs.json", ::decodeNpcEntries).entries }

    val npcProactiveRules: List<NpcProactiveRule> by lazy {
        load("npc_proactive_rules.json", ::decodeNpcProactiveRuleEntries).entries
    }

    val lines: Map<String, List<String>> by lazy {
        load("npc_lines.json", ::decodeLineEntries).entries
    }

    val aliases: Map<String, String> by lazy {
        load("place_aliases.json", ::decodeAliasEntries).entries
    }

    val keywords: NpcKeywordRules by lazy {
        load("npc_keywords.json", ::decodeNpcKeywords)
            ?: error("npc_keywords.json 读不出来")
    }

    fun rule(id: String): ResourceYieldRule =
        yieldRules.firstOrNull { it.id == id } ?: error("内置产出规则里没有 $id")

    fun npc(id: String): NpcProfile =
        npcs.firstOrNull { it.id == id } ?: error("内置 NPC 里没有 $id")

    fun place(id: String): Place = placeById[id] ?: error("内置地点里没有 $id")
}