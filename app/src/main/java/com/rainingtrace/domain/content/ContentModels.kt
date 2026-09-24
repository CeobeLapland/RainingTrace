package com.rainingtrace.domain.content

import com.rainingtrace.domain.craft.Recipe
import com.rainingtrace.domain.inventory.ResourceDefinition
import com.rainingtrace.domain.map.Place
import com.rainingtrace.domain.map.PlaceActionType
import com.rainingtrace.domain.npc.NpcKeywordRules
import com.rainingtrace.domain.npc.NpcProfile
import com.rainingtrace.domain.npc.NpcProactiveRule
import com.rainingtrace.domain.world.ResourceYieldRule
import kotlinx.coroutines.flow.StateFlow

/**
 * 内容加载诊断（开发者模式，GDD §21）。
 *
 * 内容从 JSON 读入，手写的东西一定会写错。原则与 `RuleBasedNpcMessageParser`
 * 一致：**绝不抛异常，也不假装听懂**——写错的条目要么被丢掉（[Level.ERROR]），
 * 要么被保留但记一笔（[Level.WARN]），两种情况都必须留下可见的记录。
 */
data class ContentDiagnostic(
    val level: Level,
    /** 出问题的文件（内置文件名，或 `places.json（覆盖）` 这样的标注）。 */
    val file: String,
    /** 出问题的条目；整份文件读不出来时为 null。 */
    val entryId: String?,
    val message: String,
) {
    enum class Level { ERROR, WARN }

    val isError: Boolean get() = level == Level.ERROR

    override fun toString(): String {
        val where = if (entryId.isNullOrBlank()) file else "$file / $entryId"
        return "$level $where —— $message"
    }
}

/** 一次内容加载的诊断收集器。 */
class ContentReport {

    private val collected = mutableListOf<ContentDiagnostic>()

    val items: List<ContentDiagnostic> get() = collected

    val errorCount: Int get() = collected.count { it.isError }

    fun error(file: String, entryId: String?, message: String) {
        collected += ContentDiagnostic(ContentDiagnostic.Level.ERROR, file, entryId, message)
    }

    fun warn(file: String, entryId: String?, message: String) {
        collected += ContentDiagnostic(ContentDiagnostic.Level.WARN, file, entryId, message)
    }
}

/**
 * 合并并校验之后的内容（纯数据，不含任何 Android / IO 依赖）。
 *
 * 每外置一类内容就往这里加一个字段——它是"世界内容"的单一真相。
 */
data class WorldContent(
    val places: List<Place> = emptyList(),
    val resources: List<ResourceDefinition> = emptyList(),
    val yieldRules: List<ResourceYieldRule> = emptyList(),
    /** 加工配方（输入 → 输出）。与产出规则是两套：配方无地点、无冷却、有消耗。 */
    val recipes: List<Recipe> = emptyList(),
    val npcs: List<NpcProfile> = emptyList(),
    val npcProactiveRules: List<NpcProactiveRule> = emptyList(),
    /** 台词表：key → 变体（`"$npcId.$key"` 覆盖在前，共享 `key` 兜底）。 */
    val npcLines: Map<String, List<String>> = emptyMap(),
    /** 地点口语别名 → placeId（NPC 消息解析用，匹配时要先长后短）。 */
    val placeAliases: Map<String, String> = emptyMap(),
    /** 规则解析用的中文关键词表（改它就能教会 NPC 认新词）。 */
    val npcKeywords: NpcKeywordRules = NpcKeywordRules.EMPTY,
)

/**
 * 给运行时用的内容快照：在 [WorldContent] 之上建好查找索引，
 * 所以每次读取都是 O(1)，不会在每次调用的地方重建 map。
 *
 * 仓储（`LiveRepositories`）只是读它的薄委托，因此内容重载后自然生效，
 * 不需要任何"通知仓储刷新"的机制。
 */
class ContentIndex(
    val content: WorldContent,
    val diagnostics: List<ContentDiagnostic>,
) {
    val places: List<Place> get() = content.places

    val placeById: Map<String, Place> = content.places.associateBy { it.id }

    val resources: List<ResourceDefinition> get() = content.resources

    val resourceById: Map<String, ResourceDefinition> = content.resources.associateBy { it.id }

    val yieldRules: List<ResourceYieldRule> get() = content.yieldRules

    val yieldRulesByAction: Map<PlaceActionType, List<ResourceYieldRule>> =
        content.yieldRules.groupBy { it.action }

    val recipes: List<Recipe> get() = content.recipes

    val recipeById: Map<String, Recipe> = content.recipes.associateBy { it.id }

    val npcs: List<NpcProfile> get() = content.npcs

    val npcById: Map<String, NpcProfile> = content.npcs.associateBy { it.id }

    val npcProactiveRules: List<NpcProactiveRule> get() = content.npcProactiveRules

    val npcLines: Map<String, List<String>> get() = content.npcLines

    val placeAliases: Map<String, String> get() = content.placeAliases

    val npcKeywords: NpcKeywordRules get() = content.npcKeywords

    val errorCount: Int = diagnostics.count { it.isError }

    /**
     * 各类型条数，key 是内容文件名（不含扩展名）。给开发者模式面板用：
     * UI 只遍历它，所以以后新增内容类型不用改界面。
     * 关键词表不是"条数"，所以按话题数报。
     */
    val counts: Map<String, Int> = mapOf(
        PLACES to content.places.size,
        RESOURCES to content.resources.size,
        YIELD_RULES to content.yieldRules.size,
        RECIPES to content.recipes.size,
        NPCS to content.npcs.size,
        NPC_PROACTIVE_RULES to content.npcProactiveRules.size,
        NPC_LINES to content.npcLines.size,
        PLACE_ALIASES to content.placeAliases.size,
        NPC_KEYWORDS to content.npcKeywords.topics.size,
    )

    companion object {
        /** 加载完成之前的占位值；正常情况下只存在瞬间。 */
        val EMPTY = ContentIndex(WorldContent(), emptyList())

        const val PLACES = "places"
        const val RESOURCES = "resources"
        const val YIELD_RULES = "yield_rules"
        const val RECIPES = "recipes"
        const val NPCS = "npcs"
        const val NPC_PROACTIVE_RULES = "npc_proactive_rules"
        const val NPC_LINES = "npc_lines"
        const val PLACE_ALIASES = "place_aliases"
        const val NPC_KEYWORDS = "npc_keywords"
    }
}

/**
 * 内容这一层的对外读口：给设置页的开发者模式用（只读 + 重读）。
 *
 * feature 只依赖这个接口，真实来源接上别的（服务端下发）时换实现即可。
 */
interface ContentPanel {

    val index: StateFlow<ContentIndex>

    /** 重读 assets + 覆盖层并重建索引。 */
    fun reload()
}