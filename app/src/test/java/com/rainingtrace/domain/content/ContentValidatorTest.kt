package com.rainingtrace.domain.content

import com.rainingtrace.domain.craft.Recipe
import com.rainingtrace.domain.craft.RecipeInput
import com.rainingtrace.domain.craft.RecipeOutput
import com.rainingtrace.domain.inventory.Rarity
import com.rainingtrace.domain.inventory.ResourceCategory
import com.rainingtrace.domain.inventory.ResourceDefinition
import com.rainingtrace.domain.map.Place
import com.rainingtrace.domain.map.PlaceActionType
import com.rainingtrace.domain.map.PlaceType
import com.rainingtrace.domain.map.WorldCoordinate
import com.rainingtrace.domain.npc.NpcProfile
import com.rainingtrace.domain.npc.NpcScheduleEntry
import com.rainingtrace.domain.spawn.SpawnRule
import com.rainingtrace.domain.spawn.SpawnSpot
import com.rainingtrace.domain.world.ResourceYieldRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentValidatorTest {

    private fun place(id: String) = Place(
        id = id,
        name = "地点",
        type = PlaceType.LAKE,
        coordinate = WorldCoordinate(39.7326, 116.1712),
        actions = setOf(PlaceActionType.OBSERVE),
    )

    @Test
    fun `正常内容没有诊断`() {
        val report = ContentReport()
        ContentValidator.validate(WorldContent(places = listOf(place("a"), place("b"))), report)

        assertEquals(0, report.items.size)
    }

    @Test
    fun `id 重复会被报错`() {
        val report = ContentReport()
        ContentValidator.validate(WorldContent(places = listOf(place("a"), place("a"))), report)

        assertEquals(1, report.errorCount)
    }

    @Test
    fun `空内容没有诊断`() {
        val report = ContentReport()
        ContentValidator.validate(WorldContent(), report)

        assertEquals(0, report.items.size)
    }

    @Test
    fun `产出规则引用的资源不存在时丢掉那条规则`() {
        val report = ContentReport()
        val content = WorldContent(
            resources = listOf(resource("res.known")),
            yieldRules = listOf(
                rule("rule.ok", "res.known"),
                rule("rule.dangling", "res.missing"),
            ),
        )

        val cleaned = ContentValidator.validate(content, report)

        assertEquals(listOf("rule.ok"), cleaned.yieldRules.map { it.id })
        assertEquals(1, report.errorCount)
        assertTrue(report.items.first().entryId == "rule.dangling")
    }

    private fun resource(id: String) = ResourceDefinition(
        id = id,
        name = "资源",
        category = ResourceCategory.NATURE,
        rarity = Rarity.COMMON,
    )

    private fun rule(id: String, resourceId: String) = ResourceYieldRule(
        id = id,
        resourceId = resourceId,
        action = PlaceActionType.COLLECT,
    )

    @Test
    fun `配方引用的资源不存在时丢掉那条配方`() {
        val report = ContentReport()
        val content = WorldContent(
            resources = listOf(resource("res.known")),
            recipes = listOf(
                recipe("recipe.ok", input = "res.known"),
                recipe("recipe.dangling", input = "res.missing"),
            ),
        )

        val cleaned = ContentValidator.validate(content, report)

        assertEquals(listOf("recipe.ok"), cleaned.recipes.map { it.id })
        assertEquals(1, report.errorCount)
        assertEquals("recipe.dangling", report.items.first().entryId)
    }

    /** 输入与输出都给成同一个存在的资源时才是"没有悬空"。 */
    private fun recipe(id: String, input: String) = Recipe(
        id = id,
        inputs = listOf(RecipeInput(resourceId = input, amount = 1)),
        output = RecipeOutput(resourceId = "res.known", amount = 1),
    )

    /**
     * 地点数据渐进补齐期间，作息指向未落地地点是预期状态：只丢那一条，NPC 保留。
     * 曾经的"整位剔除 + ERROR"在过渡期会把所有 NPC 一起清掉。
     */
    @Test
    fun `NPC 作息悬空只丢那一条 人不丢`() {
        val report = ContentReport()
        val npc = NpcProfile(
            id = "npc.x",
            name = "某人",
            oneLiner = "",
            schedule = listOf(
                NpcScheduleEntry(startMinute = 0, placeId = "a"),
                NpcScheduleEntry(startMinute = 60, placeId = "missing"),
            ),
        )
        val content = WorldContent(places = listOf(place("a")), npcs = listOf(npc))

        val cleaned = ContentValidator.validate(content, report)

        assertEquals("过渡期的悬空作息不该是错误", 0, report.errorCount)
        assertEquals("NPC 不该被整位剔除", listOf("npc.x"), cleaned.npcs.map { it.id })
        assertEquals(
            "悬空条目被剔除，保留可解析的",
            listOf("a"),
            cleaned.npcs.single().schedule.map { it.placeId },
        )
    }

    /**
     * 这一条以前是 `ShippedContentTest` 里的硬失败（构建前拦住），现在改成一条 WARN：
     * 作者常常先写下游配方、后补上游，中途不完整是正常的；但"做不出来却看不出来"
     * 会静默毁掉一整条链，所以必须留在设置页的诊断里。**内容一条都不能丢**。
     */
    @Test
    fun `加工品没有配方产出时只记一条 WARN`() {
        val report = ContentReport()
        val content = WorldContent(
            resources = listOf(craft("res.pigment"), craft("res.pencil")),
            recipes = listOf(
                Recipe(
                    id = "recipe.pencil",
                    inputs = listOf(RecipeInput(resourceId = "res.pigment", amount = 1)),
                    output = RecipeOutput(resourceId = "res.pencil", amount = 1),
                ),
            ),
        )

        val cleaned = ContentValidator.validate(content, report)

        assertEquals("内容不该被丢掉", 1, cleaned.recipes.size)
        assertEquals(0, report.errorCount)
        assertEquals(1, report.items.size)
        assertTrue("WARN 里要点名是哪个加工品", report.items.first().message.contains("res.pigment"))
    }

    private fun craft(id: String) = ResourceDefinition(
        id = id,
        name = "加工品",
        category = ResourceCategory.CRAFT,
        rarity = Rarity.COMMON,
    )

    // ---- 资源点刷新规则 ----

    private fun spawnRule(id: String, placeType: PlaceType, perDay: Int, spots: Int) = SpawnRule(
        id = id,
        name = "刷出来的点",
        placeType = placeType,
        spots = List(spots) { SpawnSpot(WorldCoordinate(39.7326, 116.1712), 10.0) },
        perDay = perDay,
    )

    /** `perDay` 比候选点还多不是错误：夹到候选点数就行，但要说一声。 */
    @Test
    fun `刷新规则的 perDay 超过候选点只记一条 WARN`() {
        val report = ContentReport()
        val content = WorldContent(
            resources = listOf(resource("res.berry")),
            yieldRules = listOf(
                ResourceYieldRule(
                    id = "rule.collect.berry",
                    resourceId = "res.berry",
                    action = PlaceActionType.COLLECT,
                    placeType = PlaceType.BERRY_BUSH,
                ),
            ),
            spawnRules = listOf(spawnRule("spawn.berry", PlaceType.BERRY_BUSH, perDay = 3, spots = 1)),
        )

        val cleaned = ContentValidator.validate(content, report)

        assertEquals("内容不该被丢掉", 1, cleaned.spawnRules.size)
        assertEquals(0, report.errorCount)
        assertEquals(1, report.items.size)
        assertTrue("WARN 里要说明 perDay 超了", report.items.first().message.contains("perDay"))
    }

    /** 刷出来但采不到，是最静默的坏法：走过去一看什么都没有。 */
    @Test
    fun `刷新规则刷出来的类型没有产出规则时只记一条 WARN`() {
        val report = ContentReport()
        val content = WorldContent(
            spawnRules = listOf(
                spawnRule("spawn.mushroom", PlaceType.MUSHROOM_PATCH, perDay = 1, spots = 2),
            ),
        )

        val cleaned = ContentValidator.validate(content, report)

        assertEquals("内容不该被丢掉", 1, cleaned.spawnRules.size)
        assertEquals(0, report.errorCount)
        assertEquals(1, report.items.size)
        assertTrue(
            "WARN 里要点名是哪个类型",
            report.items.first().message.contains("MUSHROOM_PATCH"),
        )
    }
}