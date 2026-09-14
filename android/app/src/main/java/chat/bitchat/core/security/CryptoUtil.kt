package chat.bitchat.core.security

import android.util.Log
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtil {
    private const val TAG = "CryptoUtil"
    const val E2EE_PREFIX = "[e2ee]"
    private const val IV_SIZE_BYTES = 12
    private const val TAG_SIZE_BITS = 128

    private val sharedSecretCache = ConcurrentHashMap<String, SecretKey>()
    private val legacySecretCache = ConcurrentHashMap<String, SecretKey>()

    fun getSharedSecret(peerId: String, myPrivateKey: PrivateKey, theirPublicKeyBytes: ByteArray): SecretKey? {
        val cacheKey = Base64.getEncoder().encodeToString(theirPublicKeyBytes)
        val cached = sharedSecretCache[cacheKey]
        if (cached != null) return cached

        return try {
            val keyFactory = KeyFactory.getInstance("EC")
            val theirPublicKey: PublicKey = keyFactory.generatePublic(X509EncodedKeySpec(theirPublicKeyBytes))

            val keyAgreement = KeyAgreement.getInstance("ECDH")
            keyAgreement.init(myPrivateKey)
            keyAgreement.doPhase(theirPublicKey, true)

            val rawSharedSecret = keyAgreement.generateSecret()
            val aesKeyBytes = MessageDigest.getInstance("SHA-256").digest(rawSharedSecret)
            val secretKey = SecretKeySpec(aesKeyBytes, "AES")
            sharedSecretCache[cacheKey] = secretKey
            secretKey
        } catch (e: Exception) {
            try {
                Log.e(TAG, "Failed to derive ECDH shared secret for $peerId", e)
            } catch (_: Throwable) {
                println("CryptoUtil: Failed to derive ECDH shared secret for $peerId: ${e.message}")
            }
            null
        }
    }

    fun getLegacySharedSecret(peerId: String, myPrivateKey: PrivateKey, theirPublicKeyBytes: ByteArray): SecretKey? {
        val cacheKey = Base64.getEncoder().encodeToString(theirPublicKeyBytes)
        val cached = legacySecretCache[cacheKey]
        if (cached != null) return cached

        return try {
            val keyFactory = KeyFactory.getInstance("EC")
            val theirPublicKey: PublicKey = keyFactory.generatePublic(X509EncodedKeySpec(theirPublicKeyBytes))

            val keyAgreement = KeyAgreement.getInstance("ECDH")
            keyAgreement.init(myPrivateKey)
            keyAgreement.doPhase(theirPublicKey, true)

            val rawSharedSecret = keyAgreement.generateSecret()
            val aesKeyBytes = rawSharedSecret.copyOf(32)
            val secretKey = SecretKeySpec(aesKeyBytes, "AES")
            legacySecretCache[cacheKey] = secretKey
            secretKey
        } catch (e: Exception) {
            try {
                Log.e(TAG, "Failed to derive legacy ECDH shared secret for $peerId", e)
            } catch (_: Throwable) {
                println("CryptoUtil: Failed to derive legacy ECDH shared secret for $peerId: ${e.message}")
            }
            null
        }
    }

    fun encrypt(plaintext: String, peerId: String, myPrivateKey: PrivateKey, theirPublicKeyBytes: ByteArray): String? {
        if (plaintext.isBlank() || theirPublicKeyBytes.isEmpty()) return null
        return try {
            val secretKey = getSharedSecret(peerId, myPrivateKey, theirPublicKeyBytes) ?: return null

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

            val buffer = ByteBuffer.allocate(iv.size + ciphertext.size)
            buffer.put(iv)
            buffer.put(ciphertext)

            val encoded = Base64.getEncoder().encodeToString(buffer.array())
            "$E2EE_PREFIX$encoded"
        } catch (e: Exception) {
            try {
                Log.e(TAG, "AES-GCM encryption failed for peer $peerId", e)
            } catch (_: Throwable) {
                println("CryptoUtil: AES-GCM encryption failed for peer $peerId: ${e.message}")
            }
            null
        }
    }

    fun decrypt(encryptedContent: String, peerId: String, myPrivateKey: PrivateKey, theirPublicKeyBytes: ByteArray): String? {
        if (!encryptedContent.startsWith(E2EE_PREFIX) || theirPublicKeyBytes.isEmpty()) return null
        return try {
            val rawBase64 = encryptedContent.removePrefix(E2EE_PREFIX)
            val data = Base64.getDecoder().decode(rawBase64)

            if (data.size < IV_SIZE_BYTES + 16) return null // Must contain IV (12B) + auth tag (16B)

            val buffer = ByteBuffer.wrap(data)
            val iv = ByteArray(IV_SIZE_BYTES)
            buffer.get(iv)

            val ciphertext = ByteArray(buffer.remaining())
            buffer.get(ciphertext)

            // 1. Attempt standard SHA-256 derived key
            val standardKey = getSharedSecret(peerId, myPrivateKey, theirPublicKeyBytes)
            if (standardKey != null) {
                try {
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, standardKey, GCMParameterSpec(TAG_SIZE_BITS, iv))
                    val plaintextBytes = cipher.doFinal(ciphertext)
                    return String(plaintextBytes, Charsets.UTF_8)
                } catch (_: Exception) {
                    // Fall through to legacy key attempt if auth tag check fails
                }
            }

            // 2. Fallback to legacy copyOf(32) derived key for backward compatibility with older builds
            val legacyKey = getLegacySharedSecret(peerId, myPrivateKey, theirPublicKeyBytes)
            if (legacyKey != null) {
                try {
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, legacyKey, GCMParameterSpec(TAG_SIZE_BITS, iv))
                    val plaintextBytes = cipher.doFinal(ciphertext)
                    return String(plaintextBytes, Charsets.UTF_8)
                } catch (_: Exception) {
                    // Both standard and legacy failed
                }
            }

            null
        } catch (e: Exception) {
            try {
                Log.e(TAG, "AES-GCM decryption failed for peer $peerId", e)
            } catch (_: Throwable) {
                println("CryptoUtil: AES-GCM decryption failed for peer $peerId: ${e.message}")
            }
            null
        }
    }
}
