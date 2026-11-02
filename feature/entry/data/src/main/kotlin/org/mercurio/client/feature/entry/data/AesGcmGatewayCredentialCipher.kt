package org.mercurio.client.feature.entry.data

import java.nio.charset.StandardCharsets
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AesGcmGatewayCredentialCipher(
    private val keyProvider: () -> SecretKey,
) {
    fun encrypt(credential: String): EncryptedGatewayCredentialRecord {
        require(credential.isNotBlank())
        val plaintext = credential.toByteArray(StandardCharsets.UTF_8)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
            cipher.updateAAD(ASSOCIATED_DATA)
            EncryptedGatewayCredentialRecord(
                version = EncryptedGatewayCredentialRecord.FORMAT_VERSION,
                initializationVector = cipher.iv,
                ciphertext = cipher.doFinal(plaintext),
            )
        } finally {
            Arrays.fill(plaintext, 0)
        }
    }

    fun decrypt(record: EncryptedGatewayCredentialRecord): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            keyProvider(),
            GCMParameterSpec(TAG_LENGTH_BITS, record.initializationVector),
        )
        cipher.updateAAD(ASSOCIATED_DATA)
        val plaintext = cipher.doFinal(record.ciphertext)
        return try {
            String(plaintext, StandardCharsets.UTF_8).also { credential ->
                require(credential.isNotBlank()) { "Invalid Gateway credential record." }
            }
        } finally {
            Arrays.fill(plaintext, 0)
        }
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_LENGTH_BITS = 128
        val ASSOCIATED_DATA = "org.mercurio.client.gateway-credential.v1".toByteArray(StandardCharsets.UTF_8)
    }
}
