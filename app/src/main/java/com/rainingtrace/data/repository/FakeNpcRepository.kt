package com.rainingtrace.data.repository

import com.rainingtrace.domain.npc.NpcProfile
import com.rainingtrace.domain.npc.NpcRepository
import com.rainingtrace.domain.npc.NpcScheduleEntry
import com.rainingtrace.domain.npc.NpcTopic
import com.rainingtrace.domain.npc.NpcTrait

/**
 * Fake 实现：手工配置的几位校园 NPC（GDD §14）。
 *
 * 作息挂到 [FakePlaceRepository] 的地点上，**一律引用它的常量**而不是手写字符串，
 * 这样地点改名/改 id 时编译期就会报错，而不是让 NPC 静默消失。
 *
 * 覆盖到的场景（改动时别把它们改没了）：
 * - 多段作息、来回走；
 * - 只有一条作息（整天在同一个地方，永远不走路）；
 * - 作息跨零点（夜里那一段的行走窗口落在 00:00 之后）；
 * - 长时间在路上（挂在自然资源点之间）。
 */
class FakeNpcRepository(
    npcs: List<NpcProfile> = DEFAULT_NPCS,
) : NpcRepository {

    private val allNpcs: List<NpcProfile> = npcs
    private val byId: Map<String, NpcProfile> = npcs.associateBy { it.id }

    override suspend fun all(): List<NpcProfile> = allNpcs

    override suspend fun byId(id: String): NpcProfile? = byId[id]

    companion object {

        private fun at(hour: Int, minute: Int = 0): Int = hour * 60 + minute

        /** 学生：早上去图书馆，中午吃饭，晚上回宿舍。 */
        val LIN = NpcProfile(
            id = "npc.bit.lin",
            name = "林",
            oneLiner = "总在图书馆靠窗那排的人，桌上永远摊着两本书。",
            schedule = listOf(
                NpcScheduleEntry(at(7), FakePlaceRepository.DORM.id, activity = "刚起床，收拾书包"),
                NpcScheduleEntry(at(9), FakePlaceRepository.LIBRARY.id, travelMinutes = 20, activity = "在靠窗的位置自习"),
                NpcScheduleEntry(at(12, 30), FakePlaceRepository.CANTEEN.id, travelMinutes = 15, activity = "排队打饭"),
                NpcScheduleEntry(at(21, 30), FakePlaceRepository.DORM.id, travelMinutes = 25, activity = "回宿舍"),
            ),
            role = "大二学生",
            traits = setOf(NpcTrait.TACITURN, NpcTrait.PUNCTUAL),
            topics = setOf(NpcTopic.BOOKS, NpcTopic.SELF),
            favoriteTopic = NpcTopic.BOOKS,
            backstory = listOf("以前在图书馆丢过一次伞，后来就一直放在储物柜里。"),
        )

        /** 作息三段：清晨在广场，白天在食堂，晚上回宿舍。 */
        val ZHOU = NpcProfile(
            id = "npc.bit.zhou",
            name = "周",
            oneLiner = "每天绕广场跑两圈，然后去食堂帮忙收餐盘。",
            schedule = listOf(
                NpcScheduleEntry(at(6), FakePlaceRepository.PLAZA.id, activity = "在广场上慢跑"),
                NpcScheduleEntry(at(11, 30), FakePlaceRepository.CANTEEN.id, travelMinutes = 20, activity = "在食堂帮忙"),
                NpcScheduleEntry(at(21), FakePlaceRepository.DORM.id, travelMinutes = 20, activity = "回宿舍休息"),
            ),
            role = "食堂帮工",
            traits = setOf(NpcTrait.WARM, NpcTrait.TALKATIVE),
            topics = setOf(NpcTopic.FOOD, NpcTopic.RUNNING, NpcTopic.WEATHER),
            favoriteTopic = NpcTopic.RUNNING,
            backstory = listOf("每天绕广场跑两圈，下雨也跑。"),
        )

        /** 只有一条作息：整天在湖边，永远不走路。 */
        val XU = NpcProfile(
            id = "npc.bit.xu",
            name = "徐",
            oneLiner = "在湖边支着画架，一坐就是一下午。",
            schedule = listOf(
                NpcScheduleEntry(at(0), FakePlaceRepository.NORTH_LAKE.id, activity = "在湖边画画"),
            ),
            role = "画画的人",
            traits = setOf(NpcTrait.DREAMY, NpcTrait.RESERVED),
            topics = setOf(NpcTopic.ART, NpcTopic.WEATHER, NpcTopic.NIGHT),
            favoriteTopic = NpcTopic.ART,
            backstory = listOf("画架是旧的，说用了很多年，一直没换。"),
        )

        /** 作息跨零点：23:00 在花园，凌晨 2 点才回宿舍，早上又去图书馆。 */
        val HE = NpcProfile(
            id = "npc.bit.he",
            name = "何",
            oneLiner = "习惯很晚还在外面走，说夜里安静。",
            schedule = listOf(
                NpcScheduleEntry(at(8), FakePlaceRepository.LIBRARY.id, travelMinutes = 30, activity = "在图书馆翻旧书目卡"),
                NpcScheduleEntry(at(23), FakePlaceRepository.GARDEN.id, travelMinutes = 30, activity = "在花园里散步"),
                NpcScheduleEntry(at(2), FakePlaceRepository.DORM.id, travelMinutes = 45, activity = "回去睡觉"),
            ),
            role = "夜里走动的人",
            traits = setOf(NpcTrait.RESERVED, NpcTrait.CURIOUS),
            topics = setOf(NpcTopic.NIGHT, NpcTopic.BOOKS, NpcTopic.SELF),
            favoriteTopic = NpcTopic.NIGHT,
            backstory = listOf("习惯很晚还在外面走，说夜里安静。"),
        )

        /** 长时间在路上：在几个自然资源点之间巡。 */
        val QI = NpcProfile(
            id = "npc.bit.qi",
            name = "齐",
            oneLiner = "总背着布袋在花园北边转，说是在看果子熟没熟。",
            schedule = listOf(
                NpcScheduleEntry(at(7), FakePlaceRepository.ORCHARD.id, travelMinutes = 60, activity = "在果林里转"),
                NpcScheduleEntry(at(13), FakePlaceRepository.BERRY_BUSH.id, travelMinutes = 90, activity = "在浆果丛边蹲着"),
                NpcScheduleEntry(at(19), FakePlaceRepository.MUSHROOM_PATCH.id, travelMinutes = 90, activity = "在树根边找菌子"),
            ),
            role = "巡林的人",
            traits = setOf(NpcTrait.PRACTICAL, NpcTrait.TACITURN),
            topics = setOf(NpcTopic.PLANTS, NpcTopic.WEATHER, NpcTopic.FOOD),
            favoriteTopic = NpcTopic.PLANTS,
            backstory = listOf("总背着布袋在花园北边转，说是在看果子熟没熟。"),
        )

        val DEFAULT_NPCS = listOf(LIN, ZHOU, XU, HE, QI)
    }
}
