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

    @Volatile
    private var cachedPrivateKey: PrivateKey? = null
    @Volatile
    private var cachedPublicKeyBytes: ByteArray? = null

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
            cachedPrivateKey = keyStore.getKey(KEY_ALIAS, null) as? PrivateKey
            val cert = keyStore.getCertificate(KEY_ALIAS)
            cachedPublicKeyBytes = cert?.publicKey?.encoded
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Keystore identity key", e)
        }
    }

    fun getPublicKeyBytes(): ByteArray {
        cachedPublicKeyBytes?.let { return it }
        ensureKeyExists()
        return cachedPublicKeyBytes ?: ByteArray(0)
    }

    fun getPrivateKey(): PrivateKey? {
        cachedPrivateKey?.let { return it }
        ensureKeyExists()
        return cachedPrivateKey
    }
}
