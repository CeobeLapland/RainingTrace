package com.rainingtrace.domain.npc

import com.rainingtrace.core.time.FakeWorldClock
import com.rainingtrace.domain.world.Season
import com.rainingtrace.domain.world.TimeOfDay
import com.rainingtrace.domain.world.WeatherKind
import com.rainingtrace.domain.world.WeatherState
import com.rainingtrace.domain.world.WorldState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * N4 记忆层：提取 / 去重 / 挑选 / 清理 / 预算 / 人物设定 / LLM 兜底。
 * 数据层没有单测，所以全部逻辑都压进 domain 纯函数 + 假仓储。
 */
class NpcMemoryTest {

    // ---- 提取与去重 ----

    @Test
    fun `对话提取话题偏好与地点事实`() {
        val parsed = ParsedPlayerMessage(
            raw = "最近在读书",
            topics = setOf(NpcTopic.BOOKS),
            mentionedPlaceName = "图书馆",
        )
        val facts = NpcMemoryMaintenance.factsFromDialogue(
            npcId = "npc.x",
            npcName = "某人",
            parsed = parsed,
            mentionedPlaceName = parsed.mentionedPlaceName,
            commitmentFacts = null,
            nowEpochMs = 1000L,
        )

        assertTrue(facts.any { it.kind == NpcMemoryKind.PREFERENCE && "书" in it.content })
        assertTrue(facts.any { it.kind == NpcMemoryKind.PLACE && "图书馆" in it.content })
        assertTrue(facts.none { it.kind == NpcMemoryKind.COMMITMENT })
    }

    @Test
    fun `答应见面会记一条高优先级约定`() {
        val facts = NpcMemoryMaintenance.factsFromDialogue(
            npcId = "npc.x",
            npcName = "某人",
            parsed = ParsedPlayerMessage(raw = "明天见"),
            mentionedPlaceName = null,
            commitmentFacts = CommitmentFacts(timeLabel = "明天下午", placeName = "图书馆", agreed = true),
            nowEpochMs = 1000L,
        )

        val commitment = facts.single { it.kind == NpcMemoryKind.COMMITMENT }
        assertEquals(7, commitment.importance)
        assertTrue(commitment.content.contains("图书馆"))
    }

    @Test
    fun `同内容不重复记`() {
        val existing = listOf(fact("npc.x", "玩家最近爱聊「书」。", 2, 100L))
        val candidates = listOf(
            fact("npc.x", "玩家最近爱聊「书」。", 2, 200L),
            fact("npc.x", "玩家最近爱聊「画」。", 2, 200L),
        )

        val fresh = NpcMemoryMaintenance.dedupe(existing, candidates)

        assertEquals(listOf("玩家最近爱聊「画」。"), fresh.map { it.content })
    }

    // ---- 挑选与清理 ----

    @Test
    fun `topK 按重要性再按新鲜度排`() {
        val facts = listOf(
            fact("npc.x", "低1", 1, 100L),
            fact("npc.x", "中", 5, 200L),
            fact("npc.x", "高旧", 9, 300L),
            fact("npc.x", "高新", 9, 400L).copy(lastReferencedAtEpochMs = 500L),
        )

        val top = NpcMemoryMaintenance.topK(facts, k = 2)

        assertEquals(listOf("高新", "高旧"), top.map { it.content })
    }

    @Test
    fun `超限清理把最低优先级合并成一条 SUMMARY`() {
        val facts = (0 until 45).map { fact("npc.x", "事$it", (it % 5) + 1, it * 100L) }

        val (kept, dropped, summary) = NpcMemoryMaintenance.prune(facts, nowEpochMs = 9_999_999L)

        assertEquals("清理后不超上限", NpcMemoryMaintenance.PRUNE_KEEP, kept.size)
        assertTrue("低优先级被合并掉", dropped.isNotEmpty())
        assertEquals(NpcMemoryKind.SUMMARY, summary!!.kind)
        assertTrue(summary.content.contains("事"))
    }

    @Test
    fun `已有 SUMMARY 时并入而不是再开一条`() {
        val summary = fact("npc.x", "旧总结", 5, 100L).copy(kind = NpcMemoryKind.SUMMARY)
        val facts = (0 until 44).map { fact("npc.x", "事$it", 1, it * 100L) } + summary

        val (_, _, newSummary) = NpcMemoryMaintenance.prune(facts, 9_999_999L)

        assertTrue("应该并入旧总结", newSummary!!.content.startsWith("旧总结"))
    }

    // ---- 预算裁剪 ----

    @Test
    fun `预算优先保人格和新鲜消息`() {
        val persona = "P".repeat(100)
        val memory = (1..10).map { "M".repeat(30) } // 每条约 34 字（含分隔符）
        val recent = (1..10).map { "R".repeat(20) } // 每条约 26 字（含分隔符）

        val trimmed = PromptBudget.trimToBudget(persona, memory, recent, maxChars = 200)

        assertTrue("总量不能超预算", trimmed.totalChars <= 200)
        assertEquals("人格必须完整保留", persona, trimmed.persona)
        assertEquals("预算内装下两条记忆", 2, trimmed.memory.size)
        assertEquals("剩余预算只够一条最近消息", 1, trimmed.recent.size)
    }

    // ---- 人物设定 ----

    @Test
    fun `默认人格从结构化字段组装`() {
        val profile = NpcProfile(
            id = "npc.x",
            name = "某人",
            oneLiner = "总在图书馆靠窗。",
            schedule = emptyList(),
            role = "图书馆管理员",
            traits = setOf(NpcTrait.TACITURN, NpcTrait.PUNCTUAL),
            topics = setOf(NpcTopic.BOOKS),
            favoriteTopic = NpcTopic.BOOKS,
        )

        val persona = NpcPersonaPrompt.personaFor(profile)

        assertTrue(persona.startsWith("你是某人"))
        assertTrue(persona.contains("图书馆管理员"))
        assertTrue(persona.contains(NpcTrait.TACITURN.label))
        assertTrue(persona.contains("书"))
        assertTrue(persona.contains("总在图书馆靠窗"))
    }

    @Test
    fun `手写 personaPrompt 优先于自动组装`() {
        val profile = NpcProfile(
            id = "npc.x",
            name = "某人",
            oneLiner = "",
            schedule = emptyList(),
            personaPrompt = "你是雨夜里等人的人。",
        )

        assertEquals("你是雨夜里等人的人。", NpcPersonaPrompt.personaFor(profile))
    }

    // ---- LLM 叙事：失败与空 client 都要回退模板 ----

    @Test
    fun `client 为空时回退模板`() = runTest {
        val fallback = FakeNarrative("模板句")
        val service = LlmNarrativeService(client = null, fallback = fallback)

        val result = service.respond(context())

        assertEquals("模板句", result.text)
        assertEquals(NpcMessageSource.TEMPLATE, result.source)
    }

    @Test
    fun `client 返回 null 时回退模板`() = runTest {
        val fallback = FakeNarrative("模板句")
        val service = LlmNarrativeService(
            client = FakeLlm(null),
            fallback = fallback,
            memoryRepository = FakeMemoryRepo(),
        )

        val result = service.respond(context())

        assertEquals("模板句", result.text)
        assertEquals(NpcMessageSource.TEMPLATE, result.source)
    }

    @Test
    fun `client 成功时产出 AI 文本并 touch 记忆`() = runTest {
        val repo = FakeMemoryRepo().apply {
            facts += fact("npc.x", "玩家最近爱聊「书」。", 2, 100L)
        }
        val service = LlmNarrativeService(
            client = FakeLlm("那本旧书我也喜欢。"),
            fallback = FakeNarrative("模板句"),
            memoryRepository = repo,
            nowEpochMs = { 500L },
        )

        val result = service.respond(context())

        assertEquals("那本旧书我也喜欢。", result.text)
        assertEquals(NpcMessageSource.AI, result.source)
        assertEquals(listOf(repo.facts.first().id), repo.touched)
    }

    // ---- 编辑入口（NpcMemoryEditor） ----

    @Test
    fun `手写添加判重且超限会清理`() = runTest {
        val repo = FakeMemoryRepo()
        val writer = NpcMemoryWriter(repository = repo, clock = FakeWorldClock(Instant.ofEpochMilli(1000L)))
        val editor = NpcMemoryEditor(repository = repo, writer = writer)

        editor.add("npc.x", "他记得玩家爱喝书屋的热茶。", 6)
        editor.add("npc.x", "他记得玩家爱喝书屋的热茶。", 3)
        assertEquals("同内容不该重复记", 1, repo.facts.size)

        (0 until 60).forEach { i -> editor.add("npc.x", "杂事$i", 1) }
        assertTrue("超限后会被合并", repo.facts.size <= NpcMemoryMaintenance.PRUNE_KEEP + 1)
        assertTrue("合并出 SUMMARY", repo.facts.any { it.kind == NpcMemoryKind.SUMMARY })
    }

    @Test
    fun `编辑覆盖内容与重要度 删除移除`() = runTest {
        val repo = FakeMemoryRepo().apply { append(fact("npc.x", "旧内容", 3, 100L)) }
        val writer = NpcMemoryWriter(repository = repo, clock = FakeWorldClock(Instant.ofEpochMilli(1000L)))
        val editor = NpcMemoryEditor(repository = repo, writer = writer)
        val original = repo.facts.single()

        editor.update("npc.x", original.id, "新内容", 9)
        assertEquals("新内容", repo.facts.single().content)
        assertEquals(9, repo.facts.single().importance)
        assertEquals("来源/时间不动", "chat", repo.facts.single().source)

        editor.delete("npc.x", original.id)
        assertTrue(repo.facts.isEmpty())
    }

    @Test
    fun `重要度越界会夹到一和十`() = runTest {
        val repo = FakeMemoryRepo()
        val writer = NpcMemoryWriter(repository = repo, clock = FakeWorldClock(Instant.ofEpochMilli(1000L)))
        val editor = NpcMemoryEditor(repository = repo, writer = writer)

        editor.add("npc.x", "很重要的事", 99)

        assertEquals(10, repo.facts.single().importance)
    }

    // ---- 工具 ----

    private fun fact(npcId: String, content: String, importance: Int, created: Long) = NpcMemoryFact(
        id = "mem.$npcId.$content.$created",
        npcId = npcId,
        kind = NpcMemoryKind.EVENT,
        content = content,
        importance = importance,
        source = "chat",
        createdAtEpochMs = created,
    )

    private fun context() = NpcDialogueContext(
        profile = NpcProfile(
            id = "npc.x",
            name = "某人",
            oneLiner = "",
            schedule = emptyList(),
            role = "图书馆管理员",
            traits = setOf(NpcTrait.TACITURN),
        ),
        state = NpcState.initial("npc.x"),
        stage = RelationshipStage.STRANGER,
        mood = NpcMood.CALM,
        presence = null,
        parsed = ParsedPlayerMessage(raw = "你好"),
        worldState = WorldState(
            instant = Instant.ofEpochMilli(0L),
            localDate = LocalDate.of(2026, 10, 4),
            minuteOfDay = 600,
            timeOfDay = TimeOfDay.DAY,
            weather = WeatherState(WeatherKind.CLEAR),
            season = Season.AUTUMN,
        ),
        recentMessages = emptyList(),
    )

    private class FakeLlm(private val text: String?) : LlmClient {
        override suspend fun complete(request: LlmRequest): String? = text
    }

    private class FakeNarrative(private val text: String) : NarrativeService {
        override suspend fun respond(context: NpcDialogueContext): GeneratedDialogue =
            GeneratedDialogue(text = text, source = NpcMessageSource.TEMPLATE)
    }

    private class FakeMemoryRepo : NpcMemoryRepository {
        val facts = mutableListOf<NpcMemoryFact>()
        val touched = mutableListOf<String>()

        override suspend fun factsFor(npcId: String): List<NpcMemoryFact> = facts.filter { it.npcId == npcId }
        override suspend fun all(): List<NpcMemoryFact> = facts
        override suspend fun append(fact: NpcMemoryFact) {
            facts += fact
        }
        override suspend fun upsert(fact: NpcMemoryFact) {
            val idx = facts.indexOfFirst { it.id == fact.id }
            if (idx >= 0) facts[idx] = fact else facts += fact
        }
        override suspend fun remove(ids: List<String>) {
            facts.removeAll { it.id in ids }
        }
        override suspend fun touch(npcId: String, ids: List<String>, nowEpochMs: Long) {
            touched += ids
        }
    }
}
