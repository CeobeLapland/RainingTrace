package com.rainingtrace.data.content

import com.rainingtrace.domain.content.ContentReport
import com.rainingtrace.domain.npc.NpcEditDraft
import com.rainingtrace.domain.npc.NpcScheduleEntry
import com.rainingtrace.domain.npc.NpcTopic
import com.rainingtrace.domain.npc.NpcTrait
import com.rainingtrace.domain.npc.validateNpcDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内容工作台（NPC）的域内保证：
 * 1. 草稿校验能抓出"会静默失效"的问题；
 * 2. 覆盖层写入是**无损**的（尤其是作息变体这种编辑器暂时不改的字段）。
 */
class NpcEditorContentTest {

    private val placeIds = setOf("place.bit.dorm_3", "place.bit.library")
    private val resourceIds = setOf("res.umbrella", "res.pencil")

    private fun draft(
        name: String = "林砚",
        topics: Set<NpcTopic> = setOf(NpcTopic.BOOKS),
        favorite: NpcTopic? = NpcTopic.BOOKS,
        schedule: List<NpcScheduleEntry> = listOf(
            NpcScheduleEntry(420, "place.bit.dorm_3", 0, "起床"),
        ),
        liked: List<String> = listOf("res.pencil"),
    ) = NpcEditDraft(
        id = "npc.bit.lin",
        name = name,
        oneLiner = "总在图书馆靠窗。",
        role = "建筑系大二",
        traits = setOf(NpcTrait.TACITURN),
        topics = topics,
        favoriteTopic = favorite,
        backstory = listOf("丢过一次伞"),
        giftLiked = liked,
        giftDisliked = emptyList(),
        homePlaceId = "place.bit.dorm_3",
        enabled = true,
        personaPrompt = null,
        schedule = schedule,
        scheduleVariants = emptyList(),
    )

    @Test
    fun `合法草稿没有校验问题`() {
        val issues = validateNpcDraft(draft(), placeIds, resourceIds)

        assertTrue("不该有问题：$issues", issues.isEmpty())
        assertNotNull("合法草稿应能转回领域对象", draft().toProfile())
    }

    /** 名字/一句话为空只是"还没填"，不是错误，也不能拦保存。 */
    @Test
    fun `空名字与空一句话不拦保存`() {
        val empty = draft(name = "").copy(oneLiner = "")

        val issues = validateNpcDraft(empty, placeIds, resourceIds)
        val profile = empty.toProfile()

        assertTrue("空名字不该产生提示：$issues", issues.none { it.field == "name" })
        assertNotNull("空名字也必须能保存", profile)
        assertEquals("", profile!!.name)
    }

    /** 编辑中途（取消话题勾选后 favorite 悬空）必须能被草稿态容纳，而不是崩。 */
    @Test
    fun `favorite 不在 topics 时草稿仍可存在 保存会自动清空`() {
        val invalid = draft(topics = setOf(NpcTopic.WEATHER), favorite = NpcTopic.BOOKS)

        val issues = validateNpcDraft(invalid, placeIds, resourceIds)

        assertTrue(issues.any { it.field == "favoriteTopic" })
        val profile = invalid.toProfile()
        assertNotNull("半成品也必须能保存（不因一个字段卡死）", profile)
        assertNull("保存时自动清掉非法的 favorite", profile!!.favoriteTopic)
    }

    @Test
    fun `悬空地点与悬空礼物都会被点名`() {
        val invalid = draft(
            schedule = listOf(NpcScheduleEntry(420, "place.missing", 0, "起床")),
            liked = listOf("res.missing"),
        )

        val issues = validateNpcDraft(invalid, placeIds, resourceIds)

        assertTrue(issues.any { "place.missing" in it.message })
        assertTrue(issues.any { "res.missing" in it.message })
    }

    /**
     * 时间越界与负步行时间**在领域类型构造期就被 require 拦住**（`NpcScheduleEntry`），
     * 所以编辑器不需要再校验一遍——这里钉住"它确实拦得住"，避免以后有人放宽 require。
     */
    @Test
    fun `作息条目自身拒绝越界时间与负步行时间`() {
        val badMinute = runCatching { NpcScheduleEntry(24 * 60, "place.bit.dorm_3", 0, "") }
        val badTravel = runCatching { NpcScheduleEntry(420, "place.bit.dorm_3", -5, "") }

        assertTrue("startMinute 越界必须被拦住", badMinute.isFailure)
        assertTrue("负步行时间必须被拦住", badTravel.isFailure)
    }

    /**
     * 覆盖层是按 id 整条替换的：编辑器只改基础字段时，作息变体也必须原样写回，
     * 否则一次保存就会把已有的「雨天」「外出」清空。
     * **不绑定具体角色**：内容会换，取当前内置的第一位即可。
     */
    @Test
    fun `覆盖层编码往返是无损的`() {
        val original = ShippedContent.npcs.first { it.scheduleVariants.isNotEmpty() || it.personaPrompt != null }

        val encoded = encodeNpcOverlay(listOf(original))
        val decoded = decodeNpcEntries(encoded, "npcs.json（覆盖）", ContentReport()).entries.single()

        assertEquals(original.id, decoded.id)
        assertEquals(original.name, decoded.name)
        assertEquals(original.role, decoded.role)
        assertEquals(original.backstory, decoded.backstory)
        assertEquals(original.giftPreferences, decoded.giftPreferences)
        assertEquals("作息不能丢", original.schedule, decoded.schedule)
        assertEquals("作息变体不能丢", original.scheduleVariants, decoded.scheduleVariants)
        assertEquals(original.personaPrompt, decoded.personaPrompt)
    }

    @Test
    fun `编码后的覆盖层能被正常解析且没有错误`() {
        val report = ContentReport()

        val decoded = decodeNpcEntries(
            encodeNpcOverlay(ShippedContent.npcs),
            "npcs.json（覆盖）",
            report,
        )

        assertEquals(
            "编码再解码不该产生 ERROR：${report.items.filter { it.isError }}",
            0,
            report.errorCount,
        )
        assertEquals(ShippedContent.npcs.size, decoded.entries.size)
    }
}
