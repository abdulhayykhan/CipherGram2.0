package com.example.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CipherGramDao {

    // --- Key Management ---
    @Query("SELECT * FROM user_key_pairs WHERE instagramUsername = :username LIMIT 1")
    suspend fun getUserKeyPair(username: String): UserKeyPairEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserKeyPair(keyPair: UserKeyPairEntity)

    @Query("SELECT * FROM contact_keys WHERE contactUsername = :username LIMIT 1")
    suspend fun getContactKey(username: String): ContactKeyEntity?

    @Query("SELECT * FROM contact_keys")
    fun getAllContactKeys(): Flow<List<ContactKeyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContactKey(contactKey: ContactKeyEntity)

    @Query("DELETE FROM contact_keys WHERE contactUsername = :username")
    suspend fun deleteContactKey(username: String)

    // --- Chat Management ---
    @Query("SELECT * FROM chats ORDER BY lastMessageTime DESC")
    fun getAllChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE threadId = :threadId LIMIT 1")
    suspend fun getChatById(threadId: String): ChatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity)

    @Update
    suspend fun updateChat(chat: ChatEntity)

    @Query("DELETE FROM chats WHERE threadId = :threadId")
    suspend fun deleteChat(threadId: String)

    // --- Message Management ---
    @Query("SELECT * FROM messages WHERE threadId = :threadId ORDER BY timestamp ASC")
    fun getMessagesForThread(threadId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE messageId = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE threadId = :threadId")
    suspend fun clearMessagesForThread(threadId: String)
}
