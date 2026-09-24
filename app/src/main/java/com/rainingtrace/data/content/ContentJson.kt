package com.rainingtrace.data.content

import com.rainingtrace.domain.content.ContentReport
import com.rainingtrace.domain.inventory.Rarity
import com.rainingtrace.domain.inventory.ResourceCategory
import com.rainingtrace.domain.inventory.ResourceDefinition
import com.rainingtrace.domain.map.Place
import com.rainingtrace.domain.map.PlaceActionType
import com.rainingtrace.domain.map.PlaceType
import com.rainingtrace.domain.map.WorldCoordinate
import com.rainingtrace.domain.npc.NpcProfile
import com.rainingtrace.domain.npc.NpcProactiveRule
import com.rainingtrace.domain.npc.NpcKeywordRules
import com.rainingtrace.domain.npc.NpcScheduleEntry
import com.rainingtrace.domain.npc.NpcTopic
import com.rainingtrace.domain.npc.NpcTrait
import com.rainingtrace.domain.npc.NpcTriggerCondition
import com.rainingtrace.domain.npc.TimeHint
import com.rainingtrace.domain.world.ResourceYieldRule
import com.rainingtrace.domain.world.Season
import com.rainingtrace.domain.world.TimeOfDay
import com.rainingtrace.domain.world.WeatherKind
import com.rainingtrace.domain.world.WorldCondition
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * 内容 JSON 的读写（GDD §21 的数据形态）。
 *
 * 三条硬规则：
 * 1. **枚举按名字解析，忽略大小写与首尾空白**，解析失败的错误里列出全部合法值
 *    ——手写 JSON 最常见的错就是把 `LIGHT_RAIN` 写成 `rain`。
 * 2. **逐条解析**：一条写坏只丢这一条，不让整份文件陪葬。
 * 3. **领域模型自己就是校验器**：DTO → 领域对象的构造全包在 `runCatching` 里，
 *    `Place.init` / `WeatherIn.init` 那些 `require` 自动变成一条诊断而不是崩溃。
 */
internal val ContentJsonFormat = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    encodeDefaults = true
}

/**
 * 实体型内容文件的统一根结构（地点 / 资源 / 产出规则 / NPC / 主动规则）。
 *
 * `entries` 收 [JsonElement] 而不是具体类型，是为了让每条单独解析——
 * 这样"某一条少了 lat"只会丢那一条。
 */
@Serializable
internal data class EntityFileEnvelope(
    val schemaVersion: Int = 1,
    val removedIds: List<String> = emptyList(),
    val entries: List<JsonElement> = emptyList(),
)

@Serializable
internal data class PlaceDto(
    val id: String = "",
    val name: String = "",
    val type: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val actions: List<String> = emptyList(),
    val description: String = "",
)

internal data class DecodedEntries<T>(
    val entries: List<T>,
    val removedIds: List<String>,
)

/** 记事本存出来的 UTF-8 会带 BOM，不去掉的话第一行的 key 就认不出来了。 */
internal fun stripBom(text: String): String =
    if (text.startsWith('\uFEFF')) text.substring(1) else text

internal fun <T : Enum<T>> parseEnum(raw: String, values: List<T>): T? {
    val trimmed = raw.trim()
    return values.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
}

internal fun <T : Enum<T>> legalValues(values: List<T>): String =
    values.joinToString(" / ") { it.name }

/**
 * 读实体型文件的信封，并把每条交给 [decodeEntry] 单独解析。
 * 文件级 JSON 语法错误会丢掉整个文件（没办法），条目级错误只丢那一条。
 */
internal fun <T> decodeEntityFile(
    text: String,
    file: String,
    report: ContentReport,
    decodeEntry: (JsonElement, String, ContentReport) -> T?,
): DecodedEntries<T> {
    val envelope = runCatching {
        ContentJsonFormat.decodeFromString<EntityFileEnvelope>(stripBom(text))
    }.getOrElse {
        report.error(file, null, "JSON 读不出来：${it.message}")
        return DecodedEntries(emptyList(), emptyList())
    }
    val entries = envelope.entries.mapNotNull { element -> decodeEntry(element, file, report) }
    return DecodedEntries(entries, envelope.removedIds)
}

internal fun decodePlaceEntries(
    text: String,
    file: String,
    report: ContentReport,
): DecodedEntries<Place> = decodeEntityFile(text, file, report) { element, f, r ->
    val dto = runCatching {
        ContentJsonFormat.decodeFromJsonElement(PlaceDto.serializer(), element)
    }.getOrElse {
        r.error(f, null, "条目读不出来：${it.message}")
        return@decodeEntityFile null
    }
    decodePlace(dto, f, r)
}

internal fun decodePlace(dto: PlaceDto, file: String, report: ContentReport): Place? {
    val type = parseEnum(dto.type, PlaceType.entries)
    if (type == null) {
        report.error(file, dto.id, "未知的 type「${dto.type}」，合法值：${legalValues(PlaceType.entries)}")
        return null
    }

    val actions = mutableSetOf<PlaceActionType>()
    dto.actions.forEach { raw ->
        val parsed = parseEnum(raw, PlaceActionType.entries)
        if (parsed == null) {
            report.error(
                file,
                dto.id,
                "未知的 action「$raw」，合法值：${legalValues(PlaceActionType.entries)}",
            )
        } else {
            actions += parsed
        }
    }

    return runCatching {
        Place(
            id = dto.id,
            name = dto.name,
            type = type,
            coordinate = WorldCoordinate(dto.lat, dto.lng),
            actions = actions,
            description = dto.description,
        )
    }.getOrElse {
        report.error(file, dto.id, "地点不合法：${it.message}")
        null
    }
}

/** 反向映射：写覆盖层时用（GDD §21 的"玩家能自己配"）。 */
internal fun Place.toDto(): PlaceDto = PlaceDto(
    id = id,
    name = name,
    type = type.name,
    lat = coordinate.latDegrees,
    lng = coordinate.lngDegrees,
    actions = actions.map { it.name }.sorted(),
    description = description,
)

// ---------------------------------------------------------------------------
// 资源
// ---------------------------------------------------------------------------

@Serializable
internal data class ResourceDto(
    val id: String = "",
    val name: String = "",
    val category: String = "",
    val rarity: String = "",
    val tags: List<String> = emptyList(),
    val description: String = "",
)

internal fun decodeResourceEntries(
    text: String,
    file: String,
    report: ContentReport,
): DecodedEntries<ResourceDefinition> = decodeEntityFile(text, file, report) { element, f, r ->
    val dto = runCatching {
        ContentJsonFormat.decodeFromJsonElement(ResourceDto.serializer(), element)
    }.getOrElse {
        r.error(f, null, "条目读不出来：${it.message}")
        return@decodeEntityFile null
    }
    decodeResource(dto, f, r)
}

internal fun decodeResource(
    dto: ResourceDto,
    file: String,
    report: ContentReport,
): ResourceDefinition? {
    val category = parseEnum(dto.category, ResourceCategory.entries)
    if (category == null) {
        report.error(
            file,
            dto.id,
            "未知的 category「${dto.category}」，合法值：${legalValues(ResourceCategory.entries)}",
        )
        return null
    }
    val rarity = parseEnum(dto.rarity, Rarity.entries)
    if (rarity == null) {
        report.error(
            file,
            dto.id,
            "未知的 rarity「${dto.rarity}」，合法值：${legalValues(Rarity.entries)}",
        )
        return null
    }
    return runCatching {
        ResourceDefinition(
            id = dto.id,
            name = dto.name,
            category = category,
            rarity = rarity,
            tags = dto.tags.map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
            description = dto.description,
        )
    }.getOrElse {
        report.error(file, dto.id, "资源不合法：${it.message}")
        null
    }
}

// ---------------------------------------------------------------------------
// 世界状态条件（判别式联合：靠 type 字段分辨是哪一种）
// ---------------------------------------------------------------------------

/** `type` 的合法值（大小写不敏感），错误消息里原样列出，方便手写的人对照。 */
private val CONDITION_TYPES =
    listOf("weatherIn", "timeOfDayIn", "seasonIn", "betweenMinutes", "all", "not")

/**
 * "会下雨"的简写。代码里 `WeatherKind.RAINY` 是一个真实概念，
 * 手写规则时一定会有人写 `RAINY`，与其报错不如认它。
 */
private const val RAINY_SHORTHAND = "RAINY"

/**
 * 一个 DTO 装下所有条件类型：用可选字段而不是多态序列化，
 * 好处是未知字段/写错字段能给出人话报错，而不是一堆 serializer 异常。
 */
@Serializable
internal data class WorldConditionDto(
    val type: String = "",
    /** weatherIn */
    val kinds: List<String> = emptyList(),
    /** timeOfDayIn */
    val times: List<String> = emptyList(),
    /** seasonIn */
    val seasons: List<String> = emptyList(),
    /** betweenMinutes：当天第几分钟，左闭右开；end < start 表示跨零点。 */
    val start: Int? = null,
    val end: Int? = null,
    /** all：全部满足（AND）。 */
    val conditions: List<WorldConditionDto> = emptyList(),
    /** not：取反。 */
    val condition: WorldConditionDto? = null,
)

internal fun decodeWorldCondition(
    dto: WorldConditionDto,
    file: String,
    entryId: String?,
    report: ContentReport,
): WorldCondition? {
    val type = dto.type.trim().lowercase()
    if (CONDITION_TYPES.none { it.equals(dto.type.trim(), ignoreCase = true) }) {
        report.error(
            file,
            entryId,
            "未知的条件类型「${dto.type}」，合法值：${CONDITION_TYPES.joinToString(" / ")}",
        )
        return null
    }
    // 领域条件的 require 就是校验（空集合、空时间窗都会被挡下），
    // 所以这里不重复写规则，只把异常翻译成一条诊断。
    return runCatching {
        when (type) {
            "weatherin" -> WorldCondition.WeatherIn(parseWeatherKinds(dto.kinds, file, entryId, report))
            "timeofdayin" -> WorldCondition.TimeOfDayIn(
                parseEnumSet(dto.times, TimeOfDay.entries, "时段", file, entryId, report),
            )
            "seasonin" -> WorldCondition.SeasonIn(
                parseEnumSet(dto.seasons, Season.entries, "季节", file, entryId, report),
            )
            "betweenminutes" -> WorldCondition.BetweenMinutes(
                requireNotNull(dto.start) { "betweenMinutes 需要 start（当天第几分钟）" },
                requireNotNull(dto.end) { "betweenMinutes 需要 end（当天第几分钟）" },
            )
            "not" -> WorldCondition.Not(
                requireNotNull(
                    decodeWorldCondition(
                        requireNotNull(dto.condition) { "not 需要 condition" },
                        file,
                        entryId,
                        report,
                    ),
                ) { "not 的内部条件解析失败" },
            )
            else -> WorldCondition.All(
                dto.conditions.mapNotNull { decodeWorldCondition(it, file, entryId, report) },
            )
        }
    }.getOrElse {
        report.error(file, entryId, "条件不合法：${it.message}")
        null
    }
}

internal fun parseWeatherKinds(
    raw: List<String>,
    file: String,
    entryId: String?,
    report: ContentReport,
): Set<WeatherKind> {
    val result = mutableSetOf<WeatherKind>()
    raw.forEach { item ->
        if (item.trim().equals(RAINY_SHORTHAND, ignoreCase = true)) {
            result += WeatherKind.RAINY
        } else {
            val parsed = parseEnum(item, WeatherKind.entries)
            if (parsed == null) {
                report.error(
                    file,
                    entryId,
                    "未知的天气「${item.trim()}」，合法值：${legalValues(WeatherKind.entries)} 或 $RAINY_SHORTHAND",
                )
            } else {
                result += parsed
            }
        }
    }
    return result
}

internal fun <T : Enum<T>> parseEnumSet(
    raw: List<String>,
    values: List<T>,
    label: String,
    file: String,
    entryId: String?,
    report: ContentReport,
): Set<T> {
    val result = mutableSetOf<T>()
    raw.forEach { item ->
        val parsed = parseEnum(item, values)
        if (parsed == null) {
            report.error(
                file,
                entryId,
                "未知的$label「${item.trim()}」，合法值：${legalValues(values)}",
            )
        } else {
            result += parsed
        }
    }
    return result
}

// ---------------------------------------------------------------------------
// 产出规则
// ---------------------------------------------------------------------------

@Serializable
internal data class YieldRuleDto(
    val id: String = "",
    val resourceId: String = "",
    val action: String = "",
    /** 省略 = 任意地点类型。 */
    val placeType: String? = null,
    val conditions: List<WorldConditionDto> = emptyList(),
    val amount: Int = 1,
    val cooldownMs: Long = ResourceYieldRule.DEFAULT_COOLDOWN_MS,
)

internal fun decodeYieldRuleEntries(
    text: String,
    file: String,
    report: ContentReport,
): DecodedEntries<ResourceYieldRule> = decodeEntityFile(text, file, report) { element, f, r ->
    val dto = runCatching {
        ContentJsonFormat.decodeFromJsonElement(YieldRuleDto.serializer(), element)
    }.getOrElse {
        r.error(f, null, "条目读不出来：${it.message}")
        return@decodeEntityFile null
    }
    decodeYieldRule(dto, f, r)
}

internal fun decodeYieldRule(
    dto: YieldRuleDto,
    file: String,
    report: ContentReport,
): ResourceYieldRule? {
    val action = parseEnum(dto.action, PlaceActionType.entries)
    if (action == null) {
        report.error(
            file,
            dto.id,
            "未知的 action「${dto.action}」，合法值：${legalValues(PlaceActionType.entries)}",
        )
        return null
    }
    var placeType: PlaceType? = null
    if (dto.placeType != null) {
        placeType = parseEnum(dto.placeType, PlaceType.entries)
        if (placeType == null) {
            report.error(
                file,
                dto.id,
                "未知的 placeType「${dto.placeType}」，合法值：${legalValues(PlaceType.entries)}",
            )
            return null
        }
    }
    val conditions = dto.conditions.mapNotNull { decodeWorldCondition(it, file, dto.id, report) }

    return runCatching {
        ResourceYieldRule(
            id = dto.id,
            resourceId = dto.resourceId,
            action = action,
            placeType = placeType,
            conditions = conditions,
            amount = dto.amount,
            cooldownMs = dto.cooldownMs,
        )
    }.getOrElse {
        report.error(file, dto.id, "产出规则不合法：${it.message}")
        null
    }
}

// ---------------------------------------------------------------------------
// NPC 档案
// ---------------------------------------------------------------------------

@Serializable
internal data class NpcScheduleEntryDto(
    val startMinute: Int = -1,
    val placeId: String = "",
    val travelMinutes: Int = 0,
    val activity: String = "",
)

@Serializable
internal data class NpcProfileDto(
    val id: String = "",
    val name: String = "",
    val oneLiner: String = "",
    val role: String = "",
    val traits: List<String> = emptyList(),
    val topics: List<String> = emptyList(),
    val favoriteTopic: String? = null,
    val backstory: List<String> = emptyList(),
    val schedule: List<NpcScheduleEntryDto> = emptyList(),
)

internal fun decodeNpcEntries(
    text: String,
    file: String,
    report: ContentReport,
): DecodedEntries<NpcProfile> = decodeEntityFile(text, file, report) { element, f, r ->
    val dto = runCatching {
        ContentJsonFormat.decodeFromJsonElement(NpcProfileDto.serializer(), element)
    }.getOrElse {
        r.error(f, null, "条目读不出来：${it.message}")
        return@decodeEntityFile null
    }
    decodeNpc(dto, f, r)
}

/**
 * 性格与话题写错时**只丢那一个成员**，NPC 本身保留：
 * 它们只影响语气，不该因为一个笔误让整位 NPC 从世界里消失。
 */
internal fun decodeNpc(
    dto: NpcProfileDto,
    file: String,
    report: ContentReport,
): NpcProfile? {
    val traits = mutableSetOf<NpcTrait>()
    dto.traits.forEach { raw ->
        val parsed = parseEnum(raw, NpcTrait.entries)
        if (parsed == null) {
            report.warn(file, dto.id, "未知的性格「${raw.trim()}」，已忽略；合法值：${legalValues(NpcTrait.entries)}")
        } else {
            traits += parsed
        }
    }

    val topics = mutableSetOf<NpcTopic>()
    dto.topics.forEach { raw ->
        val parsed = parseEnum(raw, NpcTopic.entries)
        if (parsed == null) {
            report.warn(file, dto.id, "未知的话题「${raw.trim()}」，已忽略；合法值：${legalValues(NpcTopic.entries)}")
        } else {
            topics += parsed
        }
    }

    var favoriteTopic: NpcTopic? = null
    if (dto.favoriteTopic != null) {
        favoriteTopic = parseEnum(dto.favoriteTopic, NpcTopic.entries)
        if (favoriteTopic == null) {
            report.warn(
                file,
                dto.id,
                "未知的 favoriteTopic「${dto.favoriteTopic}」，已忽略；合法值：${legalValues(NpcTopic.entries)}",
            )
        }
    }

    // favoriteTopic ∉ topics 会被 NpcProfile 的 require 拦下（那正是我们要的：
    // 它会让专属台词永远不出现，属于"静默失效"，必须响亮）。
    // 作息条目的 startMinute 越界同理。所以整块构建都放进 runCatching——
    // 漏在外面的话，一条写坏的作息会在 AppContainer 构造期直接把 App 崩掉。
    return runCatching {
        val schedule = dto.schedule.map { entry ->
            NpcScheduleEntry(
                startMinute = entry.startMinute,
                placeId = entry.placeId,
                travelMinutes = entry.travelMinutes,
                activity = entry.activity,
            )
        }
        NpcProfile(
            id = dto.id,
            name = dto.name,
            oneLiner = dto.oneLiner,
            schedule = schedule,
            role = dto.role,
            traits = traits,
            topics = topics,
            favoriteTopic = favoriteTopic,
            backstory = dto.backstory,
        )
    }.getOrElse {
        report.error(file, dto.id, "NPC 不合法：${it.message}")
        null
    }
}

// ---------------------------------------------------------------------------
// NPC 主动消息规则
// ---------------------------------------------------------------------------

/** `type` 的合法值；错误消息里原样列出。 */
private val TRIGGER_TYPES = listOf(
    "world", "weatherBecame", "playerEnteredPlace",
    "playerMetNpc", "playerIdleFor", "playerMissedCommitment", "all",
)

/** 一个 DTO 装下所有触发条件，理由同 [WorldConditionDto]。 */
@Serializable
internal data class NpcTriggerDto(
    val type: String = "",
    /** world */
    val condition: WorldConditionDto? = null,
    /** weatherBecame */
    val kinds: List<String> = emptyList(),
    /** playerEnteredPlace */
    val placeType: String? = null,
    /** playerMetNpc / playerMissedCommitment */
    val npcId: String? = null,
    /** playerIdleFor */
    val days: Int? = null,
    /** all */
    val conditions: List<NpcTriggerDto> = emptyList(),
)

@Serializable
internal data class NpcProactiveRuleDto(
    val id: String = "",
    val npcId: String = "",
    val text: String = "",
    val cooldownMs: Long = NpcProactiveRule.DEFAULT_COOLDOWN_MS,
    val priority: Int = 0,
    val condition: NpcTriggerDto? = null,
)

internal fun decodeNpcProactiveRuleEntries(
    text: String,
    file: String,
    report: ContentReport,
): DecodedEntries<NpcProactiveRule> = decodeEntityFile(text, file, report) { element, f, r ->
    val dto = runCatching {
        ContentJsonFormat.decodeFromJsonElement(NpcProactiveRuleDto.serializer(), element)
    }.getOrElse {
        r.error(f, null, "条目读不出来：${it.message}")
        return@decodeEntityFile null
    }
    decodeNpcProactiveRule(dto, f, r)
}

internal fun decodeNpcProactiveRule(
    dto: NpcProactiveRuleDto,
    file: String,
    report: ContentReport,
): NpcProactiveRule? {
    val triggerDto = dto.condition
    if (triggerDto == null) {
        report.error(file, dto.id, "缺少 condition")
        return null
    }
    val condition = decodeNpcTrigger(triggerDto, file, dto.id, report) ?: return null

    return runCatching {
        NpcProactiveRule(
            id = dto.id,
            npcId = dto.npcId,
            condition = condition,
            text = dto.text,
            cooldownMs = dto.cooldownMs,
            priority = dto.priority,
        )
    }.getOrElse {
        report.error(file, dto.id, "主动消息规则不合法：${it.message}")
        null
    }
}

internal fun decodeNpcTrigger(
    dto: NpcTriggerDto,
    file: String,
    entryId: String?,
    report: ContentReport,
): NpcTriggerCondition? {
    val type = dto.type.trim().lowercase()
    if (TRIGGER_TYPES.none { it.equals(dto.type.trim(), ignoreCase = true) }) {
        report.error(
            file,
            entryId,
            "未知的触发类型「${dto.type}」，合法值：${TRIGGER_TYPES.joinToString(" / ")}",
        )
        return null
    }
    return runCatching {
        when (type) {
            "world" -> NpcTriggerCondition.World(
                decodeWorldCondition(
                    requireNotNull(dto.condition) { "world 需要 condition" },
                    file,
                    entryId,
                    report,
                ) ?: error("world 的 condition 解析失败"),
            )
            "weatherbecame" -> NpcTriggerCondition.WeatherBecame(
                parseWeatherKinds(dto.kinds, file, entryId, report),
            )
            "playerenteredplace" -> NpcTriggerCondition.PlayerEnteredPlace(
                requireNotNull(
                    parseEnum(dto.placeType.orEmpty(), PlaceType.entries),
                ) { "未知的 placeType「${dto.placeType}」，合法值：${legalValues(PlaceType.entries)}" },
            )
            "playermetnpc" -> NpcTriggerCondition.PlayerMetNpc(
                requireNotNull(dto.npcId) { "playerMetNpc 需要 npcId" },
            )
            "playeridlefor" -> NpcTriggerCondition.PlayerIdleFor(
                requireNotNull(dto.days) { "playerIdleFor 需要 days" },
            )
            "playermissedcommitment" -> NpcTriggerCondition.PlayerMissedCommitment(
                requireNotNull(dto.npcId) { "playerMissedCommitment 需要 npcId" },
            )
            else -> NpcTriggerCondition.All(
                dto.conditions.mapNotNull { decodeNpcTrigger(it, file, entryId, report) },
            )
        }
    }.getOrElse {
        report.error(file, entryId, "触发条件不合法：${it.message}")
        null
    }
}

// ---------------------------------------------------------------------------
// 映射型内容：台词表与地点别名
// ---------------------------------------------------------------------------

internal data class DecodedMap<V>(
    val entries: Map<String, V>,
    val removedKeys: List<String>,
)

@Serializable
internal data class LinesFileDto(
    val schemaVersion: Int = 1,
    val removedKeys: List<String> = emptyList(),
    val lines: Map<String, List<String>> = emptyMap(),
)

@Serializable
internal data class AliasesFileDto(
    val schemaVersion: Int = 1,
    val removedAliases: List<String> = emptyList(),
    val aliases: Map<String, String> = emptyMap(),
)

/**
 * 台词表：**空变体列表的 key 直接丢掉**并报错。
 *
 * 理由：`TemplateNarrativeService` 会从这些列表里随机取一条，
 * 空列表会让它在运行时除零/越界。丢掉 + 报错比崩掉好，
 * 而它自己的结构性兜底（见该类的 `FALLBACK_LINES`）保证还有话可说。
 */
internal fun decodeLineEntries(
    text: String,
    file: String,
    report: ContentReport,
): DecodedMap<List<String>> {
    val dto = runCatching {
        ContentJsonFormat.decodeFromString<LinesFileDto>(stripBom(text))
    }.getOrElse {
        report.error(file, null, "JSON 读不出来：${it.message}")
        return DecodedMap(emptyMap(), emptyList())
    }

    val lines = dto.lines.mapNotNull { (key, variants) ->
        val cleaned = variants.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleaned.isEmpty()) {
            report.error(file, key, "这个 key 没有任何台词，已忽略")
            null
        } else {
            key to cleaned
        }
    }.toMap()

    return DecodedMap(lines, dto.removedKeys)
}

internal fun decodeAliasEntries(
    text: String,
    file: String,
    report: ContentReport,
): DecodedMap<String> {
    val dto = runCatching {
        ContentJsonFormat.decodeFromString<AliasesFileDto>(stripBom(text))
    }.getOrElse {
        report.error(file, null, "JSON 读不出来：${it.message}")
        return DecodedMap(emptyMap(), emptyList())
    }

    val aliases = dto.aliases.mapNotNull { (alias, placeId) ->
        val key = alias.trim()
        if (key.isEmpty()) {
            report.error(file, alias, "别名为空，已忽略")
            null
        } else {
            key to placeId.trim()
        }
    }.toMap()

    return DecodedMap(aliases, dto.removedAliases)
}

// ---------------------------------------------------------------------------
// NPC 解析关键词表（整块内容，不做逐条合并）
// ---------------------------------------------------------------------------

@Serializable
internal data class NpcKeywordsFileDto(
    val schemaVersion: Int = 1,
    val topics: Map<String, List<String>> = emptyMap(),
    val times: List<NpcTimeKeywordDto> = emptyList(),
    val meet: List<String> = emptyList(),
    val scheduleQuestions: List<String> = emptyList(),
    val questionMarkers: List<String> = emptyList(),
)

@Serializable
internal data class NpcTimeKeywordDto(
    val text: String = "",
    val hint: String = "",
)

/**
 * 关键词表**整块替换**（不按条目合并）：它是一套互相配合的匹配规则，
 * 半份新半份旧比"整份换掉"更难理解。返回 null 表示读不出来，
 * 调用方保留上一份好数据。
 */
internal fun decodeNpcKeywords(
    text: String,
    file: String,
    report: ContentReport,
): NpcKeywordRules? {
    val dto = runCatching {
        ContentJsonFormat.decodeFromString<NpcKeywordsFileDto>(stripBom(text))
    }.getOrElse {
        report.error(file, null, "JSON 读不出来：${it.message}")
        return null
    }

    val topics = dto.topics.mapNotNull { (rawTopic, keywords) ->
        val topic = parseEnum(rawTopic, NpcTopic.entries)
        if (topic == null) {
            report.error(
                file,
                rawTopic,
                "未知的话题「$rawTopic」，合法值：${legalValues(NpcTopic.entries)}",
            )
            null
        } else {
            topic to keywords.map { it.trim() }.filter { it.isNotEmpty() }
        }
    }.toMap()

    val times = dto.times.mapNotNull { entry ->
        val hint = parseEnum(entry.hint, TimeHint.entries)
        if (hint == null) {
            report.error(
                file,
                entry.text,
                "未知的时间提示「${entry.hint}」，合法值：${legalValues(TimeHint.entries)}",
            )
            null
        } else {
            entry.text.trim() to hint
        }
    }

    // 顺序敏感（"明天下午"必须排在"明天"前面），所以这里**不排序**，
    // 保留文件里的顺序——那是作者的意图。
    return NpcKeywordRules(
        topics = topics,
        times = times,
        meet = dto.meet.map { it.trim() }.filter { it.isNotEmpty() },
        scheduleQuestions = dto.scheduleQuestions.map { it.trim() }.filter { it.isNotEmpty() },
        questionMarkers = dto.questionMarkers.map { it.trim() }.filter { it.isNotEmpty() },
    ).also {
        if (it.topics.isEmpty()) report.warn(file, null, "关键词表里一个话题都没有，NPC 会听不懂所有话")
        if (it.meet.isEmpty()) report.warn(file, null, "没有任何\"想见面\"的说法，约定功能会失效")
    }
}