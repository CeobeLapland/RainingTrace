package com.rainingtrace.domain.npc

import com.rainingtrace.core.time.FakeWorldClock
import com.rainingtrace.domain.footprint.FootprintEvent
import com.rainingtrace.domain.footprint.FootprintEventType
import com.rainingtrace.domain.footprint.FootprintRepository
import com.rainingtrace.domain.map.Place
import com.rainingtrace.domain.map.PlaceActionType
import com.rainingtrace.domain.map.PlaceRepository
import com.rainingtrace.domain.map.PlaceType
import com.rainingtrace.domain.map.WorldCoordinate
import com.rainingtrace.domain.world.FakeWorldStateProvider
import com.rainingtrace.domain.world.SeededRandomSource
import com.rainingtrace.domain.world.WeatherKind
import com.rainingtrace.domain.world.WeatherState
import com.rainingtrace.domain.world.deriveWorldState
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SendNpcMessageUseCaseTest {

    private class FakeMessages : NpcMessageRepository {
        val all = mutableListOf<NpcMessage>()

        override fun observeConversations(): Flow<List<NpcConversation>> = flowOf(emptyList())

        override fun observeUnreadCount(): Flow<Int> = flowOf(all.count { it.fromNpc && !it.read })

        override fun observeThread(npcId: String, limit: Int): Flow<List<NpcMessage>> =
            flowOf(all.filter { it.npcId == npcId }.takeLast(limit))

        override suspend fun recent(npcId: String, limit: Int): List<NpcMessage> =
            all.filter { it.npcId == npcId }.takeLast(limit)

        override suspend fun append(message: NpcMessage) {
            all += message
        }

        override suspend fun markRead(npcId: String) {
            for (i in all.indices) {
                val m = all[i]
                if (m.npcId == npcId && m.fromNpc && !m.read) all[i] = m.copy(read = true)
            }
        }
    }

    private class FakeStates : NpcStateRepository {
        val states = mutableMapOf<String, NpcState>()

        override fun observeStates(): Flow<Map<String, NpcState>> = flowOf(states)

        override suspend fun stateOf(npcId: String): NpcState =
            states[npcId] ?: NpcState.initial(npcId)

        override suspend fun save(state: NpcState) {
            states[state.npcId] = state
        }
    }

    private class FakeFootprints : FootprintRepository {
        val events = mutableListOf<FootprintEvent>()

        override suspend fun append(event: FootprintEvent) {
            events += event
        }

        override suspend fun eventsBetween(fromEpochMs: Long, toEpochMs: Long): List<FootprintEvent> =
            events.filter { it.timestampEpochMs in fromEpochMs..toEpochMs }

        override suspend fun eventsOfType(type: FootprintEventType): List<FootprintEvent> =
            events.filter { it.eventType == type }
    }

    private class FakeCommitments : NpcCommitmentRepository {
        val all = mutableListOf<NpcCommitment>()
        override suspend fun all(): List<NpcCommitment> = all
        override suspend fun save(commitment: NpcCommitment) {
            all += commitment
        }
    }

    private class FakeNpcs(private val npcs: List<NpcProfile>) : NpcRepository {
        override suspend fun all(): List<NpcProfile> = npcs
        override suspend fun byId(id: String): NpcProfile? = npcs.firstOrNull { it.id == id }
    }

    private class FakePlaces(private val places: List<Place>) : PlaceRepository {
        override suspend fun placeById(id: String): Place? = places.firstOrNull { it.id == id }
        override suspend fun nearby(coordinate: WorldCoordinate, radiusMeters: Double): List<Place> =
            emptyList()

        override suspend fun all(): List<Place> = places
    }

    private val library = Place(
        id = "place.bit.library",
        name = "图书馆",
        type = PlaceType.LIBRARY,
        coordinate = WorldCoordinate(39.73, 116.17),
        actions = setOf(PlaceActionType.OBSERVE),
    )

    private val lin = NpcProfile(
        id = "npc.bit.lin",
        name = "林",
        oneLiner = "",
        schedule = listOf(
            NpcScheduleEntry(
                startMinute = 0,
                placeId = "place.bit.library",
                activity = "在靠窗的位置自习",
            ),
        ),
        traits = setOf(NpcTrait.TACITURN),
        topics = setOf(NpcTopic.BOOKS),
        favoriteTopic = NpcTopic.BOOKS,
    )

    private val npcs = FakeNpcs(listOf(lin))
    private val places = FakePlaces(listOf(library))

    private val nowInstant =
        LocalDate.of(2026, 9, 21).atTime(10, 0).toInstant(ZoneOffset.ofHours(8))

    private val clock = FakeWorldClock(nowInstant)

    private val worldState = FakeWorldStateProvider(
        deriveWorldState(nowInstant, WeatherState(WeatherKind.CLEAR)),
    )

    private fun useCase(
        messages: FakeMessages = FakeMessages(),
        states: FakeStates = FakeStates(),
        footprints: FakeFootprints = FakeFootprints(),
        commitments: FakeCommitments = FakeCommitments(),
        narrative: NarrativeService = TemplateNarrativeService(SeededRandomSource(1L)),
    ) = SendNpcMessageUseCase(
        clock = clock,
        npcRepository = npcs,
        placeRepository = places,
        npcPresence = NpcPresenceUseCase(npcs, places),
        parser = RuleBasedNpcMessageParser(),
        narrative = narrative,
        messageRepository = messages,
        stateRepository = states,
        footprintRepository = footprints,
        worldState = worldState,
        random = SeededRandomSource(1L),
        commitmentRepository = commitments,
    )

    private fun footprint(
        type: FootprintEventType,
        placeId: String,
        atEpochMs: Long,
    ) = FootprintEvent(
        id = "f${type.name}$atEpochMs",
        timestampEpochMs = atEpochMs,
        coordinate = library.coordinate,
        eventType = type,
        payload = mapOf("npcId" to lin.id, "placeId" to placeId),
    )

    @Test
    fun `writes the player message first and the reply second`() = runTest {
        val messages = FakeMessages()

        val result = useCase(messages = messages)("npc.bit.lin", "今天在图书馆看书")

        assertTrue(result is SendMessageResult.Sent)
        assertEquals(2, messages.all.size)
        assertEquals(NpcMessageSpeaker.PLAYER, messages.all[0].speaker)
        assertEquals(NpcMessageSpeaker.NPC, messages.all[1].speaker)
        assertFalse(messages.all[1].read)
    }

    @Test
    fun `affection comes from the parsed intent, not from the reply text`() = runTest {
        val states = FakeStates()

        // 喜欢的话题：1（聊天）+ 2（最爱）= 3
        useCase(states = states)("npc.bit.lin", "今天在图书馆看书")

        assertEquals(3, states.states.getValue(lin.id).affection)
    }

    @Test
    fun `the daily cap stops affection from being farmed`() = runTest {
        val states = FakeStates()
        val send = useCase(states = states)

        repeat(3) { send("npc.bit.lin", "今天在图书馆看书") }

        assertEquals(DAILY_AFFECTION_CAP, states.states.getValue(lin.id).affection)
    }

    @Test
    fun `the talk is recorded as a footprint with the granted delta`() = runTest {
        val footprints = FakeFootprints()

        useCase(footprints = footprints)("npc.bit.lin", "今天在图书馆看书")

        val event = footprints.events.single { it.eventType == FootprintEventType.NPC_TALKED }
        assertEquals(lin.id, event.payload["npcId"])
        assertEquals(library.id, event.payload["placeId"])
        assertEquals("3", event.payload["affectionDelta"])
    }

    @Test
    fun `a rejected narrative text falls back without touching the state`() = runTest {
        val messages = FakeMessages()
        val states = FakeStates()
        val promising = object : NarrativeService {
            override suspend fun respond(context: NpcDialogueContext) =
                GeneratedDialogue(text = "我等你")
        }

        useCase(messages = messages, states = states, narrative = promising)(
            "npc.bit.lin",
            "今天天气不错",
        )

        val reply = messages.all.last()
        assertNotEquals("我等你", reply.text)
        assertTrue(reply.text, DialogueValidator.validate(reply.text))
        // 好感只由解析结果决定（天气话题不是最爱 → 1），AI 文本影响不了它
        assertEquals(1, states.states.getValue(lin.id).affection)
    }

    @Test
    fun `the first talk after meeting references the encounter`() = runTest {
        val messages = FakeMessages()
        val footprints = FakeFootprints()
        footprints.append(footprint(FootprintEventType.NPC_MET, library.id, 0L))

        useCase(messages = messages, footprints = footprints)("npc.bit.lin", "今天在图书馆看书")

        assertTrue(messages.all.last().text, "打招呼" in messages.all.last().text)
    }

    @Test
    fun `a later talk references where we last spoke`() = runTest {
        val messages = FakeMessages()
        val footprints = FakeFootprints()
        val twoDaysAgo = nowInstant.toEpochMilli() - 2L * 24 * 60 * 60 * 1000
        footprints.append(footprint(FootprintEventType.NPC_TALKED, library.id, twoDaysAgo))

        useCase(messages = messages, footprints = footprints)("npc.bit.lin", "今天在图书馆看书")

        assertTrue(messages.all.last().text, "上次在「图书馆」" in messages.all.last().text)
    }

    @Test
    fun `empty input and unknown npc are rejected without writing anything`() = runTest {
        val messages = FakeMessages()
        val send = useCase(messages = messages)

        assertEquals(SendMessageResult.Empty, send("npc.bit.lin", "   "))
        assertEquals(SendMessageResult.UnknownNpc, send("npc.nobody", "在吗"))
        assertTrue(messages.all.isEmpty())
    }

    @Test
    fun `the interaction time is stored so idle triggers can use it`() = runTest {
        val states = FakeStates()

        useCase(states = states)("npc.bit.lin", "今天在图书馆看书")

        assertEquals(nowInstant.toEpochMilli(), states.states.getValue(lin.id).lastInteractionAtEpochMs)
    }

    // ---- 片 3：约定 ----

    @Test
    fun `he agrees and the promise is actually written down`() = runTest {
        val messages = FakeMessages()
        val commitments = FakeCommitments()

        // 他整天都在图书馆，所以"明天下午在图书馆见"他答应得了
        useCase(messages = messages, commitments = commitments)(
            "npc.bit.lin",
            "明天下午在图书馆等我",
        )

        val saved = commitments.all.single()
        assertEquals(lin.id, saved.npcId)
        assertEquals(library.id, saved.placeId)
        assertEquals("2026-09-22", saved.dateKey) // 明天
        assertEquals(14 * 60, saved.startMinute)
        assertEquals(NpcCommitmentStatus.AGREED, saved.status)

        // 承诺词只有在真的写进库之后才被放行，所以这里能说到地点
        val reply = messages.all.last()
        assertTrue(reply.text, "图书馆" in reply.text)
    }

    @Test
    fun `no commitment is written when the time or place is unclear`() = runTest {
        val messages = FakeMessages()
        val commitments = FakeCommitments()
        val send = useCase(messages = messages, commitments = commitments)

        // 没地点
        send("npc.bit.lin", "明天下午来找我吧")
        // 没时间
        send("npc.bit.lin", "在图书馆等我")
        // 只提到地点、不想见面
        send("npc.bit.lin", "我今天在图书馆看书")

        assertTrue(commitments.all.isEmpty())
        messages.all.filter { it.fromNpc }.forEach {
            assertFalse("不该出现承诺词：${it.text}", it.text.contains("说好了"))
        }
    }
}
