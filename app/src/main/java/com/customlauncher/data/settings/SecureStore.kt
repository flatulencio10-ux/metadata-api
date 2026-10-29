package com.customlauncher.data.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Encrypted key/value storage for secrets (API keys). Values never enter the layout profile,
 * so exported profiles can be shared without leaking credentials.
 */
class SecureStore(private val context: Context) {

    private val prefs: SharedPreferences by lazy { open() }

    /** Bumped on every write so Compose UIs can re-read values. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    private fun open(): SharedPreferences = try {
        create()
    } catch (e: Exception) {
        // A restored backup or keystore reset makes old ciphertext unreadable: start fresh.
        Log.w(TAG, "Encrypted prefs unreadable, resetting", e)
        context.deleteSharedPreferences(FILE)
        create()
    }

    private fun create(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun get(key: String): String? = prefs.getString(key, null)?.takeIf { it.isNotBlank() }

    fun put(key: String, value: String?) {
        prefs.edit().apply { if (value.isNullOrBlank()) remove(key) else putString(key, value.trim()) }.apply()
        _version.value++
    }

    companion object {
        private const val TAG = "SecureStore"
        private const val FILE = "secure_store"
        const val FOOTBALL_API_KEY = "football_api_key"
    }
}
