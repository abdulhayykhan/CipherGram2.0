package com.example.database

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

object FirebaseSyncManager {
    private const val TAG = "FirebaseSyncManager"

    // Safe accessor to verify if Firebase is initialized.
    // This allows the app to fall back cleanly to 100% offline Room database
    // if a custom or invalid google-services.json is used.
    val isFirebaseAvailable: Boolean
        get() = try {
            FirebaseApp.getInstance()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Firebase is not initialized. Running in Offline-Only Mode.")
            false
        }

    private val firestore: FirebaseFirestore?
        get() = if (isFirebaseAvailable) FirebaseFirestore.getInstance() else null

    private val listeners = mutableMapOf<String, ListenerRegistration>()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    /**
     * Publishes the logged-in user's details and public ECDH identity key to Firestore
     * so that other users can discover them and establish end-to-end encrypted tunnels automatically.
     */
    fun publishUserProfile(username: String, fullName: String, publicKeyBase64: String, avatarUrl: String) {
        val db = firestore ?: return
        coroutineScope.launch {
            try {
                val userRef = db.collection("users").document(username.lowercase().trim())
                val data = mapOf(
                    "username" to username.lowercase().trim(),
                    "fullName" to fullName,
                    "publicKeyBase64" to publicKeyBase64,
                    "avatarUrl" to avatarUrl,
                    "lastSeen" to System.currentTimeMillis()
                )
                userRef.set(data).await()
                Log.d(TAG, "Successfully published user profile for $username on Firestore!")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to publish user profile to Firestore: ${e.message}")
            }
        }
    }

    /**
     * Attempts to query a user's details (specifically their ECDH public key) from Firestore.
     * This automates key exchange so friends do not need to manually exchange key hashes!
     */
    suspend fun findUserProfile(username: String): Map<String, Any>? {
        val db = firestore ?: return null
        return try {
            val doc = db.collection("users").document(username.lowercase().trim()).get().await()
            if (doc.exists()) doc.data else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch user profile for $username: ${e.message}")
            null
        }
    }

    /**
     * Publishes an outgoing message to the real-time Firebase Firestore channel.
     * This pushes the E2EE ciphertext to the cloud safely!
     */
    fun publishMessage(
        threadId: String,
        messageId: String,
        senderUsername: String,
        payload: String,
        isEncrypted: Boolean,
        timestamp: Long,
        mediaUrl: String? = null,
        mediaType: String? = null,
        mediaThumbnail: String? = null,
        mediaCaption: String? = null
    ) {
        val db = firestore ?: return
        coroutineScope.launch {
            try {
                val messageRef = db.collection("threads")
                    .document(threadId)
                    .collection("messages")
                    .document(messageId)

                val messageData = mutableMapOf<String, Any>(
                    "messageId" to messageId,
                    "threadId" to threadId,
                    "senderUsername" to senderUsername,
                    "payload" to payload,
                    "isEncrypted" to isEncrypted,
                    "timestamp" to timestamp
                )

                mediaUrl?.let { messageData["instagramMediaUrl"] = it }
                mediaType?.let { messageData["instagramMediaType"] = it }
                mediaThumbnail?.let { messageData["instagramMediaThumbnail"] = it }
                mediaCaption?.let { messageData["instagramMediaCaption"] = it }

                // 1. Write the message
                messageRef.set(messageData).await()

                // 2. Update the parent thread summary for list discovery
                val threadRef = db.collection("threads").document(threadId)
                val threadData = mapOf(
                    "threadId" to threadId,
                    "lastMessageText" to if (isEncrypted) "🔒 Encrypted Message" else payload,
                    "lastMessageTime" to timestamp
                )
                threadRef.set(threadData, com.google.firebase.firestore.SetOptions.merge()).await()

                Log.d(TAG, "Successfully published message $messageId on thread $threadId to Firestore.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to publish message to Firestore: ${e.message}")
            }
        }
    }

    /**
     * Starts listening to a real-time Firestore thread channel.
     * Pulls down incoming messages from friends, decrypts them, and caches them to Room!
     */
    fun startListeningToThread(
        context: Context,
        threadId: String,
        localUsername: String,
        repository: CipherGramRepository
    ) {
        val db = firestore ?: return

        // Prevent redundant listener registration
        if (listeners.containsKey(threadId)) return

        Log.d(TAG, "Registering active real-time Firestore listener for thread: $threadId")

        val listener = db.collection("threads")
            .document(threadId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Firestore listener error for thread $threadId: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshots == null || snapshots.isEmpty) return@addSnapshotListener

                coroutineScope.launch {
                    for (doc in snapshots.documentChanges) {
                        if (doc.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                            val data = doc.document.data
                            val msgId = data["messageId"] as? String ?: continue
                            val sender = data["senderUsername"] as? String ?: continue
                            val payload = data["payload"] as? String ?: ""
                            val isEncrypted = data["isEncrypted"] as? Boolean ?: false
                            val timestamp = data["timestamp"] as? Long ?: System.currentTimeMillis()
                            val mediaUrl = data["instagramMediaUrl"] as? String
                            val mediaType = data["instagramMediaType"] as? String
                            val mediaThumbnail = data["instagramMediaThumbnail"] as? String
                            val mediaCaption = data["instagramMediaCaption"] as? String

                            // Create the message model
                            val message = MessageEntity(
                                messageId = msgId,
                                threadId = threadId,
                                senderUsername = sender,
                                payload = payload,
                                decryptedText = null, // Decrypted dynamically on repository import
                                timestamp = timestamp,
                                isEncrypted = isEncrypted,
                                instagramMediaUrl = mediaUrl,
                                instagramMediaType = mediaType,
                                instagramMediaThumbnail = mediaThumbnail,
                                instagramMediaCaption = mediaCaption
                            )

                            // 1. Fetch sender's public key from Firestore dynamically if we don't have it!
                            // This guarantees seamless E2EE setup even if users never manually swapped keys!
                            if (isEncrypted && sender != localUsername) {
                                val localContactKey = repository.getContactKey(sender)
                                if (localContactKey == null) {
                                    val senderProfile = findUserProfile(sender)
                                    val remotePubKeyB64 = senderProfile?.get("publicKeyBase64") as? String
                                    if (!remotePubKeyB64.isNullOrEmpty()) {
                                        Log.d(TAG, "Auto-importing public key from Firestore for sender: $sender")
                                        repository.insertContactKey(
                                            ContactKeyEntity(
                                                contactUsername = sender,
                                                publicKeyBase64 = remotePubKeyB64,
                                                isVerified = false
                                            )
                                        )
                                    }
                                }
                            }

                            // 2. Decrypt & Cache the message locally to Room database
                            val processedMessage = repository.decryptIncomingMessage(
                                localUsername = localUsername,
                                senderUsername = sender,
                                message = message
                            )

                            // 3. Update parent chat list summary
                            val existingChat = repository.getChatById(threadId)
                            if (existingChat != null) {
                                if (existingChat.lastMessageTime < timestamp) {
                                    val textToShow = if (isEncrypted && sender != localUsername) {
                                        processedMessage.decryptedText ?: "🔒 Encrypted Message"
                                    } else if (isEncrypted) {
                                        "🔒 Encrypted Message"
                                    } else {
                                        payload
                                    }
                                    repository.insertChat(
                                        existingChat.copy(
                                            lastMessageText = textToShow,
                                            lastMessageTime = timestamp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

        listeners[threadId] = listener
    }

    /**
     * Stops listening to a thread to conserve resources and battery.
     */
    fun stopListeningToThread(threadId: String) {
        listeners[threadId]?.remove()
        listeners.remove(threadId)
        Log.d(TAG, "Stopped active Firestore listener for thread: $threadId")
    }

    /**
     * Publishes a thread metadata to discovery pool so the recipient can find it in their chats.
     */
    fun publishThreadMetadata(
        threadId: String,
        contactUsername: String,
        contactFullName: String,
        avatarUrl: String,
        isE2EEnabled: Boolean
    ) {
        val db = firestore ?: return
        coroutineScope.launch {
            try {
                val threadRef = db.collection("threads").document(threadId)
                val threadData = mapOf(
                    "threadId" to threadId,
                    "contactUsername" to contactUsername,
                    "contactFullName" to contactFullName,
                    "avatarUrl" to avatarUrl,
                    "isE2EEnabled" to isE2EEnabled,
                    "lastMessageTime" to System.currentTimeMillis()
                )
                threadRef.set(threadData, com.google.firebase.firestore.SetOptions.merge()).await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to publish thread metadata to Firestore: ${e.message}")
            }
        }
    }
}
