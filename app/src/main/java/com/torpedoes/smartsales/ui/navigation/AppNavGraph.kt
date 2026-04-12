package com.torpedoes.smartsales.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.torpedoes.smartsales.ui.screens.MainScreen
import com.torpedoes.smartsales.ui.screens.auth.ForgotPasswordScreen
import com.torpedoes.smartsales.ui.screens.auth.LoginScreen
import com.torpedoes.smartsales.ui.screens.auth.SignUpScreen
import com.torpedoes.smartsales.ui.screens.splash.SplashScreen

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = NavRoutes.SPLASH) {

        composable(NavRoutes.SPLASH) {
            SplashScreen(
                onNavigateToLogin = {
                    navController.navigate(NavRoutes.LOGIN) {
                        popUpTo(NavRoutes.SPLASH) { inclusive = true }
                    }
                },
                onNavigateToDashboard = {                   // ← new: already logged in
                    navController.navigate(NavRoutes.DASHBOARD) {
                        popUpTo(NavRoutes.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route     = "${NavRoutes.LOGIN}?prefillEmail={prefillEmail}",
            arguments = listOf(navArgument("prefillEmail") {
                type         = NavType.StringType
                defaultValue = ""
            })
        ) { backStackEntry ->
            val prefill = backStackEntry.arguments?.getString("prefillEmail") ?: ""
            LoginScreen(
                prefillEmail               = prefill,
                onNavigateToDashboard      = {
                    navController.navigate(NavRoutes.DASHBOARD) {
                        popUpTo(NavRoutes.SPLASH) { inclusive = true }
                    }
                },
                onNavigateToForgotPassword = {
                    navController.navigate(NavRoutes.FORGOT_PASSWORD)
                },
                onNavigateToSignUp         = {
                    navController.navigate(NavRoutes.SIGN_UP)
                }
            )
        }

        composable(NavRoutes.SIGN_UP) {
            SignUpScreen(
                onSignUpSuccess   = {
                    navController.navigate(NavRoutes.DASHBOARD) {
                        popUpTo(NavRoutes.SPLASH) { inclusive = true }
                    }
                },
                onNavigateToLogin = { prefillEmail ->
                    val route = if (prefillEmail.isNotBlank())
                        "${NavRoutes.LOGIN}?prefillEmail=$prefillEmail"
                    else NavRoutes.LOGIN
                    navController.navigate(route) {
                        popUpTo(NavRoutes.SIGN_UP) { inclusive = true }
                    }
                }
            )
        }

        composable(NavRoutes.FORGOT_PASSWORD) {
            ForgotPasswordScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(NavRoutes.DASHBOARD) {
            MainScreen()
        }
    }
}