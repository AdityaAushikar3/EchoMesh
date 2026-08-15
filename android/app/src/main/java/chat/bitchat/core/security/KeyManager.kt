package chat.bitchat.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.ECGenParameterSpec

object KeyManager {
    private const val TAG = "KeyManager"
    private const val KEY_ALIAS = "echo_mesh_identity_key"

    init {
        ensureKeyExists()
    }

    @Synchronized
    private fun ensureKeyExists() {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
                // 32 is KeyProperties.PURPOSE_AGREE (API 31+); combined with ENCRYPT/DECRYPT for backward compatibility
                val purpose = KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT or 32
                kpg.initialize(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        purpose
                    ).setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                        .build()
                )
                kpg.generateKeyPair()
                Log.i(TAG, "Generated hardware-backed EC identity key pair")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Keystore identity key", e)
        }
    }

    fun getPublicKeyBytes(): ByteArray {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val cert = keyStore.getCertificate(KEY_ALIAS) ?: return ByteArray(0)
            cert.publicKey.encoded
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export public key bytes", e)
            ByteArray(0)
        }
    }

    fun getPrivateKey(): PrivateKey? {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            keyStore.getKey(KEY_ALIAS, null) as? PrivateKey
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve private key from Keystore", e)
            null
        }
    }
}
