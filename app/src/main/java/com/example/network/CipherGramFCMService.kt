package com.example.network

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.database.CipherGramDatabase
import com.example.database.MessageEntity
import com.example.database.ChatEntity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class CipherGramFCMService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed FCM token: $token")
        
        val sharedPrefs = applicationContext.getSharedPreferences("ciphergram_prefs", Context.MODE_PRIVATE)
        val username = sharedPrefs.getString("saved_username", null)
        
        if (!username.isNullOrEmpty()) {
            serviceScope.launch {
                registerDeviceOnServer(username, token)
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "From: ${remoteMessage.from}")

        val action = remoteMessage.data["action"]
        Log.d(TAG, "FCM Message Data Action: $action")

        if (action == "WAKE_SYNC") {
            val sharedPrefs = applicationContext.getSharedPreferences("ciphergram_prefs", Context.MODE_PRIVATE)
            val username = sharedPrefs.getString("saved_username", null)
            if (!username.isNullOrEmpty()) {
                Log.d(TAG, "WAKE_SYNC received. Re-establishing background WebSocket pipe to drain queue for: $username")
                serviceScope.launch {
                    reestablishWebSocketAndDrain(username)
                }
            }
        }
    }

    private fun registerDeviceOnServer(username: String, token: String) {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val json = JSONObject().apply {
            put("username", username)
            put("fcm_token", token)
        }
        val request = Request.Builder()
            .url("https://ciphergram-signaling-620054685556.europe-west1.run.app/api/register_device")
            .post(json.toString().toRequestBody(mediaType))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "Successfully registered FCM token for user: $username")
                } else {
                    Log.e(TAG, "Failed to register FCM token. Code: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error registering FCM token on server", e)
        }
    }

    private suspend fun reestablishWebSocketAndDrain(username: String) {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

        val wsUrl = "wss://ciphergram-signaling-620054685556.europe-west1.run.app/ws/$username"
        val request = Request.Builder().url(wsUrl).build()

        val database = CipherGramDatabase.getDatabase(applicationContext)
        val dao = database.cipherGramDao()

        val latch = CompletableDeferred<Unit>()

        val webSocketListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Background WebSocket connected successfully to drain signaling queue")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Background WS message: $text")
                try {
                    val json = JSONObject(text)
                    if (json.has("payload")) {
                        val sender = json.getString("sender")
                        val payload = json.getString("payload")
                        val threadId = json.optString("threadId", "thread_$sender")
                        val isEncrypted = json.optBoolean("isEncrypted", false)
                        val timestamp = json.optLong("timestamp", System.currentTimeMillis())
                        val msgId = "msg_${System.currentTimeMillis()}_${(100..999).random()}"

                        serviceScope.launch {
                            val existing = dao.getMessageById(msgId)
                            if (existing == null) {
                                val message = MessageEntity(
                                    messageId = msgId,
                                    threadId = threadId,
                                    senderUsername = sender,
                                    payload = payload,
                                    decryptedText = null, // Will be decrypted on UI or next active session
                                    timestamp = timestamp,
                                    isEncrypted = isEncrypted
                                )
                                dao.insertMessage(message)
                                
                                // Insert/Update chat
                                val chat = dao.getChatById(threadId)
                                if (chat != null) {
                                    dao.insertChat(chat.copy(
                                        lastMessageText = if (isEncrypted) "🔒 Encrypted Message" else payload,
                                        lastMessageTime = timestamp
                                    ))
                                } else {
                                    dao.insertChat(ChatEntity(
                                        threadId = threadId,
                                        contactUsername = sender,
                                        contactFullName = sender,
                                        avatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=150&q=80",
                                        lastMessageText = if (isEncrypted) "🔒 Encrypted Message" else payload,
                                        lastMessageTime = timestamp,
                                        isE2EEnabled = isEncrypted
                                    ))
                                }

                                // Trigger a visible Android system notification for the secure message
                                showSecureNotification(sender)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed parsing background message frame", e)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                latch.complete(Unit)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Background WS failure", t)
                latch.complete(Unit)
            }
        }

        val ws = client.newWebSocket(request, webSocketListener)

        // Hold the connection open for 6 seconds to let all queued messages drain
        delay(6000)
        ws.close(1000, "Sync complete")
        latch.complete(Unit)
    }

    private fun showSecureNotification(sender: String) {
        val channelId = "secure_messages_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Secure Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "End-to-end encrypted notification updates"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Open MainActivity when notification is clicked
        val intent = Intent(this, com.example.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New Secure Message")
            .setContentText("Incoming encrypted CipherGram from @$sender")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "CipherGramFCMService"
    }
}
