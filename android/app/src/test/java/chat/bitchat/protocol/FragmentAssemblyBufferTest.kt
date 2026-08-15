package chat.bitchat.protocol

import chat.bitchat.core.bluetooth.BLEFragmentAssemblyBuffer
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class FragmentAssemblyBufferTest {

    @Test
    fun testFragmentUnpackingAndAssembly() {
        val originalType: Byte = 0x02 // Message type
        val fragmentId = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val dataPart1 = "Hello ".toByteArray()
        val dataPart2 = "World!".toByteArray()

        // Construct fragment 1 payload
        val p1 = ByteBuffer.allocate(13 + dataPart1.size).apply {
            put(fragmentId)
            putShort(0) // index 0
            putShort(2) // total 2
            put(originalType)
            put(dataPart1)
        }.array()

        // Construct fragment 2 payload
        val p2 = ByteBuffer.allocate(13 + dataPart2.size).apply {
            put(fragmentId)
            putShort(1) // index 1
            putShort(2) // total 2
            put(originalType)
            put(dataPart2)
        }.array()

        // Unpack checks
        val f1 = BLEFragmentAssemblyBuffer.unpackFragmentPayload(p1)
        assertNotNull(f1)
        assertArrayEquals(fragmentId, f1?.fragmentId)
        assertEquals(0, f1?.index)
        assertEquals(2, f1?.total)
        assertEquals(originalType, f1?.originalType)
        assertArrayEquals(dataPart1, f1?.data)

        val f2 = BLEFragmentAssemblyBuffer.unpackFragmentPayload(p2)
        assertNotNull(f2)
        assertArrayEquals(fragmentId, f2?.fragmentId)
        assertEquals(1, f2?.index)
        assertEquals(2, f2?.total)
        assertEquals(originalType, f2?.originalType)
        assertArrayEquals(dataPart2, f2?.data)

        // Assembly checks
        val buffer = BLEFragmentAssemblyBuffer()
        
        // Add part 1
        val res1 = buffer.addFragment(f1!!.fragmentId, f1.index, f1.total, f1.originalType, f1.data)
        assertNull(res1) // Not complete yet

        // Add part 2
        val res2 = buffer.addFragment(f2!!.fragmentId, f2.index, f2.total, f2.originalType, f2.data)
        assertNotNull(res2)
        assertEquals("Hello World!", String(res2!!.data))
        assertEquals(originalType, res2.type)
    }

    @Test
    fun testOutOfBoundsIndex() {
        val buffer = BLEFragmentAssemblyBuffer()
        val fragmentId = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val data = "test".toByteArray()
        val originalType: Byte = 0x01

        // Negative index
        assertNull(buffer.addFragment(fragmentId, -1, 3, originalType, data))
        
        // Index >= total
        assertNull(buffer.addFragment(fragmentId, 3, 3, originalType, data))
        assertNull(buffer.addFragment(fragmentId, 4, 3, originalType, data))

        // Total <= 0
        assertNull(buffer.addFragment(fragmentId, 0, 0, originalType, data))
        assertNull(buffer.addFragment(fragmentId, 0, -5, originalType, data))

        // Total > MAX_FRAGMENTS (10000)
        assertNull(buffer.addFragment(fragmentId, 0, 10001, originalType, data))
    }

    @Test
    fun testPrematureSessionDeletionOnFailure() {
        val buffer = BLEFragmentAssemblyBuffer()
        val fragmentId = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val originalType: Byte = 0x02
        
        // 1. Add valid part 0
        assertNull(buffer.addFragment(fragmentId, 0, 3, originalType, "Part0".toByteArray()))

        // 2. Add an invalid out-of-bounds fragment (index 5, total 3) - should be rejected and return null
        assertNull(buffer.addFragment(fragmentId, 5, 3, originalType, "InvalidPart".toByteArray()))

        // 3. Add valid part 1 - should still succeed (returning null because not complete yet)
        assertNull(buffer.addFragment(fragmentId, 1, 3, originalType, "Part1".toByteArray()))

        // 4. Add valid part 2 - should successfully assemble!
        val res = buffer.addFragment(fragmentId, 2, 3, originalType, "Part2".toByteArray())
        assertNotNull(res)
        assertEquals("Part0Part1Part2", String(res!!.data))
        assertEquals(originalType, res.type)
    }

    @Test
    fun testSessionInactivityTimeout() {
        var currentTime = 1000L
        val buffer = BLEFragmentAssemblyBuffer(timeProvider = { currentTime })
        val fragmentId = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val originalType: Byte = 0x02

        // 1. Add fragment at t = 1000ms
        assertNull(buffer.addFragment(fragmentId, 0, 3, originalType, "Part0".toByteArray()))

        // 2. Advance time by 15 seconds (t = 16000ms) and add fragment 1.
        // This should refresh lastActiveAt.
        currentTime += 15000
        assertNull(buffer.addFragment(fragmentId, 1, 3, originalType, "Part1".toByteArray()))

        // 3. Advance time by 20 seconds (t = 36000ms).
        // Since lastActiveAt was refreshed at t=16000ms, the inactivity is 20s (<= 30s), so the session is NOT evicted.
        // Let's add part 2 and it should complete successfully!
        currentTime += 20000
        val res = buffer.addFragment(fragmentId, 2, 3, originalType, "Part2".toByteArray())
        assertNotNull(res)
        assertEquals("Part0Part1Part2", String(res!!.data))

        // 4. Test eviction: start a new session at t=36000ms
        val fragmentId2 = byteArrayOf(8, 7, 6, 5, 4, 3, 2, 1)
        assertNull(buffer.addFragment(fragmentId2, 0, 2, originalType, "Part0".toByteArray()))

        // Advance time by 31 seconds (t = 67000ms).
        // The session for fragmentId2 should be evicted due to inactivity (>30 seconds).
        currentTime += 31000
        // When we add fragment 1, the session has been evicted, so it creates a new session.
        // Since it only has fragment 1 (fragment 0 is lost), it returns null and is not complete.
        assertNull(buffer.addFragment(fragmentId2, 1, 2, originalType, "Part1".toByteArray()))
    }

    @Test
    fun testSessionCapacityLimit() {
        var currentTime = 1000L
        val buffer = BLEFragmentAssemblyBuffer(timeProvider = { currentTime })
        
        // We will create 50 sessions (indices 0 to 49) at different active times.
        // Let's stagger their lastActiveAt times.
        val sessions = (0 until 50).map { i ->
            byteArrayOf(0, 0, 0, 0, 0, 0, 0, i.toByte())
        }

        for (i in 0 until 50) {
            currentTime += 100 // Stagger times
            assertNull(buffer.addFragment(sessions[i], 0, 2, 0x01, "Part0_$i".toByteArray()))
        }

        // The oldest session is sessions[0], created at t = 1100.
        // Let's add a 51st session. This should evict the oldest session (sessions[0]).
        val newSessionId = byteArrayOf(9, 9, 9, 9, 9, 9, 9, 9)
        currentTime += 100
        assertNull(buffer.addFragment(newSessionId, 0, 2, 0x01, "NewPart0".toByteArray()))

        // But adding fragment 1 for sessions[1] (which was NOT the oldest and shouldn't be evicted) should complete successfully!
        val res1 = buffer.addFragment(sessions[1], 1, 2, 0x01, "Part1_1".toByteArray())
        assertNotNull(res1)
        assertEquals("Part0_1Part1_1", String(res1!!.data))

        // Since sessions[0] was evicted, adding fragment 1 for sessions[0] should not complete it (as it creates a new session missing fragment 0).
        assertNull(buffer.addFragment(sessions[0], 1, 2, 0x01, "Part1_0".toByteArray()))
    }
}
