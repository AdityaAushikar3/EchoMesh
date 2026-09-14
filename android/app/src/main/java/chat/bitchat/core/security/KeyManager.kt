package chat.bitchat.core.security

import android.content.Context
import android.os.Build
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

    @Volatile
    private var fallbackStorage: FallbackKeyStorage? = null

    fun init(context: Context) {
        if (fallbackStorage == null) {
            fallbackStorage = AndroidKeyStoreEncryptedStorage(context.applicationContext)
        }
    }

    fun setFallbackStorage(storage: FallbackKeyStorage) {
        fallbackStorage = storage
    }

    @Synchronized
    private fun ensureKeyExists() {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
                val purpose = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    KeyProperties.PURPOSE_AGREE_KEY or KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                } else {
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                }
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
            try {
                Log.e(TAG, "Failed to initialize Keystore identity key, attempting software fallback", e)
            } catch (_: Throwable) {
                println("KeyManager: Failed to initialize Keystore identity key, attempting software fallback: ${e.message}")
            }
        }

        // Fallback for API 26-30 or devices without hardware KeyStore ECDH support
        if (cachedPrivateKey == null) {
            // 1. Try loading from secure persistent fallback storage
            val loaded = try {
                fallbackStorage?.loadKeyPair()
            } catch (e: Exception) {
                try {
                    Log.e(TAG, "Error loading persisted fallback key pair", e)
                } catch (_: Throwable) {
                    println("KeyManager: Error loading persisted fallback key pair: ${e.message}")
                }
                null
            }

            if (loaded != null) {
                cachedPrivateKey = loaded.first
                cachedPublicKeyBytes = loaded.second
                try {
                    Log.i(TAG, "Restored persisted software EC identity key pair")
                } catch (_: Throwable) {
                    println("KeyManager: Restored persisted software EC identity key pair")
                }
                return
            }

            // 2. Generate and persist new software key pair
            try {
                val kpg = KeyPairGenerator.getInstance("EC")
                kpg.initialize(ECGenParameterSpec("secp256r1"))
                val kp = kpg.generateKeyPair()
                cachedPrivateKey = kp.private
                cachedPublicKeyBytes = kp.public.encoded
                fallbackStorage?.saveKeyPair(kp.private, kp.public.encoded)
                try {
                    Log.i(TAG, "Generated and persisted software fallback EC identity key pair")
                } catch (_: Throwable) {
                    println("KeyManager: Generated and persisted software fallback EC identity key pair")
                }
            } catch (e: Exception) {
                try {
                    Log.e(TAG, "Failed to generate software fallback key pair", e)
                } catch (_: Throwable) {
                    println("KeyManager: Failed to generate software fallback key pair: ${e.message}")
                }
            }
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

    /** Testing helper to clear in-memory cache and test retrieval / persistence cycle */
    fun clearCacheForTesting() {
        cachedPrivateKey = null
        cachedPublicKeyBytes = null
    }
}
