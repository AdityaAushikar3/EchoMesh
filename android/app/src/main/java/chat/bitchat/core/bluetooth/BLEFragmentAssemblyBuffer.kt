package chat.bitchat.core.bluetooth

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class BLEFragmentAssemblyBuffer(
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {
    private val assemblyMap = mutableMapOf<String, AssemblySession>()

    data class AssemblySession(
        val fragmentId: String,
        val total: Int,
        val originalType: Byte,
        val fragments: MutableMap<Int, ByteArray> = mutableMapOf(),
        val createdAt: Long = System.currentTimeMillis(),
        var lastActiveAt: Long = System.currentTimeMillis()
    )

    data class CompleteMessage(
        val type: Byte,
        val data: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as CompleteMessage
            if (type != other.type) return false
            if (!data.contentEquals(other.data)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = type.toInt()
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    @Synchronized
    fun addFragment(
        fragmentId: ByteArray,
        index: Int,
        total: Int,
        originalType: Byte,
        data: ByteArray
    ): CompleteMessage? {
        if (index < 0 || index >= total || total <= 0 || total > MAX_FRAGMENTS) {
            return null
        }

        val idHex = fragmentId.joinToString("") { String.format("%02x", it) }
        
        // Clean up stale sessions (> 30 seconds of inactivity)
        val now = timeProvider()
        assemblyMap.entries.removeIf { now - it.value.lastActiveAt > 30000 }

        if (!assemblyMap.containsKey(idHex) && assemblyMap.size >= MAX_SESSIONS) {
            val oldestKey = assemblyMap.entries.minByOrNull { it.value.lastActiveAt }?.key
            if (oldestKey != null) {
                assemblyMap.remove(oldestKey)
            }
        }

        val session = assemblyMap.getOrPut(idHex) {
            AssemblySession(idHex, total, originalType, createdAt = now, lastActiveAt = now)
        }

        if (session.total != total || session.originalType != originalType) {
            return null
        }

        session.fragments[index] = data
        session.lastActiveAt = now

        if (session.fragments.size == total) {
            val baos = ByteArrayOutputStream()
            for (i in 0 until total) {
                val chunk = session.fragments[i] ?: return null // Missing fragment
                baos.write(chunk)
            }
            assemblyMap.remove(idHex)
            return CompleteMessage(session.originalType, baos.toByteArray())
        }
        return null
    }

    companion object {
        const val MAX_FRAGMENTS = 10000
        const val MAX_SESSIONS = 50

        fun unpackFragmentPayload(payload: ByteArray): FragmentData? {
            if (payload.size < 13) return null
            val buffer = ByteBuffer.wrap(payload).order(java.nio.ByteOrder.BIG_ENDIAN)
            
            val fragmentId = ByteArray(8)
            buffer.get(fragmentId)
            
            val index = buffer.getShort().toInt() and 0xFFFF
            val total = buffer.getShort().toInt() and 0xFFFF
            val originalType = buffer.get()
            
            val data = ByteArray(buffer.remaining())
            buffer.get(data)
            
            return FragmentData(fragmentId, index, total, originalType, data)
        }
    }

    data class FragmentData(
        val fragmentId: ByteArray,
        val index: Int,
        val total: Int,
        val originalType: Byte,
        val data: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as FragmentData
            if (!fragmentId.contentEquals(other.fragmentId)) return false
            if (index != other.index) return false
            if (total != other.total) return false
            if (originalType != other.originalType) return false
            if (!data.contentEquals(other.data)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = fragmentId.contentHashCode()
            result = 31 * result + index
            result = 31 * result + total
            result = 31 * result + originalType.toInt()
            result = 31 * result + data.contentHashCode()
            return result
        }
    }
}
