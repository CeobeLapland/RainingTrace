package com.rainingtrace.feature.shell

import androidx.annotation.DrawableRes
import com.rainingtrace.R

/**
 * 导航路由常量。一级五个 Tab：世界 / 家 / 摄像 / 消息 / 我的；
 * 日记是「我的」之下的子路由。
 */
object Routes {
    const val WORLD = "world"
    const val HOME = "home"
    const val CAMERA = "camera"
    const val MESSAGES = "messages"
    const val ME = "me"
    const val JOURNAL = "journal"
    const val INVENTORY = "inventory"
    const val CRAFT = "craft"
    const val WAREHOUSE = "warehouse"
    const val SETTINGS = "settings"

    /**
     * 与某个 NPC 的聊天页（消息 Tab 的子路由）。
     *
     * 项目里唯一带导航参数的路由：聊天页是标准的主从导航，
     * 用"一次性请求对象"反而要额外处理 consume 时机，进程被回收后也恢复不了。
     */
    const val NPC_CHAT = "npc_chat/{npcId}"
}

/** 聊天页的导航参数名。 */
const val ARG_NPC_ID = "npcId"

/** 聊天页路径；配合 [Routes.NPC_CHAT] 使用。 */
fun npcChatRoute(npcId: String): String = "npc_chat/$npcId"

data class TabSpec(
    val route: String,
    val label: String,
    @param:DrawableRes val iconRes: Int,
)

/** 底栏顺序：摄像居中，作为"现实输入器"入口。 */
val BOTTOM_TABS: List<TabSpec> = listOf(
    TabSpec(Routes.WORLD, "世界", R.drawable.ic_tab_world),
    TabSpec(Routes.HOME, "家", R.drawable.ic_tab_home),
    TabSpec(Routes.CAMERA, "摄像", R.drawable.ic_tab_camera),
    TabSpec(Routes.MESSAGES, "消息", R.drawable.ic_tab_messages),
    TabSpec(Routes.ME, "我的", R.drawable.ic_tab_me),
)

/** 顶部窄栏分区名。 */
fun topBarTitle(route: String?): String = when (route) {
    Routes.WORLD -> "世界"
    Routes.HOME -> "家"
    Routes.MESSAGES -> "消息"
    Routes.ME -> "我的"
    else -> "雨迹"
}
