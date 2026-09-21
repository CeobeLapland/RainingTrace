package com.rainingtrace.domain.npc

import com.rainingtrace.domain.map.Place
import com.rainingtrace.domain.map.PlaceActionType
import com.rainingtrace.domain.map.PlaceType
import com.rainingtrace.domain.map.WorldCoordinate
import com.rainingtrace.domain.world.WeatherKind
import com.rainingtrace.domain.world.WeatherState
import com.rainingtrace.domain.world.deriveWorldState
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleBasedNpcMessageParserTest {

    private fun place(id: String, name: String) = Place(
        id = id,
        name = name,
        type = PlaceType.OTHER,
        coordinate = WorldCoordinate(39.0, 116.0),
        actions = setOf(PlaceActionType.OBSERVE),
    )

    private val places = listOf(
        place("place.lake", "北湖"),
        place("place.library", "图书馆"),
        place("place.garden", "湖心花园"),
    )

    private val aliases = mapOf(
        "湖边" to "place.lake",
        "花园" to "place.garden",
        "湖心花园" to "place.garden",
    )

    private val profile = NpcProfile(id = "npc.x", name = "甲", oneLiner = "", schedule = emptyList())

    private val parser = RuleBasedNpcMessageParser()

    private fun context() = ParseContext(
        npc = profile,
        places = places,
        placeAliases = aliases,
        worldState = deriveWorldState(
            instant = LocalDate.of(2026, 9, 21).atTime(10, 0).toInstant(ZoneOffset.ofHours(8)),
            weather = WeatherState(WeatherKind.CLEAR),
        ),
        presence = null,
    )

    private suspend fun parse(text: String) = parser.parse(text, context())

    @Test
    fun `matches topics from keywords`() = runTest {
        assertTrue(NpcTopic.BOOKS in parse("今天在图书馆看书").topics)
        assertTrue(NpcTopic.WEATHER in parse("外面下雨了").topics)
    }

    @Test
    fun `longer place alias wins over the shorter ones`() = runTest {
        // "湖心花园"里同时含"花园"和"湖"；先长后短才不会指错地方
        val parsed = parse("我在湖心花园")
        assertEquals("place.garden", parsed.mentionedPlaceId)
        assertEquals("湖心花园", parsed.mentionedPlaceName)
    }

    @Test
    fun `alias resolves to the formal place name`() = runTest {
        val parsed = parse("我在湖边")
        assertEquals("place.lake", parsed.mentionedPlaceId)
        assertEquals("北湖", parsed.mentionedPlaceName)
    }

    @Test
    fun `time hint prefers the specific phrase`() = runTest {
        assertEquals(TimeHint.TOMORROW_AFTERNOON, parse("明天下午你在哪").timeHint)
        assertEquals(TimeHint.TONIGHT, parse("今晚有空吗").timeHint)
        assertEquals(TimeHint.LATER_TODAY, parse("待会儿见").timeHint)
    }

    @Test
    fun `wants to meet and asks about schedule are kept apart`() = runTest {
        val meet = parse("明天在图书馆等我")
        assertTrue(meet.wantsToMeet)
        assertFalse(meet.asksAboutSchedule)

        val ask = parse("明天下午你在哪")
        assertTrue(ask.asksAboutSchedule)
        assertFalse(ask.wantsToMeet)
    }

    @Test
    fun `empty and symbol-only input is not understood`() = runTest {
        assertFalse(parse("").understood)
        assertEquals(0.0, parse("").confidence, 0.0)

        assertFalse(parse("。。。").understood)
        assertEquals(0.0, parse("。。。").confidence, 0.0)
    }

    @Test
    fun `understood input reports full confidence`() = runTest {
        val parsed = parse("你在图书馆看书吗")
        assertTrue(parsed.understood)
        assertEquals(1.0, parsed.confidence, 0.0)
        assertTrue(parsed.isQuestion)
    }

    @Test
    fun `unknown words alone produce nothing`() = runTest {
        val parsed = parse("嗯嗯好的")
        assertTrue(parsed.topics.isEmpty())
        assertNull(parsed.mentionedPlaceId)
        assertNull(parsed.timeHint)
    }
}
