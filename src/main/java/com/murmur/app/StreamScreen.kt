package com.murmur.app

import android.app.Activity
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.murmur.app.ui.StreamNoticeDialog
import com.murmurrelay.core.MurmurRelay
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive


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
    val createdAt = remember { mutableStateOf<Long?>(null) }
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
            showQR = false
            lastInviteId?.let {
                StreamRepository.deleteInvite(it)
            }
        }
    }

    LaunchedEffect(isCreator, isFresh) {
        if (!isCreator) return@LaunchedEffect

        val streamRef = com.google.firebase.database.FirebaseDatabase.getInstance()
            .getReference("streams")
            .child(streamId)

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
                    .addOnFailureListener { }
            } else {
                startInviteFlow()
            }
        }.addOnFailureListener {
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

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                viewModel.touchPresence()
                viewModel.refreshStreamStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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

                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        var expanded by remember { mutableStateOf(false) }
                        val showRedDot = remember { mutableStateOf(UiPrefs.shouldShowRedDot(context)) }

                        Box {
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
                                                    val payload = DeepLinkUtil.buildJoinQrPayload(
                                                        streamId = streamId,
                                                        relayKey = getOrCreateRelayKey(),
                                                        nonce = inviteId
                                                    )
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

                            }
                        }

                    }
                }

                Text(
                    text = "$memberCount in stream",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (memberCount == 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,                    modifier = Modifier.padding(top = 4.dp, start = 16.dp)
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

                    Text(
                        text = "${input.length}/${StreamConfig.MESSAGE_LIMIT}",
                        color = if (input.length >= 400) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentWidth(Alignment.End)
                            .padding(end = 12.dp, bottom = 4.dp)
                    )

                    val messageLimit = StreamConfig.MESSAGE_LIMIT
                    val canSend = input.isNotBlank() && messages.size < messageLimit

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextField(
                            value = input,
                            onValueChange = { if (it.length <= StreamConfig.MESSAGE_LIMIT) input = it },
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
                            // slight nudge because the send icon is asymmetric
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
        }

        LaunchedEffect(showQR) {

            if (isCreator && !showQR) {
                lastInviteId?.let {
                    StreamRepository.deleteInvite(it)
                    lastInviteId = null
                }
            }

            while (showQR && isActive) {
                delay(5_000)

                lastInviteId?.let {
                    StreamRepository.deleteInvite(it)
                }

                StreamRepository.createInviteId(streamId) { newId ->
                    if (newId != null && showQR) {
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
