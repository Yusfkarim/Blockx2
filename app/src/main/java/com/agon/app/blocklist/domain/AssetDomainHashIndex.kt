package com.agon.app.blocklist.domain

import android.content.Context
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Exact, memory-mapped index for the bundled domain list.
 *
 * The asset stores sorted 64-bit FNV-1a hashes. Lookup checks the full host and each registrable
 * suffix, preserving the same subdomain behavior as [DomainIndex] without allocating millions of
 * trie nodes or keeping the 47 MB source text in memory.
 */
class AssetDomainHashIndex private constructor(
    private val buffer: MappedByteBuffer?,
    val size: Int,
) {
    val isEmpty: Boolean get() = buffer == null || size == 0

    fun matches(rawDomain: String): Boolean {
        if (isEmpty) return false
        var candidate = TextNormalizer.normalizeDomain(rawDomain)
        if (candidate.isEmpty() || !candidate.contains('.')) return false
        while (candidate.contains('.')) {
            if (containsHash(fnv1a64(candidate))) return true
            candidate = candidate.substringAfter('.', missingDelimiterValue = "")
            if (candidate.isEmpty()) return false
        }
        return false
    }

    private fun containsHash(target: Long): Boolean {
        val mapped = buffer ?: return false
        var low = 0
        var high = size - 1
        while (low <= high) {
            val middle = (low + high).ushr(1)
            val value = mapped.getLong(HEADER_SIZE + middle * Long.SIZE_BYTES)
            val comparison = java.lang.Long.compareUnsigned(value, target)
            when {
                comparison < 0 -> low = middle + 1
                comparison > 0 -> high = middle - 1
                else -> return true
            }
        }
        return false
    }

    companion object {
        val EMPTY = AssetDomainHashIndex(null, 0)

        private const val ASSET_NAME = "blocked_sites.didx"
        private const val MAGIC = "DLSITES1"
        private const val HEADER_SIZE = 16
        private const val FNV_OFFSET_BASIS = -3750763034362895579L
        private const val FNV_PRIME = 1099511628211L

        fun load(context: Context): AssetDomainHashIndex = runCatching {
            context.assets.openFd(ASSET_NAME).use { asset ->
                FileInputStream(asset.fileDescriptor).channel.use { channel ->
                    val mapped = channel.map(
                        FileChannel.MapMode.READ_ONLY,
                        asset.startOffset,
                        asset.length,
                    ).apply { order(ByteOrder.LITTLE_ENDIAN) }
                    validate(mapped)
                    val storedCount = mapped.getLong(8)
                    require(storedCount in 1..Int.MAX_VALUE.toLong())
                    val count = storedCount.toInt()
                    require(asset.length >= HEADER_SIZE + count.toLong() * Long.SIZE_BYTES)
                    AssetDomainHashIndex(mapped, count)
                }
            }
        }.getOrDefault(EMPTY)

        internal fun fnv1a64(value: String): Long {
            var hash = FNV_OFFSET_BASIS
            for (byte in value.toByteArray(Charsets.UTF_8)) {
                hash = hash xor (byte.toLong() and 0xffL)
                hash *= FNV_PRIME
            }
            return hash
        }

        private fun validate(buffer: ByteBuffer) {
            require(buffer.capacity() >= HEADER_SIZE)
            val magic = ByteArray(8)
            for (index in magic.indices) magic[index] = buffer.get(index)
            require(magic.toString(Charsets.US_ASCII) == MAGIC)
        }
    }
}
