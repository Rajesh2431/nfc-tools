package com.tapandgo.core.security

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-encrypted backups using only platform-vetted primitives
 * (spec § 10: no custom crypto): PBKDF2-HMAC-SHA256 key derivation and
 * AES-256-GCM authenticated encryption. The envelope is JSON so the format
 * and parameters are self-describing and versioned.
 */
object BackupCrypto {
    const val FORMAT = "tapandgo-encrypted"
    const val VERSION = 1
    const val DEFAULT_ITERATIONS = 210_000
    const val MIN_PASSWORD = 8
    private const val MAX_ITERATIONS = 2_000_000

    @Serializable
    data class Envelope(
        val format: String = FORMAT,
        val version: Int = VERSION,
        val kdf: String = "PBKDF2WithHmacSHA256",
        val iterations: Int,
        val salt: String,
        val iv: String,
        val ciphertext: String,
    )

    sealed interface Result {
        data class Ok(val plaintext: String) : Result
        data object WrongPasswordOrCorrupt : Result
        data object NotEncrypted : Result
        data object Unsupported : Result
    }

    // encodeDefaults: format/version/kdf must be written so files are self-describing.
    private val json = Json { ignoreUnknownKeys = false; encodeDefaults = true }
    private val b64 = Base64.getEncoder()
    private val b64d = Base64.getDecoder()

    private fun key(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun encrypt(plaintext: String, password: CharArray, iterations: Int = DEFAULT_ITERATIONS, random: SecureRandom = SecureRandom()): String {
        require(password.size >= MIN_PASSWORD) { "password too short" }
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(128, iv))
        cipher.updateAAD("$FORMAT/$VERSION".toByteArray())
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return json.encodeToString(Envelope.serializer(), Envelope(iterations = iterations, salt = b64.encodeToString(salt), iv = b64.encodeToString(iv), ciphertext = b64.encodeToString(ct)))
    }

    /** Cheap check before asking the user for a password. */
    fun isEncrypted(text: String): Boolean = text.length < 64 * 1024 * 1024 && text.contains("\"$FORMAT\"")

    fun decrypt(text: String, password: CharArray): Result {
        val env = runCatching { json.decodeFromString(Envelope.serializer(), text) }.getOrNull() ?: return Result.NotEncrypted
        if (env.format != FORMAT) return Result.NotEncrypted
        if (env.version != VERSION || env.kdf != "PBKDF2WithHmacSHA256" || env.iterations !in 10_000..MAX_ITERATIONS) return Result.Unsupported
        return try {
            val salt = b64d.decode(env.salt)
            val iv = b64d.decode(env.iv)
            if (salt.size != 16 || iv.size != 12) return Result.Unsupported
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(password, salt, env.iterations), GCMParameterSpec(128, iv))
            cipher.updateAAD("$FORMAT/$VERSION".toByteArray())
            Result.Ok(cipher.doFinal(b64d.decode(env.ciphertext)).toString(Charsets.UTF_8))
        } catch (_: AEADBadTagException) {
            Result.WrongPasswordOrCorrupt
        } catch (_: IllegalArgumentException) {
            Result.WrongPasswordOrCorrupt
        }
    }
}
