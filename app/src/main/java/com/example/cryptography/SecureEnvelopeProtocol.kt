package com.example.cryptography

import android.util.Base64
import java.util.regex.Pattern

object SecureEnvelopeProtocol {
    private const val HEADER = "-----BEGIN CIPHERGRAM ENVELOPE-----"
    private const val FOOTER = "-----END CIPHERGRAM ENVELOPE-----"
    private const val NOTICE = "🔒 Secure E2EE Message. Please view in-app to decrypt.\n"

    private val ENVELOPE_PATTERN: Pattern = Pattern.compile(
        "-----BEGIN CIPHERGRAM ENVELOPE-----\\r?\\n([a-zA-Z0-9+/=\\r\\n\\s]+)\\r?\\n-----END CIPHERGRAM ENVELOPE-----"
    )

    fun pack(encryptedPayload: ByteArray): String {
        val base64Encoded = Base64.encodeToString(encryptedPayload, Base64.NO_WRAP)
        return buildString {
            append(NOTICE)
            append(HEADER).append("\n")
            append(base64Encoded).append("\n")
            append(FOOTER)
        }
    }

    fun unpack(incomingText: String): ByteArray? {
        val matcher = ENVELOPE_PATTERN.matcher(incomingText)
        return if (matcher.find()) {
            try {
                val base64Payload = matcher.group(1)?.replace("\\s".toRegex(), "")
                Base64.decode(base64Payload, Base64.DEFAULT)
            } catch (e: Exception) {
                null
            }
        } else {
            // Support fallback to pattern match without \r just in case
            val simplerPattern = Pattern.compile("-----BEGIN CIPHERGRAM ENVELOPE-----\\n([a-zA-Z0-9+/=\\s]+)\\n-----END CIPHERGRAM ENVELOPE-----")
            val simplerMatcher = simplerPattern.matcher(incomingText)
            if (simplerMatcher.find()) {
                try {
                    val base64Payload = simplerMatcher.group(1)?.replace("\\s".toRegex(), "")
                    Base64.decode(base64Payload, Base64.DEFAULT)
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        }
    }
}
