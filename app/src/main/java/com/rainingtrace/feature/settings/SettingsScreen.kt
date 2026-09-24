package com.rainingtrace.feature.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rainingtrace.R
import com.rainingtrace.core.common.AppContainer
import com.rainingtrace.core.ui.label
import com.rainingtrace.domain.content.ContentIndex
import com.rainingtrace.domain.map.GridLevel
import com.rainingtrace.domain.settings.BackgroundInterval
import com.rainingtrace.domain.settings.DayWindow
import com.rainingtrace.domain.settings.LocationMode
import com.rainingtrace.domain.settings.NpcClockOffset
import com.rainingtrace.domain.settings.ProactiveLevel
import com.rainingtrace.domain.world.Season
import com.rainingtrace.domain.world.TimeOfDay
import com.rainingtrace.domain.world.WeatherKind

@Composable
fun SettingsRoute(
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onBack() }

    val viewModel: SettingsViewModel = viewModel {
        SettingsViewModel(
            settings = container.settingsRepository,
            changeGridLevel = container.changeGridLevel,
            mutableWeather = container.mutableWeatherProvider,
            seasonSource = container.seasonSource,
            timeOfDaySource = container.timeOfDaySource,
            contentPanel = container.contentStore,
        )
    }
    val gridLevel by viewModel.gridLevel.collectAsStateWithLifecycle()
    val locationMode by viewModel.locationMode.collectAsStateWithLifecycle()
    val tracking by viewModel.tracking.collectAsStateWithLifecycle()
    val weatherKind by viewModel.weatherKind.collectAsStateWithLifecycle()
    val season by viewModel.season.collectAsStateWithLifecycle()
    val seasonOverride by viewModel.seasonOverride.collectAsStateWithLifecycle()
    val npcClockOffset by viewModel.npcClockOffset.collectAsStateWithLifecycle()
    val npcMessages by viewModel.npcMessages.collectAsStateWithLifecycle()
    val timeOfDayOverride by viewModel.timeOfDayOverride.collectAsStateWithLifecycle()
    val contentIndex by viewModel.contentIndex.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* 拒绝也不影响记录，只是看不到那条常驻通知 */ }

    fun requestNotificationPermission() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !granted) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(48.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_back),
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "设置",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                SectionLabel("迷雾格子大小")
                Text(
                    text = "只改变迷雾的六边形粗细，走过的轨迹和地点不会移动；切换后迷雾会按轨迹重新铺。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(8.dp))
                GridLevel.entries.forEach { level ->
                    GridLevelRow(
                        level = level,
                        selected = level == gridLevel,
                        onClick = { viewModel.selectGridLevel(level) },
                    )
                }

                SectionLabel("定位方式")
                Text(
                    text = "GPS：用真实位置开雾与记录轨迹（只存本机）；Fake：点击地图移动，用于调试。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(8.dp))
                OptionRow(
                    title = "GPS 真实定位",
                    hint = "走到哪雾开到哪",
                    selected = locationMode == LocationMode.GPS,
                    onClick = { viewModel.selectLocationMode(LocationMode.GPS) },
                )
                OptionRow(
                    title = "Fake 点击移动",
                    hint = "调试用，不申请定位权限",
                    selected = locationMode == LocationMode.FAKE,
                    onClick = { viewModel.selectLocationMode(LocationMode.FAKE) },
                )

                SectionLabel("足迹记录")
                Text(
                    text = "开启后，回到桌面也会按下面间隔记录位置（系统会显示一条常驻通知）。" +
                        "后台只做「定位 → 去噪 → 写本机轨迹表」这一件事，" +
                        "迷雾、地图渲染、世界状态都不在后台跑，回到前台会自动补算。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(8.dp))
                ToggleRow(
                    title = "记录我的足迹",
                    hint = if (locationMode == LocationMode.GPS) {
                        "关闭后解锁屏期间不再记录"
                    } else {
                        "需要先切到 GPS 真实定位"
                    },
                    checked = tracking.enabled,
                    onCheckedChange = { enabled ->
                        if (enabled) requestNotificationPermission()
                        viewModel.setTrackingEnabled(enabled)
                    },
                )

                if (tracking.enabled) {
                    Text(
                        text = "后台记录间隔",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 20.dp, top = 8.dp),
                    )
                    BackgroundInterval.entries.forEach { interval ->
                        OptionRow(
                            title = interval.label,
                            hint = intervalHint(interval),
                            selected = tracking.backgroundInterval == interval,
                            onClick = { viewModel.setBackgroundInterval(interval) },
                        )
                    }

                    ToggleRow(
                        title = "仅白天记录",
                        hint = "夜里不写任何轨迹点",
                        checked = tracking.daytimeOnly,
                        onCheckedChange = viewModel::setDaytimeOnly,
                    )
                    if (tracking.daytimeOnly) {
                        DayWindow.entries.forEach { window ->
                            OptionRow(
                                title = window.label,
                                hint = "这个时段内才后台记录",
                                selected = tracking.dayWindow == window,
                                onClick = { viewModel.setDayWindow(window) },
                            )
                        }
                    }

                    if (!hasBackgroundLocationPermission(context)) {
                        Text(
                            text = "把定位权限设为「始终允许」：部分系统会限制「仅使用期间」的后台定位。" +
                                "点这里去应用设置里改。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { openAppSettings(context) }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                        )
                    }
                }

                // 消息：这是玩家偏好，不是调试项。
                SectionLabel("消息")
                Text(
                    text = "他们会偶尔主动找你说话——按自己的作息、天气和你去过的地方。" +
                        "选择你想要的频率，随时可以改。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(8.dp))
                ProactiveLevel.entries.forEach { level ->
                    OptionRow(
                        title = level.label,
                        hint = when (level) {
                            ProactiveLevel.QUIET -> "他们不会主动发消息，你去找他们就好"
                            ProactiveLevel.NORMAL -> "每天最多 ${level.dailyLimit} 条，推荐"
                            ProactiveLevel.ACTIVE -> "每天最多 ${level.dailyLimit} 条"
                        },
                        selected = npcMessages.proactiveLevel == level,
                        onClick = { viewModel.setProactiveLevel(level) },
                    )
                }
                ToggleRow(
                    title = "显示好感数值",
                    hint = "关掉后聊天页只显示关系阶段，不显示数字",
                    checked = npcMessages.showAffection,
                    onCheckedChange = viewModel::setShowAffection,
                )

                // 真实天气 API 接入前，靠这里手动切天气来验证"世界状态影响产出"。
                if (viewModel.canSetWeather) {
                    SectionLabel("世界状态（调试）")
                    Text(
                        text = "真实天气还没接。手动切换会立刻影响地图上的天气与地点产出，" +
                            "比如雨天在湖边观察会掉「湖泊记忆碎片」，雨夜还能看到「镜月鱼影」。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    WeatherKind.entries.forEach { kind ->
                        OptionRow(
                            title = kind.label(),
                            hint = weatherHint(kind),
                            selected = weatherKind == kind,
                            onClick = { viewModel.setWeatherKind(kind) },
                        )
                    }
                }

                // 时段：默认按真实时间，固定住才能验"黎明/夜晚限定"的内容。
                if (viewModel.canSetTimeOfDay) {
                    Text(
                        text = "时段",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 20.dp, top = 12.dp),
                    )
                    Text(
                        text = "默认按真实时间推导（05/08/17/20 点为界）。固定成某一段才能在白天验" +
                            "「夜晚限定」这类内容；地图左上角的 chip 会显示当前时段。",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    OptionRow(
                        title = "自动",
                        hint = "按真实时间",
                        selected = timeOfDayOverride == null,
                        onClick = { viewModel.setTimeOfDayOverride(null) },
                    )
                    TimeOfDay.entries.forEach { entry ->
                        OptionRow(
                            title = entry.label(),
                            hint = timeOfDayHint(entry),
                            selected = timeOfDayOverride == entry,
                            onClick = { viewModel.setTimeOfDayOverride(entry) },
                        )
                    }
                }

                // 季节按节气推导；手动选择只是开发者模式下的覆盖，用来验证"季节限定"内容。
                if (viewModel.canSetSeason) {
                    Text(
                        text = "季节",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 20.dp, top = 12.dp),
                    )
                    Text(
                        text = "按节气推导（立春/立夏/立秋/立冬 为界）。当前：${season.label()}。" +
                            "手动选择只是覆盖，用来提前验证季节性内容。",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    OptionRow(
                        title = "自动",
                        hint = "按节气推导",
                        selected = seasonOverride == null,
                        onClick = { viewModel.setSeason(null) },
                    )
                    Season.entries.forEach { entry ->
                        OptionRow(
                            title = entry.label(),
                            hint = seasonHint(entry),
                            selected = seasonOverride == entry,
                            onClick = { viewModel.setSeason(entry) },
                        )
                    }
                }

                // NPC 的位置读的是真实"当天第几分钟"，调试区只能覆盖"时段"四个桶，
                // 所以想看 NPC 走路得真等到他的行走窗口。挪一下就能立刻看到。
                SectionLabel("NPC（调试）")
                Text(
                    text = "只改变 NPC 此刻在哪，不影响你的轨迹、迷雾和世界状态。" +
                        "想验证「有人在走路」时，切到某个人正在赶路的时段即可。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(8.dp))
                NpcClockOffset.entries.forEach { offset ->
                    OptionRow(
                        title = offset.label,
                        hint = if (offset.minutes == 0) {
                            "按真实时间算 NPC 的位置"
                        } else {
                            "NPC 的时间 ${if (offset.minutes > 0) "+" else "-"}${kotlin.math.abs(offset.minutes) / 60} 小时"
                        },
                        selected = npcClockOffset == offset,
                        onClick = { viewModel.setNpcClockOffset(offset) },
                    )
                }

                // 内容全部来自 JSON：内置一份在 assets/content，你改的那份放 files/content。
                if (viewModel.canEditContent) {
                    SectionLabel("内容（开发者模式）")
                    Text(
                        text = "地点、资源、NPC、台词都从 JSON 读：内置的在 assets/content/*.json，" +
                            "要改就把同名文件放进应用的 files/content/ 目录（adb push），" +
                            "再点下面重新读取——不用重编译、也不用重装包。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    contentIndex?.let { index ->
                        Text(
                            text = index.counts.entries.joinToString(" · ") { (kind, count) ->
                                "${contentKindLabel(kind)} $count"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                        Spacer(Modifier.height(4.dp))
                        if (index.diagnostics.isEmpty()) {
                            Text(
                                text = "没有发现问题。",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        } else {
                            index.diagnostics.forEach { diagnostic ->
                                Text(
                                    text = diagnostic.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (diagnostic.isError) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    ActionRow(
                        title = "重新读取内容",
                        hint = "改完 files/content/*.json 之后点这里",
                        onClick = viewModel::reloadContent,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 8.dp),
    )
}

@Composable
private fun ToggleRow(
    title: String,
    hint: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun intervalHint(interval: BackgroundInterval): String = when (interval) {
    BackgroundInterval.S30 -> "最细，走一段就有多个点，也更费电"
    BackgroundInterval.M1 -> "推荐，校园散步够用"
    BackgroundInterval.M2 -> "省电，轨迹更粗"
    BackgroundInterval.M5 -> "极省电，只保留大致去向"
}

private fun weatherHint(kind: WeatherKind): String = when (kind) {
    WeatherKind.CLEAR -> "默认；只出保底产出"
    WeatherKind.CLOUDY -> "无特殊产出"
    WeatherKind.LIGHT_RAIN -> "雨天限定：湖边碎片、花园苔痕"
    WeatherKind.HEAVY_RAIN -> "雨天限定同上"
    WeatherKind.SNOW -> "配冬天可采到「霜纹」"
    WeatherKind.FOG -> "无特殊产出"
    WeatherKind.WIND -> "无特殊产出"
}

private fun seasonHint(season: Season): String = when (season) {
    Season.SPRING -> "花园掉花瓣"
    Season.SUMMER -> "暂无季节限定内容"
    Season.AUTUMN -> "花园掉松果"
    Season.WINTER -> "配雪天花园出「霜纹」"
}

private fun timeOfDayHint(timeOfDay: TimeOfDay): String = when (timeOfDay) {
    TimeOfDay.DAWN -> "05:00–08:00；可采到带露的草叶"
    TimeOfDay.DAY -> "08:00–17:00；图书馆的阅读随记"
    TimeOfDay.DUSK -> "17:00–20:00；也是阅读随记"
    TimeOfDay.NIGHT -> "20:00–05:00；夜晚水声、雨夜镜月鱼影"
}

private fun hasBackgroundLocationPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_BACKGROUND_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

/** 后台定位权限在 Android 11+ 只能去系统设置里改，这里直接跳到应用详情页。 */
private fun openAppSettings(context: android.content.Context) {
    runCatching {
        context.startActivity(
            Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@Composable
private fun GridLevelRow(
    level: GridLevel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
            ),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = level.label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = levelHint(level),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun levelHint(level: GridLevel): String = when (level) {
    GridLevel.S -> "最细腻，一栋楼约占一两格"
    GridLevel.M -> "推荐，校园尺度"
    GridLevel.L -> "更粗，开图更快"
    GridLevel.XL -> "概览，适合大范围探索"
}

@Composable
private fun OptionRow(
    title: String,
    hint: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
            ),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 立即执行的动作行（区别于 OptionRow：没有"选中"状态）。 */
@Composable
private fun ActionRow(
    title: String,
    hint: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 内容类型的中文名；没登记的 key 直接显示原名，不隐藏。 */
private fun contentKindLabel(kind: String): String = when (kind) {
    ContentIndex.PLACES -> "地点"
    else -> kind
}
