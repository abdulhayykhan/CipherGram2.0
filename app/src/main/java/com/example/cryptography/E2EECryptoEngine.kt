package com.example.cryptography

import android.util.Base64
import android.util.Log
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Robust End-to-End Encryption (E2EE) engine implementing Elliptic Curve Diffie-Hellman (ECDH)
 * key agreement and AES-GCM symmetric message encryption.
 */
object E2EECryptoEngine {
    private const val TAG = "E2EECryptoEngine"
    private const val KEY_ALGORITHM = "EC"
    private const val EC_CURVE = "secp256r1"
    private const val SYMMETRIC_ALGORITHM = "AES"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128

    /**
     * Generates a new Elliptic Curve KeyPair (secp256r1).
     */
    fun generateKeyPair(): KeyPair? {
        return try {
            val keyPairGenerator = KeyPairGenerator.getInstance(KEY_ALGORITHM)
            val ecGenParameterSpec = ECGenParameterSpec(EC_CURVE)
            keyPairGenerator.initialize(ecGenParameterSpec, SecureRandom())
            keyPairGenerator.generateKeyPair()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate EC key pair: ${e.message}", e)
            null
        }
    }

    /**
     * Reconstructs an EC Public Key from its Base64-encoded X509 serialization.
     */
    fun publicKeyFromBase64(base64Str: String): PublicKey? {
        return try {
            val keyBytes = Base64.decode(base64Str, Base64.DEFAULT)
            val spec = X509EncodedKeySpec(keyBytes)
            val kf = KeyFactory.getInstance(KEY_ALGORITHM)
            kf.generatePublic(spec)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode public key from Base64", e)
            null
        }
    }

    /**
     * Encodes a Public Key into its Base64 X509 serialization format.
     */
    fun publicKeyToBase64(publicKey: PublicKey): String {
        return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
    }

    /**
     * Performs Elliptic Curve Diffie-Hellman (ECDH) key agreement and derives
     * an AES symmetric key using SHA-256 of the shared secret.
     */
    fun deriveSymmetricKey(localPrivateKey: PrivateKey, remotePublicKey: PublicKey): SecretKeySpec? {
        return try {
            val agreement = KeyAgreement.getInstance("ECDH")
            agreement.init(localPrivateKey)
            agreement.doPhase(remotePublicKey, true)
            val sharedSecret = agreement.generateSecret()

            // Hash shared secret with SHA-256 to derive AES key
            val digest = MessageDigest.getInstance("SHA-256")
            val derivedBytes = digest.digest(sharedSecret)
            SecretKeySpec(derivedBytes, SYMMETRIC_ALGORITHM)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to derive symmetric key via ECDH: ${e.message}", e)
            null
        }
    }

    /**
     * Encrypts a plaintext message using AES-GCM under the derived symmetric key.
     * The output is a secure transport envelope containing the IV + Ciphertext encoded in Base64.
     */
    fun encrypt(plaintext: String, symmetricKey: SecretKeySpec): String? {
        return try {
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            val iv = ByteArray(GCM_IV_LENGTH_BYTES).apply {
                SecureRandom().nextBytes(this)
            }
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.ENCRYPT_MODE, symmetricKey, gcmSpec)
            
            val plaintextBytes = plaintext.toByteArray(Charsets.UTF_8)
            val ciphertext = cipher.doFinal(plaintextBytes)

            // Combine IV and Ciphertext for delivery: [IV (12 bytes)] + [Ciphertext]
            val combined = ByteArray(iv.size + ciphertext.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)

            // Wrap in safety transport indicator
            val base64Payload = Base64.encodeToString(combined, Base64.NO_WRAP)
            "🔒[CipherGram_E2EE:$base64Payload]"
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failure: ${e.message}", e)
            null
        }
    }

    /**
     * Decrypts a secure CipherGram transport envelope.
     */
    fun decrypt(encryptedEnvelope: String, symmetricKey: SecretKeySpec): String? {
        return try {
            if (!encryptedEnvelope.startsWith("🔒[CipherGram_E2EE:") || !encryptedEnvelope.endsWith("]")) {
                Log.w(TAG, "Invalid envelope format")
                return null
            }
            
            val base64Payload = encryptedEnvelope
                .removePrefix("🔒[CipherGram_E2EE:")
                .removeSuffix("]")
            
            val combined = Base64.decode(base64Payload, Base64.DEFAULT)
            if (combined.size <= GCM_IV_LENGTH_BYTES) {
                Log.e(TAG, "Payload size is too small")
                return null
            }

            val iv = ByteArray(GCM_IV_LENGTH_BYTES)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH_BYTES)

            val ciphertextSize = combined.size - GCM_IV_LENGTH_BYTES
            val ciphertext = ByteArray(ciphertextSize)
            System.arraycopy(combined, GCM_IV_LENGTH_BYTES, ciphertext, 0, ciphertextSize)

            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, symmetricKey, gcmSpec)

            val decryptedBytes = cipher.doFinal(ciphertext)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failure: ${e.message}", e)
            null
        }
    }

    /**
     * Generates an E2EE Safety Number (Fingerprint) of the key exchange for out-of-band verification.
     */
    fun computeSafetyNumber(localPublicKey: PublicKey, remotePublicKey: PublicKey): String {
        return try {
            val combined = localPublicKey.encoded + remotePublicKey.encoded
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(combined)
            // Extract a clean 12-digit safety number
            val sb = java.lang.StringBuilder()
            for (i in 0 until 6) {
                val value = ((hash[i * 2].toInt() and 0xFF) shl 8) or (hash[i * 2 + 1].toInt() and 0xFF)
                val chunk = (value % 100000).toString().padStart(5, '0')
                sb.append(chunk).append(" ")
            }
            sb.toString().trim()
        } catch (e: Exception) {
            "00000 00000 00000 00000 00000"
        }
    }
}
