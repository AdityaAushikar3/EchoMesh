package chat.bitchat.protocol

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

object CompressionUtil {
    private const val COMPRESSION_THRESHOLD_BYTES = 64
    private const val MAX_DECOMPRESSED_SIZE_BYTES = 500 * 1024

    fun compress(data: ByteArray): ByteArray? {
        if (data.size <= COMPRESSION_THRESHOLD_BYTES) return null

        val deflater = Deflater()
        deflater.setInput(data)
        deflater.finish()

        val bos = ByteArrayOutputStream(data.size)
        val buf = ByteArray(1024)
        while (!deflater.finished()) {
            val count = deflater.deflate(buf)
            bos.write(buf, 0, count)
        }
        deflater.end()

        val compressed = bos.toByteArray()
        return if (compressed.size < data.size) compressed else null
    }

    fun decompress(compressedData: ByteArray, originalSize: Int): ByteArray? {
        if (originalSize <= 0 || originalSize > MAX_DECOMPRESSED_SIZE_BYTES) return null
        val inflater = Inflater()
        inflater.setInput(compressedData)
        val result = ByteArray(originalSize)
        return try {
            val decompressedSize = inflater.inflate(result)
            inflater.end()
            if (decompressedSize == originalSize) result else null
        } catch (e: Exception) {
            inflater.end()
            null
        }
    }

    fun shouldCompress(data: ByteArray): Boolean {
        if (data.size <= COMPRESSION_THRESHOLD_BYTES) return false

        val sample = data.take(256)
        val uniqueByteCount = sample.toSet().size
        val uniqueByteRatio = uniqueByteCount.toDouble() / sample.size.toDouble()
        return uniqueByteRatio < 0.9
    }
}
