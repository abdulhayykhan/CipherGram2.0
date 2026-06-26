package com.example.cryptography

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import javax.crypto.KeyAgreement

object HardwareCryptoEngine {
    private const val TAG = "HardwareCryptoEngine"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS_PREFIX = "ciphergram_ec_"

    fun generateHardwareKeyPair(username: String): PublicKey? {
        return try {
            val alias = "$KEY_ALIAS_PREFIX$username"
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
            }

            val kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC, 
                ANDROID_KEYSTORE
            )
            
            val parameterSpec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_AGREE_KEY
            ).run {
                setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                setUserAuthenticationRequired(false)
                build()
            }

            kpg.initialize(parameterSpec)
            val keyPair = kpg.generateKeyPair()
            keyPair.public
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate hardware-backed keypair", e)
            null
        }
    }

    private fun getPrivateKey(username: String): PrivateKey? {
        return try {
            val alias = "$KEY_ALIAS_PREFIX$username"
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val entry = keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
            entry?.privateKey
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve PrivateKey reference", e)
            null
        }
    }

    fun deriveSharedSecret(username: String, remotePublicKey: PublicKey): ByteArray? {
        return try {
            val localPrivateKey = getPrivateKey(username) ?: return null
            val keyAgreement = KeyAgreement.getInstance("ECDH")
            keyAgreement.init(localPrivateKey)
            keyAgreement.doPhase(remotePublicKey, true)
            keyAgreement.generateSecret()
        } catch (e: Exception) {
            Log.e(TAG, "ECDH Key Agreement failed", e)
            null
        }
    }
}
