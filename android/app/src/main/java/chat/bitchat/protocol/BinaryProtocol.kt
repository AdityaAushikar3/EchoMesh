package chat.bitchat.protocol

import java.nio.ByteBuffer

object BinaryProtocol {
    const val V1_HEADER_SIZE = 14
    const val V2_HEADER_SIZE = 16
    const val SENDER_ID_SIZE = 8
    const val RECIPIENT_ID_SIZE = 8
    const val SIGNATURE_SIZE = 64

    object Flags {
        const val HAS_RECIPIENT: Byte = 0x01
        const val HAS_SIGNATURE: Byte = 0x02
        const val IS_COMPRESSED: Byte = 0x04
        const val HAS_ROUTE: Byte = 0x08
        const val IS_RSR: Byte = 0x10
    }

    private fun lengthFieldSize(version: Byte): Int {
        return if (version.toInt() == 2) 4 else 2
    }

    fun headerSize(version: Byte): Int? {
        return when (version.toInt()) {
            1 -> V1_HEADER_SIZE
            2 -> V2_HEADER_SIZE
            else -> null
        }
    }

    fun encode(packet: BitchatPacket, padding: Boolean = true): ByteArray? {
        val version = packet.version
        if (version.toInt() != 1 && version.toInt() != 2) return null

        var payload = packet.payload
        var isCompressed = false
        var originalPayloadSize = 0
        if (CompressionUtil.shouldCompress(payload)) {
            val maxRepresentable = if (version.toInt() == 2) Int.MAX_VALUE else 65535
            if (payload.size <= maxRepresentable) {
                val compressed = CompressionUtil.compress(payload)
                if (compressed != null) {
                    originalPayloadSize = payload.size
                    payload = compressed
                    isCompressed = true
                }
            }
        }

        val lengthFieldBytes = lengthFieldSize(version)
        val originalRoute = if (version.toInt() >= 2) (packet.route ?: emptyList()) else emptyList()
        val sanitizedRoute = mutableListOf<ByteArray>()
        for (hop in originalRoute) {
            if (hop.isEmpty()) return null
            val sanitizedHop = ByteArray(SENDER_ID_SIZE)
            if (hop.size >= SENDER_ID_SIZE) {
                System.arraycopy(hop, 0, sanitizedHop, 0, SENDER_ID_SIZE)
            } else {
                System.arraycopy(hop, 0, sanitizedHop, 0, hop.size)
            }
            sanitizedRoute.add(sanitizedHop)
        }
        if (sanitizedRoute.size > 255) return null

        val hasRoute = sanitizedRoute.isNotEmpty()
        val routeLength = if (hasRoute) 1 + sanitizedRoute.size * SENDER_ID_SIZE else 0
        val originalSizeFieldBytes = if (isCompressed) lengthFieldBytes else 0
        val payloadDataSize = payload.size + originalSizeFieldBytes

        if (version.toInt() == 1 && payloadDataSize > 65535) return null

        val headerSize = headerSize(version) ?: return null
        val estimatedHeader = headerSize + SENDER_ID_SIZE + (if (packet.recipientID == null) 0 else RECIPIENT_ID_SIZE) + routeLength
        val estimatedPayload = payloadDataSize
        val estimatedSignature = if (packet.signature == null) 0 else SIGNATURE_SIZE
        
        val buffer = ByteBuffer.allocate(estimatedHeader + estimatedPayload + estimatedSignature + 256)
        
        buffer.put(version)
        buffer.put(packet.type)
        buffer.put(packet.ttl)
        buffer.putLong(packet.timestamp)

        var flags: Byte = 0
        if (packet.recipientID != null) flags = (flags.toInt() or Flags.HAS_RECIPIENT.toInt()).toByte()
        if (packet.signature != null) flags = (flags.toInt() or Flags.HAS_SIGNATURE.toInt()).toByte()
        if (isCompressed) flags = (flags.toInt() or Flags.IS_COMPRESSED.toInt()).toByte()
        if (hasRoute && version.toInt() >= 2) flags = (flags.toInt() or Flags.HAS_ROUTE.toInt()).toByte()
        if (packet.isRSR) flags = (flags.toInt() or Flags.IS_RSR.toInt()).toByte()
        buffer.put(flags)

        if (version.toInt() == 2) {
            buffer.putInt(payloadDataSize)
        } else {
            buffer.putShort(payloadDataSize.toShort())
        }

        // SenderID
        val senderBytes = ByteArray(SENDER_ID_SIZE)
        System.arraycopy(packet.senderID, 0, senderBytes, 0, minOf(packet.senderID.size, SENDER_ID_SIZE))
        buffer.put(senderBytes)

        // RecipientID
        if (packet.recipientID != null) {
            val recipientBytes = ByteArray(RECIPIENT_ID_SIZE)
            System.arraycopy(packet.recipientID, 0, recipientBytes, 0, minOf(packet.recipientID.size, RECIPIENT_ID_SIZE))
            buffer.put(recipientBytes)
        }

        // Route
        if (hasRoute) {
            buffer.put(sanitizedRoute.size.toByte())
            for (hop in sanitizedRoute) {
                buffer.put(hop)
            }
        }

        // Compressed prefix
        if (isCompressed) {
            if (version.toInt() == 2) {
                buffer.putInt(originalPayloadSize)
            } else {
                buffer.putShort(originalPayloadSize.toShort())
            }
        }

        buffer.put(payload)

        // Signature
        if (packet.signature != null) {
            val sigBytes = ByteArray(SIGNATURE_SIZE)
            System.arraycopy(packet.signature!!, 0, sigBytes, 0, minOf(packet.signature!!.size, SIGNATURE_SIZE))
            buffer.put(sigBytes)
        }

        val encoded = ByteArray(buffer.position())
        buffer.flip()
        buffer.get(encoded)

        if (padding) {
            val optimalSize = MessagePadding.optimalBlockSize(encoded.size)
            return MessagePadding.pad(encoded, optimalSize)
        }
        return encoded
    }

    fun decode(data: ByteArray): BitchatPacket? {
        val decoded = decodeCore(data)
        if (decoded != null) return decoded

        val unpadded = MessagePadding.unpad(data)
        if (unpadded.size == data.size) return null
        return decodeCore(unpadded)
    }

    private fun decodeCore(raw: ByteArray): BitchatPacket? {
        if (raw.size < V1_HEADER_SIZE + SENDER_ID_SIZE) return null

        val buffer = ByteBuffer.wrap(raw)
        val version = buffer.get()
        if (version.toInt() != 1 && version.toInt() != 2) return null

        val lengthFieldBytes = lengthFieldSize(version)
        val headerSize = headerSize(version) ?: return null
        val minimumRequired = headerSize + SENDER_ID_SIZE
        if (raw.size < minimumRequired) return null

        val type = buffer.get()
        val ttl = buffer.get()
        val timestamp = buffer.getLong()

        val flags = buffer.get().toInt()
        val hasRecipient = (flags and Flags.HAS_RECIPIENT.toInt()) != 0
        val hasSignature = (flags and Flags.HAS_SIGNATURE.toInt()) != 0
        val isCompressed = (flags and Flags.IS_COMPRESSED.toInt()) != 0
        val hasRoute = version.toInt() >= 2 && (flags and Flags.HAS_ROUTE.toInt()) != 0
        val isRSR = (flags and Flags.IS_RSR.toInt()) != 0

        val payloadLength = if (version.toInt() == 2) {
            buffer.getInt()
        } else {
            buffer.getShort().toInt() and 0xFFFF
        }

        if (payloadLength < 0) return null

        val senderID = ByteArray(SENDER_ID_SIZE)
        buffer.get(senderID)

        var recipientID: ByteArray? = null
        if (hasRecipient) {
            if (buffer.remaining() < RECIPIENT_ID_SIZE) return null
            recipientID = ByteArray(RECIPIENT_ID_SIZE)
            buffer.get(recipientID)
        }

        var route: MutableList<ByteArray>? = null
        if (hasRoute) {
            if (!buffer.hasRemaining()) return null
            val routeCount = buffer.get().toInt() and 0xFF
            if (routeCount > 0) {
                if (buffer.remaining() < routeCount * SENDER_ID_SIZE) return null
                route = mutableListOf()
                for (i in 0 until routeCount) {
                    val hop = ByteArray(SENDER_ID_SIZE)
                    buffer.get(hop)
                    route.add(hop)
                }
            }
        }

        val payload: ByteArray
        if (isCompressed) {
            if (payloadLength < lengthFieldBytes) return null
            val originalSize = if (version.toInt() == 2) {
                buffer.getInt()
            } else {
                buffer.getShort().toInt() and 0xFFFF
            }

            val compressedSize = payloadLength - lengthFieldBytes
            if (compressedSize <= 0 || buffer.remaining() < compressedSize) return null
            val compressed = ByteArray(compressedSize)
            buffer.get(compressed)

            val compressionRatio = originalSize.toDouble() / compressedSize.toDouble()
            if (compressionRatio > 50000.0) {
                return null
            }

            val decompressed = CompressionUtil.decompress(compressed, originalSize)
            if (decompressed == null || decompressed.size != originalSize) return null
            payload = decompressed
        } else {
            if (buffer.remaining() < payloadLength) return null
            payload = ByteArray(payloadLength)
            buffer.get(payload)
        }

        var signature: ByteArray? = null
        if (hasSignature) {
            if (buffer.remaining() < SIGNATURE_SIZE) return null
            signature = ByteArray(SIGNATURE_SIZE)
            buffer.get(signature)
        }

        return BitchatPacket(
            version = version,
            type = type,
            senderID = senderID,
            recipientID = recipientID,
            timestamp = timestamp,
            payload = payload,
            signature = signature,
            ttl = ttl,
            route = route,
            isRSR = isRSR
        )
    }
}
