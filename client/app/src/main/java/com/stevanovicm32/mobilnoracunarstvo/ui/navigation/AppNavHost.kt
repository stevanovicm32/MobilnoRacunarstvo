package com.stevanovicm32.mobilnoracunarstvo.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.stevanovicm32.mobilnoracunarstvo.GameApp
import com.stevanovicm32.mobilnoracunarstvo.ui.auth.LoginScreen
import com.stevanovicm32.mobilnoracunarstvo.ui.leaderboard.LeaderboardScreen
import com.stevanovicm32.mobilnoracunarstvo.ui.map.MapScreen

object Routes {
    const val LOGIN = "login"
    const val MAP = "map"
    const val LEADERBOARD = "leaderboard"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        startDestination = if (GameApp.instance.authRepository.isLoggedIn()) {
            Routes.MAP
        } else {
            Routes.LOGIN
        }
    }

    LaunchedEffect(Unit) {
        GameApp.instance.sessionManager.sessionExpired.collect {
            navController.navigateToLogin()
        }
    }

    val destination = startDestination ?: return

    NavHost(
        navController = navController,
        startDestination = destination,
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.MAP) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.MAP) {
            MapScreen(
                onOpenLeaderboard = {
                    navController.navigate(Routes.LEADERBOARD)
                },
                onLogout = {
                    navController.navigateToLogin()
                },
            )
        }
        composable(Routes.LEADERBOARD) {
            LeaderboardScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}

private fun NavHostController.navigateToLogin() {
    navigate(Routes.LOGIN) {
        popUpTo(graph.startDestinationId) {
            inclusive = true
        }
        launchSingleTop = true
    }
}
