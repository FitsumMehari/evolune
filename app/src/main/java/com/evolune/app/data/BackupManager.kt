package com.evolune.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupManager(private val context: Context, private val repository: EvoluneRepository) {
    suspend fun export(uri: Uri, password: String) = withContext(Dispatchers.IO) {
        val plaintext = repository.exportJson().toByteArray(Charsets.UTF_8)
        val payload = if (password.isBlank()) {
            MAGIC_PLAIN + plaintext
        } else {
            val random = SecureRandom()
            val salt = ByteArray(16).also(random::nextBytes)
            val iv = ByteArray(12).also(random::nextBytes)
            val key = deriveKey(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            val encrypted = cipher.doFinal(plaintext)
            MAGIC_ENCRYPTED + ByteBuffer.allocate(salt.size + iv.size + encrypted.size).put(salt).put(iv).put(encrypted).array()
        }
        context.contentResolver.openOutputStream(uri, "w")!!.use { it.write(payload) }
    }

    suspend fun restore(uri: Uri, password: String) = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val json = when {
            bytes.startsWith(MAGIC_PLAIN) -> bytes.copyOfRange(MAGIC_PLAIN.size, bytes.size).toString(Charsets.UTF_8)
            bytes.startsWith(MAGIC_ENCRYPTED) -> {
                require(password.isNotBlank()) { "This backup is encrypted. Enter its password." }
                val body = bytes.copyOfRange(MAGIC_ENCRYPTED.size, bytes.size)
                require(body.size > 28) { "Backup is incomplete" }
                val salt = body.copyOfRange(0, 16)
                val iv = body.copyOfRange(16, 28)
                val encrypted = body.copyOfRange(28, body.size)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
                cipher.doFinal(encrypted).toString(Charsets.UTF_8)
            }
            else -> error("Not an Evolune backup")
        }
        repository.restoreJson(json)
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, 210_000, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    companion object {
        private val MAGIC_PLAIN = "EVOLUNE1P\n".toByteArray()
        private val MAGIC_ENCRYPTED = "EVOLUNE1E\n".toByteArray()
    }
}
