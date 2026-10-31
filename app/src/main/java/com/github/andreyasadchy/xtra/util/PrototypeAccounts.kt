package com.github.andreyasadchy.xtra.util

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Saved credentials are device-bound; inactive accounts never create workers or sockets. */
object PrototypeAccounts {
    private fun vault(c: Context) = c.getSharedPreferences("account_vault", Context.MODE_PRIVATE)
    private val clients = listOf(C.GQL_CLIENT_ID2, C.GQL_CLIENT_ID_WEB, C.HELIX_CLIENT_ID)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("prototype_accounts", null) as? SecretKey) ?: KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        ).apply {
            init(KeyGenParameterSpec.Builder("prototype_accounts", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    private fun decrypt(value: String): JSONObject {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        }
        return JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
    }
    fun save(c: Context) {
        val id = c.tokenPrefs().getString(C.USER_ID, null)?.takeIf { it.matches(Regex("[0-9]+")) } ?: return
        val tokens = JSONObject()
        c.tokenPrefs().all.forEach { (k, v) -> tokens.put(k, v) }
        val config = JSONObject()
        clients.forEach { c.prefs().getString(it, null)?.let { v -> config.put(it, v) } }
        check(vault(c).edit().putString(id, encrypt(JSONObject().put("tokens", tokens).put("clients", config).toString())).commit())
    }
    fun accounts(c: Context): List<Pair<String, String>> = vault(c).all.keys.filter { it.matches(Regex("[0-9]+")) }
        .mapNotNull { id -> runCatching { id to decrypt(vault(c).getString(id, null)!!).getJSONObject("tokens").optString(C.USERNAME, id) }.getOrNull() }
        .sortedBy { it.second.lowercase() }
    fun removeActive(c: Context): Boolean = c.tokenPrefs().getString(C.USER_ID, null)
        ?.let { vault(c).edit().remove(it).commit() } ?: true
    /** Called only in the transition process after the previous session process has exited. */
    fun activate(c: Context, id: String?) {
        val record = id?.let { decrypt(requireNotNull(vault(c).getString(it, null))) }
        val editor = c.tokenPrefs().edit().clear()
        record?.getJSONObject("tokens")?.let { tokens ->
            tokens.keys().forEach { k ->
                when (val v = tokens.get(k)) {
                    is String -> editor.putString(k, v)
                    is Boolean -> editor.putBoolean(k, v)
                    is Number -> editor.putLong(k, v.toLong())
                }
            }
        }
        // Keep this account's identity headers, but never reuse an old integrity/session proof.
        record?.getJSONObject("tokens")?.optString(C.GQL_HEADERS)?.takeIf { it.isNotBlank() }?.let {
            val oldHeaders = JSONObject(it)
            val headers = JSONObject()
            listOf(C.HEADER_CLIENT_ID, C.HEADER_TOKEN).forEach { k ->
                if (oldHeaders.has(k)) headers.put(k, oldHeaders.getString(k))
            }
            editor.putString(C.GQL_HEADERS, headers.toString())
        }
        editor.putLong(C.INTEGRITY_EXPIRATION, 0)
        editor.remove(WhisperHistorySession.VERIFIED_HEADERS).remove(WhisperHistorySession.VERIFIED_UNTIL)
        check(editor.commit())
        record?.getJSONObject("clients")?.let { config ->
            val e = c.prefs().edit()
            clients.forEach { k -> if (config.has(k)) e.putString(k, config.getString(k)) else e.remove(k) }
            check(e.commit())
        }
    }
}
