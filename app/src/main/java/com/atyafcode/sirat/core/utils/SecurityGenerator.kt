package com.atyafcode.sirat.core.utils

import java.security.SecureRandom

object SecurityGenerator {
    private val secureRandom = SecureRandom()
    
    private val PIN_CHARACTERS = "0123456789"
    private val PASSWORD_CHARACTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*"

    fun generateRandomPin(length: Int): String {
        val pin = StringBuilder(length)
        for (i in 0 until length) {
            pin.append(PIN_CHARACTERS[secureRandom.nextInt(PIN_CHARACTERS.length)])
        }
        return pin.toString()
    }

    fun generateRandomPassword(length: Int): String {
        val password = StringBuilder(length)
        for (i in 0 until length) {
            password.append(PASSWORD_CHARACTERS[secureRandom.nextInt(PASSWORD_CHARACTERS.length)])
        }
        return password.toString()
    }
}
