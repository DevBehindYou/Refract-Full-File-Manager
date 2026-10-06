package com.devbehindyou.atomicfilemanager.data.vault

import android.content.Context
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.streamingaead.StreamingAeadConfig

/**
 * The vault key (ALL_IN_ONE_PLAN.md 2.5): one Tink keyset, created on first use, stored in app
 * preferences wrapped by an Android Keystore key, so it is useless off this device. If the app is
 * uninstalled or the device reset, vault files can't be recovered; the UI says so before first use.
 * Access to the vault screens stays behind the app lock.
 */
object VaultKeys {
    private const val KEYSET_NAME = "atomic_vault_keyset"
    private const val PREFS_FILE = "atomic_vault_prefs"
    private const val MASTER_KEY_URI = "android-keystore://atomic_vault_master"
    private const val TEMPLATE = "AES256_GCM_HKDF_1MB"

    @Volatile private var cached: StreamingAead? = null

    fun streamingAead(context: Context): StreamingAead =
        cached ?: synchronized(this) {
            cached ?: create(context.applicationContext).also { cached = it }
        }

    private fun create(context: Context): StreamingAead {
        StreamingAeadConfig.register()
        val handle =
            AndroidKeysetManager.Builder()
                .withSharedPref(context, KEYSET_NAME, PREFS_FILE)
                .withKeyTemplate(KeyTemplates.get(TEMPLATE))
                .withMasterKeyUri(MASTER_KEY_URI)
                .build()
                .keysetHandle
        return handle.getPrimitive(RegistryConfiguration.get(), StreamingAead::class.java)
    }
}
