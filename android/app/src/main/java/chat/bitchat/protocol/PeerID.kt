package chat.bitchat.protocol

import java.util.Locale

data class PeerID(
    val prefix: Prefix,
    val bare: String
) : Comparable<PeerID> {

    enum class Prefix(val value: String) {
        EMPTY(""),
        MESH("mesh:"),
        NAME("name:"),
        NOISE("noise:"),
        GEODM("nostr_"),
        GEOCHAT("nostr:"),
        GROUP("group_"),
        BRIDGE("bridge:");

        companion object {
            fun fromString(str: String): Prefix {
                return values().firstOrNull { it != EMPTY && str.startsWith(it.value) } ?: EMPTY
            }
        }
    }

    val id: String
        get() = prefix.value + bare

    init {
        // Equivalent to String(bare).lowercased() in Swift
        // Confined to lowercase to ensure consistency in identity checks
    }

    companion object {
        private const val NOSTR_CONV_KEY_PREFIX_LENGTH = 16
        private const val NOSTR_SHORT_KEY_DISPLAY_LENGTH = 8
        private const val MAX_ID_LENGTH = 64
        private const val HEX_ID_LENGTH = 16 // 8 bytes = 16 hex chars

        fun nostrGeoDM(pubKey: String): PeerID {
            return PeerID(Prefix.GEODM, pubKey.take(NOSTR_CONV_KEY_PREFIX_LENGTH).lowercase(Locale.US))
        }

        fun nostrGeoChat(pubKey: String): PeerID {
            return PeerID(Prefix.GEOCHAT, pubKey.take(NOSTR_SHORT_KEY_DISPLAY_LENGTH).lowercase(Locale.US))
        }

        fun bridge(pubKey: String): PeerID {
            return PeerID(Prefix.BRIDGE, pubKey.take(NOSTR_CONV_KEY_PREFIX_LENGTH).lowercase(Locale.US))
        }

        fun fromString(str: String): PeerID {
            val prefix = Prefix.fromString(str)
            val bare = if (prefix != Prefix.EMPTY) {
                str.drop(prefix.value.length)
            } else {
                str
            }
            return PeerID(prefix, bare.lowercase(Locale.US))
        }

        fun fromBytes(bytes: ByteArray): PeerID {
            return fromString(String(bytes, Charsets.UTF_8))
        }

        fun fromHexBytes(bytes: ByteArray): PeerID {
            return fromString(bytes.toHexString())
        }

        fun group(groupID: ByteArray): PeerID {
            return fromString(Prefix.GROUP.value + groupID.toHexString())
        }

        fun fromPublicKey(publicKey: ByteArray): PeerID {
            val fingerprint = publicKey.sha256().toHexString()
            return fromString(fingerprint.take(HEX_ID_LENGTH))
        }

        fun fromRoutingData(routingData: ByteArray): PeerID? {
            if (routingData.size != 8) return null
            return fromString(routingData.toHexString())
        }
    }

    val groupIDData: ByteArray?
        get() {
            if (!isGroup || bare.length != 32) return null
            return try {
                bare.hexToByteArray()
            } catch (e: Exception) {
                null
            }
        }

    val isEmpty: Boolean
        get() = id.isEmpty()

    val isGeoChat: Boolean
        get() = prefix == Prefix.GEOCHAT

    val isGeoDM: Boolean
        get() = prefix == Prefix.GEODM

    val isGroup: Boolean
        get() = prefix == Prefix.GROUP

    val isBridge: Boolean
        get() = prefix == Prefix.BRIDGE

    val isHex: Boolean
        get() = bare.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }

    val isShort: Boolean
        get() = bare.length == HEX_ID_LENGTH && isHex

    val isNoiseKeyHex: Boolean
        get() = noiseKey != null

    val noiseKey: ByteArray?
        get() {
            if (bare.length != MAX_ID_LENGTH) return null
            return try {
                bare.hexToByteArray()
            } catch (e: Exception) {
                null
            }
        }

    val routingData: ByteArray?
        get() {
            val direct = try { id.hexToByteArray() } catch (e: Exception) { null }
            if (direct != null && direct.size == 8) return direct

            val bareData = try { bare.hexToByteArray() } catch (e: Exception) { null }
            if (bareData != null && bareData.size == 8) return bareData

            val short = toShort()
            return try { short.id.hexToByteArray() } catch (e: Exception) { null }
        }

    fun toShort(): PeerID {
        val key = noiseKey
        if (key != null) {
            return fromPublicKey(key)
        }
        return this
    }

    val isValid: Boolean
        get() {
            if (prefix != Prefix.EMPTY) {
                return fromString(bare).isValid
            }

            if (isShort || isNoiseKeyHex) {
                return true
            }

            if (id.length == HEX_ID_LENGTH || id.length == MAX_ID_LENGTH) {
                return false
            }

            if (id.isEmpty() || id.length >= MAX_ID_LENGTH) {
                return false
            }

            return id.all { it.isLetterOrDigit() || it == '-' || it == '_' }
        }

    override fun compareTo(other: PeerID): Int {
        return this.id.compareTo(other.id)
    }

    override fun toString(): String {
        return id
    }
}
