package com.nityam.movsync.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nityam.movsync.ui.create.CreateRoomScreen
import com.nityam.movsync.ui.home.HomeScreen
import com.nityam.movsync.ui.join.JoinRoomScreen
import com.nityam.movsync.ui.lobby.LobbyScreen
import com.nityam.movsync.ui.watch.LocalWatchScreen
import com.nityam.movsync.ui.watch.WatchScreen
import com.nityam.movsync.ui.settings.AboutScreen
import com.nityam.movsync.ui.settings.HelpScreen
import com.nityam.movsync.ui.settings.PrivacyPolicyScreen
import com.nityam.movsync.ui.settings.SettingsScreen

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Route.Home.path) {
        composable(Route.Home.path) {
            HomeScreen(
                onCreateRoom = { uri -> navController.navigate(Route.Create.create(uri)) },
                onJoinRoom = { navController.navigate(Route.Join.path) },
                onLocalPlay = { uri -> 
                    navController.navigate(Route.LocalWatch.create(uri))
                },
                navController = navController
            )
        }
        composable(
            route = Route.Create.path,
            arguments = listOf(
                navArgument("uri") { type = NavType.StringType }
            )
        ) { entry ->
            val uriString = String(android.util.Base64.decode(entry.arguments?.getString("uri").orEmpty(), android.util.Base64.URL_SAFE))
            val uri = Uri.parse(uriString)
            CreateRoomScreen(
                videoUri = uri,
                onBack = { navController.popBackStack() },
                onOpenLobby = { code, u ->
                    navController.navigate(Route.Lobby.create(code, isHost = true, uri = u))
                }
            )
        }
        composable(Route.Join.path) {
            JoinRoomScreen(
                onBack = { navController.popBackStack() },
                onJoined = { code, uri ->
                    navController.navigate(Route.Lobby.create(code, isHost = false, uri = uri))
                }
            )
        }
        composable(
            route = Route.Lobby.path,
            arguments = listOf(
                navArgument("roomCode") { type = NavType.StringType },
                navArgument("isHost") { type = NavType.BoolType },
                navArgument("uri") { type = NavType.StringType }
            )
        ) { entry ->
            val roomCode = entry.arguments?.getString("roomCode").orEmpty()
            val isHost = entry.arguments?.getBoolean("isHost") ?: false
            val uriBase64 = entry.arguments?.getString("uri").orEmpty()
            val uri = decodeUriOrNull(uriBase64) ?: Uri.EMPTY
            LobbyScreen(
                roomCode = roomCode,
                isHost = isHost,
                videoUri = uri,
                onBack = { navController.popBackStack(Route.Home.path, inclusive = false) },
                onStartWatching = {
                    navController.navigate(Route.Watch.create(roomCode, isHost, uri))
                }
            )
        }
        composable(
            route = Route.Watch.path,
            arguments = listOf(
                navArgument("roomCode") { type = NavType.StringType },
                navArgument("isHost") { type = NavType.BoolType },
                navArgument("uri") { type = NavType.StringType }
            )
        ) { entry ->
            WatchScreen(
                roomCode = entry.arguments?.getString("roomCode").orEmpty(),
                isHost = entry.arguments?.getBoolean("isHost") ?: false,
                videoUri = decodeUriOrNull(entry.arguments?.getString("uri").orEmpty()) ?: Uri.EMPTY,
                onLeave = { navController.popBackStack(Route.Home.path, inclusive = false) }
            )
        }
        composable(
            route = Route.LocalWatch.path,
            arguments = listOf(
                navArgument("uri") { type = NavType.StringType }
            )
        ) { entry ->
            LocalWatchScreen(
                videoUri = decodeUriOrNull(entry.arguments?.getString("uri").orEmpty()) ?: Uri.EMPTY,
                onLeave = { navController.popBackStack() }
            )
        }
        composable(Route.About.path) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        composable(Route.Help.path) {
            HelpScreen(onBack = { navController.popBackStack() })
        }
        composable(Route.PrivacyPolicy.path) {
            PrivacyPolicyScreen(onBack = { navController.popBackStack() })
        }
        composable(Route.Settings.path) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onAboutClick = { navController.navigate("about") },
                onHelpClick = { navController.navigate("help") },
                onPrivacyClick = { navController.navigate("privacy") }
            )
        }
    }
}

sealed class Route(val path: String) {
    data object Home : Route("home")
    data object Create : Route("create/{uri}") {
        fun create(uri: Uri): String {
            return "create/${encodeUri(uri)}"
        }
    }
    data object Join : Route("join")
    data object Lobby : Route("lobby/{roomCode}/{isHost}/{uri}") {
        fun create(code: String, isHost: Boolean, uri: Uri): String {
            return "lobby/$code/$isHost/${encodeUri(uri)}"
        }
    }
    data object Watch : Route("watch/{roomCode}/{isHost}/{uri}") {
        fun create(code: String, isHost: Boolean, uri: Uri): String {
            return "watch/$code/$isHost/${encodeUri(uri)}"
        }
    }
    data object LocalWatch : Route("localWatch/{uri}") {
        fun create(uri: Uri): String {
            return "localWatch/${encodeUri(uri)}"
        }
    }
    data object About : Route("about")
    data object Help : Route("help")
    data object PrivacyPolicy : Route("privacy")
    data object Settings : Route("settings")
}

private fun encodeUri(uri: Uri): String {
    return android.util.Base64.encodeToString(
        uri.toString().toByteArray(),
        android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
    )
}

private fun decodeUriOrNull(value: String): Uri? {
    if (value.isBlank()) return null
    return Uri.parse(String(android.util.Base64.decode(value, android.util.Base64.URL_SAFE)))
}
