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
import android.widget.Toast


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
                    navController.navigate("stream/$newId?fresh=true")
                },
                onJoinStream = { joinId ->
                    if (joinId.length == 6) {
                        // Likely an invite ID
                        val db = Firebase.database.reference
                        db.child("invites").child(joinId).get().addOnSuccessListener { snapshot ->
                            if (snapshot.exists()) {
                                val createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: 0L
                                val now = System.currentTimeMillis()

                                if (now - createdAt < 5 * 60 * 1000) {
                                    val streamId = snapshot.child("streamId").getValue(String::class.java)
                                    if (!streamId.isNullOrBlank()) {
                                        StreamSession.setStreamId(context, streamId)
                                        StreamSession.setIsCreator(context, false)
                                        navController.navigate("stream/$streamId?fresh=true")
                                    }
                                } else {
                                    db.child("invites").child(joinId).removeValue()
                                    Toast.makeText(context, "This invite has expired.", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                Toast.makeText(context, "Invalid invite code.", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        // Likely a direct streamId
                        StreamSession.setStreamId(context, joinId)
                        StreamSession.setIsCreator(context, false)
                        navController.navigate("stream/$joinId")
                    }
                }

            )
        }


        composable("stream/{streamId}?fresh={fresh}") { backStackEntry ->
            val streamId = backStackEntry.arguments?.getString("streamId") ?: ""
            val isFresh = backStackEntry.arguments?.getString("fresh")?.toBoolean() ?: false

            StreamScreen(
                streamId = streamId,
                onLeaveStream = { navController.navigate("start") },
                isFresh = isFresh
            )
        }
    }
}