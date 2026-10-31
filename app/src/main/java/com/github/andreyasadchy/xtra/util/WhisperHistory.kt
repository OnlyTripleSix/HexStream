package com.github.andreyasadchy.xtra.util

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Serializable
data class WhisperEntry(val id: String, val fromId: String, val from: String, val toId: String, val to: String, val text: String, val time: Long)

/** Bounded, device-key encrypted history; never part of settings exports. */
object WhisperHistory {
    private fun prefs(context: Context) = context.getSharedPreferences("hex_whisper_history", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val alias = "hex_whisper_history"
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return store.getKey(alias, null) as? SecretKey ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun read(context: Context, account: String): List<WhisperEntry> {
        val value = prefs(context).getString(account, null) ?: return emptyList()
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12))) }
        return Json.decodeFromString(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
    }
    fun write(context: Context, account: String, entries: List<WhisperEntry>) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val bytes = cipher.iv + cipher.doFinal(Json.encodeToString(entries.takeLast(500)).toByteArray(Charsets.UTF_8))
        val editor = prefs(context).edit()
        editor.putString(account, Base64.encodeToString(bytes, Base64.NO_WRAP))
        check(editor.commit()) { "Could not save local whisper history." }
    }
    fun clear(context: Context, account: String) {
        val editor = prefs(context).edit()
        editor.remove(account)
        check(editor.commit())
    }
}
