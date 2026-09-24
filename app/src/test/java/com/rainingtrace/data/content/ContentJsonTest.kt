package com.rainingtrace.data.content

import com.rainingtrace.domain.content.ContentReport
import com.rainingtrace.domain.inventory.Rarity
import com.rainingtrace.domain.inventory.ResourceCategory
import com.rainingtrace.domain.map.PlaceActionType
import com.rainingtrace.domain.map.PlaceType
import com.rainingtrace.domain.world.TimeOfDay
import com.rainingtrace.domain.world.WeatherKind
import com.rainingtrace.domain.world.WorldCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JSON 读取的"永不崩溃"回归：手写 JSON 一定会写错，
 * 错的地方必须变成诊断，而不是异常或静默丢失。
 */
class ContentJsonTest {

    private fun entry(
        id: String,
        type: String = "LAKE",
        actions: String = """["OBSERVE"]""",
        extra: String = "",
    ) = """{"id":"$id","name":"某地","type":"$type","lat":39.73,"lng":116.17,"actions":$actions$extra}"""

    private fun file(vararg entries: String) =
        """{"schemaVersion":1,"removedIds":[],"entries":[${entries.joinToString(",")}]}"""

    private fun decode(text: String, report: ContentReport) =
        decodePlaceEntries(text, "places.json", report)

    @Test
    fun `正常文件解析成地点`() {
        val report = ContentReport()
        val decoded = decode(file(entry("place.a", type = "LAKE")), report)

        assertEquals(0, report.errorCount)
        assertEquals(1, decoded.entries.size)
        assertEquals(PlaceType.LAKE, decoded.entries.first().type)
    }

    @Test
    fun `枚举忽略大小写与空白`() {
        val report = ContentReport()
        val decoded = decode(
            file(entry("place.a", type = " lake ", actions = """[" observe "]""")),
            report,
        )

        assertEquals(0, report.errorCount)
        assertEquals(PlaceType.LAKE, decoded.entries.first().type)
        assertEquals(setOf(PlaceActionType.OBSERVE), decoded.entries.first().actions)
    }

    @Test
    fun `未知 type 只丢这一条，其余条目照常`() {
        val report = ContentReport()
        val decoded = decode(file(entry("place.bad", type = "TELEPORT"), entry("place.ok")), report)

        assertEquals(1, report.errorCount)
        assertEquals(listOf("place.ok"), decoded.entries.map { it.id })
        // 错误消息必须列出合法值，否则手写的人只能猜。
        assertTrue(report.items.first().message.contains("LAKE"))
    }

    @Test
    fun `未知 action 记错误但地点保留`() {
        val report = ContentReport()
        val decoded = decode(file(entry("place.a", actions = """["OBSERVE","FLY"]""")), report)

        assertEquals(1, report.errorCount)
        assertEquals(listOf("place.a"), decoded.entries.map { it.id })
        assertEquals(setOf(PlaceActionType.OBSERVE), decoded.entries.first().actions)
    }

    @Test
    fun `动作全无效时整条被丢（地点必须至少有一个动作）`() {
        val report = ContentReport()
        val decoded = decode(file(entry("place.a", actions = """["FLY"]""")), report)

        assertTrue(decoded.entries.isEmpty())
        assertEquals(2, report.errorCount) // 未知 action + 地点不合法
    }

    @Test
    fun `坐标越界时整条被丢`() {
        val report = ContentReport()
        val decoded = decode(
            """{"entries":[{"id":"place.a","name":"某地","type":"LAKE","lat":999.0,"lng":116.17,"actions":["OBSERVE"]}]}""",
            report,
        )

        assertTrue(decoded.entries.isEmpty())
        assertEquals(1, report.errorCount)
    }

    @Test
    fun `未知字段被忽略而不是报错`() {
        val report = ContentReport()
        val decoded = decode(file(entry("place.a", extra = ",\"note\":\"随手写点备注\"")), report)

        assertEquals(0, report.errorCount)
        assertEquals(1, decoded.entries.size)
    }

    @Test
    fun `带 BOM 的文件照样能读`() {
        val report = ContentReport()
        val decoded = decode("\uFEFF" + file(entry("place.a")), report)

        assertEquals(0, report.errorCount)
        assertEquals(1, decoded.entries.size)
    }

    @Test
    fun `JSON 语法错误不抛异常，只留诊断`() {
        val report = ContentReport()
        val decoded = decode("""{"entries":[""", report)

        assertTrue(decoded.entries.isEmpty())
        assertTrue(report.errorCount >= 1)
    }

    @Test
    fun `空文本不抛异常`() {
        val report = ContentReport()
        val decoded = decode("", report)

        assertTrue(decoded.entries.isEmpty())
        assertTrue(report.errorCount >= 1)
    }

    @Test
    fun `removedIds 被原样带出`() {
        val report = ContentReport()
        val decoded = decode(
            """{"removedIds":["place.a"],"entries":[${entry("place.b")}]}""",
            report,
        )

        assertEquals(listOf("place.a"), decoded.removedIds)
        assertFalse(decoded.entries.isEmpty())
    }

    // ---- 资源 ----

    @Test
    fun `资源按名字解析类别与稀有度`() {
        val report = ContentReport()
        val decoded = decodeResourceEntries(
            """{"entries":[{"id":"res.a","name":"东西","category":"nature","rarity":" uncommon ",
               "tags":["x","","y"],"description":"。"}]}""",
            "resources.json",
            report,
        )

        assertEquals(0, report.errorCount)
        val resource = decoded.entries.single()
        assertEquals(ResourceCategory.NATURE, resource.category)
        assertEquals(Rarity.UNCOMMON, resource.rarity)
        assertEquals(setOf("x", "y"), resource.tags)
    }

    @Test
    fun `未知稀有度只丢这一条`() {
        val report = ContentReport()
        val decoded = decodeResourceEntries(
            """{"entries":[{"id":"res.a","name":"东西","category":"NATURE","rarity":"LEGENDARY"}]}""",
            "resources.json",
            report,
        )

        assertTrue(decoded.entries.isEmpty())
        assertEquals(1, report.errorCount)
        assertTrue(report.items.first().message.contains("COMMON"))
    }

    // ---- 条件 ----

    private fun condition(json: String): Pair<WorldCondition?, ContentReport> {
        val report = ContentReport()
        val dto = ContentJsonFormat.decodeFromString(WorldConditionDto.serializer(), json)
        return decodeWorldCondition(dto, "yield_rules.json", "rule.a", report) to report
    }

    @Test
    fun `雨天简写展开成所有会下雨的天气`() {
        val (parsed, report) = condition("""{"type":"weatherIn","kinds":["RAINY"]}""")

        assertEquals(0, report.errorCount)
        assertEquals(WeatherKind.RAINY, (parsed as WorldCondition.WeatherIn).kinds)
        assertEquals(2, WeatherKind.RAINY.size)
    }

    @Test
    fun `时段条件解析成集合`() {
        val (parsed, _) = condition("""{"type":"timeOfDayIn","times":["NIGHT","DUSK"]}""")

        assertEquals(
            setOf(TimeOfDay.NIGHT, TimeOfDay.DUSK),
            (parsed as WorldCondition.TimeOfDayIn).times,
        )
    }

    @Test
    fun `跨零点的分钟区间照常解析`() {
        val (parsed, report) = condition("""{"type":"betweenMinutes","start":1320,"end":120}""")

        assertEquals(0, report.errorCount)
        assertTrue(parsed is WorldCondition.BetweenMinutes)
    }

    @Test
    fun `区间缺 end 时报错而不是崩`() {
        val (parsed, report) = condition("""{"type":"betweenMinutes","start":600}""")

        assertNull(parsed)
        assertEquals(1, report.errorCount)
    }

    @Test
    fun `未知条件类型列出合法值`() {
        val (parsed, report) = condition("""{"type":"moonPhase"}""")

        assertNull(parsed)
        assertTrue(report.items.first().message.contains("weatherIn"))
    }

    @Test
    fun `all 递归解析内层条件`() {
        val (parsed, report) = condition(
            """{"type":"all","conditions":[
                 {"type":"weatherIn","kinds":["SNOW"]},
                 {"type":"seasonIn","seasons":["WINTER"]}]}""",
        )

        assertEquals(0, report.errorCount)
        assertEquals(2, (parsed as WorldCondition.All).conditions.size)
    }

    @Test
    fun `not 包住内部条件`() {
        val (parsed, report) = condition(
            """{"type":"not","condition":{"type":"weatherIn","kinds":["RAINY"]}}""",
        )

        assertEquals(0, report.errorCount)
        assertTrue((parsed as WorldCondition.Not).condition is WorldCondition.WeatherIn)
    }

    @Test
    fun `not 缺内部条件时报错而不是崩`() {
        val (parsed, report) = condition("""{"type":"not"}""")

        assertNull(parsed)
        assertEquals(1, report.errorCount)
    }

    @Test
    fun `not 的内部条件写坏时整体作废`() {
        val (parsed, report) = condition(
            """{"type":"not","condition":{"type":"moonPhase"}}""",
        )

        assertNull(parsed)
        // 一条"未知条件类型" + 一条"not 的内部条件解析失败"
        assertTrue(report.errorCount >= 2)
    }

    // ---- 产出规则 ----

    @Test
    fun `产出规则解析出动作与地点类型`() {
        val report = ContentReport()
        val decoded = decodeYieldRuleEntries(
            """{"entries":[{"id":"rule.a","resourceId":"res.x","action":"COLLECT","placeType":"LAKE",
               "amount":2,"cooldownMs":1000,"conditions":[{"type":"timeOfDayIn","times":["NIGHT"]}]}]}""",
            "yield_rules.json",
            report,
        )

        assertEquals(0, report.errorCount)
        val rule = decoded.entries.single()
        assertEquals(PlaceActionType.COLLECT, rule.action)
        assertEquals(PlaceType.LAKE, rule.placeType)
        assertEquals(2, rule.amount)
        assertEquals(1, rule.conditions.size)
    }

    @Test
    fun `省略 placeType 表示任意地点类型`() {
        val report = ContentReport()
        val decoded = decodeYieldRuleEntries(
            """{"entries":[{"id":"rule.a","resourceId":"res.x","action":"OBSERVE"}]}""",
            "yield_rules.json",
            report,
        )

        assertEquals(0, report.errorCount)
        assertNull(decoded.entries.single().placeType)
        assertEquals(1, decoded.entries.single().amount)
    }

    @Test
    fun `未知 placeType 只丢这一条规则`() {
        val report = ContentReport()
        val decoded = decodeYieldRuleEntries(
            """{"entries":[
                 {"id":"rule.bad","resourceId":"res.x","action":"OBSERVE","placeType":"VOLCANO"},
                 {"id":"rule.ok","resourceId":"res.x","action":"OBSERVE"}]}""",
            "yield_rules.json",
            report,
        )

        assertEquals(listOf("rule.ok"), decoded.entries.map { it.id })
        assertEquals(1, report.errorCount)
    }
}