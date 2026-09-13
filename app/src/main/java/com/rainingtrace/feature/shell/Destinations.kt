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
}

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
