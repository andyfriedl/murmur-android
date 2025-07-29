package com.murmur.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.murmur.app.ui.theme.LocalChatBackground
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material3.AlertDialog
import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import com.murmur.app.ui.theme.logoTintBackground
import androidx.compose.runtime.mutableStateOf
import com.murmur.app.UiPrefs



@Composable
fun StreamScreen(
    streamId: String,
    onLeaveStream: () -> Unit,
) {


    var showQR by remember { mutableStateOf(false) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isGeneratingQR by remember { mutableStateOf(false) }
    var lastInviteId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val activeStreamId = remember {
        streamId ?: StreamSession.getOrCreateStreamId(context)
    }
    val viewModel = remember(context, activeStreamId) {
        StreamViewModel(context, activeStreamId)
    }

    val streamDeleted by viewModel.streamDeleted.collectAsState()
    LaunchedEffect(streamDeleted) {
        if (streamDeleted) {
            onLeaveStream()
        }
    }

    val shouldLeave by viewModel.shouldLeaveStream.collectAsState()
    LaunchedEffect(shouldLeave) {
        if (shouldLeave) {
            onLeaveStream()
        }
    }

    val isCreator by viewModel.isCreator.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val memberCount by viewModel.memberCount.collectAsState()

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



    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()



    // Automatically scroll to the bottom when messages update
    LaunchedEffect(messages.size) {
        listState.animateScrollToItem(messages.size)
    }
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
                            Icon(
                                painter = painterResource(id = R.drawable.ic_logo),
                                contentDescription = "murmur logo",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "murmur",
                                style = MaterialTheme.typography.titleLarge.copy(fontSize = 28.sp),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        // Nuke / Leave button
                        var expanded by remember { mutableStateOf(false) }
                        val context = LocalContext.current
                        val showRedDot = remember { mutableStateOf(UiPrefs.shouldShowRedDot(context)) }

                        Box {

                            Box(
                                modifier = Modifier
                                    .size(48.dp) // makes the clickable area bigger
                                    .background(
                                        color = MaterialTheme.colorScheme.logoTintBackground,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        UiPrefs.markRedDotDismissed(context)
                                        showRedDot.value = false
                                        expanded = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = "Menu",
                                        tint = Color.Black,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    if (showRedDot.value) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .background(Color.Red, shape = RoundedCornerShape(50))
                                                .align(Alignment.TopEnd)
                                        )
                                    }
                                }
                            }

                            val isDark = isSystemInDarkTheme()
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                            ) {

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                        .background(
                                            color = if (isDark) Color.White else Color.Black,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .clickable {
                                            expanded = false
                                            isGeneratingQR = true
                                            StreamRepository.createInviteId(streamId) { inviteId ->
                                                isGeneratingQR = false
                                                if (inviteId != null) {
                                                    val generated =
                                                        QRCodeHelper.generateQRCode(inviteId)
                                                    qrBitmap = generated
                                                    lastInviteId = inviteId
                                                    showQR = true
                                                }
                                            }
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    Text(
                                        text = "Invite to Stream",
                                        color = if (isDark) Color.Black else Color.White,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                                Spacer(modifier = Modifier.height(18.dp))

                                val isDark = isSystemInDarkTheme()

                                if (isCreator) {
                                    OutlinedButton(
                                        onClick = {
                                            expanded = false
                                            StreamSession.clearStreamId(context)
                                            viewModel.nukeStream { onLeaveStream() }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.5.dp, Color.Red),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = Color.Red
                                        )
                                    ) {
                                        Text(
                                            text = "Nuke Stream",
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            expanded = false
                                            viewModel.leaveStream { onLeaveStream() }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.5.dp, Color.Red),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = Color.Red
                                        )
                                    ) {
                                        Text(
                                            text = "Leave Stream",
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }


                            }


                        }

                    }
                }

                Text(
                    text = "$memberCount in stream",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    state = listState,
                    verticalArrangement = Arrangement.Bottom
                    // 👈 no horizontalAlignment here
                ) {
                    if (messages.isEmpty()) {
                        item {
                            Text(
                                text = "No messages yet...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(top = 24.dp, start = 16.dp)
                                    .align(Alignment.Start)
                            )
                        }
                    } else {
                        items(messages.size) { index ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp, horizontal = 8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = LocalChatBackground.current,
                                    tonalElevation = 2.dp,
                                    modifier = Modifier.padding(end = 48.dp)
                                ) {
                                    Text(
                                        text = messages[index],
                                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp),
                                        color = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.padding(
                                            horizontal = 16.dp,
                                            vertical = 10.dp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }






                Spacer(modifier = Modifier.height(44.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextField(
                        value = input,
                        onValueChange = { input = it },
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
                    Box(
                        modifier = Modifier
                            .padding(start = 10.dp, top = 8.dp) // ← adjust left and vertical spacing
                            .size(52.dp)
                            .background(
                                color = MaterialTheme.colorScheme.logoTintBackground,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                if (input.isNotBlank()) {
                                    viewModel.sendMessage(input)
                                    input = ""
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = Color.Black,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                }
            }
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
                title = { Text("Scan to Join") },
                text = {
                    qrBitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier.size(256.dp)
                        )
                    } ?: Text("Error generating QR code.")
                },
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

    }
}

@Composable
fun StreamActionButton(
    label: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Red,
            contentColor = Color.Black
        ),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.size(70.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            )
        }
    }
}

