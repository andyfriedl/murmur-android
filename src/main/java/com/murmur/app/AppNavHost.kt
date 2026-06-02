package com.murmur.app

import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.murmurrelay.core.MurmurRelay

@Composable
fun AppNavHost(navController: NavHostController) {
    val context = LocalContext.current
    val testLobbyMode = BuildConfig.TEST_MODE_LOBBY
    val testLobbyId = "test_lobby"


    NavHost(
        navController = navController,
        startDestination = "start"
    ) {
        composable(
            route = "start",
            // leaving start -> stream (forward)
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(240)
                )
            },
            // coming back to start (back)
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(240)
                )
            }
        ) {
            StartScreen(
                navController = navController,
                onCreateStream = {
                    StreamSession.clearStreamId(context)

                    if (testLobbyMode) {
                        StreamSession.setStreamId(context, testLobbyId)
                        StreamSession.setIsCreator(context, false)
                        navController.navigate("stream/$testLobbyId?fresh=true")
                    } else {
                        val newId = StreamSession.getOrCreateStreamId(context)
                        StreamSession.setStreamId(context, newId)
                        StreamSession.setIsCreator(context, true)
                        StreamSession.setCreatorId(context, newId)
                        StreamSession.setRelayChannelKey(context, MurmurRelay.createChannelKey())
                        navController.navigate("stream/$newId?fresh=true")
                    }
                },
                onJoinStream = { joinId ->
                    if (testLobbyMode) {
                        StreamSession.setStreamId(context, testLobbyId)
                        StreamSession.setIsCreator(context, false)
                        navController.navigate("stream/$testLobbyId?fresh=true")
                        return@StartScreen
                    }

                    StreamSession.setStreamId(context, joinId)
                    StreamSession.setIsCreator(context, false)
                    navController.navigate("stream/$joinId")
                }

            )
        }

        composable(
            route = "stream/{streamId}?fresh={fresh}",
            enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(240)) },
            exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(240)) },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(240)) }
        ) { backStackEntry ->
            val streamId = backStackEntry.arguments?.getString("streamId") ?: ""
            val isFresh = backStackEntry.arguments?.getString("fresh")?.toBoolean() ?: false

            StreamScreen(
                streamId = streamId,
                onLeaveStream = {
                    val didPop = navController.popBackStack()
                    if (!didPop) {
                        navController.navigate("start") { launchSingleTop = true }
                    }
                },
                isFresh = isFresh
            )
        }
    }
}
