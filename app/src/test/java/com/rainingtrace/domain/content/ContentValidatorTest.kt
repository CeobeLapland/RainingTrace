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
}