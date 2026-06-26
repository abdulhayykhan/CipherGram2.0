package com.example.database

import android.util.Log
import com.example.cryptography.E2EECryptoEngine
import com.example.cryptography.HardwareCryptoEngine
import com.example.cryptography.SecureEnvelopeProtocol
import com.example.network.FirebaseMessagingGateway
import com.example.network.MessagingGateway
import com.example.network.OkHttpWebSocketGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.spec.SecretKeySpec

class CipherGramRepository(
    private val dao: CipherGramDao,
    val gateway: MessagingGateway = if (try { com.google.firebase.FirebaseApp.getInstance(); true } catch (e: Exception) { false }) {
        FirebaseMessagingGateway()
    } else {
        OkHttpWebSocketGateway()
    }
) {

    companion object {
        private const val TAG = "CipherGramRepository"
    }

    val allChats: Flow<List<ChatEntity>> = dao.getAllChats()

    fun getMessagesForThread(threadId: String): Flow<List<MessageEntity>> {
        return dao.getMessagesForThread(threadId)
    }

    suspend fun getChatById(threadId: String): ChatEntity? = dao.getChatById(threadId)

    suspend fun insertChat(chat: ChatEntity) = dao.insertChat(chat)

    suspend fun updateChat(chat: ChatEntity) = dao.updateChat(chat)

    suspend fun deleteChat(threadId: String) = dao.deleteChat(threadId)

    suspend fun getUserKeyPair(username: String): UserKeyPairEntity? = dao.getUserKeyPair(username)

    suspend fun insertUserKeyPair(keyPair: UserKeyPairEntity) = dao.insertUserKeyPair(keyPair)

    suspend fun getContactKey(username: String): ContactKeyEntity? = dao.getContactKey(username)

    suspend fun insertContactKey(contactKey: ContactKeyEntity) = dao.insertContactKey(contactKey)

    suspend fun insertMessage(message: MessageEntity) = dao.insertMessage(message)

    suspend fun clearMessages(threadId: String) = dao.clearMessagesForThread(threadId)

    /**
     * Attempts to encrypt an outgoing message payload using hardware-backed E2EE.
     * If keys are missing, falls back to plaintext (hybrid mode).
     */
    suspend fun encryptOutgoingMessage(
        threadId: String,
        senderUsername: String,
        contactUsername: String,
        text: String,
        mediaUrl: String? = null,
        mediaType: String? = null,
        mediaThumbnail: String? = null,
        mediaCaption: String? = null
    ): MessageEntity {
        val userKeys = dao.getUserKeyPair(senderUsername)
        val contactKeys = dao.getContactKey(contactUsername)

        var isEncrypted: Boolean
        var finalPayload: String
        var decryptedText: String

        if (userKeys != null && contactKeys != null) {
            try {
                // Reconstruct remote public key
                val remotePublicKey = E2EECryptoEngine.publicKeyFromBase64(contactKeys.publicKeyBase64)

                if (remotePublicKey != null) {
                    val aesKey = if (userKeys.privateKeyBase64 == "HARDWARE_BACKED") {
                        val sharedSecret = HardwareCryptoEngine.deriveSharedSecret(senderUsername, remotePublicKey)
                        if (sharedSecret != null) {
                            val digest = java.security.MessageDigest.getInstance("SHA-256")
                            val derivedBytes = digest.digest(sharedSecret)
                            SecretKeySpec(derivedBytes, "AES")
                        } else null
                    } else {
                        val privateKeyBytes = android.util.Base64.decode(userKeys.privateKeyBase64, android.util.Base64.DEFAULT)
                        val pkcs8Spec = PKCS8EncodedKeySpec(privateKeyBytes)
                        val kf = KeyFactory.getInstance("EC")
                        val localPrivateKey = kf.generatePrivate(pkcs8Spec)
                        E2EECryptoEngine.deriveSymmetricKey(localPrivateKey, remotePublicKey)
                    }

                    if (aesKey != null) {
                        val encryptedBytes = E2EECryptoEngine.encryptToBytes(text, aesKey)
                        if (encryptedBytes != null) {
                            isEncrypted = true
                            finalPayload = SecureEnvelopeProtocol.pack(encryptedBytes)
                            decryptedText = text // Cache locally for direct viewing
                        } else {
                            throw Exception("AES GCM encryption failed")
                        }
                    } else {
                        throw Exception("Key agreement failed")
                    }
                } else {
                    throw Exception("Could not parse recipient public key")
                }
            } catch (e: Exception) {
                Log.e(TAG, "E2EE encryption failed, falling back to plaintext: ${e.message}")
                isEncrypted = false
                finalPayload = text
                decryptedText = text
            }
        } else {
            // Standard unencrypted chat (hybrid channel simulation)
            isEncrypted = false
            finalPayload = text
            decryptedText = text
        }

        val messageEntity = MessageEntity(
            messageId = "msg_${System.currentTimeMillis()}_${(100..999).random()}",
            threadId = threadId,
            senderUsername = senderUsername,
            payload = finalPayload,
            decryptedText = decryptedText,
            timestamp = System.currentTimeMillis(),
            isEncrypted = isEncrypted,
            instagramMediaUrl = mediaUrl,
            instagramMediaType = mediaType,
            instagramMediaThumbnail = mediaThumbnail,
            instagramMediaCaption = mediaCaption
        )

        // Save local copy
        dao.insertMessage(messageEntity)

        // Update chat list metadata
        val chat = dao.getChatById(threadId)
        if (chat != null) {
            dao.insertChat(
                chat.copy(
                    lastMessageText = if (isEncrypted) "🔒 Encrypted Message" else text,
                    lastMessageTime = messageEntity.timestamp
                )
            )
        }

        return messageEntity
    }

    /**
     * Decrypts an incoming message payload if encrypted.
     */
    suspend fun decryptIncomingMessage(
        localUsername: String,
        senderUsername: String,
        message: MessageEntity
    ): MessageEntity = withContext(Dispatchers.IO) {
        if (!message.isEncrypted || message.decryptedText != null) {
            return@withContext message
        }

        val userKeys = dao.getUserKeyPair(localUsername)
        val senderKeys = dao.getContactKey(senderUsername)

        if (userKeys != null && senderKeys != null) {
            try {
                // Reconstruct remote public key
                val remotePublicKey = E2EECryptoEngine.publicKeyFromBase64(senderKeys.publicKeyBase64)

                if (remotePublicKey != null) {
                    val aesKey = if (userKeys.privateKeyBase64 == "HARDWARE_BACKED") {
                        val sharedSecret = HardwareCryptoEngine.deriveSharedSecret(localUsername, remotePublicKey)
                        if (sharedSecret != null) {
                            val digest = java.security.MessageDigest.getInstance("SHA-256")
                            val derivedBytes = digest.digest(sharedSecret)
                            SecretKeySpec(derivedBytes, "AES")
                        } else null
                    } else {
                        val privateKeyBytes = android.util.Base64.decode(userKeys.privateKeyBase64, android.util.Base64.DEFAULT)
                        val pkcs8Spec = PKCS8EncodedKeySpec(privateKeyBytes)
                        val kf = KeyFactory.getInstance("EC")
                        val localPrivateKey = kf.generatePrivate(pkcs8Spec)
                        E2EECryptoEngine.deriveSymmetricKey(localPrivateKey, remotePublicKey)
                    }

                    if (aesKey != null) {
                        val unpackedBytes = SecureEnvelopeProtocol.unpack(message.payload)
                        val decrypted = if (unpackedBytes != null) {
                            E2EECryptoEngine.decryptFromBytes(unpackedBytes, aesKey)
                        } else {
                            E2EECryptoEngine.decrypt(message.payload, aesKey)
                        }

                        if (decrypted != null) {
                            val updatedMessage = message.copy(decryptedText = decrypted)
                            dao.insertMessage(updatedMessage)
                            return@withContext updatedMessage
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Decryption of incoming message failed: ${e.message}")
            }
        }

        return@withContext message.copy(decryptedText = "[Error: Unable to decrypt message. Session expired or keys mismatch.]")
    }

    /**
     * Intercepts messages from the gateway, executes the cryptographic extraction pipeline
     * on Dispatchers.IO, and commits the derived cleartext record smoothly.
     */
    fun observeAndProcessGatewayMessages(threadId: String, localUsername: String): Flow<MessageEntity> = callbackFlow {
        val collectorScope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
        val job = collectorScope.launch {
            gateway.observeIncomingMessages(threadId, localUsername).collect { rawMessage ->
                // Check if message is already stored to prevent duplication
                val existing = dao.getMessageById(rawMessage.messageId)
                if (existing == null) {
                    // Import sender public key from gateway registry if not locally present
                    if (rawMessage.isEncrypted && rawMessage.senderUsername != localUsername) {
                        val localContactKey = dao.getContactKey(rawMessage.senderUsername)
                        if (localContactKey == null) {
                            val senderProfile = gateway.findUserProfile(rawMessage.senderUsername)
                            val remotePubKeyB64 = senderProfile?.get("publicKeyBase64") as? String
                            if (!remotePubKeyB64.isNullOrEmpty()) {
                                Log.d(TAG, "Auto-importing public key via gateway for sender: ${rawMessage.senderUsername}")
                                dao.insertContactKey(
                                    ContactKeyEntity(
                                        contactUsername = rawMessage.senderUsername,
                                        publicKeyBase64 = remotePubKeyB64,
                                        isVerified = false
                                    )
                                )
                            }
                        }
                    }

                    // Process and decrypt the incoming packet
                    val processedMessage = decryptIncomingMessage(
                        localUsername = localUsername,
                        senderUsername = rawMessage.senderUsername,
                        message = rawMessage
                    )

                    // Write processed message directly to SQLite
                    dao.insertMessage(processedMessage)

                    // Update parent chat entry summary
                    val existingChat = dao.getChatById(threadId)
                    if (existingChat != null) {
                        if (existingChat.lastMessageTime < processedMessage.timestamp) {
                            val textToShow = if (processedMessage.isEncrypted && processedMessage.senderUsername != localUsername) {
                                processedMessage.decryptedText ?: "🔒 Encrypted Message"
                            } else if (processedMessage.isEncrypted) {
                                "🔒 Encrypted Message"
                            } else {
                                processedMessage.payload
                            }
                            dao.insertChat(
                                existingChat.copy(
                                    lastMessageText = textToShow,
                                    lastMessageTime = processedMessage.timestamp
                                )
                            )
                        }
                    }

                    trySend(processedMessage)
                }
            }
        }

        awaitClose {
            job.cancel()
        }
    }
}
