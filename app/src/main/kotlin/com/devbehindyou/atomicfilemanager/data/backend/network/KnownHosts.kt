package com.devbehindyou.atomicfilemanager.data.backend.network

import java.util.concurrent.ConcurrentHashMap

/**
 * Host-key fingerprints the user has trusted, one per server (ALL_IN_ONE_PLAN.md 3.1). The first
 * connection stores the server's key (trust on first use); a later different key is refused,
 * because the server may not be the one the user saved.
 */
interface KnownHosts {
    fun fingerprint(serverKey: String): String?

    fun remember(
        serverKey: String,
        fingerprint: String,
    )

    /** Forgets a server's key, for when the user confirms the server really changed. */
    fun forget(serverKey: String)
}

/** For tests and previews; production keeps keys in [PrefsKnownHosts]. */
class InMemoryKnownHosts : KnownHosts {
    private val keys = ConcurrentHashMap<String, String>()

    override fun fingerprint(serverKey: String): String? = keys[serverKey]

    override fun remember(
        serverKey: String,
        fingerprint: String,
    ) {
        keys[serverKey] = fingerprint
    }

    override fun forget(serverKey: String) {
        keys.remove(serverKey)
    }
}
