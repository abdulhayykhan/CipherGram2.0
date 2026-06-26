package com.example

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import java.util.regex.Pattern

class ShareHandlerActivity : ComponentActivity() {
    companion object {
        private const val TAG = "ShareHandlerActivity"
        private val INSTAGRAM_URL_PATTERN: Pattern = Pattern.compile(
            "https://(www\\.)?instagram\\.com/(p|reel)/([a-zA-Z0-9_-]+)"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent) {
        if (Intent.ACTION_SEND == intent.action && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (sharedText != null) {
                val extractedUrl = parseMediaUrl(sharedText)
                if (extractedUrl != null) {
                    launchAppWithSharePayload(extractedUrl)
                }
            }
        }
        finish()
    }

    private fun parseMediaUrl(text: String): String? {
        val matcher = INSTAGRAM_URL_PATTERN.matcher(text)
        return if (matcher.find()) matcher.group(0) else null
    }

    private fun launchAppWithSharePayload(url: String) {
        Log.d(TAG, "Intercepted shared URL: $url")
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("SHARED_MEDIA_URL", url)
        }
        startActivity(launchIntent)
    }
}
