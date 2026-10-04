package com.rainingtrace.domain.content

import com.rainingtrace.domain.craft.Recipe
import com.rainingtrace.domain.inventory.ResourceCategory
import com.rainingtrace.domain.map.defaultActionsFor
import com.rainingtrace.domain.npc.NpcProfile
import com.rainingtrace.domain.npc.NpcProactiveRule
import com.rainingtrace.domain.spawn.SpawnRule

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
        validateUniqueIds(content.spawnRules, MERGED, report) { it.id }

        warnUncraftableCraftGoods(content, report)

        return content.copy(
            yieldRules = validYieldRules(content, report),
            recipes = validRecipes(content, report),
            npcs = validNpcs(content, report),
            npcProactiveRules = validProactiveRules(content, report),
            placeAliases = validAliases(content, report),
            spawnRules = validSpawnRules(content, report),
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
     * 链条完整性：**加工品（CRAFT）被当作入料，却没有配方能产出它** —— 那些配方永远做不出来。
     *
     * 只记一条 **WARN**（不是 ERROR，也不丢任何东西）：作者常常先写下游、后补上游，
     * 中途不完整是正常的；但"做不出来却看不出来"会静默毁掉一整条链，
     * 所以必须留在设置页「内容（开发者模式）」的诊断里看得见。
     */
    private fun warnUncraftableCraftGoods(content: WorldContent, report: ContentReport) {
        val craftOutputs = content.recipes.map { it.output.resourceId }.toSet()
        val craftIds = content.resources
            .filter { it.category == ResourceCategory.CRAFT }
            .map { it.id }
            .toSet()

        val broken = content.recipes
            .flatMap { recipe -> recipe.inputs.map { it.resourceId } }
            .filter { it in craftIds && it !in craftOutputs }
            .distinct()

        if (broken.isNotEmpty()) {
            report.warn(
                MERGED,
                null,
                "这些加工品被当作入料、却没有任何配方能产出（相关配方永远做不出来）：${broken.joinToString()}",
            )
        }
    }

    /**
     * NPC 作息里指向不存在地点的**单条条目**被剔除，NPC 本身保留。
     *
     * 曾经的策略是"一条悬空就丢掉整位 NPC + ERROR"（防止半截作息让他瞬移）。现在放开：
     * 地点数据是渐进补齐的（`places_design.md` 有 139 处待填坐标），悬空作息是**过渡期的
     * 正常状态**而不是内容错误。逐条剔除后，他只在"能解析的地点"上出现，其余时段不出现；
     * `resolveSchedule` 与它同口径，所以运行时与校验语义一致。
     *
     * 代价：真正写错 placeId 的笔误不再响铃，会静默少一处出现。兜底由 `ShippedContentTest`
     * 的"启用 NPC 至少 1 条作息可解析"钉住，防止系统性全灭。
     */
    private fun validNpcs(content: WorldContent, report: ContentReport): List<NpcProfile> {
        val placeIds = content.places.map { it.id }.toSet()
        return content.npcs.map { npc ->
            npc.copy(schedule = npc.schedule.filter { it.placeId in placeIds })
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

    /**
     * 刷新规则是不是个"哑点"。候选点为空、半径非正这类硬错在 [SpawnRule] 的 `require`
     * 里就挡掉了（解析期变成诊断），这里只补两条**不该拦构建**的提醒：
     * - `perDay` 比候选点还多 → 每天最多只会刷出候选点那么多个；
     * - 该类型没有任何产出规则 → 玩家走到跟前也采不到东西（补内容的中途很正常）。
     */
    private fun validSpawnRules(content: WorldContent, report: ContentReport): List<SpawnRule> {
        val specificKeys = content.yieldRules.map { it.action to it.placeType }.toSet()
        val wildcardActions = content.yieldRules
            .filter { it.placeType == null }
            .map { it.action }
            .toSet()

        content.spawnRules.forEach { rule ->
            if (rule.perDay > rule.spots.size) {
                report.warn(
                    MERGED,
                    rule.id,
                    "perDay（${rule.perDay}）比候选点还多（${rule.spots.size} 个），" +
                        "每天最多只会刷出 ${rule.spots.size} 个",
                )
            }
            val actions = rule.actions ?: defaultActionsFor(rule.placeType)
            val reachable = actions.any { action ->
                (action to rule.placeType) in specificKeys || action in wildcardActions
            }
            if (!reachable) {
                report.warn(
                    MERGED,
                    rule.id,
                    "刷出来的「${rule.placeType}」没有任何产出规则，玩家走到跟前也采不到东西",
                )
            }
        }
        return content.spawnRules
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