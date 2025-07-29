import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.murmur.app.StartScreen
import com.murmur.app.StreamScreen
import com.murmur.app.StreamSession
import com.murmur.app.clearStreamId
import com.google.firebase.ktx.Firebase
import com.google.firebase.database.ktx.database

@Composable
fun AppNavHost(navController: NavHostController) {
    val context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = "start"
    ) {
        composable("start") {
            StartScreen(
                navController = navController, // ✅ THIS LINE WAS MISSING
                onCreateStream = {
                    StreamSession.clearStreamId(context)
                    val newId = StreamSession.getOrCreateStreamId(context)
                    StreamSession.setStreamId(context, newId)
                    StreamSession.setIsCreator(context, true)
                    StreamSession.setCreatorId(context, newId)
                    navController.navigate("stream/$newId")
                },
                onJoinStream = { joinId ->
                    if (joinId.isNotBlank()) {
                        val db = Firebase.database.reference
                        db.child("streams").child(joinId).get().addOnSuccessListener { snapshot ->
                            if (snapshot.exists()) {
                                StreamSession.setStreamId(context, joinId)
                                StreamSession.setIsCreator(context, false)
                                navController.navigate("stream/$joinId")
                            } else {
                                println("❌ Stream $joinId does not exist.")
                            }
                        }
                    }
                }
            )
        }


        composable("stream/{streamId}") { backStackEntry ->
            val streamId = backStackEntry.arguments?.getString("streamId") ?: return@composable
            StreamScreen(
                streamId = streamId,
                onLeaveStream = {
                    navController.navigate("start") {
                        popUpTo("start") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}