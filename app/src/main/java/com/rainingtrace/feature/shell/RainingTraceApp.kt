package com.rainingtrace.feature.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rainingtrace.R
import com.rainingtrace.core.common.AppContainer
import com.rainingtrace.feature.ar.ArScreen
import com.rainingtrace.feature.camera.CameraRoute
import com.rainingtrace.feature.home.HomeScreen
import com.rainingtrace.feature.inventory.InventoryRoute
import com.rainingtrace.feature.journal.JournalRoute
import com.rainingtrace.feature.map.MapScreen
import com.rainingtrace.feature.map.MapViewModel
import com.rainingtrace.feature.map.WorldStatusViewModel
import com.rainingtrace.feature.messages.ChatRoute
import com.rainingtrace.feature.messages.MessagesRoute
import com.rainingtrace.feature.profile.MeScreen
import com.rainingtrace.feature.settings.SettingsRoute
import com.rainingtrace.platform.ar.ArCoreController

/**
 * 应用外壳：顶部窄栏 + 底部五栏导航。
 *
 * - 一级 Tab（世界/家/消息/我的）显示顶栏与底栏。
 * - 摄像（CameraRoute）全屏沉浸，日记（JournalRoute）是「我的」的子页面，
 *   二者均隐藏外壳栏位，自带悬浮/页面级控件。
 */
@Composable
fun RainingTraceApp(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showChrome = currentRoute in TOP_LEVEL_ROUTES

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (showChrome) RainingTraceTopBar(title = topBarTitle(currentRoute))
        },
        bottomBar = {
            if (showChrome) {
                RainingTraceBottomBar(
                    container = container,
                    currentRoute = currentRoute,
                ) { route ->
                    navigateToTab(navController, route)
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.WORLD,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable(Routes.WORLD) {
                val mapViewModel: MapViewModel = viewModel {
                    MapViewModel(
                        clock = container.clock,
                        gridManager = container.gridManager,
                        locationProvider = container.locationProvider,
                        mapRenderer = container.mapRenderer,
                        recordTrackPoint = container.recordTrackPoint,
                        revealFog = container.revealFog,
                        trackRepository = container.trackRepository,
                        performPlaceAction = container.performPlaceAction,
                        placeRepository = container.placeRepository,
                        explorationRepository = container.explorationRepository,
                        memoryRepository = container.memoryRepository,
                        memoryFocus = container.memoryFocusRequest,
                        trackDayFocus = container.trackDayFocusRequest,
                        npcPresence = container.npcPresence,
                        npcRepository = container.npcRepository,
                        recordNpcEncounter = container.recordNpcEncounter,
                        worldState = container.worldStateProvider,
                        foregroundState = container.foregroundState,
                        debugMapTap = container.debugMapTap,
                        settings = container.settingsRepository,
                        refreshLocation = container::refreshLocation,
                        placeWriter = container.placeWriter,
                        locationHealth = container.locationProvider,
                    )
                }
                MapScreen(
                    viewModel = mapViewModel,
                    worldStatus = viewModel {
                        WorldStatusViewModel(worldState = container.worldStateProvider)
                    },
                    mapAdapter = container.mapRenderer,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            composable(Routes.HOME) {
                HomeScreen()
            }

            composable(Routes.CAMERA) {
                CameraRoute(
                    container = container,
                    arController = container.arController as ArCoreController,
                    onClose = { navController.popBackStack() },
                )
            }

            composable(Routes.MESSAGES) {
                MessagesRoute(
                    container = container,
                    onOpenChat = { npcId -> navController.navigate(npcChatRoute(npcId)) },
                )
            }

            composable(
                route = Routes.NPC_CHAT,
                arguments = listOf(navArgument(ARG_NPC_ID) { type = NavType.StringType }),
            ) { entry ->
                ChatRoute(
                    container = container,
                    npcId = entry.arguments?.getString(ARG_NPC_ID).orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.ME) {
                MeScreen(
                    onOpenJournal = { navController.navigate(Routes.JOURNAL) },
                    onOpenInventory = { navController.navigate(Routes.INVENTORY) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }

            composable(Routes.JOURNAL) {
                JournalRoute(
                    container = container,
                    onBack = { navController.popBackStack() },
                    onViewOnMap = { memory ->
                        // 先把聚焦请求放进容器，再切回世界 Tab；
                        // 地图侧可能被重建（VM 首次 collect 就会读到），也可能还活着（collect 收到变化）。
                        container.memoryFocusRequest.request(memory)
                        navigateToTab(navController, Routes.WORLD)
                    },
                    onViewTrackDay = { date ->
                        // 与记忆同构：轨迹日历「在地图查看」也走一次性聚焦请求。
                        container.trackDayFocusRequest.request(date)
                        navigateToTab(navController, Routes.WORLD)
                    },
                )
            }

            composable(Routes.INVENTORY) {
                InventoryRoute(
                    container = container,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsRoute(
                    container = container,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private val TOP_LEVEL_ROUTES = setOf(
    Routes.WORLD,
    Routes.HOME,
    Routes.MESSAGES,
    Routes.ME,
)

private fun navigateToTab(
    navController: androidx.navigation.NavHostController,
    route: String,
) {
    if (route == Routes.CAMERA) {
        // 摄像作为覆盖层压在当前一级页之上，关闭即回到来源 Tab。
        navController.navigate(Routes.CAMERA) {
            launchSingleTop = true
        }
        return
    }
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun RainingTraceTopBar(title: String) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(48.dp)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_rain_mark),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

/**
 * 底栏。**未读数在这里订阅**，而不是在 [RainingTraceApp] 顶层——
 * 放顶层的话每来一条消息都会重建 Scaffold + NavHost，导航与滚动会抖。
 */
@Composable
private fun RainingTraceBottomBar(
    container: AppContainer,
    currentRoute: String?,
    onTab: (String) -> Unit,
) {
    val unreadCount by container.npcMessageRepository.observeUnreadCount()
        .collectAsStateWithLifecycle(initialValue = 0)

    NavigationBar(
        modifier = Modifier.navigationBarsPadding(),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        BOTTOM_TABS.forEach { tab ->
            val selected = currentRoute == tab.route
            if (tab.route == Routes.CAMERA) {
                NavigationBarItem(
                    selected = false,
                    onClick = { onTab(tab.route) },
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(tab.iconRes),
                                contentDescription = tab.label,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    },
                    label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                    colors = barItemColors(),
                )
            } else {
                NavigationBarItem(
                    selected = selected,
                    onClick = { if (!selected) onTab(tab.route) },
                    icon = {
                        // 未读红点：不做的话玩家不知道 NPC 找过自己。
                        if (tab.route == Routes.MESSAGES && unreadCount > 0) {
                            BadgedBox(
                                badge = {
                                    Badge { Text(unreadCount.coerceAtMost(99).toString()) }
                                },
                            ) {
                                TabIcon(tab)
                            }
                        } else {
                            TabIcon(tab)
                        }
                    },
                    label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                    colors = barItemColors(),
                )
            }
        }
    }
}

@Composable
private fun TabIcon(tab: TabSpec) {
    Icon(
        painter = painterResource(tab.iconRes),
        contentDescription = tab.label,
        modifier = Modifier.size(23.dp),
    )
}

@Composable
private fun barItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary,
    selectedTextColor = MaterialTheme.colorScheme.primary,
    indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
)
