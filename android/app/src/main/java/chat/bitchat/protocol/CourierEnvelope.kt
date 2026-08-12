package chat.bitchat.protocol

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class CourierEnvelope(
    val recipientTag: ByteArray,
    val expiry: Long, // Epoch milliseconds
    val ciphertext: ByteArray,
    val copies: Byte = 1,
    val prekeyID: Int? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CourierEnvelope

        if (!recipientTag.contentEquals(other.recipientTag)) return false
        if (expiry != other.expiry) return false
        if (!ciphertext.contentEquals(other.ciphertext)) return false
        if (copies != other.copies) return false
        if (prekeyID != other.prekeyID) return false

        return true
    }

    override fun hashCode(): Int {
        var result = recipientTag.contentHashCode()
        result = 31 * result + expiry.hashCode()
        result = 31 * result + ciphertext.contentHashCode()
        result = 31 * result + copies.hashCode()
        result = 31 * result + (prekeyID ?: 0)
        return result
    }

    fun encode(): ByteArray? {
        if (recipientTag.size != TAG_LENGTH) return null
        if (ciphertext.isEmpty() || ciphertext.size > MAX_CIPHERTEXT_BYTES) return null

        // Allocate a generous buffer
        val estimatedSize = 128 + ciphertext.size
        val buffer = ByteBuffer.allocate(estimatedSize)

        // Type-Length-Value (TLV) packing
        // recipientTag (type = 0x01)
        buffer.put(TLVType.RECIPIENT_TAG.value)
        buffer.putShort(recipientTag.size.toShort())
        buffer.put(recipientTag)

        // expiry (type = 0x02)
        buffer.put(TLVType.EXPIRY.value)
        buffer.putShort(8.toShort())
        buffer.putLong(expiry)

        // ciphertext (type = 0x03)
        buffer.put(TLVType.CIPHERTEXT.value)
        buffer.putShort(ciphertext.size.toShort())
        buffer.put(ciphertext)

        // copies (type = 0x04) (omitted if copies is 1)
        if (copies > 1) {
            buffer.put(TLVType.COPIES.value)
            buffer.putShort(1.toShort())
            buffer.put(copies)
        }

        // prekeyID (type = 0x05)
        if (prekeyID != null) {
            buffer.put(TLVType.PREKEY_ID.value)
            buffer.putShort(4.toShort())
            buffer.putInt(prekeyID)
        }

        val result = ByteArray(buffer.position())
        buffer.flip()
        buffer.get(result)
        return result
    }

    companion object {
        const val TAG_LENGTH = 16
        const val MAX_CIPHERTEXT_BYTES = 16 * 1024
        private val TAG_CONTEXT = "bitchat-courier-tag-v1".toByteArray(StandardCharsets.UTF_8)

        private enum class TLVType(val value: Byte) {
            RECIPIENT_TAG(0x01),
            EXPIRY(0x02),
            CIPHERTEXT(0x03),
            COPIES(0x04),
            PREKEY_ID(0x05);

            companion object {
                fun fromByte(value: Byte): TLVType? {
                    return values().firstOrNull { it.value == value }
                }
            }
        }

        fun decode(data: ByteArray): CourierEnvelope? {
            val buffer = ByteBuffer.wrap(data)
            var recipientTag: ByteArray? = null
            var expiry: Long? = null
            var ciphertext: ByteArray? = null
            var copies: Byte = 1
            var prekeyID: Int? = null

            try {
                while (buffer.hasRemaining()) {
                    val typeRaw = buffer.get()
                    if (buffer.remaining() < 2) return null
                    val length = buffer.getShort().toInt() and 0xFFFF
                    if (buffer.remaining() < length) return null

                    val valueBytes = ByteArray(length)
                    buffer.get(valueBytes)

                    val type = TLVType.fromByte(typeRaw) ?: continue // skip unknown TLV types
                    when (type) {
                        TLVType.RECIPIENT_TAG -> {
                            if (length != TAG_LENGTH) return null
                            recipientTag = valueBytes
                        }
                        TLVType.EXPIRY -> {
                            if (length != 8) return null
                            expiry = ByteBuffer.wrap(valueBytes).long
                        }
                        TLVType.CIPHERTEXT -> {
                            if (length == 0 || length > MAX_CIPHERTEXT_BYTES) return null
                            ciphertext = valueBytes
                        }
                        TLVType.COPIES -> {
                            if (length != 1) return null
                            copies = valueBytes[0]
                        }
                        TLVType.PREKEY_ID -> {
                            if (length != 4) return null
                            prekeyID = ByteBuffer.wrap(valueBytes).int
                        }
                    }
                }
            } catch (e: Exception) {
                return null
            }

            if (recipientTag == null || expiry == null || ciphertext == null) {
                return null
            }

            return CourierEnvelope(recipientTag, expiry, ciphertext, copies, prekeyID)
        }

        fun epochDay(timeMillis: Long): Int {
            return (timeMillis / 1000 / 86400).toInt()
        }

        fun recipientTag(noiseStaticKey: ByteArray, epochDay: Int): ByteArray {
            val message = ByteBuffer.allocate(TAG_CONTEXT.size + 4)
            message.put(TAG_CONTEXT)
            message.putInt(epochDay)

            val mac = Mac.getInstance("HmacSHA256")
            val secretKey = SecretKeySpec(noiseStaticKey, "HmacSHA256")
            mac.init(secretKey)
            val hash = mac.doFinal(message.array())
            return hash.copyOf(TAG_LENGTH)
        }

        fun candidateTags(noiseStaticKey: ByteArray, timeMillis: Long): List<ByteArray> {
            val day = epochDay(timeMillis)
            val days = listOf(if (day == 0) 0 else day - 1, day, day + 1)
            return days.map { recipientTag(noiseStaticKey, it) }
        }
    }
}
