package chat.bitchat.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import chat.bitchat.feature.chat.ChatView
import chat.bitchat.feature.chat.ChatViewModel
import chat.bitchat.feature.chats.ChatsView
import chat.bitchat.feature.chats.ChatsViewModel
import chat.bitchat.feature.discover.DiscoverView
import chat.bitchat.feature.discover.DiscoverViewModel
import chat.bitchat.feature.home.HomeView
import chat.bitchat.feature.home.HomeViewModel
import chat.bitchat.feature.peer.PeerProfileView
import chat.bitchat.feature.peer.PeerProfileViewModel
import chat.bitchat.feature.profile.ProfileView
import chat.bitchat.feature.profile.ProfileViewModel
import chat.bitchat.feature.settings.SettingsView
import chat.bitchat.feature.settings.SettingsViewModel
import chat.bitchat.ui.components.EchoBottomBar
import chat.bitchat.ui.components.EchoTab
import chat.bitchat.ui.theme.EchoVoid
import chat.bitchat.ui.util.chatKey
import chat.bitchat.ui.util.decodePeerRouteId
import chat.bitchat.ui.util.encodePeerRouteId

sealed class Screen(val route: String) {
    data object Space : Screen("space")
    data object Discover : Screen("discover")
    data object Chats : Screen("chats")
    data object You : Screen("you")
    data object Settings : Screen("settings")
    data object Chat : Screen("chat/{peerId}") {
        fun createRoute(peerId: String) = "chat/${encodePeerRouteId(peerId)}"
    }
    data object Peer : Screen("peer/{peerId}") {
        fun createRoute(peerId: String) = "peer/${encodePeerRouteId(peerId)}"
    }
}

@Composable
fun EchoMeshNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val selectedTab = when (route) {
        Screen.Space.route -> EchoTab.Space
        Screen.Discover.route -> EchoTab.Discover
        Screen.Chats.route -> EchoTab.Chats
        Screen.You.route -> EchoTab.You
        else -> null
    }

    Scaffold(
        modifier = modifier,
        containerColor = EchoVoid,
        bottomBar = {
            if (selectedTab != null) {
                EchoBottomBar(
                    selected = selectedTab,
                    onSelect = { tab ->
                        val target = when (tab) {
                            EchoTab.Space -> Screen.Space.route
                            EchoTab.Discover -> Screen.Discover.route
                            EchoTab.Chats -> Screen.Chats.route
                            EchoTab.You -> Screen.You.route
                        }
                        navController.navigate(target) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Space.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Space.route) {
                val vm: HomeViewModel = hiltViewModel()
                HomeView(
                    viewModel = vm,
                    onPersonSelected = { device ->
                        navController.navigate(Screen.Peer.createRoute(device.chatKey()))
                    }
                )
            }
            composable(Screen.Discover.route) {
                val vm: DiscoverViewModel = hiltViewModel()
                DiscoverView(
                    viewModel = vm,
                    onPersonSelected = { device ->
                        navController.navigate(Screen.Peer.createRoute(device.chatKey()))
                    }
                )
            }
            composable(Screen.Chats.route) {
                val vm: ChatsViewModel = hiltViewModel()
                ChatsView(
                    viewModel = vm,
                    onOpenChat = { peerKey ->
                        navController.navigate(Screen.Chat.createRoute(peerKey))
                    }
                )
            }
            composable(Screen.You.route) {
                val vm: ProfileViewModel = hiltViewModel()
                ProfileView(
                    viewModel = vm,
                    onOpenSettings = { navController.navigate(Screen.Settings.route) }
                )
            }
            composable(Screen.Settings.route) {
                val vm: SettingsViewModel = hiltViewModel()
                SettingsView(
                    viewModel = vm,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.Peer.route,
                arguments = listOf(navArgument("peerId") { type = NavType.StringType })
            ) {
                val vm: PeerProfileViewModel = hiltViewModel()
                PeerProfileView(
                    viewModel = vm,
                    onNavigateBack = { navController.popBackStack() },
                    onMessage = { peerKey ->
                        navController.navigate(Screen.Chat.createRoute(peerKey))
                    }
                )
            }
            composable(
                route = Screen.Chat.route,
                arguments = listOf(navArgument("peerId") { type = NavType.StringType })
            ) {
                val vm: ChatViewModel = hiltViewModel()
                ChatView(
                    viewModel = vm,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
