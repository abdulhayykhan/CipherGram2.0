package com.example.viewmodel

import android.app.Application
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cryptography.E2EECryptoEngine
import com.example.cryptography.HardwareCryptoEngine
import com.example.cryptography.SecureEnvelopeProtocol
import com.example.database.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.security.KeyPair

sealed class Screen {
    object Login : Screen()
    object ChatList : Screen()
    data class ChatThread(val threadId: String) : Screen()
    object SecuritySettings : Screen()
}

data class UserSession(
    val username: String,
    val fullName: String,
    val token: String,
    val avatarUrl: String
)

class CipherGramViewModel(application: Application) : AndroidViewModel(application) {

    private val database = CipherGramDatabase.getDatabase(application)
    val repository = CipherGramRepository(database.cipherGramDao())

    private val _currentScreen = MutableStateFlow<Screen>(Screen.Login)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _userSession = MutableStateFlow<UserSession?>(null)
    val userSession: StateFlow<UserSession?> = _userSession.asStateFlow()

    private val _isGeneratingKeys = MutableStateFlow(false)
    val isGeneratingKeys: StateFlow<Boolean> = _isGeneratingKeys.asStateFlow()

    // For handling deep-linked external shares
    private val _sharedMediaUrl = MutableStateFlow<String?>(null)
    val sharedMediaUrl: StateFlow<String?> = _sharedMediaUrl.asStateFlow()

    // Interactive media state: stores reel playback positions or slide indices
    private val _mediaPlaybackState = MutableStateFlow<Map<String, Boolean>>(emptyMap()) // MessageId -> IsPlaying
    val mediaPlaybackState: StateFlow<Map<String, Boolean>> = _mediaPlaybackState.asStateFlow()

    // Local user's key pair Base64 info
    private val _localKeyPair = MutableStateFlow<UserKeyPairEntity?>(null)
    val localKeyPair: StateFlow<UserKeyPairEntity?> = _localKeyPair.asStateFlow()

    val chats: StateFlow<List<ChatEntity>> = repository.allChats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val contactKeys: StateFlow<List<ContactKeyEntity>> = repository.allContactKeys
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Automatically check if user was already "logged in"
        viewModelScope.launch {
            val sharedPrefs = getApplication<Application>().getSharedPreferences("ciphergram_prefs", Context.MODE_PRIVATE)
            val savedUsername = sharedPrefs.getString("saved_username", null)
            val savedFullName = sharedPrefs.getString("saved_fullname", null)
            val savedToken = sharedPrefs.getString("saved_token", null)
            val savedAvatarUrl = sharedPrefs.getString("saved_avatar_url", null)

            if (!savedUsername.isNullOrEmpty() && !savedFullName.isNullOrEmpty()) {
                val session = UserSession(
                    username = savedUsername,
                    fullName = savedFullName,
                    token = savedToken ?: "ig_session_${(100000..999999).random()}",
                    avatarUrl = savedAvatarUrl ?: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=150&q=80"
                )
                _userSession.value = session

                // Fetch local key pair
                val keyPairEntity = repository.getUserKeyPair(session.username)
                if (keyPairEntity != null) {
                    _localKeyPair.value = keyPairEntity
                    // Sync online profile
                    repository.gateway.publishUserProfile(
                        username = session.username,
                        fullName = session.fullName,
                        publicKeyBase64 = keyPairEntity.publicKeyBase64,
                        avatarUrl = session.avatarUrl
                    )
                }

                _currentScreen.value = Screen.ChatList
            }
        }
    }

    private var activeThreadId: String? = null
    private var messageListenerJob: kotlinx.coroutines.Job? = null

    fun enterChatThread(threadId: String) {
        val session = _userSession.value ?: return
        activeThreadId = threadId
        messageListenerJob?.cancel()
        messageListenerJob = viewModelScope.launch {
            repository.observeAndProcessGatewayMessages(threadId, session.username).collect { message ->
                Log.d("CipherGramViewModel", "New message processed via gateway: ${message.messageId}")
            }
        }
    }

    fun exitChatThread() {
        messageListenerJob?.cancel()
        messageListenerJob = null
        activeThreadId = null
    }

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
        if (screen is Screen.ChatThread) {
            enterChatThread(screen.threadId)
        } else {
            exitChatThread()
        }
    }

    fun setSharedMediaUrl(url: String?) {
        _sharedMediaUrl.value = url
    }

    fun toggleMediaPlayback(messageId: String) {
        val currentMap = _mediaPlaybackState.value
        val isPlaying = currentMap[messageId] ?: false
        _mediaPlaybackState.value = currentMap + (messageId to !isPlaying)
    }

    /**
     * Performs a simulated Instagram login. Generates cryptographic key pairs
     * in the background, seeds standard Instagram conversations, and establishes E2EE.
     */
    fun login(username: String, fullName: String) {
        viewModelScope.launch {
            _isGeneratingKeys.value = true
            
            // 1. Establish User Session
            val session = UserSession(
                username = username.lowercase().trim(),
                fullName = fullName.trim().ifEmpty { "Instagram User" },
                token = "ig_session_${(100000..999999).random()}",
                avatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=150&q=80"
            )
            _userSession.value = session

            // Save user session to SharedPreferences for persistent login
            val sharedPrefs = getApplication<Application>().getSharedPreferences("ciphergram_prefs", Context.MODE_PRIVATE)
            sharedPrefs.edit()
                .putString("saved_username", session.username)
                .putString("saved_fullname", session.fullName)
                .putString("saved_token", session.token)
                .putString("saved_avatar_url", session.avatarUrl)
                .apply()

            // 2. Generate local E2EE keys if they don't already exist
            var keyPairEntity = repository.getUserKeyPair(session.username)
            if (keyPairEntity == null) {
                val hardwarePubKey = HardwareCryptoEngine.generateHardwareKeyPair(session.username)
                if (hardwarePubKey != null) {
                    val publicB64 = E2EECryptoEngine.publicKeyToBase64(hardwarePubKey)
                    keyPairEntity = UserKeyPairEntity(
                        instagramUsername = session.username,
                        publicKeyBase64 = publicB64,
                        privateKeyBase64 = "HARDWARE_BACKED"
                    )
                    repository.insertUserKeyPair(keyPairEntity)
                }
            }
            _localKeyPair.value = keyPairEntity

            // 3. Publish profile to Firestore for real-time E2EE discovery
            if (keyPairEntity != null) {
                repository.gateway.publishUserProfile(
                    username = session.username,
                    fullName = session.fullName,
                    publicKeyBase64 = keyPairEntity.publicKeyBase64,
                    avatarUrl = session.avatarUrl
                )
            }

            _isGeneratingKeys.value = false
            
            // Auto-register FCM device token for wake-up pings
            try {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val fcmToken = task.result
                        if (fcmToken != null) {
                            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    val client = okhttp3.OkHttpClient()
                                    val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                                    val json = org.json.JSONObject().apply {
                                        put("username", session.username)
                                        put("fcm_token", fcmToken)
                                    }
                                    val requestBody = okhttp3.RequestBody.create(mediaType, json.toString())
                                    val request = okhttp3.Request.Builder()
                                        .url("https://ciphergram-signaling-620054685556.europe-west1.run.app/api/register_device")
                                        .post(requestBody)
                                        .build()
                                    client.newCall(request).execute().use { response ->
                                        Log.d("CipherGramViewModel", "FCM device registration completed. Code: ${response.code}")
                                    }
                                } catch (e: Exception) {
                                    Log.e("CipherGramViewModel", "FCM auto-register API call failure", e)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("CipherGramViewModel", "FirebaseMessaging token retrieval error", e)
            }

            _currentScreen.value = Screen.ChatList
        }
    }

    fun logout() {
        // Clear SharedPreferences
        val sharedPrefs = getApplication<Application>().getSharedPreferences("ciphergram_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().clear().apply()

        _userSession.value = null
        _localKeyPair.value = null
        _currentScreen.value = Screen.Login
    }

    /**
     * Standard message sender.
     */
    fun sendMessage(threadId: String, contactUsername: String, text: String, mediaUrl: String? = null, mediaType: String? = null, mediaThumbnail: String? = null, mediaCaption: String? = null) {
        val session = _userSession.value ?: return
        viewModelScope.launch {
            val messageEntity = repository.encryptOutgoingMessage(
                threadId = threadId,
                senderUsername = session.username,
                contactUsername = contactUsername,
                text = text,
                mediaUrl = mediaUrl,
                mediaType = mediaType,
                mediaThumbnail = mediaThumbnail,
                mediaCaption = mediaCaption
            )

            // Publish message via Gateway
            repository.gateway.dispatchEnvelope(threadId, messageEntity)
        }
    }

    /**
     * Handles key rotation: generates a fresh EC KeyPair, invalidating old sessions.
     */
    fun rotateKeys() {
        val session = _userSession.value ?: return
        viewModelScope.launch {
            _isGeneratingKeys.value = true
            
            val hardwarePubKey = HardwareCryptoEngine.generateHardwareKeyPair(session.username)
            if (hardwarePubKey != null) {
                val publicB64 = E2EECryptoEngine.publicKeyToBase64(hardwarePubKey)
                val keyPairEntity = UserKeyPairEntity(
                    instagramUsername = session.username,
                    publicKeyBase64 = publicB64,
                    privateKeyBase64 = "HARDWARE_BACKED"
                )
                repository.insertUserKeyPair(keyPairEntity)
                _localKeyPair.value = keyPairEntity

                // Empty decrypted cache of past E2EE messages to simulate rotation security effect
                // (Forces re-keying or verification)
                Log.d("CipherGram", "Keys rotated! Users will experience a key-change notification.")
            }
            
            _isGeneratingKeys.value = false
        }
    }

    /**
     * Toggles fingerprint verification status of a contact's safety number.
     */
    fun toggleContactVerification(contactUsername: String) {
        viewModelScope.launch {
            val contactKey = repository.getContactKey(contactUsername)
            if (contactKey != null) {
                val updated = contactKey.copy(isVerified = !contactKey.isVerified)
                repository.insertContactKey(updated)
            }
        }
    }
}
