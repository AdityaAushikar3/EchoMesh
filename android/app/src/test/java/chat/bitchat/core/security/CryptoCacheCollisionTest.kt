package chat.bitchat.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class CryptoCacheCollisionTest {

    private fun generateEcKeyPair(): java.security.KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        return kpg.generateKeyPair()
    }

    @Test
    fun testCacheKeyCollisionResistance() {
        val kp1 = generateEcKeyPair()
        val kp2 = generateEcKeyPair()

        val pubKey1Bytes = kp1.public.encoded
        val pubKey2Bytes = kp2.public.encoded

        val key1Encoded = Base64.getEncoder().encodeToString(pubKey1Bytes)
        val key2Encoded = Base64.getEncoder().encodeToString(pubKey2Bytes)

        assertNotEquals("Different public keys must produce different cache keys", key1Encoded, key2Encoded)

        val key1EncodedDuplicate = Base64.getEncoder().encodeToString(pubKey1Bytes.clone())
        assertEquals("Identical public keys must produce identical cache keys", key1Encoded, key1EncodedDuplicate)
    }

    @Test
    fun testEcdhEncryptionAndDecryptionFlow() {
        val aliceKp = generateEcKeyPair()
        val bobKp = generateEcKeyPair()

        val plaintext = "Hello EchoMesh, E2EE verification!"

        // Alice encrypts for Bob
        val encrypted = CryptoUtil.encrypt(
            plaintext = plaintext,
            peerId = "bob_peer_id",
            myPrivateKey = aliceKp.private,
            theirPublicKeyBytes = bobKp.public.encoded
        )

        assertNotNull(encrypted)
        assertTrue(encrypted!!.startsWith(CryptoUtil.E2EE_PREFIX))

        // Bob decrypts from Alice
        val decrypted = CryptoUtil.decrypt(
            encryptedContent = encrypted,
            peerId = "alice_peer_id",
            myPrivateKey = bobKp.private,
            theirPublicKeyBytes = aliceKp.public.encoded
        )

        assertEquals("Decrypted message must exactly match plaintext", plaintext, decrypted)
    }
}
