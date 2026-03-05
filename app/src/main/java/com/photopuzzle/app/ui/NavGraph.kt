package com.photopuzzle.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.photopuzzle.app.ui.screens.GameScreen
import com.photopuzzle.app.ui.screens.HomeScreen
import com.photopuzzle.app.ui.screens.StatsScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Game : Screen("game/{imageUri}/{pieceCount}") {
        fun createRoute(imageUri: String, pieceCount: Int) =
            "game/${java.net.URLEncoder.encode(imageUri, "UTF-8")}/$pieceCount"
    }
    object Stats : Screen("stats")
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Home.route) {

        composable(Screen.Home.route) {
            HomeScreen(
                onStartGame = { imageUri, pieceCount ->
                    navController.navigate(Screen.Game.createRoute(imageUri, pieceCount))
                },
                onViewStats = {
                    navController.navigate(Screen.Stats.route)
                }
            )
        }

        composable(
            route = Screen.Game.route,
            arguments = listOf(
                navArgument("imageUri") { type = NavType.StringType },
                navArgument("pieceCount") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val encodedUri = backStackEntry.arguments?.getString("imageUri") ?: ""
            val imageUri = java.net.URLDecoder.decode(encodedUri, "UTF-8")
            val pieceCount = backStackEntry.arguments?.getInt("pieceCount") ?: 25
            GameScreen(
                imageUri = imageUri,
                pieceCount = pieceCount,
                onGameComplete = { navController.popBackStack() },
                onQuit = { navController.popBackStack() }
            )
        }

        composable(Screen.Stats.route) {
            StatsScreen(onBack = { navController.popBackStack() })
        }
    }
}
