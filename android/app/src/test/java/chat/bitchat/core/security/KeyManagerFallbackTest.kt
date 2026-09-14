package chat.bitchat.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.PrivateKey

class KeyManagerFallbackTest {

    private class InMemoryFallbackStorage : FallbackKeyStorage {
        var storedKeyPair: Pair<PrivateKey, ByteArray>? = null
        var loadCalls = 0
        var saveCalls = 0

        override fun loadKeyPair(): Pair<PrivateKey, ByteArray>? {
            loadCalls++
            return storedKeyPair
        }

        override fun saveKeyPair(privateKey: PrivateKey, publicKeyBytes: ByteArray) {
            saveCalls++
            storedKeyPair = Pair(privateKey, publicKeyBytes)
        }
    }

    private lateinit var storage: InMemoryFallbackStorage

    @Before
    fun setup() {
        storage = InMemoryFallbackStorage()
        KeyManager.setFallbackStorage(storage)
        KeyManager.clearCacheForTesting()
    }

    @Test
    fun testFallbackKeyGeneratedAndPersisted() {
        val pubKey1 = KeyManager.getPublicKeyBytes()
        val privKey1 = KeyManager.getPrivateKey()

        assertNotNull(privKey1)
        assertTrue(pubKey1.isNotEmpty())
        assertEquals(1, storage.saveCalls)

        // Clear in-memory cache to simulate app process restart
        KeyManager.clearCacheForTesting()

        val pubKey2 = KeyManager.getPublicKeyBytes()
        val privKey2 = KeyManager.getPrivateKey()

        assertNotNull(privKey2)
        assertArrayEquals("Public key must persist and match after restart", pubKey1, pubKey2)
        assertEquals("Private key encoded bytes must match after restart", privKey1!!.encoded.toList(), privKey2!!.encoded.toList())
    }

    @Test
    fun testCorruptedStorageRecovery() {
        // Storage returns null or fails
        val failingStorage = object : FallbackKeyStorage {
            override fun loadKeyPair(): Pair<PrivateKey, ByteArray>? {
                throw RuntimeException("Corrupted disk block")
            }

            override fun saveKeyPair(privateKey: PrivateKey, publicKeyBytes: ByteArray) {
                // save ok
            }
        }

        KeyManager.setFallbackStorage(failingStorage)
        KeyManager.clearCacheForTesting()

        val pubKey = KeyManager.getPublicKeyBytes()
        val privKey = KeyManager.getPrivateKey()

        assertNotNull(privKey)
        assertTrue(pubKey.isNotEmpty())
    }
}
