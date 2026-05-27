package com.murmur.app

import LoadingOverlay
import android.app.Activity
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import java.util.concurrent.TimeUnit
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.foundation.shape.CircleShape // optional if you use it elsewhere
import androidx.compose.ui.draw.clip
import com.murmur.app.ui.theme.extraColors
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.MutableTransitionState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.murmur.app.ui.StreamNoticeDialog
import com.murmurrelay.core.MurmurRelay


@Composable
fun StreamScreen(
    streamId: String,
    onLeaveStream: () -> Unit,
    isFresh: Boolean = false
) {


    var showQR by remember { mutableStateOf(false) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isGeneratingQR by remember { mutableStateOf(false) }
    var lastInviteId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var showProDialog by remember { mutableStateOf(false) }
    val isProState = remember { mutableStateOf(Upgrade.isPro(context)) }
    val streamIsPro = remember { mutableStateOf(false) }
    val createdAt = remember { mutableStateOf<Long?>(null) }
    val proRuleNoteSent = rememberSaveable(streamId) { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var showCreatorEnded by remember { mutableStateOf(false) }




    val devIndicator = remember {
        mutableStateOf(
            if (BuildConfig.IS_DEV)
                context.getSharedPreferences("ui_prefs", android.content.Context.MODE_PRIVATE)
                    .getBoolean("dev_indicator", false)
            else false
        )
    }


    val inStream = StreamSession.getStreamId(context).isNullOrBlank().not()

    fun getOrCreateRelayKey(): String {
        return StreamSession.getRelayChannelKey(context)
            ?: MurmurRelay.createChannelKey().also { newKey ->
                StreamSession.setRelayChannelKey(context, newKey)
            }
    }

    BackHandler(enabled = !inStream) {
        (context as? Activity)?.finish()
    }

    val activeStreamId = remember {
        streamId ?: StreamSession.getOrCreateStreamId(context)
    }
    val viewModel = remember(context, activeStreamId) {
        StreamViewModel(context, activeStreamId)
    }

    val isCreator by viewModel.isCreator.collectAsState()
    val streamDeleted by viewModel.streamDeleted.collectAsState()
    val isTestLobby = com.murmur.app.BuildConfig.TEST_MODE_LOBBY && streamId == "test_lobby"
    val effectiveIsCreator = isCreator && !isTestLobby

    LaunchedEffect(streamDeleted, isCreator) {
        if (!streamDeleted) return@LaunchedEffect

        if (isCreator) {
            viewModel.handleStreamDeleted()
            onLeaveStream()
        } else {
            showCreatorEnded = true
        }
    }


    val shouldLeave by viewModel.shouldLeaveStream.collectAsState()
    LaunchedEffect(shouldLeave) {
        if (shouldLeave) {
            onLeaveStream()
        }
    }

    val messages by viewModel.messages.collectAsState()
    val memberCount by viewModel.memberCount.collectAsState()

    // Defensive: if the stream is flagged deleted or removed, leave immediately.
    val kicked = remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    DisposableEffect(streamId) {
        val ref = com.google.firebase.database.FirebaseDatabase.getInstance()
            .getReference("streams")
            .child(streamId)

        val listener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                if (kicked.value) return
                val exists = snapshot.exists()
                val deleted = snapshot.child("deleted").getValue(Boolean::class.java) == true
                if (!exists || deleted) {
                    kicked.value = true
                    StreamSession.clearStreamId(ctx)
                    onLeaveStream()
                }
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                if (kicked.value) return
                // Treat cancellations as "stream unavailable"
                kicked.value = true
                StreamSession.clearStreamId(ctx)
                onLeaveStream()
            }
        }

        ref.addValueEventListener(listener)
        onDispose { ref.removeEventListener(listener) }
    }


    LaunchedEffect(memberCount) {
        if (isCreator && showQR && memberCount > 1) {
            println("👥 Someone joined! Closing QR and deleting invite.")
            showQR = false
            lastInviteId?.let {
                StreamRepository.deleteInvite(it)
                println("🧽 Deleted invite: $it")
            }
        }
    }

    LaunchedEffect(isCreator, isProState.value, isTestLobby) {
        if (!isTestLobby && isCreator && isProState.value) {
            com.google.firebase.database.FirebaseDatabase.getInstance()
                .getReference("streams")
                .child(streamId)
                .child("pro")
                .setValue(true)
        }
    }

    // REPLACE your existing LaunchedEffect(isCreator, isFresh) with this:
    LaunchedEffect(isCreator, isFresh) {
        if (!isCreator) return@LaunchedEffect

        val streamRef = com.google.firebase.database.FirebaseDatabase.getInstance()
            .getReference("streams")
            .child(streamId)

        // Ensure the stream node exists and has createdAt BEFORE generating the invite
        streamRef.child("createdAt").get().addOnSuccessListener { snap ->
            fun startInviteFlow() {
                if (isFresh) {
                    isGeneratingQR = true
                    StreamRepository.createInviteId(streamId) { inviteId ->
                        isGeneratingQR = false
                        if (inviteId != null) {
                            val payload = DeepLinkUtil.buildJoinQrPayload(
                                streamId = streamId,
                                relayKey = getOrCreateRelayKey(),
                                nonce = inviteId
                            )
                            qrBitmap = QRCodeHelper.generateQRCode(payload)
                            lastInviteId = inviteId
                            showQR = true
                        }
                    }
                }
            }

            if (!snap.exists()) {
                streamRef.child("createdAt")
                    .setValue(com.google.firebase.database.ServerValue.TIMESTAMP)
                    .addOnSuccessListener { startInviteFlow() }
                    .addOnFailureListener { /* optional: toast/log */ }
            } else {
                startInviteFlow()
            }
        }.addOnFailureListener {
            // optional: toast/log
        }
    }



    DisposableEffect(streamId) {
        val ref = com.google.firebase.database.FirebaseDatabase
            .getInstance()
            .getReference("streams")
            .child(streamId)
            .child("pro")

        val listener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                streamIsPro.value = snapshot.getValue(Boolean::class.java) == true
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) { }
        }

        ref.addValueEventListener(listener)

        onDispose {
            ref.removeEventListener(listener)
        }
    }

    DisposableEffect(streamId) {
        val ref = com.google.firebase.database.FirebaseDatabase
            .getInstance()
            .getReference("streams")
            .child(streamId)
            .child("createdAt")

        val listener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                createdAt.value = snapshot.getValue(Long::class.java)
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) { }
        }
        ref.addValueEventListener(listener)
        onDispose { ref.removeEventListener(listener) }
    }

    DisposableEffect(context) {
        val sp = context.getSharedPreferences("murmur_prefs", android.content.Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "murmur_pro") {
                isProState.value = sp.getBoolean("murmur_pro", false)
            }
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                viewModel.touchPresence()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isProState.value, showProDialog) {
        if (isProState.value && showProDialog) {
            showProDialog = false
        }
    }


    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()


    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(12.dp)
            ) {

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp, start = 16.dp, end = 16.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // App name + logo
                        Row(verticalAlignment = Alignment.CenterVertically) {

                            val logoTint = if (BuildConfig.IS_DEV && devIndicator.value) Color.Red else MaterialTheme.colorScheme.primary

                            Icon(
                                painter = painterResource(id = R.drawable.murmur_logo),
                                contentDescription = "murmur logo",
                                tint = logoTint,
                                modifier = Modifier.size(40.dp)
                            )
                            if (streamIsPro.value) {
                                Spacer(Modifier.width(8.dp)) // space between logo and pro label
                                Row(verticalAlignment = Alignment.CenterVertically) {
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

                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        // Delete / Leave button
                        var expanded by remember { mutableStateOf(false) }
//                        var isDeleting by remember { mutableStateOf(false) }
                        val showRedDot = remember { mutableStateOf(UiPrefs.shouldShowRedDot(context)) }

                        Box {
                            // subtle “pop” + icon twist when the menu opens
                            val openScale by animateFloatAsState(
                                targetValue = if (expanded) 1f else 0.96f,
                                animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f)
                            )
                            val iconRotation by animateFloatAsState(
                                targetValue = if (expanded) 90f else 0f,
                                animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f)
                            )

                            IconButton(
                                onClick = {
                                    UiPrefs.markRedDotDismissed(context)
                                    showRedDot.value = false
                                    expanded = true
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .graphicsLayer(scaleX = openScale, scaleY = openScale) // <- animate container
                            ) {
                                Box {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = "Menu",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .graphicsLayer(rotationZ = iconRotation)        // <- animate icon
                                    )
                                    if (showRedDot.value) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .background(
                                                    color = MaterialTheme.colorScheme.error,
                                                    shape = RoundedCornerShape(50)
                                                )
                                                .align(Alignment.TopEnd)
                                        )
                                    }
                                }
                            }



                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                            ) {
                                if (!BuildConfig.TEST_MODE_LOBBY) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Invite to Stream",
                                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp)
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_logo),
                                                contentDescription = "Murmured Logo",
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        },
                                        onClick = {
                                            expanded = false
                                            isGeneratingQR = true
                                            StreamRepository.createInviteId(streamId) { inviteId ->
                                                isGeneratingQR = false
                                                if (inviteId != null) {
                                                    val payload = DeepLinkUtil.buildJoinQrPayload(streamId, nonce = inviteId)
                                                    val generated = QRCodeHelper.generateQRCode(payload)
                                                    qrBitmap = generated
                                                    lastInviteId = inviteId
                                                    showQR = true
                                                }
                                            }
                                        }
                                    )
                                }



                                Spacer(modifier = Modifier.height(4.dp))

                                val redStyle = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )

                                val redTrashIcon: @Composable (() -> Unit) = {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Icon",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }


                                if (effectiveIsCreator) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (isDeleting) "Deleting…" else "Delete Stream",
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isDeleting) MaterialTheme.colorScheme.onSurfaceVariant
                                                    else MaterialTheme.colorScheme.error
                                                )
                                            )
                                        },
                                        leadingIcon = {
                                            if (isDeleting) {
                                                androidx.compose.material3.CircularProgressIndicator(
                                                    modifier = Modifier.size(18.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete Icon",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        },
                                        enabled = !isDeleting,
                                        onClick = {
                                            expanded = false
                                            isDeleting = false
                                            showDeleteConfirm = true
                                        }




                                    )
                                }
                                else {
                                    DropdownMenuItem(
                                        text = { Text("Leave Stream", style = redStyle) },
                                        leadingIcon = redTrashIcon,
                                        onClick = {
                                            expanded = false
                                            viewModel.leaveStream {
                                                StreamSession.clearStreamId(context)   // <- clear local session for joiners
                                                onLeaveStream()
                                            }
                                        }
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))

                                // thin divider
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                                if (!isTestLobby) {
                                    if (isProState.value) {
                                        // Show label only, no action
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    "Pro unlocked",
                                                    style = MaterialTheme.typography.bodyLarge.copy(
                                                        fontSize = 18.sp
                                                    ),
                                                    color = MaterialTheme.extraColors.proGold
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Star,
                                                    contentDescription = "Pro",
                                                    tint = MaterialTheme.extraColors.proGold,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            },
                                            enabled = false,        // not tappable
                                            onClick = {}            // required param; does nothing
                                        )
                                    } else {
                                        // Existing Upgrade option (clickable)
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    "Upgrade to Pro",
                                                    style = MaterialTheme.typography.bodyLarge.copy(
                                                        fontSize = 18.sp
                                                    ),
                                                    color = MaterialTheme.extraColors.proGold
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Star,
                                                    contentDescription = "Pro",
                                                    tint = MaterialTheme.extraColors.proGold,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            },
                                            onClick = {
                                                expanded = false
                                                showProDialog = true
                                            }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))


                                if (BuildConfig.IS_DEV) {
                                    val proState = remember { mutableStateOf(Upgrade.isPro(context)) }

                                    DropdownMenuItem(
                                        text = { Text(if (proState.value) "Switch to Free (test)" else "Unlock Pro (test)") },
                                        onClick = {
                                            val newValue = !proState.value

                                            Upgrade.setPro(context, newValue)
                                            proState.value = newValue
                                            isProState.value = newValue

                                            if (isCreator) {
                                                val ref =
                                                    com.google.firebase.database.FirebaseDatabase.getInstance()
                                                        .getReference("streams")
                                                        .child(streamId)
                                                        .child("pro")
                                                if (newValue) ref.setValue(true) else ref.removeValue()
                                            }

                                            android.widget.Toast.makeText(
                                                context,
                                                if (newValue) "Pro enabled (test)" else "Pro disabled (test)",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()

                                            expanded = false
                                        }
                                    )
                                }


                            }
                        }


                    }
                }

                if (showProDialog) {
                    val activity = (LocalContext.current as? Activity)
                    ProDialog(
                        isPro = isProState.value,
                        onDismiss = { showProDialog = false },
                        onBuy = {
                            activity?.let {
                                BillingHelper.buyPro(it) { err ->
                                    android.widget.Toast.makeText(it, err, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }


                Text(
                    text = "$memberCount in stream",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 4.dp, start = 16.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            tonalElevation = 1.dp,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            // Build a stable (id, message) list, then reverse for bottom-anchored UI
                            val itemsForUi = remember(messages) {
                                messages.mapIndexed { id, msg -> id to msg }.asReversed()
                            }

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                state = listState,
                                reverseLayout = true,                          // newest stays near the input
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                if (itemsForUi.isEmpty()) {
                                    item {
                                        Text(
                                            text = "No messages yet...",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .padding(top = 24.dp, start = 16.dp)
                                        )
                                    }
                                } else {
                                    items(
                                        items = itemsForUi,
                                        key = { it.first }                     // stable key = original index
                                    ) { (id, msg) ->
                                        // Per-item enter animation trigger
                                        val appear = remember(id) { MutableTransitionState(false) }
                                        LaunchedEffect(id) { appear.targetState = true }

                                        AnimatedVisibility(
                                            visibleState = appear,
                                            enter = fadeIn() + slideInVertically { it / 6 }, // tweak 6→4 for more motion
                                            exit  = fadeOut()
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp, horizontal = 8.dp),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(16.dp),
                                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                                    tonalElevation = 2.dp,
                                                    modifier = Modifier.padding(end = 48.dp)
                                                ) {
                                                    Text(
                                                        text = msg,
                                                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp),
                                                        color = MaterialTheme.colorScheme.onBackground,
                                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }








                Spacer(modifier = Modifier.height(16.dp)) // reduce vertical gap above input

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {

                    // Character count above input field
                    Text(
                        text = "${input.length}/400",
                        color = if (input.length >= 400) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentWidth(Alignment.End)
                            .padding(end = 12.dp, bottom = 4.dp)
                    )

                    // 1) Compute limits and any Free restrictions (goes RIGHT AFTER the character-count Text)
                    val limit = if (streamIsPro.value) {
                        UpgradeConfig.PRO_STREAM_MESSAGE_LIMIT
                    } else {
                        UpgradeConfig.FREE_STREAM_MESSAGE_LIMIT
                    }

                    val streamExpired = !isTestLobby && !streamIsPro.value &&
                            (createdAt.value?.let { created ->
                                val ageMs = System.currentTimeMillis() - created
                                ageMs > TimeUnit.DAYS.toMillis(UpgradeConfig.FREE_STREAM_MAX_INACTIVITY_DAYS.toLong())
                            } ?: false)

                    val messageCapReached = !isTestLobby && !streamIsPro.value && messages.size >= limit


                    val freeRestrictionReason: String? = when {
                        streamExpired && isCreator ->
                            "This free stream expired. Upgrade to Pro to keep it alive and remove limits."
                        streamExpired && !isCreator ->
                            "This free stream expired. Only the creator can upgrade to Pro to keep chatting."
                        messageCapReached && isCreator ->
                            "Free message limit reached. Upgrade to Pro to continue the conversation."
                        messageCapReached && !isCreator ->
                            "Free message limit reached. Only the creator can upgrade this stream to Pro."
                        else -> null
                    }
                    if (!isTestLobby) {
                        if (freeRestrictionReason != null && !streamIsPro.value && !proRuleNoteSent.value) {
                            // Visible to everyone in the stream
                            viewModel.sendMessage(
                                "*** Murmur System Warning! *** \n Only the creator can upgrade this stream to Pro. " +
                                        "Buying Pro as a joiner only unlocks streams you create. " +
                                        "Ask the creator to upgrade to keep chatting."
                            )
                            proRuleNoteSent.value = true
                        }

                    }

                    val canSend = input.isNotBlank() && messages.size < limit && freeRestrictionReason == null

// 2) Show one banner if any Free restriction is active
                    // 2) Show one banner if any Free restriction is active
                    freeRestrictionReason?.let { msg ->
                        UpsellBanner(
                            message = msg,
                            showUpgrade = isCreator,             // only the host sees the Upgrade CTA
                            onUpgrade = { showProDialog = true } // opens the Pro dialog
                        )
                    }

// 3) Normal Row with input and send button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextField(
                            value = input,
                            onValueChange = { if (it.length <= 400) input = it },
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(
                                    text = "Message...",
                                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp),
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                cursorColor = MaterialTheme.colorScheme.onSurface,
                                focusedIndicatorColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        )

                        FilledIconButton(
                            onClick = {
                                if (input.isNotBlank()) {
                                    viewModel.sendMessage(input)
                                    input = ""
                                }
                            },
                            enabled = canSend,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .size(52.dp),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                // enabled
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                // disabled: same bg, slightly stronger icon for legibility
                                disabledContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                disabledContentColor   = MaterialTheme.colorScheme.onPrimaryContainer//.copy(alpha = 0.65f)
                            )

                        ) {
                            // slight optical nudge because the send glyph is asymmetric
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                modifier = if (canSend) Modifier.size(24.dp)
                                else Modifier.size(24.dp).offset(x = (-0.5).dp)
                            )
                        }

                    }

                }


            }

            // ---- replace your two LoadingOverlay(...) calls with this ----
            val showSetupRaw = isFresh && isCreator && createdAt.value == null
            var allowSetup by remember { mutableStateOf(false) }

            LaunchedEffect(showSetupRaw) {
                if (showSetupRaw) {
                    allowSetup = false
                    kotlinx.coroutines.delay(300)   // hard 300ms gate
                    allowSetup = true
                } else {
                    allowSetup = false
                }
            }

            var allowQR by remember { mutableStateOf(false) }
            LaunchedEffect(isGeneratingQR) {
                if (isGeneratingQR) {
                    allowQR = false
                    kotlinx.coroutines.delay(300)   // same gate for QR
                    allowQR = true
                } else {
                    allowQR = false
                }
            }

            LoadingOverlay(
                visible = showSetupRaw && allowSetup,
                message = "Setting up stream…"
            )
            LoadingOverlay(
                visible = isGeneratingQR && allowQR,
                message = "Generating invite…"
            )
// ---- end replacement ----



        }


        LaunchedEffect(showQR) {

            if (isCreator && !showQR) {
                lastInviteId?.let {
                    println("🧼 Cleaning up orphaned invite: $it")
                    StreamRepository.deleteInvite(it)
                    lastInviteId = null
                }
            }

            while (showQR && isActive) {
                delay(5_000)

                lastInviteId?.let {
                    StreamRepository.deleteInvite(it)
                    println("⏳ Invite expired: $it")
                }

                StreamRepository.createInviteId(streamId) { newId ->
                    if (newId != null && showQR) {
                        println("🔁 New invite created: $newId")
                        lastInviteId = newId
                        val payload = DeepLinkUtil.buildJoinQrPayload(
                            streamId = streamId,
                            relayKey = getOrCreateRelayKey(),
                            nonce = newId
                        )
                        qrBitmap = QRCodeHelper.generateQRCode(payload)
                    }
                }
            }
        }


        if (isCreator && showDeleteConfirm) {
            StreamNoticeDialog(
                title = "End this stream?",
                message = "Everyone will be disconnected. This can’t be undone.",
                confirmLabel = "End Stream",
                onConfirm = {
                    showDeleteConfirm = false
                    isDeleting = true
                    viewModel.nukeStream { success, _ ->
                        isDeleting = false
                        StreamSession.clearStreamId(context)
                        onLeaveStream()
                    }
                },
                onDismiss = { showDeleteConfirm = false }
            )
        }

        // Joiner notice when the creator ends the stream
        if (!isCreator && showCreatorEnded) {
            StreamNoticeDialog(
                title = "Stream ended",
                message = "The creator ended this stream.",
                confirmLabel = "Close",
                onConfirm = {
                    showCreatorEnded = false
                    StreamSession.clearStreamId(context)
                    onLeaveStream()
                },
                onDismiss = {
                    showCreatorEnded = false
                    StreamSession.clearStreamId(context)
                    onLeaveStream()
                }
            )
        }



        if (showQR) {
            AlertDialog(
                onDismissRequest = {
                    lastInviteId?.let { StreamRepository.deleteInvite(it) }
                    showQR = false
                },
                confirmButton = {
                    Button(
                        onClick = {
                            lastInviteId?.let { StreamRepository.deleteInvite(it) }
                            showQR = false
                        }
                    ) {
                        Text("Close")
                    }
                },
                title = { Text("Your Stream Invitation") },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Share this code with someone to join your stream.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        Text(
                            text = "Murmur will join automatically once scanned.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Start,          // RTL-aware "left"
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        )
                        qrBitmap?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "QR Code",
                                modifier = Modifier.size(256.dp)
                            )
                        } ?: Text("Error generating QR code.")
                    }
                },

                containerColor = MaterialTheme.colorScheme.surface
            )
        }

    }
}

@Composable
fun DevBadge(show: Boolean) {
    if (!show) return
    androidx.compose.material3.Surface(
        color = Color(0xFF9C27B0),
        shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
    ) {
        Text(
            "DEV",
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            textAlign = TextAlign.Center,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
fun ProDialog(
    isPro: Boolean,
    onDismiss: () -> Unit,
    onBuy: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            if (isPro) {
                Button(onClick = onDismiss) { Text("Done") }
            } else {
                Button(onClick = onBuy) { Text("Upgrade Now") }
            }
        },
        dismissButton = {
            if (!isPro) {
                OutlinedButton(onClick = onDismiss) { Text("Close") }
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.extraColors.proGold,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(if (isPro) "Murmur Pro — Active" else "Upgrade to Murmur Pro")
            }
        },
        text = {
            Column {
                if (isPro) {
                    Text("Thanks for supporting Murmur! Pro is unlocked on this device.")
                } else {
                    Text("Unlock Pro to get:")
                    Spacer(Modifier.height(8.dp))
                    Text("• More messages per stream")
                    Text("• Longer stream life")
                    Text("• Clear messages / keep stream")
                    Spacer(Modifier.height(12.dp))
                    Text("One‑time purchase. No account needed.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    )
}

@Composable
private fun UpsellBanner(
    message: String,
    showUpgrade: Boolean,
    onUpgrade: () -> Unit
) {
    Surface(
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            if (showUpgrade) {
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onUpgrade,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Upgrade")
                }
            }
        }
    }
}

@Composable
private fun StreamNoticeDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit = onConfirm
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onConfirm) { Text(confirmLabel) }
        },
        title = { Text(title) },
        text = { Text(message) }
    )
}

