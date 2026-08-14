package com.anxietywatch.mobile.fog

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

/** Session and fog identity encrypted with an AES/GCM Android Keystore key. */
class FogSecureStore(context: Context) {
    private val appContext = context.applicationContext
    private val legacy = appContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
    private val prefs = EncryptedSharedPreferences.create(
        appContext,
        SECURE_PREFS,
        MasterKey.Builder(appContext, KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    init { migrateLegacyIdentity() }

    fun getToken(): String? = prefs.getString(TOKEN, null)?.takeIf { it.isNotBlank() }

    fun setToken(token: String?) {
        prefs.edit().apply {
            if (token.isNullOrBlank()) remove(TOKEN) else putString(TOKEN, token)
        }.apply()
    }

    fun getAuth(): String? = prefs.getString(AUTH, null)?.takeIf { it.isNotBlank() }

    fun setAuth(auth: String?) {
        prefs.edit().apply {
            if (auth.isNullOrBlank()) remove(AUTH) else putString(AUTH, auth)
        }.apply()
        val token = runCatching { JSONObject(auth ?: "").optString("token") }.getOrNull()
        setToken(token)
    }

    fun clearAuth() {
        prefs.edit().remove(AUTH).remove(TOKEN).apply()
    }

    fun getIdentity(): String = prefs.getString(IDENTITY, null) ?: emptyIdentity()

    fun setIdentity(userId: String?, deviceId: String?, sessionId: String?, sequence: Long? = null) {
        val current = runCatching { JSONObject(getIdentity()) }.getOrElse { JSONObject() }
        if (userId != null) current.put("userId", userId)
        if (deviceId != null) current.put("deviceId", deviceId)
        if (sessionId != null) current.put("sessionId", sessionId)
        if (sequence != null) current.put("sequence", sequence)
        prefs.edit().putString(IDENTITY, current.toString()).apply()
    }

    @Synchronized
    fun nextSequence(): Long {
        val identity = JSONObject(getIdentity())
        val next = identity.optLong("sequence", 0L) + 1L
        identity.put("sequence", next)
        prefs.edit().putString(IDENTITY, identity.toString()).commit()
        return next
    }

    private fun migrateLegacyIdentity() {
        if (prefs.contains(IDENTITY)) return
        val identity = JSONObject()
            .put("userId", legacy.getString("userId", ""))
            .put("deviceId", legacy.getString("deviceId", ""))
            .put("sessionId", legacy.getString("sessionId", ""))
            .put("sequence", legacy.getLong("sequence", 0L))
        prefs.edit().putString(IDENTITY, identity.toString()).apply()
        legacy.edit().clear().apply()
    }

    private fun emptyIdentity() = JSONObject()
        .put("userId", "").put("deviceId", "").put("sessionId", "").put("sequence", 0L)
        .toString()

    companion object {
        const val KEY_ALIAS = "anxietywatch_fog_v1"
        private const val SECURE_PREFS = "fog_secure_v1"
        private const val LEGACY_PREFS = "fog_identity"
        private const val TOKEN = "token"
        private const val AUTH = "auth"
        private const val IDENTITY = "identity"
    }
}
