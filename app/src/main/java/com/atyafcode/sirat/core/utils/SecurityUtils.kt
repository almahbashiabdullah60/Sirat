package com.atyafcode.sirat.core.utils

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

object SecurityUtils {

    private const val HASH_ALGORITHM = "SHA-256"
    private const val SALT_LENGTH = 16
    private const val MAX_PASSWORD_LENGTH = 64

    /**
     * Sanitizes the input string by filtering out control characters and null bytes.
     * Also limits the length to [MAX_PASSWORD_LENGTH].
     */
    fun sanitizePassword(input: String): String {
        return input.filter { it.code >= 32 && it.code != 127 }
            .take(MAX_PASSWORD_LENGTH)
    }

    /**
     * Generates a random cryptographic salt.
     */
    fun generateSalt(): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH)
        random.nextBytes(salt)
        return salt
    }

    /**
     * Hashes the password using SHA-256 with the provided salt.
     * Returns a Base64 encoded string containing the salt and the hash.
     * Format: salt:hash
     */
    fun hashPassword(password: String, salt: ByteArray): String {
        val md = MessageDigest.getInstance(HASH_ALGORITHM)
        md.update(salt)
        val hashedPassword = md.digest(password.toByteArray(Charsets.UTF_8))
        
        val saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val hashBase64 = Base64.encodeToString(hashedPassword, Base64.NO_WRAP)
        
        return "$saltBase64:$hashBase64"
    }

    /**
     * Verifies an input password against a stored salted hash string.
     */
    fun verifyPassword(inputPassword: String, storedSaltedHash: String): Boolean {
        return try {
            val parts = storedSaltedHash.split(":")
            if (parts.size != 2) return false
            
            val salt = Base64.decode(parts[0], Base64.DEFAULT)
            val storedHash = parts[1]
            
            val md = MessageDigest.getInstance(HASH_ALGORITHM)
            md.update(salt)
            val inputHashed = md.digest(inputPassword.toByteArray(Charsets.UTF_8))
            val inputHashBase64 = Base64.encodeToString(inputHashed, Base64.NO_WRAP)
            
            inputHashBase64 == storedHash
        } catch (e: Exception) {
            false
        }
    }
}

