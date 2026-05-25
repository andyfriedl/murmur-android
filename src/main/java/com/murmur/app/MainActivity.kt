package com.murmur.app

import AppNavHost
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
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
import com.murmur.app.ui.theme.AppTheme
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import com.google.firebase.database.FirebaseDatabase
import androidx.compose.runtime.DisposableEffect
import com.murmur.app.ui.theme.extraColors
import androidx.compose.foundation.clickable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.murmur.app.ui.StreamNoticeDialog



object DeepLinkBus {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()
    fun emitSid(sid: String) { _events.tryEmit(sid) }
}


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

        if (!BuildConfig.IS_DEV) {
            getSharedPreferences("ui_prefs", MODE_PRIVATE).edit().putBoolean("dev_indicator", false).apply()
        }

        BillingHelper.init(applicationContext)
        BillingHelper.queryProductDetails(listOf("pro_upgrade1"))

        // Deep link: capture sid from murmur://join?sid=...
        intent?.data?.getQueryParameter("sid")?.let { incomingSid ->
            if (incomingSid.isNotBlank()) {
                getSharedPreferences("deeplinks", MODE_PRIVATE)
                    .edit()
                    .putString("pending_join_sid", incomingSid)
                    .apply()
            }
        }

        setContent {
            AppTheme(dynamicColor = false) {

                // create navController first so it's available everywhere in this scope
                val navController = rememberNavController()
                val ctx = LocalContext.current

                // Deep link: act on pending SID once after composition starts (cold start path)
                LaunchedEffect(Unit) {
                    val prefs = ctx.getSharedPreferences("deeplinks", android.content.Context.MODE_PRIVATE)
                    val sid = prefs.getString("pending_join_sid", null)
                    if (!sid.isNullOrBlank()) {
                        // clear first to avoid loops
                        prefs.edit().remove("pending_join_sid").apply()

                        StreamRepository.tryJoinStream(ctx, sid) { success, message ->
                            if (success) {
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    navController.navigate("stream/$sid")
                                }
                            } else {
                                android.widget.Toast
                                    .makeText(ctx, message ?: "Could not join stream.", android.widget.Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                    }
                }

                // Warm-start: react to new sid events while the activity is alive
                LaunchedEffect(Unit) {
                    DeepLinkBus.events.collect { sid ->
                        StreamRepository.tryJoinStream(ctx, sid) { success, message ->
                            if (success) {
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    navController.navigate("stream/$sid")
                                }
                            } else {
                                android.widget.Toast
                                    .makeText(ctx, message ?: "Could not join stream.", android.widget.Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                    }
                }


                AppNavHost(navController = navController)
            }
        }

    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent) // keep getIntent() in sync with the latest

        // Warm-start deep link handling: murmur://join?sid=...
        val sid = intent?.data?.getQueryParameter("sid")
        if (!sid.isNullOrBlank()) {
            DeepLinkBus.emitSid(sid)
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
    var showCreatorDeleteConfirm by remember { mutableStateOf(false) }


    BackHandler(enabled = true) {
        (context as? Activity)?.finish()
    }

    val devIndicator = remember {
        mutableStateOf(
            if (BuildConfig.IS_DEV)
                context.getSharedPreferences("ui_prefs", android.content.Context.MODE_PRIVATE)
                    .getBoolean("dev_indicator", false)
            else false
        )
    }

    // Device-level Pro entitlement for Start screen
    val deviceIsPro = remember { mutableStateOf(Upgrade.isPro(context)) }

// Keep badge in sync if user buys Pro while StartScreen is visible
    DisposableEffect(Unit) {
        val prefs = context.getSharedPreferences("murmur_prefs", android.content.Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "murmur_pro") {
                deviceIsPro.value = Upgrade.isPro(context)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }



    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header (shrink-only based on screen width)
            val logoTint = if (BuildConfig.IS_DEV && devIndicator.value) Color.Red else MaterialTheme.colorScheme.primary
            val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp

            val (logoSize, fontSizeSp) = when {
                screenWidthDp <= 340 -> 48.dp to 36.sp   // very narrow phones
                screenWidthDp <= 380 -> 56.dp to 44.sp   // small phones
                else                 -> 64.dp to 52.sp   // default / max
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.murmur_logo),
                    contentDescription = "murmur logo",
                    tint = logoTint,
                    modifier = Modifier.size(logoSize)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Box {
                    Text(
                        text = "murmur",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = fontSizeSp,
                            fontWeight = FontWeight.W900
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        softWrap = false
                    )

                    if (deviceIsPro.value) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 0.dp, y = (-6).dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Pro",
                                tint = MaterialTheme.extraColors.proGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Pro",
                                color = MaterialTheme.extraColors.proGold,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
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
                        StreamRepository.tryJoinStream(context, scannedId) { success, message ->
                            if (success) {
                                onJoinStream(scannedId)
                            } else {
                                android.widget.Toast
                                    .makeText(context, message ?: "Could not join stream.", android.widget.Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                    }
                }
            }
            if (!BuildConfig.TEST_MODE_LOBBY) {
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
                            imageVector = Icons.Outlined.QrCode, // small QR icon
                            contentDescription = "Scan QR",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Scan to Join")
                    }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.murmur_logo), // your logo
                        contentDescription = "murmur logo",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
//                    Text("Create Stream")
                    Text("Join Test Lobby")
                }
            }

            var showProDialog by remember { mutableStateOf(false) }
            val activity = LocalContext.current as? Activity

            Spacer(modifier = Modifier.height(12.dp))

            if (!deviceIsPro.value) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentWidth(Alignment.CenterHorizontally)
                        .clickable { showProDialog = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Upgrade to Pro",
                        tint = MaterialTheme.extraColors.proGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Upgrade to Pro",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.extraColors.proGold
                    )
                }


                // Show an upgrade hint under the Create button (only if not Pro yet)
                if (showProDialog) {
                    ProDialog(
                        isPro = deviceIsPro.value,
                        onDismiss = { showProDialog = false },
                        onBuy = {
                            activity?.let { act ->
                                BillingHelper.buyPro(act) { err ->
                                    Toast.makeText(act, err, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))


            if (BuildConfig.IS_DEV) {
//                Spacer(modifier = Modifier.height(12.dp))

                androidx.compose.material3.TextButton(
                    onClick = {
                        val sp = context.getSharedPreferences(
                            "ui_prefs",
                            android.content.Context.MODE_PRIVATE
                        )
                        val newVal = !devIndicator.value
                        sp.edit().putBoolean("dev_indicator", newVal).apply()
                        devIndicator.value = newVal

                    },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    Text(if (devIndicator.value) "DEV MODE" else "PROD MODE")
                }

//                Spacer(modifier = Modifier.height(62.dp))
            }



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

        // Only show Rejoin/Delete popup if stream exists
        // Only show Rejoin/Leave/Delete popup if stream exists
        if (streamId != null) {
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
                        val context = LocalContext.current

                        val deviceIsPro = remember { mutableStateOf(Upgrade.isPro(context)) }

                        DisposableEffect(context) {
                            val sp = context.getSharedPreferences("murmur_prefs", android.content.Context.MODE_PRIVATE)
                            val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                                if (key == "murmur_pro") {
                                    deviceIsPro.value = sp.getBoolean("murmur_pro", false)
                                }
                            }
                            sp.registerOnSharedPreferenceChangeListener(listener)
                            onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
                        }

                        Text(
                            text = if (isCreator) "You're already hosting a stream." else "You're already in a stream.",
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Start,          // RTL-aware "left"
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isCreator)
                                "You must delete your stream before creating or joining a new one."
                            else
                                "You must leave your stream before creating or joining a new one.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Start,          // RTL-aware "left"
                            modifier = Modifier.fillMaxWidth()    // let it align to the start edge
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        // Rejoin Button
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
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Rejoin the Stream")
                            }
                        }

                        Spacer(modifier = Modifier.height(44.dp))

                        OutlinedButton(
                            onClick = {
                                if (isCreator) {
                                    showCreatorDeleteConfirm = true
                                } else {
                                    val deviceId = StreamSession.getDeviceId(context)
                                    if (!deviceId.isNullOrBlank()) {
                                        FirebaseDatabase.getInstance()
                                            .getReference("streams")
                                            .child(streamId)
                                            .child("members")
                                            .child(deviceId)
                                            .removeValue()
                                            .addOnCompleteListener {
                                                StreamSession.clearStreamId(context)
                                                navController.navigate("start")
                                            }
                                    } else {
                                        StreamSession.clearStreamId(context)
                                        navController.navigate("start")
                                    }
                                }
                            },

                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(60.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = if (isCreator) Icons.Default.Delete else Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isCreator) "Delete Stream" else "Leave Stream",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }



                        Spacer(modifier = Modifier.height(24.dp))



                    }
                }
            }
        }

        // Creator delete confirmation dialog
        if (isCreator && showCreatorDeleteConfirm && streamId != null) {
            StreamNoticeDialog(
                title = "Delete your stream?",
                message = "Everyone will be disconnected. This can’t be undone.",
                confirmLabel = "Delete",
                onConfirm = {
                    val repo = StreamRepository(context, streamId)
                    repo.nukeStream { _, _ ->
                        showCreatorDeleteConfirm = false
                        StreamSession.clearStreamId(context)
                        navController.navigate("start")
                    }
                },
                onDismiss = { showCreatorDeleteConfirm = false }
            )
        }


    }


}





