package com.murmur.app

import AppNavHost
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.rememberNavController
import com.murmur.app.ui.theme.BluegillTheme
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import com.google.firebase.database.FirebaseDatabase




class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FirebaseAuth.getInstance().signInAnonymously()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    println("✅ Signed in anonymously: ${task.result?.user?.uid}")
                } else {
                    println("❌ Anonymous sign-in failed: ${task.exception?.message}")
                }
            }

        enableEdgeToEdge()

        setContent {
            BluegillTheme {
                val navController = rememberNavController()
                AppNavHost(navController = navController)
            }
        }
    }
}

@Composable
fun StartScreen(
    navController: NavController,
    onCreateStream: () -> Unit,
    onJoinStream: (String) -> Unit
) {
    val context = LocalContext.current
    val isCreator = StreamSession.isCreator(context)
    val streamId = StreamSession.getStreamId(context)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_logo),
                    contentDescription = "murmur logo",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(72.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "murmur",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = 62.sp,
                        fontWeight = FontWeight.W900
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.width(8.dp))

            }

            Spacer(modifier = Modifier.height(5.dp))

            Text(
                text = "The anti-social network.",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 24.sp),
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onBackground
            )


            Spacer(modifier = Modifier.height(62.dp))

            // Scan to Join (always visible now)
            val launcher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val scannedId = result.data?.getStringExtra("streamId")
                    if (!scannedId.isNullOrBlank()) {
                        onJoinStream(scannedId)
                    }
                }
            }

            Button(
                onClick = {
                    Toast.makeText(context, "Launching scanner...", Toast.LENGTH_SHORT).show()
                    val intent = Intent(context, QRScannerActivity::class.java)
                    launcher.launch(intent)
                },
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(80.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_logo),
                        contentDescription = "App Logo",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp)) // 👈 adds horizontal padding
                    Text("Scan to Join Stream")
                }
            }


            Spacer(modifier = Modifier.height(22.dp))

            // Create Stream
            OutlinedButton(
                onClick = onCreateStream,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(80.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground)
            ) {
                Text(
                    text = "+ Create Stream",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Spacer(modifier = Modifier.height(62.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.Start,
                        modifier = Modifier
                            .wrapContentWidth()
                            .padding(horizontal = 24.dp)
                    ) {
                        Text(
                            text = "No likes.\nNo names.\nNo profiles.\nNo followers.\nNo strangers.\nNo one else.",
                            style = MaterialTheme.typography.bodyLarge,
                            lineHeight = 24.sp,
                            textAlign = TextAlign.Start,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "No personal data is saved or connected to your activity.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        // Only show Rejoin/Nuke popup if stream exists
        if (isCreator && streamId != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xAA000000)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 4.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "You're already hosting a stream.",
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "You must nuke your stream before creating or joining a new one.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                navController.navigate("stream/$streamId")
                            },
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(60.dp),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onBackground,
                                contentColor = MaterialTheme.colorScheme.background
                            )
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_logo),
                                    contentDescription = "App Logo",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Rejoin the Stream")
                            }
                        }


                        Spacer(modifier = Modifier.height(44.dp))

                        val storedStreamId = StreamSession.getStreamId(context)

                        OutlinedButton(
                            onClick = {
                                if (storedStreamId != null) {
                                    // Delete the stream from Firebase
                                    FirebaseDatabase.getInstance()
                                        .getReference("streams")
                                        .child(storedStreamId)
                                        .removeValue()
                                        .addOnCompleteListener {
                                            // Clear session data locally
                                            StreamSession.clearStreamId(context)
                                            // Return to start screen
                                            navController.navigate("start")
                                        }
                                } else {
                                    // Fallback if streamId is somehow missing
                                    StreamSession.clearStreamId(context)
                                    navController.navigate("start")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(60.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.5.dp, Color.Red),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.Red
                            ),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = "Nuke Stream",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }


                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}





