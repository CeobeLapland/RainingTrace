package com.rainingtrace.domain.npc

/**
 * NPC 卡片上的交互动作（galgame 式"选项列"）。
 *
 * 结构从第一天就是列表化的：地图上点开 NPC 后，能做什么由这个列表决定。
 * 当前仅实现 [TALK]（跳聊天页）与 [PROFILE]（档案页），其余留给后续切片——
 * N5 做完送礼 / 委托 / 结伴时，往这里加枚举值并在卡片操作条点亮即可，
 * 不需要改卡片布局。
 */
enum class NpcInteraction(val label: String) {

    /** 聊天：跳到与该 NPC 的会话页（地图与消息共用同一份会话数据）。 */
    TALK("聊天"),

    /** 档案：斜绘大图 + 喜好 + 关系 + 今日作息轨迹的全屏页。 */
    PROFILE("档案"),

    // N5 预留（实现后再点亮，避免出现点不动死的按钮）：
    // GIFT("送礼"), ERRAND("委托"), WALK_TOGETHER("结伴出行"),
}