package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.database.ChatEntity
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    chats: List<ChatEntity>,
    onChatClick: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onAddChat: (username: String, fullName: String, isE2E: Boolean, publicKeyB64: String) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var showAddChatDialog by remember { mutableStateOf(false) }

    val filteredChats = chats.filter {
        it.contactUsername.contains(searchQuery, ignoreCase = true) ||
                it.contactFullName.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "CipherGram",
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                            color = OnSurfaceColor
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(CyberPrimary, RoundedCornerShape(8.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "2.0",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = "Security Keys Settings",
                            tint = CyberSecondary
                        )
                    }
                    IconButton(onClick = onLogout) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Logout",
                            tint = TextGray
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = OnSurfaceColor
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddChatDialog = true },
                containerColor = CyberPrimary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.testTag("add_chat_fab")
            ) {
                Icon(imageVector = Icons.Default.AddComment, contentDescription = "New Secure Tunnel")
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Input Box
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search chats...", color = TextGray) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = TextGray) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberPrimary,
                    unfocusedBorderColor = BorderColor,
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("search_input")
            )

            // Direct Messages Label with secure statistics
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Messages",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceColor
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Secure count",
                        tint = CyberSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${chats.count { it.isE2EEnabled }} Secure Tunnels",
                        fontSize = 12.sp,
                        color = CyberSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (filteredChats.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = "Empty list",
                            tint = TextGray,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isEmpty()) "No direct messages yet" else "No matching chats",
                            fontSize = 14.sp,
                            color = TextGray
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredChats) { chat ->
                        ChatListItem(
                            chat = chat,
                            onClick = { onChatClick(chat.threadId) }
                        )
                    }
                }
            }

            // Bottom explanation bar
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "E2EE Guard",
                        tint = CyberSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Chats marked with a green shield are secure. Unencrypted standard chats fall back gracefully to plaintext delivery wrapper.",
                        fontSize = 11.sp,
                        color = TextGray,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }

    if (showAddChatDialog) {
        AddChatDialog(
            onDismiss = { showAddChatDialog = false },
            onConfirm = { username, fullName, isE2E, pubKey ->
                onAddChat(username, fullName, isE2E, pubKey)
                showAddChatDialog = false
            }
        )
    }
}

@Composable
fun ChatListItem(
    chat: ChatEntity,
    onClick: () -> Unit
) {
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val formattedTime = timeFormat.format(Date(chat.lastMessageTime))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("chat_item_${chat.contactUsername}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar with status indicator ring
        Box(contentAlignment = Alignment.BottomEnd) {
            CipherGramAvatar(
                avatarUrl = chat.avatarUrl,
                name = chat.contactFullName.ifEmpty { chat.contactUsername },
                size = 54.dp,
                modifier = Modifier.size(54.dp)
            )

            // Online indicator dot
            if (chat.isOnline) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background)
                        .padding(2.dp)
                        .background(CyberSecondary, CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Chat Info block
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = chat.contactUsername,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.width(4.dp))

                // E2EE Shield badge
                if (chat.isE2EEnabled) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = "End-to-End Encrypted",
                        tint = CyberSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.NoEncryption,
                        contentDescription = "Standard Connection",
                        tint = TextGray,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = chat.contactFullName,
                fontSize = 12.sp,
                color = TextGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (chat.isE2EEnabled) {
                    Text(
                        text = chat.lastMessageText,
                        fontSize = 13.sp,
                        color = CyberSecondary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        text = chat.lastMessageText,
                        fontSize = 13.sp,
                        color = TextGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = formattedTime,
                    fontSize = 11.sp,
                    color = TextGray
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Open Chat",
            tint = BorderColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddChatDialog(
    onDismiss: () -> Unit,
    onConfirm: (username: String, fullName: String, isE2E: Boolean, publicKeyB64: String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var isE2E by remember { mutableStateOf(true) }
    var publicKeyB64 by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "New Secure Chat Bridge",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = OnSurfaceColor
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Instagram Username") },
                    placeholder = { Text("e.g. daniel_cyber") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberPrimary,
                        unfocusedBorderColor = BorderColor
                    ),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Display Name") },
                    placeholder = { Text("e.g. Daniel Stone") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberPrimary,
                        unfocusedBorderColor = BorderColor
                    ),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isE2E,
                        onCheckedChange = { isE2E = it },
                        colors = CheckboxDefaults.colors(checkedColor = CyberSecondary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Establish E2EE Secure Tunnel",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurfaceColor
                        )
                        Text(
                            text = "Requires exchanging public identity keys.",
                            fontSize = 11.sp,
                            color = TextGray
                        )
                    }
                }

                if (isE2E) {
                    OutlinedTextField(
                        value = publicKeyB64,
                        onValueChange = { publicKeyB64 = it },
                        label = { Text("Paste Recipient Public Key (Base64)") },
                        placeholder = { Text("Optional. Generates fresh mock key if left blank.") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberSecondary,
                            unfocusedBorderColor = BorderColor
                        ),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (username.isNotEmpty()) {
                        onConfirm(username.trim(), fullName.trim(), isE2E, publicKeyB64.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = if (isE2E) CyberSecondary else CyberPrimary),
                enabled = username.isNotEmpty()
            ) {
                Text("Establish Tunnel")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextGray)
            }
        },
        containerColor = DarkSurface
    )
}
