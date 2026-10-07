package com.devbehindyou.atomicfilemanager.data.backend.network

import android.content.Context

/** [KnownHosts] kept in app preferences; fingerprints are public, so they need no encryption. */
class PrefsKnownHosts(context: Context) : KnownHosts {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun fingerprint(serverKey: String): String? = prefs.getString(serverKey, null)

    override fun remember(
        serverKey: String,
        fingerprint: String,
    ) {
        prefs.edit().putString(serverKey, fingerprint).apply()
    }

    override fun forget(serverKey: String) {
        prefs.edit().remove(serverKey).apply()
    }

    private companion object {
        const val PREFS = "atomic_known_hosts"
    }
}
