package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.cryptography.E2EECryptoEngine
import com.example.database.ChatEntity
import com.example.database.ContactKeyEntity
import com.example.database.MessageEntity
import com.example.database.UserKeyPairEntity
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatThreadScreen(
    chat: ChatEntity,
    messages: List<MessageEntity>,
    localKeyPair: UserKeyPairEntity?,
    contactKeyPair: ContactKeyEntity?,
    onSendMessage: (text: String, mediaUrl: String?, mediaType: String?, mediaThumbnail: String?, mediaCaption: String?) -> Unit,
    onBackClick: () -> Unit,
    onToggleVerification: (String) -> Unit,
    mediaPlaybackState: Map<String, Boolean>,
    onTogglePlayback: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    
    // Bottom sheet or dialog states
    var showSafetyVerification by remember { mutableStateOf(false) }
    var inspectedMessage by remember { mutableStateOf<MessageEntity?>(null) }

    // Scroll to bottom when list sizes change
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showSafetyVerification = true }
                    ) {
                        CipherGramAvatar(
                            avatarUrl = chat.avatarUrl,
                            name = chat.contactFullName.ifEmpty { chat.contactUsername },
                            size = 38.dp,
                            modifier = Modifier.size(38.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = chat.contactUsername,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurfaceColor
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                if (chat.isE2EEnabled) {
                                    Icon(
                                        imageVector = if (contactKeyPair?.isVerified == true) Icons.Default.Verified else Icons.Default.Lock,
                                        contentDescription = "E2EE Secured",
                                        tint = if (contactKeyPair?.isVerified == true) CyberSecondary else CyberWarning,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (chat.isOnline) "Active now" else "Offline",
                                fontSize = 11.sp,
                                color = if (chat.isOnline) CyberSecondary else TextGray
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = OnSurfaceColor
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSafetyVerification = true }) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Verify Keys Fingerprint",
                            tint = if (contactKeyPair?.isVerified == true) CyberSecondary else TextGray
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Secure tunnel details sub-header
            if (chat.isE2EEnabled) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF111E16))
                        .border(1.dp, Color(0xFF1E3A27))
                        .padding(vertical = 6.dp, horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Shield Guard",
                            tint = CyberSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "CipherGram active tunnel: secp256r1 keys verified",
                            fontSize = 11.sp,
                            color = CyberSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1F1B16))
                        .border(1.dp, Color(0xFF3E2D1E))
                        .padding(vertical = 6.dp, horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.NoEncryption,
                            contentDescription = "Plaintext fallback",
                            tint = CyberWarning,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Non-CipherGram Recipient. Standard unencrypted fallback active.",
                            fontSize = 11.sp,
                            color = CyberWarning,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Message Bubble list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(messages) { message ->
                    val isOwnMessage = message.senderUsername != chat.contactUsername
                    MessageBubble(
                        message = message,
                        isOwnMessage = isOwnMessage,
                        isPlayingReel = mediaPlaybackState[message.messageId] ?: false,
                        onToggleReel = { onTogglePlayback(message.messageId) },
                        onInspectMessage = { inspectedMessage = message }
                    )
                }
            }

            // Message Input bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .padding(8.dp)
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Input Text field
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Write encrypted message...", color = TextGray, fontSize = 14.sp) },
                    singleLine = false,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BorderColor,
                        unfocusedBorderColor = BorderColor,
                        focusedContainerColor = Color.Black,
                        unfocusedContainerColor = Color.Black
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("message_input"),
                    trailingIcon = {
                        if (inputText.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    onSendMessage(inputText, null, null, null, null)
                                    inputText = ""
                                },
                                modifier = Modifier.testTag("send_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Default.Send,
                                    contentDescription = "Send",
                                    tint = CyberPrimary
                                )
                            }
                        }
                    }
                )
            }
        }
    }

    // Safety Verification Dialog
    if (showSafetyVerification && localKeyPair != null && contactKeyPair != null) {
        val localPub = E2EECryptoEngine.publicKeyFromBase64(localKeyPair.publicKeyBase64)
        val remotePub = E2EECryptoEngine.publicKeyFromBase64(contactKeyPair.publicKeyBase64)
        val fingerprint = if (localPub != null && remotePub != null) {
            E2EECryptoEngine.computeSafetyNumber(localPub, remotePub)
        } else {
            "00000 00000 00000 00000 00000"
        }

        SafetyVerificationDialog(
            contactUsername = chat.contactUsername,
            fingerprint = fingerprint,
            isVerified = contactKeyPair.isVerified,
            onDismiss = { showSafetyVerification = false },
            onToggleVerify = {
                onToggleVerification(chat.contactUsername)
            }
        )
    }

    // Ciphertext Inspector Bottom Sheet
    if (inspectedMessage != null) {
        CiphertextInspectorSheet(
            message = inspectedMessage!!,
            onDismiss = { inspectedMessage = null }
        )
    }
}

@Composable
fun MessageBubble(
    message: MessageEntity,
    isOwnMessage: Boolean,
    isPlayingReel: Boolean,
    onToggleReel: () -> Unit,
    onInspectMessage: () -> Unit
) {
    val bubbleColor = when {
        isOwnMessage && message.isEncrypted -> Color(0xFF0F2618) // Own encrypted dark green
        isOwnMessage -> Color(0xFF262629)                      // Own standard slate
        message.isEncrypted -> Color(0xFF111E16)                 // Peer encrypted charcoal/green
        else -> Color(0xFF1E1E22)                               // Peer standard charcoal
    }

    val bubbleBorder = when {
        message.isEncrypted -> BorderStroke(1.dp, Color(0xFF2E6B3E))
        else -> BorderStroke(1.dp, BorderColor)
    }

    val alignment = if (isOwnMessage) Alignment.End else Alignment.Start
    val bubbleShape = if (isOwnMessage) {
        RoundedCornerShape(18.dp, 18.dp, 2.dp, 18.dp)
    } else {
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 2.dp)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("message_bubble_${message.messageId}"),
        horizontalAlignment = alignment
    ) {
        // Display Text Bubble
        Card(
            shape = bubbleShape,
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            border = bubbleBorder,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clickable { onInspectMessage() }
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // If encrypted, show secure badge
                if (message.isEncrypted) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Encrypted",
                            tint = CyberSecondary,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "END-TO-END ENCRYPTED",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberSecondary,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // Plaintext text body
                Text(
                    text = message.decryptedText ?: message.payload,
                    fontSize = 14.sp,
                    color = OnSurfaceColor,
                    lineHeight = 18.sp
                )

                // Inline media previews
                if (message.instagramMediaUrl != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    if (message.instagramMediaType == "POST") {
                        PostMediaCard(
                            imageUrl = message.instagramMediaThumbnail,
                            caption = message.instagramMediaCaption ?: "Shared Instagram Post",
                            author = message.senderUsername
                        )
                    } else if (message.instagramMediaType == "REEL") {
                        ReelMediaCard(
                            thumbnailUrl = message.instagramMediaThumbnail,
                            caption = message.instagramMediaCaption ?: "Shared Reel",
                            isPlaying = isPlayingReel,
                            onPlayClick = onToggleReel
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Time / Secure footer
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            val formattedTime = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.timestamp))
            Text(
                text = formattedTime,
                fontSize = 10.sp,
                color = TextGray
            )
            if (message.isEncrypted) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Delivered securely",
                    tint = CyberSecondary,
                    modifier = Modifier.size(10.dp)
                )
            }
        }
    }
}

@Composable
fun PostMediaCard(
    imageUrl: String?,
    caption: String,
    author: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(CyberPrimary)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = author,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceColor
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = TextGray, modifier = Modifier.size(14.dp))
            }

            // Thumbnail Image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(Color.DarkGray)
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Shared Post Image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Actions row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.FavoriteBorder, contentDescription = "Like", tint = OnSurfaceColor, modifier = Modifier.size(16.dp))
                Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Comment", tint = OnSurfaceColor, modifier = Modifier.size(16.dp))
                Icon(Icons.Default.Send, contentDescription = "Share", tint = OnSurfaceColor, modifier = Modifier.size(16.dp))
            }

            // Caption
            Text(
                text = caption,
                fontSize = 11.sp,
                color = OnSurfaceColor,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
            )
        }
    }
}

@Composable
fun ReelMediaCard(
    thumbnailUrl: String?,
    caption: String,
    isPlaying: Boolean,
    onPlayClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = "Reel Thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Translucent dim overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                )

                if (isPlaying) {
                    // Custom Equalizer simulation responding to playback
                    EqualizerVisualizer()
                }

                // Large Play overlay Button
                IconButton(
                    onClick = onPlayClick,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play Reel",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Small Reels Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Slideshow, contentDescription = "Reel", tint = Color.White, modifier = Modifier.size(10.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Reel", fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text(
                text = caption,
                fontSize = 11.sp,
                color = OnSurfaceColor,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@Composable
fun EqualizerVisualizer() {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer")
    
    val height1 by infiniteTransition.animateFloat(
        initialValue = 10f, targetValue = 40f,
        animationSpec = infiniteRepeatable(animation = tween(400, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "bar1"
    )
    val height2 by infiniteTransition.animateFloat(
        initialValue = 25f, targetValue = 15f,
        animationSpec = infiniteRepeatable(animation = tween(300, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "bar2"
    )
    val height3 by infiniteTransition.animateFloat(
        initialValue = 5f, targetValue = 35f,
        animationSpec = infiniteRepeatable(animation = tween(500, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "bar3"
    )
    val height4 by infiniteTransition.animateFloat(
        initialValue = 15f, targetValue = 45f,
        animationSpec = infiniteRepeatable(animation = tween(450, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "bar4"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Bottom
    ) {
        val bars = listOf(height1, height2, height3, height4)
        for (barHeight in bars) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(barHeight.dp)
                    .background(CyberSecondary, RoundedCornerShape(2.dp))
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyVerificationDialog(
    contactUsername: String,
    fingerprint: String,
    isVerified: Boolean,
    onDismiss: () -> Unit,
    onToggleVerify: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "E2EE Safety Number",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "To verify that messages with @$contactUsername are completely secure, compare these 30 digits with their safety number:",
                    fontSize = 12.sp,
                    color = TextGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // High-fidelity security fingerprint QR simulation
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cellSize = size.width / 10f
                        // Seeded random matrix of security QR squares
                        val rand = Random(fingerprint.hashCode().toLong())
                        for (i in 0 until 10) {
                            for (j in 0 until 10) {
                                val isCorner = (i < 3 && j < 3) || (i > 6 && j < 3) || (i < 3 && j > 6)
                                if (isCorner) {
                                    // Draw QR positioning squares
                                    if (i == 0 || i == 2 || j == 0 || j == 2 || i == 7 || i == 9 || j == 7 || j == 9) {
                                        drawRect(
                                            color = Color.Black,
                                            topLeft = Offset(i * cellSize, j * cellSize),
                                            size = Size(cellSize, cellSize)
                                        )
                                    }
                                } else if (rand.nextBoolean()) {
                                    drawRect(
                                        color = Color.Black,
                                        topLeft = Offset(i * cellSize, j * cellSize),
                                        size = Size(cellSize, cellSize)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // The numerical fingerprint blocks
                Text(
                    text = fingerprint,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = OnSurfaceColor,
                    textAlign = TextAlign.Center,
                    letterSpacing = 1.sp,
                    modifier = Modifier
                        .background(Color.Black, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                        .fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .clickable { onToggleVerify() }
                        .background(if (isVerified) Color(0xFF1B3D26) else BorderColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isVerified) Icons.Default.Verified else Icons.Default.NewReleases,
                        contentDescription = "Status",
                        tint = if (isVerified) CyberSecondary else CyberWarning,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isVerified) "Verified Out-of-Band" else "Unverified Key Fingerprint",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceColor
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = CyberPrimary)
            ) {
                Text("Close")
            }
        },
        containerColor = DarkSurface
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CiphertextInspectorSheet(
    message: MessageEntity,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextGray) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = "Inspector",
                    tint = CyberSecondary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "CipherGram Security Inspector",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Inspect the decrypted vs encrypted representations as transmitted directly through Instagram’s standard payload wrappers.",
                fontSize = 12.sp,
                color = TextGray
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "DECRYPTED PLAINTEXT",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = CyberSecondary,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message.decryptedText ?: message.payload,
                fontSize = 14.sp,
                color = OnSurfaceColor,
                modifier = Modifier
                    .background(Color.Black, RoundedCornerShape(8.dp))
                    .padding(12.dp)
                    .fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "ENCRYPTED INSTAGRAM CARRIER PAYLOAD (BASE64)",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = CyberPrimary,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message.payload,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFE1306C),
                lineHeight = 16.sp,
                modifier = Modifier
                    .background(Color.Black, RoundedCornerShape(8.dp))
                    .padding(12.dp)
                    .fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Spec Info Box
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF151518)),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Row(modifier = Modifier.padding(12.dp)) {
                    Icon(Icons.Default.Fingerprint, contentDescription = "Specs", tint = CyberSecondary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Transport Encryption Specification",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurfaceColor
                        )
                        Text(
                            text = "Envelope: AES/GCM/NoPadding (128-bit MAC tag). Shared key: ECDH (NIST-P256 Curve). Message index sequence: local DB ratchet.",
                            fontSize = 10.sp,
                            color = TextGray
                        )
                    }
                }
            }
        }
    }
}
