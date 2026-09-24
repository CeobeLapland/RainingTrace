package com.rainingtrace.domain.npc

import com.rainingtrace.core.time.WORLD_ZONE
import com.rainingtrace.core.time.WorldClock
import com.rainingtrace.domain.footprint.FootprintEvent
import com.rainingtrace.domain.footprint.FootprintEventType
import com.rainingtrace.domain.footprint.FootprintRepository
import com.rainingtrace.domain.map.Place
import com.rainingtrace.domain.map.PlaceRepository
import com.rainingtrace.domain.world.RandomSource
import com.rainingtrace.domain.world.WorldState
import com.rainingtrace.domain.world.WorldStateProvider
import com.rainingtrace.domain.world.footprintKeys
import java.time.Instant
import java.util.UUID

/**
 * 玩家给某个 NPC 发一条消息，并拿到回复。
 *
 * **步骤顺序不可换**，因为它是"AI 文本不驱动状态"的机械保障：
 * 好感与情绪在调用 [NarrativeService] **之前**就算完了，输入只有解析结果与档案；
 * 回复文本无论来自模板还是将来的 LLM，都只写进消息表，不参与任何状态计算。
 */
class SendNpcMessageUseCase(
    private val clock: WorldClock,
    private val npcRepository: NpcRepository,
    private val placeRepository: PlaceRepository,
    private val npcPresence: NpcPresenceUseCase,
    private val parser: NpcMessageParser,
    private val narrative: NarrativeService,
    private val messageRepository: NpcMessageRepository,
    private val stateRepository: NpcStateRepository,
    private val footprintRepository: FootprintRepository,
    private val worldState: WorldStateProvider,
    private val random: RandomSource,
    /**
     * 口语别名表（内容侧维护，避免 domain 硬编码 placeId）。
     *
     * 按需取而不是构造时快照：开发者模式改了 `place_aliases.json`、
     * 或者刚在地图上记下一个新地点并补了别名之后，应当立刻听得懂，
     * 不该等重启。
     */
    private val placeAliases: () -> Map<String, String> = { emptyMap() },
    /** 承诺仓储（片 3）：答应了才允许说承诺词，所以它是"不骗人"的另一半。 */
    private val commitmentRepository: NpcCommitmentRepository? = null,
) {

    suspend operator fun invoke(npcId: String, text: String): SendMessageResult {
        val profile = npcRepository.byId(npcId) ?: return SendMessageResult.UnknownNpc
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return SendMessageResult.Empty
        if (trimmed.length > MAX_INPUT_LENGTH) return SendMessageResult.TooLong

        val nowInstant = clock.now()
        val now = nowInstant.toEpochMilli()
        val world = worldState.current()
        val presence = npcPresence.presenceOf(npcId, world)
        val places = placeRepository.all()
        val placesById = places.associateBy { it.id }

        // ① 玩家这条先落库，保证聊天记录的时间顺序。
        messageRepository.append(
            NpcMessage(
                id = UUID.randomUUID().toString(),
                npcId = npcId,
                speaker = NpcMessageSpeaker.PLAYER,
                text = trimmed,
                createdAtEpochMs = now,
                read = true,
                source = NpcMessageSource.PLAYER,
            ),
        )

        // ② 解析。
        val parsed = parser.parse(
            trimmed,
            ParseContext(profile, places, placeAliases(), world, presence),
        )

        // ③ 约定判定（片 3）：想见面 + 说了时间 + 说了地点 → 按**真实作息**判他能不能答应。
        val commitment = resolveCommitment(profile, placesById, parsed, world, nowInstant, now)

        // ④ 状态先算完（在 narrative 之前）。
        val todayKey = dateKeyOf(nowInstant)
        val current = applyDailyReset(stateRepository.stateOf(npcId), todayKey)
        val delta = affectionDeltaFor(parsed, profile)
        val (affection, todayGain) = affectionAfter(
            current = current.affection,
            delta = delta,
            todayGain = current.todayAffectionGain,
        )
        val moodEvent = moodFor(parsed, profile, presence)

        // ⑤ 拼上下文并生成文本。
        val recent = messageRepository.recent(npcId, RECENT_LIMIT)
        val facts = if (parsed.timeHint != null && (parsed.asksAboutSchedule || parsed.wantsToMeet)) {
            scheduleFacts(profile, placesById, parsed.timeHint, world.minuteOfDay)
        } else {
            null
        }
        val context = NpcDialogueContext(
            profile = profile,
            state = current,
            stage = current.stage,
            mood = moodEvent
                ?: effectiveMood(current.mood, current.moodSinceEpochMs, now, presence, world),
            presence = presence,
            parsed = parsed,
            worldState = world,
            recentMessages = recent,
            scheduleFacts = facts,
            commitmentFacts = commitment.facts,
            memoryHooks = memoryHooksFor(npcId, placesById, current, now),
        )
        val generated = narrative.respond(context)

        // ⑥ 守门人：不合法就换确定性回落。**承诺词只有在真的写了承诺之后才放行**——
        //    传 allowPromises 的条件就是 commitment.saved，没有别的路径。
        val recentNpcTexts = recent.filter { it.fromNpc }.map { it.text }
        val replyText = if (DialogueValidator.validate(generated.text, commitment.saved)) {
            generated.text
        } else {
            DialogueValidator.fallback(recentNpcTexts)
        }

        val reply = NpcMessage(
            id = UUID.randomUUID().toString(),
            npcId = npcId,
            speaker = NpcMessageSpeaker.NPC,
            text = replyText,
            createdAtEpochMs = now,
            read = false,
            source = generated.source,
            topic = generated.topic,
        )
        messageRepository.append(reply)

        stateRepository.save(
            current.copy(
                affection = affection,
                mood = moodEvent ?: current.mood,
                moodSinceEpochMs = if (moodEvent != null) now else current.moodSinceEpochMs,
                lastInteractionAtEpochMs = now,
                todayAffectionGain = todayGain,
                updatedAtEpochMs = now,
            ),
        )

        // ⑥ 留档：足迹是"他记得你"的唯一来源。
        presence?.let { p ->
            footprintRepository.append(
                FootprintEvent(
                    id = UUID.randomUUID().toString(),
                    timestampEpochMs = now,
                    // 记在他当时所在的地点：这是"这次说话发生在哪"的语义。
                    coordinate = p.coordinate,
                    eventType = FootprintEventType.NPC_TALKED,
                    payload = buildMap {
                        put("npcId", npcId)
                        put("placeId", p.placeId)
                        put("affectionDelta", (affection - current.affection).toString())
                        generated.topic?.let { put("topic", it.name) }
                        putAll(world.footprintKeys())
                    },
                ),
            )
        }

        return SendMessageResult.Sent(reply)
    }

    /**
     * 判定并（如果答应）落库这次约定。
     *
     * **只有"想见面 + 说了时间 + 说了地点"才会走到这里**——说不清时间或地点时
     * 他只是按片 1 的老样子软拒绝，绝不随口答应。
     */
    private suspend fun resolveCommitment(
        profile: NpcProfile,
        placesById: Map<String, Place>,
        parsed: ParsedPlayerMessage,
        world: WorldState,
        nowInstant: Instant,
        nowEpochMs: Long,
    ): CommitmentResolution {
        val timeHint = parsed.timeHint
        val placeId = parsed.mentionedPlaceId
        val repo = commitmentRepository
        if (!parsed.wantsToMeet || timeHint == null || placeId == null || repo == null) {
            return CommitmentResolution()
        }

        val placeName = placesById[placeId]?.name ?: placeId
        val targetMinute = timeHint.resolveMinuteOfDay(world.minuteOfDay)
        val answer = answerCommitment(profile, placesById, targetMinute, placeId)
        val agreed = answer is CommitmentAnswer.Agree || answer is CommitmentAnswer.AlreadyThere
        val facts = CommitmentFacts(
            timeLabel = timeHint.label,
            placeName = placeName,
            agreed = agreed,
        )
        if (!agreed) return CommitmentResolution(facts = facts, saved = false)

        repo.save(
            NpcCommitment(
                id = UUID.randomUUID().toString(),
                npcId = profile.id,
                placeId = placeId,
                dateKey = timeHint.resolveDateKey(world.localDate),
                startMinute = targetMinute,
                // 不跨零点（NpcScheduleOverride 就是这么约定的）：太晚的约定窗口会被压到当天末尾。
                endMinute = (targetMinute + COMMITMENT_WINDOW_MINUTES)
                    .coerceAtMost(MINUTES_PER_DAY - 1),
                travelMinutes = (answer as? CommitmentAnswer.Agree)?.travelMinutes ?: 0,
                createdAtEpochMs = nowEpochMs,
            ),
        )
        return CommitmentResolution(facts = facts, saved = true)
    }

    private data class CommitmentResolution(
        val facts: CommitmentFacts? = null,
        /** 承诺是否真的落库了；它决定承诺词能不能说。 */
        val saved: Boolean = false,
    )

    /**
     * "他记得你"的句子素材（最多两条，由叙事层随机取一条用）。
     *
     * 只做两件成本最低的事——不做"你上周三来过湖边吧"那种按地点扫全量足迹的句子：
     * payload 在库里是单列编码、SQL 没法按 key 过滤，成本随历史线性增长。
     */
    private suspend fun memoryHooksFor(
        npcId: String,
        placesById: Map<String, Place>,
        state: NpcState,
        nowEpochMs: Long,
    ): List<String> {
        val hooks = mutableListOf<String>()

        // 1) 距上次聊天超过一天 → "上次在「X」跟你聊过。"
        val lastTalk = footprintRepository.eventsOfType(FootprintEventType.NPC_TALKED)
            .lastOrNull { it.payload["npcId"] == npcId }
        if (lastTalk != null && nowEpochMs - lastTalk.timestampEpochMs >= ONE_DAY_MS) {
            placeNameOf(lastTalk.payload["placeId"], placesById)?.let {
                hooks += "上次在「$it」跟你聊过。"
            }
        }

        // 2) 第一次跟他说话，但之前在地图上遇见过 → 把那次遇见说出来。
        if (state.lastInteractionAtEpochMs == null) {
            val met = footprintRepository.eventsOfType(FootprintEventType.NPC_MET)
                .firstOrNull { it.payload["npcId"] == npcId }
            placeNameOf(met?.payload?.get("placeId"), placesById)?.let {
                hooks += "你是那天在「$it」跟我打招呼的。"
            }
        }

        return hooks
    }

    private fun placeNameOf(placeId: String?, placesById: Map<String, Place>): String? =
        placeId?.let { placesById[it]?.name }

    private fun dateKeyOf(instant: Instant): String =
        instant.atZone(WORLD_ZONE).toLocalDate().toString()

    companion object {
        /** 一条输入的上限；再长也不像聊天了。 */
        const val MAX_INPUT_LENGTH = 200

        /** 拼上下文时回看的消息条数。 */
        private const val RECENT_LIMIT = 20
        private const val ONE_DAY_MS = 24L * 60 * 60 * 1000
    }
}

sealed interface SendMessageResult {
    data class Sent(val reply: NpcMessage) : SendMessageResult
    object Empty : SendMessageResult
    object TooLong : SendMessageResult
    object UnknownNpc : SendMessageResult
}
