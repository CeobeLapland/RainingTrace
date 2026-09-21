package com.rainingtrace.domain.npc

/**
 * 承诺：玩家约了某个 NPC 在某个地方见面，他答应了。
 *
 * 片 1 里 NPC **不能答应**任何约定（答应了却不去就是骗人）；片 3 有了这张表
 * 之后才可以——因为"答应"这件事有了可以兑现的落点：作息覆盖（见 [NpcScheduleOverride]）。
 *
 * [status] 就是状态机本身（AGREED → KEPT/MISSED），所以不需要额外的去重记录。
 */
enum class NpcCommitmentStatus {
    /** 已答应，还没到时间（或还在等）。 */
    AGREED,
    KEPT,
    MISSED,
}

data class NpcCommitment(
    val id: String,
    val npcId: String,
    val placeId: String,
    /** 约定在哪一天（世界时区的本地日期，yyyy-MM-dd）。 */
    val dateKey: String,
    val startMinute: Int,
    val endMinute: Int,
    /** 他走过去要多久（由 [answerCommitment] 用真实距离算出来）；0 = 本来就在那儿。 */
    val travelMinutes: Int = 0,
    val status: NpcCommitmentStatus = NpcCommitmentStatus.AGREED,
    val createdAtEpochMs: Long,
    val resolvedAtEpochMs: Long? = null,
) {
    val isOpen: Boolean get() = status == NpcCommitmentStatus.AGREED
}

/** 承诺仓储；表很小（同时只会有几条 AGREED），所以不做增量查询。 */
interface NpcCommitmentRepository {
    suspend fun all(): List<NpcCommitment>

    suspend fun save(commitment: NpcCommitment)
}

/**
 * 作息覆盖：把"答应了某天某时在某地"变成他那天真的会在那儿。
 *
 * 复用的是片 1 就定好的架构——位置 = 时间的纯函数，所以只要把覆盖条目并进作息，
 * 地图渲染、遇见判定、消息里的"此刻在做什么"**全部自动跟着变**，不需要寻路。
 *
 * [travelMinutes] 是他走过去要多久（由 [answerCommitment] 用真实距离算出来），
 * 于是"他正在路上"也是自动的。
 */
data class NpcScheduleOverride(
    val placeId: String,
    val startMinute: Int,
    val endMinute: Int,
    val activity: String,
    val travelMinutes: Int = 0,
) {
    init {
        // 故意不处理跨零点窗口：约定都是白天的短窗口，跨零点会让"覆盖哪一段"变得含糊。
        require(startMinute < endMinute) { "override window must not wrap: $startMinute..$endMinute" }
    }
}

/**
 * 叙事层要说的"关于这次约定"的事实。
 *
 * [agreed] = false 时是"他有安排、去不了"，此时模板只能说软拒绝，不承诺任何事。
 */
data class CommitmentFacts(
    val timeLabel: String,
    val placeName: String,
    val agreed: Boolean,
)