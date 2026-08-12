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
        assertEquals("Hello World!", String(res2!!))
    }
}
