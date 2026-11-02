package org.mercurio.client.feature.entry.data

import java.nio.ByteBuffer

data class EncryptedHistoryCacheRecord(
    val version: Int,
    val initializationVector: ByteArray,
    val ciphertext: ByteArray,
) {
    fun encode(): ByteArray {
        require(version == FORMAT_VERSION)
        require(initializationVector.size in MIN_IV_SIZE..MAX_IV_SIZE)
        require(ciphertext.isNotEmpty())
        return ByteBuffer
            .allocate(HEADER_SIZE + initializationVector.size + ciphertext.size)
            .put(version.toByte())
            .put(initializationVector.size.toByte())
            .putInt(ciphertext.size)
            .put(initializationVector)
            .put(ciphertext)
            .array()
    }

    companion object {
        const val FORMAT_VERSION = 1
        private const val BYTE_MASK = 0xFF
        private const val HEADER_SIZE = 1 + 1 + Int.SIZE_BYTES
        private const val MIN_IV_SIZE = 12
        private const val MAX_IV_SIZE = 16

        fun decode(bytes: ByteArray): EncryptedHistoryCacheRecord {
            require(bytes.size >= HEADER_SIZE) { "Invalid history cache record." }
            val buffer = ByteBuffer.wrap(bytes)
            val version = buffer.get().toInt() and BYTE_MASK
            val ivSize = buffer.get().toInt() and BYTE_MASK
            val ciphertextSize = buffer.int
            require(version == FORMAT_VERSION) { "Unsupported history cache record." }
            require(ivSize in MIN_IV_SIZE..MAX_IV_SIZE) { "Invalid history cache record." }
            require(ciphertextSize > 0 && ciphertextSize == buffer.remaining() - ivSize) {
                "Invalid history cache record."
            }
            val initializationVector = ByteArray(ivSize)
            buffer.get(initializationVector)
            val ciphertext = ByteArray(ciphertextSize)
            buffer.get(ciphertext)
            return EncryptedHistoryCacheRecord(version, initializationVector, ciphertext)
        }
    }
}
