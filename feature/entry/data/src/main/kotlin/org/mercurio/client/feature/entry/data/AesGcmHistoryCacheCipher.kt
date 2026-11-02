package org.mercurio.client.feature.entry.data

import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AesGcmHistoryCacheCipher(
    private val keyProvider: () -> SecretKey,
) {
    fun encrypt(content: ByteArray): EncryptedHistoryCacheRecord {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
        cipher.updateAAD(ASSOCIATED_DATA)
        return EncryptedHistoryCacheRecord(
            version = EncryptedHistoryCacheRecord.FORMAT_VERSION,
            initializationVector = cipher.iv,
            ciphertext = cipher.doFinal(content),
        )
    }

    fun decrypt(record: EncryptedHistoryCacheRecord): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            keyProvider(),
            GCMParameterSpec(TAG_LENGTH_BITS, record.initializationVector),
        )
        cipher.updateAAD(ASSOCIATED_DATA)
        return cipher.doFinal(record.ciphertext)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_LENGTH_BITS = 128
        val ASSOCIATED_DATA = "org.mercurio.client.history-cache.v1".toByteArray(StandardCharsets.UTF_8)
    }
}
