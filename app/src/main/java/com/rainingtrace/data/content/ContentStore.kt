package com.rainingtrace.data.content

import android.content.Context
import android.util.Log
import com.rainingtrace.domain.content.ContentIndex
import com.rainingtrace.domain.content.ContentMerge
import com.rainingtrace.domain.content.ContentPanel
import com.rainingtrace.domain.content.ContentReport
import com.rainingtrace.domain.content.ContentValidator
import com.rainingtrace.domain.content.WorldContent
import com.rainingtrace.domain.npc.NpcKeywordRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * 内容的唯一入口：**内置 assets 默认 + 应用私有目录覆盖**。
 *
 * 加载在 `AppContainer` 构造期一次性同步完成（和 `gridManager` 读偏好是同一个口径，
 * 都是几毫秒级的小文件读）。刻意不做懒加载、也不做"`.value` + 空兜底"——
 * 内容被地图、设置、聊天、背包同时需要，懒加载失手会表现为"莫名其妙空掉的地图"。
 *
 * 仓储不缓存内容，而是读 [index]；所以 [reload] 之后一切自动生效。
 */
class ContentStore(private val context: Context) : ContentPanel {

    private val _index = MutableStateFlow(ContentIndex.EMPTY)
    override val index: StateFlow<ContentIndex> = _index.asStateFlow()

    fun load() = rebuild()

    /** 「重新读取内容」：重读 assets + 覆盖层并重建索引。 */
    override fun reload() = rebuild()

    private fun rebuild() {
        val report = ContentReport()
        val content = WorldContent(
            places = loadKind(
                file = PLACES_FILE,
                overlayLabel = OVERLAY_PLACES,
                decode = ::decodePlaceEntries,
                idOf = { it.id },
                report = report,
            ),
            resources = loadKind(
                file = RESOURCES,
                overlayLabel = OVERLAY_RESOURCES,
                decode = ::decodeResourceEntries,
                idOf = { it.id },
                report = report,
            ),
            yieldRules = loadKind(
                file = YIELD_RULES,
                overlayLabel = OVERLAY_YIELD_RULES,
                decode = ::decodeYieldRuleEntries,
                idOf = { it.id },
                report = report,
            ),
            npcs = loadKind(
                file = NPCS,
                overlayLabel = OVERLAY_NPCS,
                decode = ::decodeNpcEntries,
                idOf = { it.id },
                report = report,
            ),
            npcProactiveRules = loadKind(
                file = NPC_PROACTIVE_RULES,
                overlayLabel = OVERLAY_NPC_PROACTIVE_RULES,
                decode = ::decodeNpcProactiveRuleEntries,
                idOf = { it.id },
                report = report,
            ),
            npcLines = loadMap(
                file = NPC_LINES,
                overlayLabel = OVERLAY_NPC_LINES,
                decode = ::decodeLineEntries,
                report = report,
            ),
            placeAliases = loadMap(
                file = PLACE_ALIASES,
                overlayLabel = OVERLAY_PLACE_ALIASES,
                decode = ::decodeAliasEntries,
                report = report,
            ),
            npcKeywords = loadWhole(
                file = NPC_KEYWORDS,
                overlayLabel = OVERLAY_NPC_KEYWORDS,
                fallback = NpcKeywordRules.EMPTY,
                decode = ::decodeNpcKeywords,
                report = report,
            ),
        )
        // 校验会剔除引用悬空的条目，所以后面必须用返回值，不能继续用 content。
        val cleaned = ContentValidator.validate(content, report)
        logDiagnostics(cleaned, report)
        _index.value = ContentIndex(cleaned, report.items)
    }

    /**
     * 一类内容的标准加载流程：内置 assets → 用户覆盖层 → 合并。
     *
     * 内置读不出来时落成**空**并记 ERROR：内容只住在 JSON 里，代码里没有第二份，
     * 所以宁可空着（响亮、且被 `ShippedContentTest` 挡在构建前），
     * 也不要再维护一份会漂移的代码副本。
     */
    private fun <T> loadKind(
        file: String,
        overlayLabel: String,
        decode: (String, String, ContentReport) -> DecodedEntries<T>,
        idOf: (T) -> String,
        report: ContentReport,
    ): List<T> {
        val builtIn = readAsset(file)
            ?.let { decode(it, file, report).entries }
            ?.takeIf { it.isNotEmpty() }
            ?: emptyList<T>().also {
                report.error(file, null, "内置 $file 读不出内容（内容只住在 assets/content 里）")
            }

        val overlay = readOverlay(file)?.let { decode(it, overlayLabel, report) }

        return ContentMerge.byId(
            builtIn = builtIn,
            overlay = overlay?.entries.orEmpty(),
            removedIds = overlay?.removedIds.orEmpty().toSet(),
            file = overlayLabel,
            idOf = idOf,
            report = report,
        )
    }

    private fun readAsset(file: String): String? = runCatching {
        context.assets.open("$DIR/$file").use { it.readBytes().toString(Charsets.UTF_8) }
    }.getOrNull()

    /** 映射型内容（台词表 / 别名）的加载流程，同 [loadKind] 但合并用 [ContentMerge.byKey]。 */
    private fun <V> loadMap(
        file: String,
        overlayLabel: String,
        decode: (String, String, ContentReport) -> DecodedMap<V>,
        report: ContentReport,
    ): Map<String, V> {
        val builtIn = readAsset(file)
            ?.let { decode(it, file, report).entries }
            ?.takeIf { it.isNotEmpty() }
            ?: emptyMap<String, V>().also {
                report.error(file, null, "内置 $file 读不出内容（内容只住在 assets/content 里）")
            }

        val overlay = readOverlay(file)?.let { decode(it, overlayLabel, report) }

        return ContentMerge.byKey(
            builtIn = builtIn,
            overlay = overlay?.entries.orEmpty(),
            removedKeys = overlay?.removedKeys.orEmpty().toSet(),
            file = overlayLabel,
            report = report,
        )
    }

    /**
     * 整块内容的加载（关键词表这类"一套规则整体替换"的东西）。
     *
     * 与 [loadKind] / [loadMap] 的差别：没有逐条合并，**有覆盖层就整份换掉**。
     * [decode] 返回 null 表示读不出来——这时保留上一份好数据，而不是清空。
     */
    private fun <T> loadWhole(
        file: String,
        overlayLabel: String,
        fallback: T,
        decode: (String, String, ContentReport) -> T?,
        report: ContentReport,
    ): T {
        val builtIn = readAsset(file)?.let { decode(it, file, report) }
            ?: fallback.also {
                report.error(file, null, "内置 $file 读不出内容（内容只住在 assets/content 里）")
            }
        return readOverlay(file)?.let { decode(it, overlayLabel, report) } ?: builtIn
    }

    private fun readOverlay(file: String): String? {
        val target = overlayFile(context, file)
        if (!target.exists()) return null
        return runCatching { target.readText() }.getOrNull()
    }

    private fun logDiagnostics(content: WorldContent, report: ContentReport) {
        report.items.forEach { item ->
            if (item.isError) Log.e(TAG, item.toString()) else Log.w(TAG, item.toString())
        }
        Log.i(TAG, "内容加载完成：${content.countsSummary()}，诊断 ${report.items.size}" +
            "（错误 ${report.errorCount}）")
    }

    private fun WorldContent.countsSummary(): String = listOf(
        "地点 ${places.size}",
        "资源 ${resources.size}",
        "产出规则 ${yieldRules.size}",
        "NPC ${npcs.size}",
        "主动规则 ${npcProactiveRules.size}",
        "台词 key ${npcLines.size}",
        "别名 ${placeAliases.size}",
        "话题 ${npcKeywords.topics.size}",
    ).joinToString("，")

    companion object {
        const val TAG = "ContentLoad"

        private const val DIR = "content"

        /** 地点覆盖层的文件名；采点写入方复用同一份常量，避免两处拼错。 */
        internal const val PLACES_FILE = "places.json"
        private const val RESOURCES = "resources.json"
        private const val YIELD_RULES = "yield_rules.json"
        private const val NPCS = "npcs.json"
        private const val NPC_PROACTIVE_RULES = "npc_proactive_rules.json"
        private const val NPC_LINES = "npc_lines.json"
        private const val PLACE_ALIASES = "place_aliases.json"
        private const val NPC_KEYWORDS = "npc_keywords.json"

        /** 覆盖层的诊断标注：同一份文件名要能区分"内置"和"你改的"。 */
        private const val OVERLAY_PLACES = "$PLACES_FILE（覆盖）"
        private const val OVERLAY_RESOURCES = "$RESOURCES（覆盖）"
        private const val OVERLAY_YIELD_RULES = "$YIELD_RULES（覆盖）"
        private const val OVERLAY_NPCS = "$NPCS（覆盖）"
        private const val OVERLAY_NPC_PROACTIVE_RULES = "$NPC_PROACTIVE_RULES（覆盖）"
        private const val OVERLAY_NPC_LINES = "$NPC_LINES（覆盖）"
        private const val OVERLAY_PLACE_ALIASES = "$PLACE_ALIASES（覆盖）"
        private const val OVERLAY_NPC_KEYWORDS = "$NPC_KEYWORDS（覆盖）"

        /** 覆盖层的真实路径（写入方与读取方共用同一处拼接，避免拼错目录）。 */
        fun overlayFile(context: Context, file: String): File =
            File(File(context.filesDir, DIR), file)
    }
}