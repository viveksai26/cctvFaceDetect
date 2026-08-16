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

data class SavedDvrCredentials(val id: String, val host: String, val username: String, val password: String, val rtspPort: Int, val numCameras: Int)

/** Stores DVR credentials encrypted with a device-bound Android Keystore key. */
class DvrCredentialStore(context: Context) {
    private val preferences = context.getSharedPreferences("dvr_credentials", Context.MODE_PRIVATE)

    fun loadAll(): List<SavedDvrCredentials> {
        val ids = preferences.getStringSet("dvr_ids", emptySet()) ?: return emptyList()
        return ids.mapNotNull { id ->
            runCatching {
                SavedDvrCredentials(
                    id = id,
                    host = decrypt(preferences.getString("host_$id", "") ?: ""),
                    username = decrypt(preferences.getString("username_$id", "") ?: ""),
                    password = decrypt(preferences.getString("password_$id", "") ?: ""),
                    rtspPort = preferences.getInt("rtsp_port_$id", 554),
                    numCameras = preferences.getInt("num_cameras_$id", 8),
                )
            }.getOrNull()
        }
    }

    fun save(connection: CpPlusDvrConnection) {
        val id = connection.host.replace(".", "_")
        val ids = preferences.getStringSet("dvr_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        ids.add(id)
        preferences.edit()
            .putStringSet("dvr_ids", ids)
            .putString("host_$id", encrypt(connection.host))
            .putString("username_$id", encrypt(connection.username))
            .putString("password_$id", encrypt(connection.password))
            .putInt("rtsp_port_$id", connection.rtspPort)
            .putInt("num_cameras_$id", connection.numCameras)
            .apply()
    }

    fun delete(id: String) {
        val ids = preferences.getStringSet("dvr_ids", emptySet())?.toMutableSet() ?: return
        ids.remove(id)
        preferences.edit()
            .putStringSet("dvr_ids", ids)
            .remove("host_$id")
            .remove("username_$id")
            .remove("password_$id")
            .remove("rtsp_port_$id")
            .apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
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
