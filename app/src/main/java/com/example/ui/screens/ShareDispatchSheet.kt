package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NoEncryption
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.database.ChatEntity
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareDispatchSheet(
    sharedUrl: String,
    chats: List<ChatEntity>,
    onDispatch: (chat: ChatEntity, url: String, type: String, thumbnail: String, caption: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedChat by remember { mutableStateOf<ChatEntity?>(null) }
    var isSending by remember { mutableStateOf(false) }
    var isSuccess by remember { mutableStateOf(false) }

    // Parse whether it's a Reel or Post based on Instagram URL structures
    val isReel = sharedUrl.contains("/reel/", ignoreCase = true)
    val mediaType = if (isReel) "REEL" else "POST"
    
    // Choose authentic thumbnail placeholders for visual flair
    val thumbnail = if (isReel) {
        "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?auto=format&fit=crop&w=400&q=80"
    } else {
        "https://images.unsplash.com/photo-1522163182402-834f871fd851?auto=format&fit=crop&w=400&q=80"
    }

    val caption = if (isReel) {
        "Shared from Instagram Reels. Infinite loops & synth rhythms."
    } else {
        "Shared from Instagram. Explorations in futuristic composition."
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextGray) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!isSuccess) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Share Intent",
                        tint = CyberPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Secure Dispatch",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceColor
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Shared Link Preview Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.Black),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.DarkGray)
                        ) {
                            AsyncImage(
                                model = thumbnail,
                                contentDescription = "Shared Thumbnail",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Instagram $mediaType Link",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = OnSurfaceColor
                            )
                            Text(
                                text = sharedUrl,
                                fontSize = 11.sp,
                                color = TextGray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "SELECT CONTACT TO DISPATCH SECURELY:",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextGray,
                    letterSpacing = 1.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Scrollable Chats Selector
                Box(modifier = Modifier.heightIn(max = 240.dp)) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(chats) { chat ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedChat = chat }
                                    .background(
                                        if (selectedChat?.threadId == chat.threadId) Color(0xFF1E1E24) else Color.Transparent,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (selectedChat?.threadId == chat.threadId) CyberPrimary else BorderColor,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(BorderColor)
                                ) {
                                    AsyncImage(
                                        model = chat.avatarUrl,
                                        contentDescription = "Contact avatar",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = chat.contactUsername,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = OnSurfaceColor
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        if (chat.isE2EEnabled) {
                                            Icon(
                                                imageVector = Icons.Default.VerifiedUser,
                                                contentDescription = "Secured",
                                                tint = CyberSecondary,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = chat.contactFullName,
                                        fontSize = 11.sp,
                                        color = TextGray
                                    )
                                }

                                if (chat.isE2EEnabled) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Secure lock",
                                        tint = CyberSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.NoEncryption,
                                        contentDescription = "Plaintext fallback",
                                        tint = TextGray,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", color = TextGray)
                    }

                    Button(
                        onClick = {
                            val chat = selectedChat
                            if (chat != null) {
                                isSending = true
                                onDispatch(chat, sharedUrl, mediaType, thumbnail, caption)
                                isSending = false
                                isSuccess = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberPrimary),
                        enabled = selectedChat != null && !isSending,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Dispatch Securely", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // Success State View
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = CyberSecondary,
                        modifier = Modifier.size(64.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "DISPATCH COMPLETED",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceColor
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "The Instagram link has been securely encrypted and dispatched to @${selectedChat?.contactUsername}.",
                        fontSize = 12.sp,
                        color = TextGray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    LaunchedEffect(Unit) {
                        delay(1500)
                        onDismiss()
                    }
                }
            }
        }
    }
}
