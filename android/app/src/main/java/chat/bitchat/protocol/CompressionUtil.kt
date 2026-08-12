package chat.bitchat.protocol

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

object CompressionUtil {
    private const val COMPRESSION_THRESHOLD_BYTES = 100

    fun compress(data: ByteArray): ByteArray? {
        if (data.size < COMPRESSION_THRESHOLD_BYTES) return null

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
        if (data.size < COMPRESSION_THRESHOLD_BYTES) return false

        val uniqueByteCount = data.toSet().size
        val sampleSize = minOf(data.size, 256)
        val uniqueByteRatio = uniqueByteCount.toDouble() / sampleSize.toDouble()
        return uniqueByteRatio < 0.9
    }
}
