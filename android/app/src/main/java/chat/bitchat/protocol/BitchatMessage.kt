package chat.bitchat.protocol

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.UUID

sealed class DeliveryStatus {
    object Sending : DeliveryStatus() {
        override fun toString(): String = "Sending..."
    }
    object Sent : DeliveryStatus() {
        override fun toString(): String = "Sent"
    }
    object Carried : DeliveryStatus() {
        override fun toString(): String = "Carried by a friend"
    }
    data class Delivered(val to: String, val at: Long) : DeliveryStatus() {
        override fun toString(): String = "Delivered to $to"
    }
    data class Read(val by: String, val at: Long) : DeliveryStatus() {
        override fun toString(): String = "Read by $by"
    }
    data class Failed(val reason: String) : DeliveryStatus() {
        override fun toString(): String = "Failed: $reason"
    }
    data class PartiallyDelivered(val reached: Int, val total: Int) : DeliveryStatus() {
        override fun toString(): String = "Delivered to $reached/$total"
    }
}

data class BitchatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String,
    val content: String,
    val timestamp: Long, // Epoch milliseconds
    val isRelay: Boolean,
    val originalSender: String? = null,
    val isPrivate: Boolean = false,
    val recipientNickname: String? = null,
    val senderPeerID: PeerID? = null,
    val mentions: List<String>? = null,
    var deliveryStatus: DeliveryStatus? = null,
    val isBridged: Boolean = false
) {
    fun toBinaryPayload(): ByteArray {
        val idBytes = id.toByteArray(StandardCharsets.UTF_8)
        val senderBytes = sender.toByteArray(StandardCharsets.UTF_8)
        val contentBytes = content.toByteArray(StandardCharsets.UTF_8)

        val origSenderBytes = originalSender?.toByteArray(StandardCharsets.UTF_8)
        val recipNicknameBytes = recipientNickname?.toByteArray(StandardCharsets.UTF_8)
        val peerIDBytes = senderPeerID?.id?.toByteArray(StandardCharsets.UTF_8)

        var flags = 0
        if (isRelay) flags = flags or 0x01
        if (isPrivate) flags = flags or 0x02
        if (origSenderBytes != null) flags = flags or 0x04
        if (recipNicknameBytes != null) flags = flags or 0x08
        if (peerIDBytes != null) flags = flags or 0x10
        if (!mentions.isNullOrEmpty()) flags = flags or 0x20
        if (isBridged) flags = flags or 0x40

        // Calculate size
        var size = 1 + 8 // flags (1) + timestamp (8)
        size += 1 + minOf(idBytes.size, 255)
        size += 1 + minOf(senderBytes.size, 255)
        size += 2 + minOf(contentBytes.size, 65535)

        if (origSenderBytes != null) {
            size += 1 + minOf(origSenderBytes.size, 255)
        }
        if (recipNicknameBytes != null) {
            size += 1 + minOf(recipNicknameBytes.size, 255)
        }
        if (peerIDBytes != null) {
            size += 1 + minOf(peerIDBytes.size, 255)
        }
        if (!mentions.isNullOrEmpty()) {
            size += 1 // count byte
            for (mention in mentions.take(255)) {
                val mentionBytes = mention.toByteArray(StandardCharsets.UTF_8)
                size += 1 + minOf(mentionBytes.size, 255)
            }
        }

        val buffer = ByteBuffer.allocate(size)
        buffer.put(flags.toByte())
        buffer.putLong(timestamp)

        // ID
        val idLen = minOf(idBytes.size, 255)
        buffer.put(idLen.toByte())
        buffer.put(idBytes, 0, idLen)

        // Sender
        val senderLen = minOf(senderBytes.size, 255)
        buffer.put(senderLen.toByte())
        buffer.put(senderBytes, 0, senderLen)

        // Content
        val contentLen = minOf(contentBytes.size, 65535)
        buffer.putShort(contentLen.toShort())
        buffer.put(contentBytes, 0, contentLen)

        // Optional fields
        if (origSenderBytes != null) {
            val len = minOf(origSenderBytes.size, 255)
            buffer.put(len.toByte())
            buffer.put(origSenderBytes, 0, len)
        }
        if (recipNicknameBytes != null) {
            val len = minOf(recipNicknameBytes.size, 255)
            buffer.put(len.toByte())
            buffer.put(recipNicknameBytes, 0, len)
        }
        if (peerIDBytes != null) {
            val len = minOf(peerIDBytes.size, 255)
            buffer.put(len.toByte())
            buffer.put(peerIDBytes, 0, len)
        }
        if (!mentions.isNullOrEmpty()) {
            val count = minOf(mentions.size, 255)
            buffer.put(count.toByte())
            for (mention in mentions.take(count)) {
                val mBytes = mention.toByteArray(StandardCharsets.UTF_8)
                val len = minOf(mBytes.size, 255)
                buffer.put(len.toByte())
                buffer.put(mBytes, 0, len)
            }
        }

        return buffer.array()
    }

    companion object {
        fun fromBinaryPayload(data: ByteArray): BitchatMessage? {
            if (data.size < 13) return null

            val buffer = ByteBuffer.wrap(data)
            val flags = buffer.get().toInt()
            val isRelay = (flags and 0x01) != 0
            val isPrivate = (flags and 0x02) != 0
            val hasOriginalSender = (flags and 0x04) != 0
            val hasRecipientNickname = (flags and 0x08) != 0
            val hasSenderPeerID = (flags and 0x10) != 0
            val hasMentions = (flags and 0x20) != 0
            val isBridged = (flags and 0x40) != 0

            val timestamp = buffer.getLong()

            // ID
            val idLength = buffer.get().toInt() and 0xFF
            if (buffer.remaining() < idLength) return null
            val idBytes = ByteArray(idLength)
            buffer.get(idBytes)
            val id = String(idBytes, StandardCharsets.UTF_8)

            // Sender
            val senderLength = buffer.get().toInt() and 0xFF
            if (buffer.remaining() < senderLength) return null
            val senderBytes = ByteArray(senderLength)
            buffer.get(senderBytes)
            val sender = String(senderBytes, StandardCharsets.UTF_8)

            // Content
            val contentLength = buffer.getShort().toInt() and 0xFFFF
            if (buffer.remaining() < contentLength) return null
            val contentBytes = ByteArray(contentLength)
            buffer.get(contentBytes)
            val content = String(contentBytes, StandardCharsets.UTF_8)

            // Optional fields
            var originalSender: String? = null
            if (hasOriginalSender) {
                if (!buffer.hasRemaining()) return null
                val len = buffer.get().toInt() and 0xFF
                if (buffer.remaining() < len) return null
                val bytes = ByteArray(len)
                buffer.get(bytes)
                originalSender = String(bytes, StandardCharsets.UTF_8)
            }

            var recipientNickname: String? = null
            if (hasRecipientNickname) {
                if (!buffer.hasRemaining()) return null
                val len = buffer.get().toInt() and 0xFF
                if (buffer.remaining() < len) return null
                val bytes = ByteArray(len)
                buffer.get(bytes)
                recipientNickname = String(bytes, StandardCharsets.UTF_8)
            }

            var senderPeerID: PeerID? = null
            if (hasSenderPeerID) {
                if (!buffer.hasRemaining()) return null
                val len = buffer.get().toInt() and 0xFF
                if (buffer.remaining() < len) return null
                val bytes = ByteArray(len)
                buffer.get(bytes)
                senderPeerID = PeerID.fromString(String(bytes, StandardCharsets.UTF_8))
            }

            var mentions: MutableList<String>? = null
            if (hasMentions) {
                if (!buffer.hasRemaining()) return null
                val count = buffer.get().toInt() and 0xFF
                if (count > 0) {
                    mentions = mutableListOf()
                    for (i in 0 until count) {
                        if (!buffer.hasRemaining()) return null
                        val len = buffer.get().toInt() and 0xFF
                        if (buffer.remaining() < len) return null
                        val bytes = ByteArray(len)
                        buffer.get(bytes)
                        mentions.add(String(bytes, StandardCharsets.UTF_8))
                    }
                }
            }

            return BitchatMessage(
                id = id,
                sender = sender,
                content = content,
                timestamp = timestamp,
                isRelay = isRelay,
                originalSender = originalSender,
                isPrivate = isPrivate,
                recipientNickname = recipientNickname,
                senderPeerID = senderPeerID,
                mentions = mentions,
                isBridged = isBridged,
                deliveryStatus = if (isPrivate) DeliveryStatus.Sending else null
            )
        }
    }
}
