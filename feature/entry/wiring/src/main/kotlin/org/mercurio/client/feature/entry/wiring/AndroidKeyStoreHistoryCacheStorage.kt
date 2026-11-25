package org.mercurio.client.feature.entry.wiring

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.mercurio.client.feature.entry.data.HistoryCacheStorage
import org.mercurio.client.feature.entry.domain.GatewayConnectionPersistenceException
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * The Keystore-tier blob storage of the history cache: one AtomicFile under the
 * no-backup directory holding only ciphertext, protected by a cache-specific AES-256
 * key that never leaves the AndroidKeyStore and is never persisted alongside content.
 */
class AndroidKeyStoreHistoryCacheStorage(
    context: Context,
) : HistoryCacheStorage {
    private val cacheFile =
        File(
            context.applicationContext.noBackupFilesDir,
            CACHE_FILE_NAME,
        )
    private val atomicFile = AtomicFile(cacheFile)

    override fun read(): ByteArray? =
        synchronized(storageLock) {
            runCatching { atomicFile.readFully() }.getOrNull()
        }

    override fun write(bytes: ByteArray) {
        synchronized(storageLock) {
            val stream =
                try {
                    atomicFile.startWrite()
                } catch (error: IOException) {
                    throw GatewayConnectionPersistenceException(error)
                }
            try {
                stream.write(bytes)
                stream.flush()
                atomicFile.finishWrite(stream)
            } catch (error: IOException) {
                runCatching { atomicFile.failWrite(stream) }
                throw GatewayConnectionPersistenceException(error)
            }
        }
    }

    override fun clear() {
        synchronized(storageLock) {
            val cacheFiles = cacheFiles()
            val hasCacheRecord = cacheFiles.any(File::exists)
            val deletionFailure = deleteAll(cacheFiles)
            val keyFailure = deleteKeyEntry(hasCacheRecord)
            (keyFailure ?: deletionFailure)?.let { throw GatewayConnectionPersistenceException(it) }
        }
    }

    private fun cacheFiles(): List<File> =
        listOf(
            cacheFile,
            File("${cacheFile.path}.bak"),
            File("${cacheFile.path}.new"),
        )

    private fun deleteAll(files: List<File>): Throwable? {
        var failure: Throwable? = null
        files.forEach { file ->
            if (file.exists() && !file.delete()) {
                failure = IllegalStateException("Could not remove the history cache record.")
            }
        }
        return failure
    }

    private fun deleteKeyEntry(hasCacheRecord: Boolean): Throwable? =
        try {
            val keyStore = loadKeyStore()
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
            null
        } catch (error: GeneralSecurityException) {
            error.takeIf { hasCacheRecord }
        } catch (error: IOException) {
            error.takeIf { hasCacheRecord }
        }

    fun loadOrCreateKey(): SecretKey =
        runCatching { createOrLoadKey() }.getOrElse { error ->
            throw GatewayConnectionPersistenceException(error)
        }

    private fun createOrLoadKey(): SecretKey {
        val keyStore = loadKeyStore()
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        return KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply {
                init(
                    KeyGenParameterSpec
                        .Builder(
                            KEY_ALIAS,
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        ).setKeySize(KEY_SIZE_BITS)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .build(),
                )
            }.generateKey()
    }

    private fun loadKeyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }

    companion object {
        const val CACHE_FILE_NAME = "gateway_history_cache.enc"
        const val KEY_ALIAS = "history_cache_key_v1"

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_SIZE_BITS = 256
        private val storageLock = Any()
    }
}
