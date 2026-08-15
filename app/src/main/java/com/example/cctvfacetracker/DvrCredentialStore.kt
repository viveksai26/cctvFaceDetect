package com.example.cctvfacetracker

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class SavedDvrCredentials(val username: String, val password: String, val rtspPort: Int)

/** Stores DVR credentials encrypted with a device-bound Android Keystore key. */
class DvrCredentialStore(context: Context) {
    private val preferences = context.getSharedPreferences("dvr_credentials", Context.MODE_PRIVATE)

    fun load(): SavedDvrCredentials? = runCatching {
        SavedDvrCredentials(
            username = decrypt(preferences.getString("username", null) ?: return null),
            password = decrypt(preferences.getString("password", null) ?: return null),
            rtspPort = preferences.getInt("rtsp_port", 554),
        )
    }.getOrNull()

    fun save(connection: CpPlusDvrConnection) {
        preferences.edit()
            .putString("username", encrypt(connection.username))
            .putString("password", encrypt(connection.password))
            .putInt("rtsp_port", connection.rtspPort)
            .apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        return "${Base64.encodeToString(cipher.iv, Base64.NO_WRAP)}:${Base64.encodeToString(cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)}"
    }

    private fun decrypt(value: String): String {
        val (iv, encrypted) = value.split(":", limit = 2)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        }
        return cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)).decodeToString()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build())
            }.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "cctv_dvr_credentials"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
