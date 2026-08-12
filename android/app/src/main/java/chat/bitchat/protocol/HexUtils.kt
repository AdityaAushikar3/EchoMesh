package chat.bitchat.protocol

import java.security.MessageDigest
import java.util.Locale

fun ByteArray.toHexString(): String {
    val hexChars = CharArray(this.size * 2)
    for (i in this.indices) {
        val v = this[i].toInt() and 0xFF
        hexChars[i * 2] = Character.forDigit(v ushr 4, 16)
        hexChars[i * 2 + 1] = Character.forDigit(v and 0x0F, 16)
    }
    return String(hexChars).lowercase(Locale.US)
}

fun String.hexToByteArray(): ByteArray {
    val s = this.replace(":", "").replace("-", "")
    val len = s.length
    if (len % 2 != 0) {
        throw IllegalArgumentException("Hex string must have an even number of characters")
    }
    val data = ByteArray(len / 2)
    var i = 0
    while (i < len) {
        val d1 = Character.digit(s[i], 16)
        val d2 = Character.digit(s[i + 1], 16)
        if (d1 == -1 || d2 == -1) {
            throw IllegalArgumentException("Invalid hex character in string")
        }
        data[i / 2] = ((d1 shl 4) + d2).toByte()
        i += 2
    }
    return data
}

fun ByteArray.sha256(): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(this)
}
