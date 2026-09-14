package chat.bitchat.core.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BLEFragmentAssemblyBufferThrottlingTest {

    @Test
    fun testThrottledCleanupAndCompleteAssembly() {
        var currentTime = 1000L
        val buffer = BLEFragmentAssemblyBuffer(timeProvider = { currentTime })

        val fragmentId = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val chunk1 = "Hello ".toByteArray(Charsets.UTF_8)
        val chunk2 = "World!".toByteArray(Charsets.UTF_8)

        // 1. Add chunk 1 at T=1000ms
        val res1 = buffer.addFragment(fragmentId, 0, 2, 0x01.toByte(), chunk1)
        assertNull(res1)

        // 2. Add chunk 2 at T=2000ms (within 30-sec expiry window, throttled cleanup should not prune active)
        currentTime = 2000L
        val res2 = buffer.addFragment(fragmentId, 1, 2, 0x01.toByte(), chunk2)
        assertNotNull(res2)
        assertEquals(0x01.toByte(), res2!!.type)
        assertEquals("Hello World!", String(res2.data, Charsets.UTF_8))
    }

    @Test
    fun testStaleSessionEventuallyPruned() {
        var currentTime = 1000L
        val buffer = BLEFragmentAssemblyBuffer(timeProvider = { currentTime })

        val staleFragmentId = byteArrayOf(1, 1, 1, 1, 1, 1, 1, 1)
        buffer.addFragment(staleFragmentId, 0, 2, 0x01.toByte(), "Incomplete".toByteArray(Charsets.UTF_8))

        // Fast-forward 35 seconds (beyond 30s expiry and beyond 10s throttle interval)
        currentTime = 36_000L

        // Incoming fragment for another message triggers throttled cleanup
        val newFragmentId = byteArrayOf(2, 2, 2, 2, 2, 2, 2, 2)
        buffer.addFragment(newFragmentId, 0, 1, 0x01.toByte(), "Single".toByteArray(Charsets.UTF_8))

        // Try adding remaining chunk of stale session at T=37_000
        currentTime = 37_000L
        val staleChunk2 = buffer.addFragment(staleFragmentId, 1, 2, 0x01.toByte(), " Chunk".toByteArray(Charsets.UTF_8))

        // Since chunk 0 was pruned due to >30s inactivity, chunk 1 alone cannot complete the message
        assertNull("Stale session was pruned so adding chunk 1 alone cannot complete it", staleChunk2)
    }
}
