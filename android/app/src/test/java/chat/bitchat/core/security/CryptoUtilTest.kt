package chat.bitchat.core.security

import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import javax.crypto.Cipher

class CryptoUtilTest {

    private fun generateTestECKeyPair(): java.security.KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        return kpg.generateKeyPair()
    }

    @Test
    fun testBidirectionalECDHAndAesGcmEncryption() {
        val deviceA = generateTestECKeyPair()
        val deviceB = generateTestECKeyPair()

        val pubKeyA = deviceA.public.encoded
        val pubKeyB = deviceB.public.encoded

        val messageAtoB = "Hello Device B! This is an off-grid encrypted mesh message."
        val encryptedAtoB = CryptoUtil.encrypt(
            plaintext = messageAtoB,
            peerId = "dev_b",
            myPrivateKey = deviceA.private,
            theirPublicKeyBytes = pubKeyB
        )

        assertNotNull("Ciphertext from A to B should not be null", encryptedAtoB)
        assertTrue("Ciphertext must start with [e2ee] prefix", encryptedAtoB!!.startsWith(CryptoUtil.E2EE_PREFIX))

        val decryptedByB = CryptoUtil.decrypt(
            encryptedContent = encryptedAtoB,
            peerId = "dev_a",
            myPrivateKey = deviceB.private,
            theirPublicKeyBytes = pubKeyA
        )

        assertEquals("Device B should successfully decrypt Device A's message", messageAtoB, decryptedByB)

        val messageBtoA = "Acknowledged Device A! Secure mesh channel established."
        val encryptedBtoA = CryptoUtil.encrypt(
            plaintext = messageBtoA,
            peerId = "dev_a",
            myPrivateKey = deviceB.private,
            theirPublicKeyBytes = pubKeyA
        )

        assertNotNull("Ciphertext from B to A should not be null", encryptedBtoA)

        val decryptedByA = CryptoUtil.decrypt(
            encryptedContent = encryptedBtoA!!,
            peerId = "dev_b",
            myPrivateKey = deviceA.private,
            theirPublicKeyBytes = pubKeyB
        )

        assertEquals("Device A should successfully decrypt Device B's message", messageBtoA, decryptedByA)
    }

    @Test
    fun testShortAndLongAndUnicodeMessages() {
        val deviceA = generateTestECKeyPair()
        val deviceB = generateTestECKeyPair()

        // 1. Short message
        val shortMsg = "Hi"
        val encShort = CryptoUtil.encrypt(shortMsg, "dev_b", deviceA.private, deviceB.public.encoded)!!
        val decShort = CryptoUtil.decrypt(encShort, "dev_a", deviceB.private, deviceA.public.encoded)
        assertEquals("Short message decryption failed", shortMsg, decShort)

        // 2. Unicode and emoji
        val unicodeMsg = "👋🌍 EchoMesh 🚀 🔐 こんにちは — Test with special chars & emojis 🚀"
        val encUnicode = CryptoUtil.encrypt(unicodeMsg, "dev_b", deviceA.private, deviceB.public.encoded)!!
        val decUnicode = CryptoUtil.decrypt(encUnicode, "dev_a", deviceB.private, deviceA.public.encoded)
        assertEquals("Unicode message decryption failed", unicodeMsg, decUnicode)

        // 3. Long message (> 10,000 characters)
        val longMsg = "EchoMesh packet test ".repeat(600)
        val encLong = CryptoUtil.encrypt(longMsg, "dev_b", deviceA.private, deviceB.public.encoded)!!
        val decLong = CryptoUtil.decrypt(encLong, "dev_a", deviceB.private, deviceA.public.encoded)
        assertEquals("Long message decryption failed", longMsg, decLong)
    }

    @Test
    fun testEmptyAndBlankInputs() {
        val deviceA = generateTestECKeyPair()
        val deviceB = generateTestECKeyPair()

        assertNull("Blank plaintext should return null", CryptoUtil.encrypt("", "dev_b", deviceA.private, deviceB.public.encoded))
        assertNull("Whitespace plaintext should return null", CryptoUtil.encrypt("   ", "dev_b", deviceA.private, deviceB.public.encoded))
        assertNull("Empty public key should return null", CryptoUtil.encrypt("Valid message", "dev_b", deviceA.private, ByteArray(0)))
        assertNull("Malformed encrypted prefix should return null", CryptoUtil.decrypt("Not an encrypted message", "dev_a", deviceB.private, deviceA.public.encoded))
    }

    @Test
    fun testWrongPeerKeyRejection() {
        val deviceA = generateTestECKeyPair()
        val deviceB = generateTestECKeyPair()
        val deviceC = generateTestECKeyPair() // Attacker / eavesdropper

        val message = "Confidential node-to-node exchange"
        val encrypted = CryptoUtil.encrypt(message, "dev_b", deviceA.private, deviceB.public.encoded)!!

        // Device C attempts to decrypt using Device C's private key
        val decryptedByC = CryptoUtil.decrypt(encrypted, "dev_a", deviceC.private, deviceA.public.encoded)
        assertNull("Third-party node with different key must fail AES-GCM decryption", decryptedByC)
    }

    @Test
    fun testTamperedCiphertextRejection() {
        val deviceA = generateTestECKeyPair()
        val deviceB = generateTestECKeyPair()

        val encrypted = CryptoUtil.encrypt(
            plaintext = "Top secret mesh routing payload",
            peerId = "dev_b",
            myPrivateKey = deviceA.private,
            theirPublicKeyBytes = deviceB.public.encoded
        )!!

        val rawB64 = encrypted.removePrefix(CryptoUtil.E2EE_PREFIX)
        val rawBytes = Base64.getDecoder().decode(rawB64)
        // Flip one bit in the ciphertext / auth tag
        rawBytes[rawBytes.size - 1] = (rawBytes[rawBytes.size - 1].toInt() xor 0x01).toByte()

        val tamperedEncrypted = CryptoUtil.E2EE_PREFIX + Base64.getEncoder().encodeToString(rawBytes)

        val decrypted = CryptoUtil.decrypt(
            encryptedContent = tamperedEncrypted,
            peerId = "dev_a",
            myPrivateKey = deviceB.private,
            theirPublicKeyBytes = deviceA.public.encoded
        )

        assertNull("Tampered ciphertext must be rejected by AES-GCM authentication tag", decrypted)
    }

    @Test
    fun testLegacyCiphertextBackwardCompatibility() {
        val deviceA = generateTestECKeyPair()
        val deviceB = generateTestECKeyPair()

        val legacySecretKey = CryptoUtil.getLegacySharedSecret("dev_b", deviceA.private, deviceB.public.encoded)
        assertNotNull(legacySecretKey)

        val plaintext = "Legacy message encrypted by previous build"
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, legacySecretKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val buffer = java.nio.ByteBuffer.allocate(iv.size + ciphertext.size)
        buffer.put(iv)
        buffer.put(ciphertext)
        val legacyEncrypted = CryptoUtil.E2EE_PREFIX + Base64.getEncoder().encodeToString(buffer.array())

        val decrypted = CryptoUtil.decrypt(
            encryptedContent = legacyEncrypted,
            peerId = "dev_a",
            myPrivateKey = deviceB.private,
            theirPublicKeyBytes = deviceA.public.encoded
        )

        assertEquals("CryptoUtil.decrypt should seamlessly fall back and decrypt legacy ciphertexts", plaintext, decrypted)
    }
}
