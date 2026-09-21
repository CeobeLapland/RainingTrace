package com.rainingtrace.data.repository

import com.rainingtrace.domain.map.PlaceType
import com.rainingtrace.domain.npc.NpcProactiveRule
import com.rainingtrace.domain.npc.NpcProactiveRuleCatalog
import com.rainingtrace.domain.npc.NpcTriggerCondition
import com.rainingtrace.domain.world.Season
import com.rainingtrace.domain.world.TimeOfDay
import com.rainingtrace.domain.world.WeatherKind
import com.rainingtrace.domain.world.WorldCondition

/**
 * 默认的主动消息规则（8 条，覆盖现有 5 个 NPC）。
 *
 * 放在 data 层而不是 domain：内容要引用具体的 npcId，和 [FakeNpcRepository] /
 * [FakePlaceRepository] 一起维护才不会写错名字（地点用 [PlaceType] 表达语义，
 * 不需要写 id）。
 *
 * `{place}` / `{activity}` 两个槽位由引擎用**他此刻的真实位置**填，
 * 所以规则里说出来的地点永远是准的。
 */
class FakeNpcProactiveRuleCatalog(
    override val rules: List<NpcProactiveRule> = DEFAULT,
) : NpcProactiveRuleCatalog {

    companion object {
        private const val HOUR_MS = 60L * 60 * 1000

        val DEFAULT: List<NpcProactiveRule> = listOf(
            // 下雨 + 白天：林在图书馆，想让你也来。
            NpcProactiveRule(
                id = "rule.lin.rain_library",
                npcId = FakeNpcRepository.LIN.id,
                condition = NpcTriggerCondition.All(
                    listOf(
                        NpcTriggerCondition.WeatherBecame(WeatherKind.RAINY),
                        NpcTriggerCondition.World(
                            WorldCondition.TimeOfDayIn(setOf(TimeOfDay.DAY)),
                        ),
                    ),
                ),
                text = "下雨了，{place}这边人少，挺安静的。",
                cooldownMs = 12 * HOUR_MS,
            ),

            // 清早 + 你两天没露面：周在广场跑完步，顺口喊你。
            NpcProactiveRule(
                id = "rule.zhou.plaza_morning",
                npcId = FakeNpcRepository.ZHOU.id,
                condition = NpcTriggerCondition.All(
                    listOf(
                        NpcTriggerCondition.World(
                            WorldCondition.BetweenMinutes(6 * 60, 8 * 60),
                        ),
                        NpcTriggerCondition.PlayerIdleFor(2),
                    ),
                ),
                text = "刚跑完两圈。这两天没见着你，是不是起晚了。",
                cooldownMs = 24 * HOUR_MS,
            ),

            // 你刚去过湖边 + 黄昏：徐提一句那会儿的光。
            NpcProactiveRule(
                id = "rule.xu.lake_dusk",
                npcId = FakeNpcRepository.XU.id,
                condition = NpcTriggerCondition.All(
                    listOf(
                        NpcTriggerCondition.PlayerEnteredPlace(PlaceType.LAKE),
                        NpcTriggerCondition.World(
                            WorldCondition.TimeOfDayIn(setOf(TimeOfDay.DUSK)),
                        ),
                    ),
                ),
                text = "你刚去过湖边吧。那会儿的光最好，我正好收了画架。",
                cooldownMs = 8 * HOUR_MS,
            ),

            // 夜里起雾：何出来走路，说一句。
            NpcProactiveRule(
                id = "rule.he.night_fog",
                npcId = FakeNpcRepository.HE.id,
                condition = NpcTriggerCondition.All(
                    listOf(
                        NpcTriggerCondition.World(
                            WorldCondition.TimeOfDayIn(setOf(TimeOfDay.NIGHT)),
                        ),
                        NpcTriggerCondition.WeatherBecame(setOf(WeatherKind.FOG)),
                    ),
                ),
                text = "起雾了。这个点出来走，雾里几乎看不见人。",
                cooldownMs = 12 * HOUR_MS,
            ),

            // 秋天白天：齐在果林，报一句果子的进度。
            NpcProactiveRule(
                id = "rule.qi.orchard_autumn",
                npcId = FakeNpcRepository.QI.id,
                condition = NpcTriggerCondition.All(
                    listOf(
                        NpcTriggerCondition.World(
                            WorldCondition.SeasonIn(setOf(Season.AUTUMN)),
                        ),
                        NpcTriggerCondition.World(
                            WorldCondition.TimeOfDayIn(setOf(TimeOfDay.DAY)),
                        ),
                    ),
                ),
                text = "{place}这边的果子快熟了，再等几天。",
                cooldownMs = 24 * HOUR_MS,
            ),

            // 你刚遇见何：齐来"转述"一句。
            NpcProactiveRule(
                id = "rule.qi.heard_about_he",
                npcId = FakeNpcRepository.QI.id,
                condition = NpcTriggerCondition.PlayerMetNpc(FakeNpcRepository.HE.id),
                text = "听说你今天碰到何了。他话不多，人不坏。",
                cooldownMs = 24 * HOUR_MS,
            ),

            // 春天下雨：徐说湖面的圈。
            NpcProactiveRule(
                id = "rule.xu.spring_rain",
                npcId = FakeNpcRepository.XU.id,
                condition = NpcTriggerCondition.All(
                    listOf(
                        NpcTriggerCondition.WeatherBecame(WeatherKind.RAINY),
                        NpcTriggerCondition.World(
                            WorldCondition.SeasonIn(setOf(Season.SPRING)),
                        ),
                    ),
                ),
                text = "春天的雨最有意思，湖面上全是圈。",
                cooldownMs = 12 * HOUR_MS,
            ),

            // 三天没说话：周直接来问一句。
            NpcProactiveRule(
                id = "rule.zhou.idle_3d",
                npcId = FakeNpcRepository.ZHOU.id,
                condition = NpcTriggerCondition.PlayerIdleFor(3),
                text = "有几天没见你了，食堂还是老样子。",
                cooldownMs = 48 * HOUR_MS,
            ),

            // 约好了没来（片 3）：他不生气，但会提一句。
            NpcProactiveRule(
                id = "rule.qi.missed_commitment",
                npcId = FakeNpcRepository.QI.id,
                condition = NpcTriggerCondition.PlayerMissedCommitment(FakeNpcRepository.QI.id),
                text = "昨天在{place}等了一会儿，没见着你。没事，改天吧。",
                cooldownMs = 48 * HOUR_MS,
            ),
            NpcProactiveRule(
                id = "rule.zhou.missed_commitment",
                npcId = FakeNpcRepository.ZHOU.id,
                condition = NpcTriggerCondition.PlayerMissedCommitment(FakeNpcRepository.ZHOU.id),
                text = "昨天答应你那事，我到了，你没来。下次提前说一声。",
                cooldownMs = 48 * HOUR_MS,
            ),
        )
    }
}
