package chat.bitchat.protocol

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * Compact binary ACK payload.
 * Layout: [1-byte idLength][N-byte messageId UTF-8][8-byte timestamp]
 * Typical size: 1 + 36 (UUID) + 8 = 45 bytes — fits in one BLE MTU.
 */
data class DeliveryAckPayload(
    val messageId: String,
    val acknowledgedAt: Long
) {
    fun toBytes(): ByteArray {
        val idBytes = messageId.toByteArray(StandardCharsets.UTF_8)
        val len = minOf(idBytes.size, 255)
        val buffer = ByteBuffer.allocate(1 + len + 8)
        buffer.put(len.toByte())
        buffer.put(idBytes, 0, len)
        buffer.putLong(acknowledgedAt)
        return buffer.array()
    }

    companion object {
        fun fromBytes(data: ByteArray): DeliveryAckPayload? {
            if (data.size < 10) return null // minimum: 1 + 1-char id + 8
            val buffer = ByteBuffer.wrap(data)
            val idLen = buffer.get().toInt() and 0xFF
            if (buffer.remaining() < idLen + 8) return null
            val idBytes = ByteArray(idLen)
            buffer.get(idBytes)
            val timestamp = buffer.getLong()
            return DeliveryAckPayload(
                messageId = String(idBytes, StandardCharsets.UTF_8),
                acknowledgedAt = timestamp
            )
        }
    }
}
