package chat.bitchat.core.bluetooth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionTieBreakerTest {

    @Test
    fun testLexicographicallyLargerInitiatesConnection() {
        val myId = "dev_200"
        val peerId = "dev_100"

        // dev_200 > dev_100 -> my device initiates
        assertTrue(ConnectionTieBreaker.shouldInitiateConnection(myId, peerId))

        // dev_100 < dev_200 -> my device does NOT initiate (waits for dev_200)
        assertFalse(ConnectionTieBreaker.shouldInitiateConnection(peerId, myId))
    }

    @Test
    fun testIdenticalIdentitiesAllowsConnection() {
        val id = "dev_123"
        assertTrue(ConnectionTieBreaker.shouldInitiateConnection(id, id))
    }

    @Test
    fun testMissingOrLegacyIdentitiesAllowsConnection() {
        val myId = "dev_500"

        // Missing peer identity
        assertTrue(ConnectionTieBreaker.shouldInitiateConnection(myId, null))
        assertTrue(ConnectionTieBreaker.shouldInitiateConnection(myId, ""))

        // Legacy peer without dev_* prefix
        val legacyPeer = "AA:BB:CC:DD:EE:FF"
        assertTrue(ConnectionTieBreaker.shouldInitiateConnection(myId, legacyPeer))

        // Legacy local identity without dev_* prefix
        assertTrue(ConnectionTieBreaker.shouldInitiateConnection("AA:BB:CC:DD:EE:FF", "dev_500"))
    }
}
