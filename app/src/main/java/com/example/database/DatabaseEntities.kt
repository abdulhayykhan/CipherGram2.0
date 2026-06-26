package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_key_pairs")
data class UserKeyPairEntity(
    @PrimaryKey val instagramUsername: String,
    val publicKeyBase64: String,
    val privateKeyBase64: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "contact_keys")
data class ContactKeyEntity(
    @PrimaryKey val contactUsername: String,
    val publicKeyBase64: String,
    val isVerified: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val threadId: String,
    val contactUsername: String,
    val contactFullName: String,
    val avatarUrl: String,
    val isE2EEnabled: Boolean = false,
    val lastMessageText: String = "",
    val lastMessageTime: Long = System.currentTimeMillis(),
    val isOnline: Boolean = false
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val messageId: String,
    val threadId: String,
    val senderUsername: String,
    val payload: String, // Transport layer text (ciphertext or plaintext)
    val decryptedText: String? = null, // Cached decryption for speed
    val timestamp: Long = System.currentTimeMillis(),
    val isEncrypted: Boolean = false,
    val instagramMediaUrl: String? = null,
    val instagramMediaType: String? = null, // "POST", "REEL"
    val instagramMediaThumbnail: String? = null,
    val instagramMediaCaption: String? = null
)
