package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cryptography.E2EECryptoEngine
import com.example.database.ChatEntity
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.CipherGramViewModel
import com.example.viewmodel.Screen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: CipherGramViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle cold launch send intent
        handleSendIntent(intent)

        setContent {
            MyApplicationTheme {
                val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
                val userSession by viewModel.userSession.collectAsStateWithLifecycle()
                val isGeneratingKeys by viewModel.isGeneratingKeys.collectAsStateWithLifecycle()
                val localKeyPair by viewModel.localKeyPair.collectAsStateWithLifecycle()
                val sharedMediaUrl by viewModel.sharedMediaUrl.collectAsStateWithLifecycle()
                val mediaPlaybackState by viewModel.mediaPlaybackState.collectAsStateWithLifecycle()
                val chats by viewModel.chats.collectAsStateWithLifecycle()
                val contactKeys by viewModel.contactKeys.collectAsStateWithLifecycle()

                // Intercept System Back Presses cleanly
                BackHandler(enabled = currentScreen !is Screen.Login) {
                    when (currentScreen) {
                        is Screen.ChatThread -> viewModel.navigateTo(Screen.ChatList)
                        is Screen.SecuritySettings -> viewModel.navigateTo(Screen.ChatList)
                        else -> finish()
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BoxWithScreenTransition(
                        currentScreen = currentScreen,
                        chats = chats,
                        contactKeys = contactKeys,
                        viewModel = viewModel,
                        isGeneratingKeys = isGeneratingKeys,
                        localKeyPair = localKeyPair,
                        mediaPlaybackState = mediaPlaybackState,
                        modifier = Modifier.padding(innerPadding)
                    )

                    // Automatically display the native-feeling secure share overlay if triggered
                    sharedMediaUrl?.let { url ->
                        ShareDispatchSheet(
                            sharedUrl = url,
                            chats = chats,
                            onDispatch = { chat, sharedUrl, mediaType, thumbnail, caption ->
                                viewModel.sendMessage(
                                    threadId = chat.threadId,
                                    contactUsername = chat.contactUsername,
                                    text = "Dispatched secure media: $sharedUrl",
                                    mediaUrl = sharedUrl,
                                    mediaType = mediaType,
                                    mediaThumbnail = thumbnail,
                                    mediaCaption = caption
                                )
                                // Highlight the chat and navigate to thread
                                viewModel.navigateTo(Screen.ChatThread(chat.threadId))
                                viewModel.setSharedMediaUrl(null)
                            },
                            onDismiss = {
                                viewModel.setSharedMediaUrl(null)
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Handle hot launch send intent
        handleSendIntent(intent)
    }

    private fun handleSendIntent(intent: Intent?) {
        if (intent != null) {
            val sharedMediaUrl = intent.getStringExtra("SHARED_MEDIA_URL")
            if (!sharedMediaUrl.isNullOrEmpty()) {
                viewModel.setSharedMediaUrl(sharedMediaUrl)
                return
            }
        }
        if (intent != null && intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrEmpty()) {
                viewModel.setSharedMediaUrl(sharedText)
            }
        }
    }
}

@Composable
fun BoxWithScreenTransition(
    currentScreen: Screen,
    chats: List<ChatEntity>,
    contactKeys: List<com.example.database.ContactKeyEntity>,
    viewModel: CipherGramViewModel,
    isGeneratingKeys: Boolean,
    localKeyPair: com.example.database.UserKeyPairEntity?,
    mediaPlaybackState: Map<String, Boolean>,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            if (targetState is Screen.ChatThread || targetState is Screen.SecuritySettings) {
                // Slide in from right (forward)
                slideInHorizontally { width -> width } + fadeIn() togetherWith
                        slideOutHorizontally { width -> -width } + fadeOut()
            } else {
                // Slide in from left (backward)
                slideInHorizontally { width -> -width } + fadeIn() togetherWith
                        slideOutHorizontally { width -> width } + fadeOut()
            }
        },
        label = "screen_transition",
        modifier = modifier
    ) { screen ->
        when (screen) {
            is Screen.Login -> {
                LoginScreen(
                    onLoginSuccess = { username, fullName ->
                        viewModel.login(username, fullName)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            is Screen.ChatList -> {
                ChatListScreen(
                    chats = chats,
                    onChatClick = { threadId ->
                        viewModel.navigateTo(Screen.ChatThread(threadId))
                    },
                    onNavigateToSettings = {
                        viewModel.navigateTo(Screen.SecuritySettings)
                    },
                    onAddChat = { username, fullName, isE2E, pubKey ->
                        val newThreadId = "thread_${username.lowercase()}"
                        
                        coroutineScope.launch {
                            var finalFullName = fullName.ifEmpty { username }
                            var finalPubKey = pubKey
                            var hasE2E = isE2E

                            // Look up friend's profile via Gateway
                            if (viewModel.repository.gateway.isAvailable) {
                                val remoteProfile = viewModel.repository.gateway.findUserProfile(username)
                                if (remoteProfile != null) {
                                    val dbName = remoteProfile["fullName"] as? String
                                    val dbPubKey = remoteProfile["publicKeyBase64"] as? String
                                    if (!dbName.isNullOrEmpty()) {
                                        finalFullName = dbName
                                    }
                                    if (!dbPubKey.isNullOrEmpty()) {
                                        finalPubKey = dbPubKey
                                        hasE2E = true // Auto-enable E2EE since they have a key!
                                    }
                                }
                            }

                            val newChat = ChatEntity(
                                threadId = newThreadId,
                                contactUsername = username,
                                contactFullName = finalFullName,
                                avatarUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=150&q=80",
                                isE2EEnabled = hasE2E,
                                lastMessageText = if (hasE2E) "🔐 E2EE Secure Tunnel Active" else "Hey let's chat!",
                                lastMessageTime = System.currentTimeMillis()
                            )

                            // Save contact details to Database
                            viewModel.repository.insertChat(newChat)
                            
                            // Save contact's keys if E2EE
                            if (hasE2E) {
                                val keyToInsert = finalPubKey.ifEmpty {
                                    // Generate a mock public key if none provided
                                    val mockKP = E2EECryptoEngine.generateKeyPair()
                                    if (mockKP != null) E2EECryptoEngine.publicKeyToBase64(mockKP.public) else ""
                                }
                                viewModel.repository.insertContactKey(
                                    com.example.database.ContactKeyEntity(
                                        contactUsername = username,
                                        publicKeyBase64 = keyToInsert,
                                        isVerified = finalPubKey.isNotEmpty()
                                    )
                                )
                            }
                            
                            // Register thread metadata via Gateway so recipient can find it
                            if (viewModel.repository.gateway.isAvailable) {
                                val currentUserSession = viewModel.userSession.value
                                if (currentUserSession != null) {
                                    viewModel.repository.gateway.publishThreadMetadata(
                                        threadId = newThreadId,
                                        contactUsername = currentUserSession.username,
                                        contactFullName = currentUserSession.fullName,
                                        avatarUrl = currentUserSession.avatarUrl,
                                        isE2EEnabled = hasE2E
                                    )
                                }
                            }

                            viewModel.navigateTo(Screen.ChatThread(newThreadId))
                        }
                    },
                    onLogout = {
                        viewModel.logout()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            is Screen.ChatThread -> {
                val chat = chats.firstOrNull { it.threadId == screen.threadId }
                if (chat != null) {
                    val messagesState = viewModel.repository.getMessagesForThread(screen.threadId)
                        .collectAsState(initial = emptyList())
                    
                    var contactKeyPair by remember { mutableStateOf<com.example.database.ContactKeyEntity?>(null) }
                    
                    LaunchedEffect(chat.contactUsername) {
                        contactKeyPair = viewModel.repository.getContactKey(chat.contactUsername)
                    }

                    ChatThreadScreen(
                        chat = chat,
                        messages = messagesState.value,
                        localKeyPair = localKeyPair,
                        contactKeyPair = contactKeyPair,
                        onSendMessage = { text, url, type, thumb, caption ->
                            viewModel.sendMessage(
                                threadId = chat.threadId,
                                contactUsername = chat.contactUsername,
                                text = text,
                                mediaUrl = url,
                                mediaType = type,
                                mediaThumbnail = thumb,
                                mediaCaption = caption
                            )
                        },
                        onBackClick = {
                            viewModel.navigateTo(Screen.ChatList)
                        },
                        onToggleVerification = { username ->
                            viewModel.toggleContactVerification(username)
                            // Reload verification specs
                            coroutineScope.launch {
                                contactKeyPair = viewModel.repository.getContactKey(username)
                            }
                        },
                        mediaPlaybackState = mediaPlaybackState,
                        onTogglePlayback = { msgId ->
                            viewModel.toggleMediaPlayback(msgId)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            is Screen.SecuritySettings -> {
                SecuritySettingsScreen(
                    localKeyPair = localKeyPair,
                    contactKeys = contactKeys,
                    isRotating = isGeneratingKeys,
                    onRotateKeys = {
                        viewModel.rotateKeys()
                    },
                    onToggleVerification = { username ->
                        viewModel.toggleContactVerification(username)
                    },
                    onBackClick = {
                        viewModel.navigateTo(Screen.ChatList)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
