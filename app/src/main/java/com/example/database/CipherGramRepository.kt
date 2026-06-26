package com.example.database

import android.util.Log
import com.example.cryptography.E2EECryptoEngine
import kotlinx.coroutines.flow.Flow
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.spec.SecretKeySpec

class CipherGramRepository(private val dao: CipherGramDao) {

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
     * Attempts to encrypt a outgoing message payload using real E2EE.
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
                // Reconstruct local private key
                val privateKeyBytes = android.util.Base64.decode(userKeys.privateKeyBase64, android.util.Base64.DEFAULT)
                val pkcs8Spec = PKCS8EncodedKeySpec(privateKeyBytes)
                val kf = KeyFactory.getInstance("EC")
                val localPrivateKey = kf.generatePrivate(pkcs8Spec)

                // Reconstruct remote public key
                val remotePublicKey = E2EECryptoEngine.publicKeyFromBase64(contactKeys.publicKeyBase64)

                if (remotePublicKey != null) {
                    // Derive shared secret and AES key
                    val aesKey = E2EECryptoEngine.deriveSymmetricKey(localPrivateKey, remotePublicKey)
                    if (aesKey != null) {
                        val encrypted = E2EECryptoEngine.encrypt(text, aesKey)
                        if (encrypted != null) {
                            isEncrypted = true
                            finalPayload = encrypted
                            decryptedText = text // Cache locally for direct viewing
                        } else {
                            throw Exception("AES encryption returned null")
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
    ): MessageEntity {
        if (!message.isEncrypted || message.decryptedText != null) {
            return message
        }

        val userKeys = dao.getUserKeyPair(localUsername)
        val senderKeys = dao.getContactKey(senderUsername)

        if (userKeys != null && senderKeys != null) {
            try {
                // Reconstruct local private key
                val privateKeyBytes = android.util.Base64.decode(userKeys.privateKeyBase64, android.util.Base64.DEFAULT)
                val pkcs8Spec = PKCS8EncodedKeySpec(privateKeyBytes)
                val kf = KeyFactory.getInstance("EC")
                val localPrivateKey = kf.generatePrivate(pkcs8Spec)

                // Reconstruct remote public key
                val remotePublicKey = E2EECryptoEngine.publicKeyFromBase64(senderKeys.publicKeyBase64)

                if (remotePublicKey != null) {
                    val aesKey = E2EECryptoEngine.deriveSymmetricKey(localPrivateKey, remotePublicKey)
                    if (aesKey != null) {
                        val decrypted = E2EECryptoEngine.decrypt(message.payload, aesKey)
                        if (decrypted != null) {
                            val updatedMessage = message.copy(decryptedText = decrypted)
                            dao.insertMessage(updatedMessage)
                            return updatedMessage
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Decryption of incoming message failed: ${e.message}")
            }
        }

        return message.copy(decryptedText = "[Error: Unable to decrypt message. Session expired or keys mismatch.]")
    }
}
