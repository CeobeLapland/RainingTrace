package com.rainingtrace.domain.npc

import com.rainingtrace.domain.world.SeededRandomSource
import com.rainingtrace.domain.world.WeatherKind
import com.rainingtrace.domain.world.WeatherState
import com.rainingtrace.domain.world.deriveWorldState
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateNarrativeServiceTest {

    private val service = TemplateNarrativeService(SeededRandomSource(42L))

    private fun profile(
        traits: Set<NpcTrait> = emptySet(),
        favorite: NpcTopic? = NpcTopic.BOOKS,
    ) = NpcProfile(
        id = "npc.test",
        name = "甲",
        oneLiner = "",
        schedule = emptyList(),
        traits = traits,
        topics = setOfNotNull(favorite),
        favoriteTopic = favorite,
    )

    private fun context(
        parsed: ParsedPlayerMessage,
        profile: NpcProfile = profile(),
        stage: RelationshipStage = RelationshipStage.NODDING,
        mood: NpcMood = NpcMood.CALM,
        facts: ScheduleFacts? = null,
        recent: List<NpcMessage> = emptyList(),
        hooks: List<String> = emptyList(),
    ) = NpcDialogueContext(
        profile = profile,
        state = NpcState(npcId = profile.id, affection = 30),
        stage = stage,
        mood = mood,
        presence = null,
        parsed = parsed,
        worldState = deriveWorldState(
            instant = LocalDate.of(2026, 9, 21).atTime(12, 0).toInstant(ZoneOffset.ofHours(8)),
            weather = WeatherState(WeatherKind.CLEAR),
        ),
        recentMessages = recent,
        scheduleFacts = facts,
        memoryHooks = hooks,
    )

    private val factsAboutLibrary = ScheduleFacts(
        targetMinuteOfDay = 840,
        timeLabel = "明天下午",
        placeName = "图书馆",
        activity = "在靠窗的位置自习",
        walking = false,
        isSameAsNow = false,
    )

    private fun npcMessage(text: String) = NpcMessage(
        id = "m",
        npcId = "npc.test",
        speaker = NpcMessageSpeaker.NPC,
        text = text,
        createdAtEpochMs = 0,
        read = true,
        source = NpcMessageSource.TEMPLATE,
    )

    @Test
    fun `same seed produces the same reply`() = runTest {
        val parsed = ParsedPlayerMessage(raw = "书", topics = setOf(NpcTopic.BOOKS))

        val a = TemplateNarrativeService(SeededRandomSource(7L)).respond(context(parsed)).text
        val b = TemplateNarrativeService(SeededRandomSource(7L)).respond(context(parsed)).text

        assertEquals(a, b)
    }

    @Test
    fun `a schedule question is answered with the real place`() = runTest {
        val parsed = ParsedPlayerMessage(
            raw = "明天下午你在哪",
            timeHint = TimeHint.TOMORROW_AFTERNOON,
            asksAboutSchedule = true,
            confidence = 1.0,
        )

        val text = service.respond(context(parsed, facts = factsAboutLibrary)).text

        assertTrue(text, "图书馆" in text)
        assertTrue(text, "明天下午" in text)
    }

    @Test
    fun `a meeting request is declined and still tells the truth`() = runTest {
        val parsed = ParsedPlayerMessage(
            raw = "明天在图书馆等我",
            mentionedPlaceId = "place.bit.library",
            timeHint = TimeHint.TOMORROW_AFTERNOON,
            wantsToMeet = true,
            confidence = 1.0,
        )

        val text = service.respond(context(parsed, facts = factsAboutLibrary)).text

        // 没有承诺词（片 1 不答应约定），但说了他明天下午真在哪
        assertTrue("不该出现承诺词：$text", DialogueValidator.validate(text))
        assertTrue(text, "图书馆" in text)
    }

    @Test
    fun `unparsed input never pretends to understand`() = runTest {
        val text = service.respond(context(ParsedPlayerMessage(raw = "嗯嗯"))).text

        assertTrue(text, DialogueValidator.validate(text))
        assertTrue("应该只是一句短反问：$text", text.length <= 8)
    }

    @Test
    fun `a memory hook replaces the greeting`() = runTest {
        val parsed = ParsedPlayerMessage(raw = "书", topics = setOf(NpcTopic.BOOKS))

        val text = service
            .respond(context(parsed, hooks = listOf("上次在「北湖」跟你聊过。")))
            .text

        assertTrue(text, text.startsWith("上次在「北湖」跟你聊过。"))
    }

    @Test
    fun `a line already used recently is not repeated`() = runTest {
        val parsed = ParsedPlayerMessage(raw = "书", topics = setOf(NpcTopic.BOOKS))

        val first = service.respond(context(parsed)).text
        val second = service.respond(context(parsed, recent = listOf(npcMessage(first)))).text

        assertNotEquals(first, second)
    }

    @Test
    fun `relationship stage changes the tone`() = runTest {
        val parsed = ParsedPlayerMessage(raw = "书", topics = setOf(NpcTopic.BOOKS))

        val stranger = TemplateNarrativeService(SeededRandomSource(3L))
            .respond(context(parsed, stage = RelationshipStage.STRANGER)).text
        val friend = TemplateNarrativeService(SeededRandomSource(3L))
            .respond(context(parsed, stage = RelationshipStage.FRIEND)).text

        assertNotEquals(stranger, friend)
    }

    @Test
    fun `the validator blocks promises, blanks and overlength text`() {
        assertFalse(DialogueValidator.validate("我等你"))
        assertFalse(DialogueValidator.validate(""))
        assertFalse(DialogueValidator.validate("a".repeat(DialogueValidator.MAX_LENGTH + 1)))
        assertFalse(DialogueValidator.validate("残留占位符 {place}"))
        assertTrue(DialogueValidator.validate("今天光线不错，画了两张。"))
    }
}
