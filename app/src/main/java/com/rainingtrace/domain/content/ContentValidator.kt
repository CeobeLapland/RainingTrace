package com.rainingtrace.domain.content

import com.rainingtrace.domain.craft.Recipe
import com.rainingtrace.domain.npc.NpcProfile
import com.rainingtrace.domain.npc.NpcProactiveRule

/**
 * 内容的引用完整性校验（纯函数，可单测）。
 *
 * 合并之后的最终检查：这里的每一条都是"内容会静默说谎"的防线——
 * 比如 NPC 的作息指向一个不存在的地点，会让他整天不出现而不报错。
 *
 * 降级粒度**刻意不对称**（见 GDD §21 与 handoff 的"别静默剔除"提醒）：
 * 加法的内容（地点 / 资源 / 产出规则）只丢那一条；
 * NPC 的一条作息坏了则整位 NPC 都不要，因为半截作息的 NPC 会瞬移，
 * 那是"静默说谎"，比缺席糟得多。缺席是响亮的（日志 + 诊断面板 + 单测）。
 *
 * 返回清理后的内容：**调用方拿返回值，不要继续用入参**。
 */
object ContentValidator {

    /** 合并结果的文件名：诊断不归咎于某个源文件。 */
    const val MERGED = "合并结果"

    fun validate(content: WorldContent, report: ContentReport): WorldContent {
        validateUniqueIds(content.places, MERGED, report) { it.id }
        validateUniqueIds(content.resources, MERGED, report) { it.id }
        validateUniqueIds(content.yieldRules, MERGED, report) { it.id }
        validateUniqueIds(content.recipes, MERGED, report) { it.id }
        validateUniqueIds(content.npcs, MERGED, report) { it.id }
        validateUniqueIds(content.npcProactiveRules, MERGED, report) { it.id }

        return content.copy(
            yieldRules = validYieldRules(content, report),
            recipes = validRecipes(content, report),
            npcs = validNpcs(content, report),
            npcProactiveRules = validProactiveRules(content, report),
            placeAliases = validAliases(content, report),
        )
    }

    /**
     * 别名必须指向存在的地点，否则玩家说了一个词、解析指到了空处：
     * `RuleBasedNpcMessageParser` 会把 mentionedPlaceId 填成不存在的 id，
     * NPC 就会说出一个地图上没有的地方。
     */
    private fun validAliases(content: WorldContent, report: ContentReport): Map<String, String> {
        val placeIds = content.places.map { it.id }.toSet()
        return content.placeAliases.filter { (alias, placeId) ->
            val known = placeId in placeIds
            if (!known) {
                report.error(MERGED, alias, "别名指向不存在的地点：$placeId")
            }
            known
        }
    }

    /**
     * 产出规则引用的资源必须存在，否则规则会命中却什么也不产出——
     * 玩家看到"有东西"却拿不到，比这条规则干脆不存在更糟。
     */
    private fun validYieldRules(content: WorldContent, report: ContentReport) =
        content.yieldRules.filter { rule ->
            val known = rule.resourceId in content.resources.map { it.id }
            if (!known) {
                report.error(MERGED, rule.id, "产出规则引用的资源不存在：${rule.resourceId}")
            }
            known
        }

    /**
     * 配方引用的资源（输入与输出）都必须存在，否则要么"做了什么都得不到"、
     * 要么"永远缺一个不存在的材料"——两者都是静默失效，玩家只能干瞪眼。
     */
    private fun validRecipes(content: WorldContent, report: ContentReport): List<Recipe> {
        val resourceIds = content.resources.map { it.id }.toSet()
        return content.recipes.filter { recipe ->
            val dangling = (recipe.inputs.map { it.resourceId } + recipe.output.resourceId)
                .filterNot { it in resourceIds }
            if (dangling.isEmpty()) {
                true
            } else {
                report.error(MERGED, recipe.id, "配方引用的资源不存在：${dangling.joinToString()}")
                false
            }
        }
    }

    /**
     * NPC 作息里的地点必须存在。
     *
     * 一条作息悬空就**丢掉整位 NPC**（而不是丢掉那一条作息）：半截作息的 NPC
     * 会瞬移或永远不离开上一处，那是"静默说谎"，比缺席糟得多。
     * 缺席是响亮的——日志、诊断面板、ShippedContentTest 三处都会说。
     */
    private fun validNpcs(content: WorldContent, report: ContentReport): List<NpcProfile> {
        val placeIds = content.places.map { it.id }.toSet()
        return content.npcs.filter { npc ->
            val dangling = npc.schedule.filterNot { it.placeId in placeIds }
            if (dangling.isEmpty()) {
                true
            } else {
                report.error(
                    MERGED,
                    npc.id,
                    "作息指向不存在的地点（${dangling.joinToString { it.placeId }}），" +
                        "整位 NPC 被剔除——半截作息会让他瞬移，那比不出现更容易骗到人",
                )
                false
            }
        }
    }

    /** 主动消息规则引用的 NPC 必须存在；否则这条规则永远没有发送者。 */
    private fun validProactiveRules(
        content: WorldContent,
        report: ContentReport,
    ): List<NpcProactiveRule> {
        val npcIds = content.npcs.map { it.id }.toSet()
        return content.npcProactiveRules.filter { rule ->
            val known = rule.npcId in npcIds
            if (!known) {
                report.error(MERGED, rule.id, "主动消息规则引用的 NPC 不存在：${rule.npcId}")
            }
            known
        }
    }

    private fun <T> validateUniqueIds(
        items: List<T>,
        file: String,
        report: ContentReport,
        idOf: (T) -> String,
    ) {
        val seen = mutableSetOf<String>()
        items.forEach { item ->
            val id = idOf(item)
            if (!seen.add(id)) {
                report.error(file, id, "id 重复：同 id 只会有一条被查到，另一条永远看不见")
            }
        }
    }
}