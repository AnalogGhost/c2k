package org.ctok.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.ctok.ui.screen.history.HistoryScreen
import org.ctok.ui.screen.home.HomeScreen
import org.ctok.ui.screen.program.ProgramSelectScreen
import org.ctok.ui.screen.workout.WorkoutScreen

object Routes {
    const val HOME    = "home"
    const val PROGRAM = "program/{programId}"
    const val WORKOUT = "workout/{programId}/{week}/{day}"
    const val HISTORY = "history"

    fun program(programId: String) = "program/$programId"
    fun workout(programId: String, week: Int, day: Int) = "workout/$programId/$week/$day"
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                onSelectProgram = { programId ->
                    navController.navigate(Routes.program(programId))
                },
                onOpenHistory = {
                    navController.navigate(Routes.HISTORY)
                }
            )
        }

        composable(
            route = Routes.PROGRAM,
            arguments = listOf(navArgument("programId") { type = NavType.StringType })
        ) { backStack ->
            val programId = backStack.arguments!!.getString("programId")!!
            ProgramSelectScreen(
                programId = programId,
                onStartWorkout = { week, day ->
                    navController.navigate(Routes.workout(programId, week, day))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.WORKOUT,
            arguments = listOf(
                navArgument("programId") { type = NavType.StringType },
                navArgument("week")      { type = NavType.IntType },
                navArgument("day")       { type = NavType.IntType }
            )
        ) { backStack ->
            val args = backStack.arguments!!
            WorkoutScreen(
                programId = args.getString("programId")!!,
                week      = args.getInt("week"),
                day       = args.getInt("day"),
                onFinished = {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                }
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(onBack = { navController.popBackStack() })
        }
    }
}
