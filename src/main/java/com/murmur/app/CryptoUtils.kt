package com.murmur.app

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    private const val secretKey = "bluegillbluegill" // 16-char key for AES

    private fun getKey(): SecretKeySpec {
        return SecretKeySpec(secretKey.toByteArray(Charsets.UTF_8), "AES")
    }

    fun encrypt(text: String): String {
        val cipher = Cipher.getInstance("AES")
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        val encrypted = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        val base64 = Base64.encodeToString(encrypted, Base64.DEFAULT)
        return "ENC:$base64"
    }

    fun decrypt(encrypted: String): String {
        return try {
            if (!encrypted.startsWith("ENC:")) return "[Invalid format]"
            val raw = encrypted.removePrefix("ENC:")
            val cipher = Cipher.getInstance("AES")
            cipher.init(Cipher.DECRYPT_MODE, getKey())
            val decoded = Base64.decode(raw, Base64.DEFAULT)
            val decrypted = cipher.doFinal(decoded)
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            encrypted // fail silently
        }
    }
}

