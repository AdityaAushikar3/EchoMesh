package chat.bitchat.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface FallbackKeyStorage {
    fun loadKeyPair(): Pair<PrivateKey, ByteArray>?
    fun saveKeyPair(privateKey: PrivateKey, publicKeyBytes: ByteArray)
}

class AndroidKeyStoreEncryptedStorage(private val context: Context) : FallbackKeyStorage {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "FallbackKeyStorage"
        private const val PREFS_NAME = "echo_mesh_fallback_vault"
        private const val KEY_ENTRY = "enc_ec_keypair"
        private const val AES_WRAPPER_ALIAS = "echo_mesh_fallback_aes"
        private const val IV_SIZE = 12
        private const val TAG_BITS = 128
    }

    private fun getOrCreateAesWrapperKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!keyStore.containsAlias(AES_WRAPPER_ALIAS)) {
                val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                keyGen.init(
                    KeyGenParameterSpec.Builder(
                        AES_WRAPPER_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build()
                )
                keyGen.generateKey()
            }
            keyStore.getKey(AES_WRAPPER_ALIAS, null) as? SecretKey
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get or create AES wrapper in AndroidKeyStore", e)
            null
        }
    }

    override fun loadKeyPair(): Pair<PrivateKey, ByteArray>? {
        val encryptedBase64 = prefs.getString(KEY_ENTRY, null) ?: return null
        return try {
            val raw = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            if (raw.size < IV_SIZE + 16) return null

            val iv = raw.copyOfRange(0, IV_SIZE)
            val ciphertext = raw.copyOfRange(IV_SIZE, raw.size)

            val wrapperKey = getOrCreateAesWrapperKey() ?: return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, wrapperKey, GCMParameterSpec(TAG_BITS, iv))
            val plaintext = cipher.doFinal(ciphertext)

            val buffer = ByteBuffer.wrap(plaintext)
            val privLen = buffer.int
            if (privLen <= 0 || privLen > buffer.remaining()) return null
            val privBytes = ByteArray(privLen)
            buffer.get(privBytes)

            val pubLen = buffer.int
            if (pubLen <= 0 || pubLen > buffer.remaining()) return null
            val pubBytes = ByteArray(pubLen)
            buffer.get(pubBytes)

            val keyFactory = KeyFactory.getInstance("EC")
            val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privBytes))
            Pair(privateKey, pubBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load/decrypt persisted software fallback key pair", e)
            null
        }
    }

    override fun saveKeyPair(privateKey: PrivateKey, publicKeyBytes: ByteArray) {
        try {
            val privBytes = privateKey.encoded ?: return
            val totalSize = 4 + privBytes.size + 4 + publicKeyBytes.size
            val buffer = ByteBuffer.allocate(totalSize)
            buffer.putInt(privBytes.size)
            buffer.put(privBytes)
            buffer.putInt(publicKeyBytes.size)
            buffer.put(publicKeyBytes)
            val plaintext = buffer.array()

            val wrapperKey = getOrCreateAesWrapperKey() ?: return
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, wrapperKey)
            val iv = cipher.iv ?: return
            val ciphertext = cipher.doFinal(plaintext)

            val combined = ByteArray(iv.size + ciphertext.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)

            val encoded = Base64.encodeToString(combined, Base64.NO_WRAP)
            prefs.edit().putString(KEY_ENTRY, encoded).apply()
            Log.i(TAG, "Persisted software fallback EC key pair securely")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save software fallback key pair", e)
        }
    }
}
