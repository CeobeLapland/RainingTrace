package com.rainingtrace.domain.npc

import com.rainingtrace.domain.map.WorldCoordinate
import com.rainingtrace.domain.world.WeatherKind
import com.rainingtrace.domain.world.WeatherState
import com.rainingtrace.domain.world.deriveWorldState
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NpcAffectionTest {

    private fun stateAt(hour: Int, weather: WeatherKind = WeatherKind.CLEAR) = deriveWorldState(
        instant = LocalDate.of(2026, 9, 21).atTime(hour, 0).toInstant(ZoneOffset.ofHours(8)),
        weather = WeatherState(weather),
    )

    private fun presence(walking: Boolean) = NpcPresence(
        npcId = "npc.x",
        npcName = "甲",
        coordinate = WorldCoordinate(39.0, 116.0),
        placeId = "p",
        placeName = "地点",
        activity = "做点什么",
        walking = walking,
        progress = 0.0,
    )

    private val plain = NpcProfile(id = "npc.n", name = "名", oneLiner = "", schedule = emptyList())

    private val curious = NpcProfile(
        id = "npc.x",
        name = "甲",
        oneLiner = "",
        schedule = emptyList(),
        traits = setOf(NpcTrait.CURIOUS),
        topics = setOf(NpcTopic.BOOKS),
        favoriteTopic = NpcTopic.BOOKS,
    )

    @Test
    fun `chat gives one point`() {
        val parsed = ParsedPlayerMessage(raw = "嗯", mentionedPlaceId = "p", confidence = 1.0)

        assertEquals(1, affectionDeltaFor(parsed, plain))
    }

    @Test
    fun `favorite topic gives extra`() {
        val parsed = ParsedPlayerMessage(raw = "书", topics = setOf(NpcTopic.BOOKS), confidence = 1.0)

        assertEquals(3, affectionDeltaFor(parsed, curious))
    }

    @Test
    fun `a curious npc likes questions`() {
        val parsed = ParsedPlayerMessage(
            raw = "在吗",
            mentionedPlaceId = "p",
            isQuestion = true,
            confidence = 1.0,
        )

        assertEquals(2, affectionDeltaFor(parsed, curious))
    }

    @Test
    fun `not understood gives nothing`() {
        assertEquals(0, affectionDeltaFor(ParsedPlayerMessage(raw = "嗯嗯"), curious))
    }

    @Test
    fun `daily cap limits how much can be gained today`() {
        val (affection, gain) = affectionAfter(current = 20, delta = 3, todayGain = 4)

        assertEquals(21, affection)
        assertEquals(DAILY_AFFECTION_CAP, gain)
    }

    @Test
    fun `nothing is gained once the cap is reached`() {
        val (affection, gain) = affectionAfter(current = 20, delta = 3, todayGain = DAILY_AFFECTION_CAP)

        assertEquals(20, affection)
        assertEquals(DAILY_AFFECTION_CAP, gain)
    }

    @Test
    fun `affection never exceeds the maximum`() {
        val (affection, _) = affectionAfter(current = 99, delta = 5, todayGain = 0)

        assertEquals(AFFECTION_MAX, affection)
    }

    @Test
    fun `daily reset only happens on a new day`() {
        val state = NpcState(npcId = "n", todayAffectionGain = 4, todayDateKey = "2026-09-21")

        assertEquals(4, applyDailyReset(state, "2026-09-21").todayAffectionGain)
        assertEquals(0, applyDailyReset(state, "2026-09-22").todayAffectionGain)
    }

    @Test
    fun `relationship stages follow the thresholds`() {
        assertEquals(RelationshipStage.STRANGER, RelationshipStage.of(9))
        assertEquals(RelationshipStage.NODDING, RelationshipStage.of(10))
        assertEquals(RelationshipStage.NODDING, RelationshipStage.of(29))
        assertEquals(RelationshipStage.ACQUAINTED, RelationshipStage.of(30))
        assertEquals(RelationshipStage.ACQUAINTED, RelationshipStage.of(69))
        assertEquals(RelationshipStage.FRIEND, RelationshipStage.of(70))
    }

    @Test
    fun `baseline mood follows the world and walking`() {
        assertEquals(NpcMood.BUSY, baselineMoodOf(presence(walking = true), stateAt(12)))
        assertEquals(NpcMood.TIRED, baselineMoodOf(presence(false), stateAt(22)))
        assertEquals(NpcMood.DOWN, baselineMoodOf(presence(false), stateAt(22, WeatherKind.LIGHT_RAIN)))
        assertEquals(NpcMood.CALM, baselineMoodOf(presence(false), stateAt(12)))
    }

    @Test
    fun `event mood decays back to the baseline`() {
        val state = stateAt(12)

        assertEquals(
            NpcMood.GLAD,
            effectiveMood(NpcMood.GLAD, 0L, MOOD_DECAY_MS - 1, presence(false), state),
        )
        assertEquals(
            NpcMood.CALM,
            effectiveMood(NpcMood.GLAD, 0L, MOOD_DECAY_MS, presence(false), state),
        )
    }

    @Test
    fun `mood only changes on notable input`() {
        assertNull(
            moodFor(ParsedPlayerMessage(raw = "嗯", mentionedPlaceId = "p"), curious, presence(false)),
        )
        assertEquals(
            NpcMood.GLAD,
            moodFor(
                ParsedPlayerMessage(raw = "书", topics = setOf(NpcTopic.BOOKS)),
                curious,
                presence(false),
            ),
        )
    }
}
