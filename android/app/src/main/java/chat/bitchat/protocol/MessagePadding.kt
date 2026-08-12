package chat.bitchat.protocol

object MessagePadding {
    private val BLOCK_SIZES = intArrayOf(256, 512, 1024, 2048)

    fun pad(data: ByteArray, targetSize: Int): ByteArray {
        if (data.size >= targetSize) return data

        val paddingNeeded = targetSize - data.size
        if (paddingNeeded <= 0 || paddingNeeded > 255) return data

        val padded = ByteArray(targetSize)
        System.arraycopy(data, 0, padded, 0, data.size)
        for (i in data.size until targetSize) {
            padded[i] = paddingNeeded.toByte()
        }
        return padded
    }

    fun unpad(data: ByteArray): ByteArray {
        if (data.isEmpty()) return data
        val last = data.last()
        val paddingLength = last.toInt() and 0xFF
        if (paddingLength <= 0 || paddingLength > data.size) return data

        val start = data.size - paddingLength
        for (i in start until data.size) {
            if (data[i] != last) return data
        }

        val result = ByteArray(start)
        System.arraycopy(data, 0, result, 0, start)
        return result
    }

    fun optimalBlockSize(dataSize: Int): Int {
        val totalSize = dataSize + 16
        for (blockSize in BLOCK_SIZES) {
            if (totalSize <= blockSize) {
                return blockSize
            }
        }
        return dataSize
    }
}
