package chat.bitchat.protocol

data class BitchatPacket(
    val version: Byte = 1,
    val type: Byte,
    val senderID: ByteArray,
    val recipientID: ByteArray?,
    val timestamp: Long, // Epoch milliseconds
    val payload: ByteArray,
    var signature: ByteArray? = null,
    var ttl: Byte,
    val route: List<ByteArray>? = null,
    val routeMetrics: List<Byte>? = null,
    val isRSR: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BitchatPacket

        if (version != other.version) return false
        if (type != other.type) return false
        if (!senderID.contentEquals(other.senderID)) return false
        if (recipientID != null) {
            if (other.recipientID == null) return false
            if (!recipientID.contentEquals(other.recipientID)) return false
        } else if (other.recipientID != null) return false
        if (timestamp != other.timestamp) return false
        if (!payload.contentEquals(other.payload)) return false
        if (signature != null) {
            if (other.signature == null) return false
            if (!signature.contentEquals(other.signature)) return false
        } else if (other.signature != null) return false
        if (ttl != other.ttl) return false
        if (route != null) {
            if (other.route == null) return false
            if (route.size != other.route.size) return false
            for (i in route.indices) {
                if (!route[i].contentEquals(other.route[i])) return false
            }
        } else if (other.route != null) return false
        if (routeMetrics != other.routeMetrics) return false
        if (isRSR != other.isRSR) return false

        return true
    }

    override fun hashCode(): Int {
        var result = version.toInt()
        result = 31 * result + type.toInt()
        result = 31 * result + senderID.contentHashCode()
        result = 31 * result + (recipientID?.contentHashCode() ?: 0)
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + (signature?.contentHashCode() ?: 0)
        result = 31 * result + ttl.toInt()
        var routeHash = 0
        if (route != null) {
            for (hop in route) {
                routeHash = 31 * routeHash + hop.contentHashCode()
            }
        }
        result = 31 * result + routeHash
        result = 31 * result + (routeMetrics?.hashCode() ?: 0)
        result = 31 * result + isRSR.hashCode()
        return result
    }

    fun toBinaryData(padding: Boolean = true): ByteArray? {
        return BinaryProtocol.encode(this, padding)
    }

    fun toBinaryDataForSigning(): ByteArray? {
        val unsignedPacket = BitchatPacket(
            version = version,
            type = type,
            senderID = senderID,
            recipientID = recipientID,
            timestamp = timestamp,
            payload = payload,
            signature = null,
            ttl = 0,
            route = route,
            routeMetrics = routeMetrics,
            isRSR = false
        )
        return BinaryProtocol.encode(unsignedPacket, padding = false)
    }

    companion object {
        fun fromBinaryData(data: ByteArray): BitchatPacket? {
            return BinaryProtocol.decode(data)
        }
    }
}
