package com.devbehindyou.refract.data.backend.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.devbehindyou.refract.domain.model.NetworkProtocol
import com.devbehindyou.refract.domain.model.NetworkServerConfig
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Manages persistence of saved network locations and encrypted credential storage.
 */
class NetworkCredentialsStore(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val secretKey: SecretKeySpec by lazy {
        val digest = MessageDigest.getInstance("SHA-256")
        val seed = "${context.packageName}.refract.credentials.v1"
        val keyBytes = digest.digest(seed.toByteArray(StandardCharsets.UTF_8))
        SecretKeySpec(keyBytes, "AES")
    }

    private val ivSpec = IvParameterSpec(ByteArray(16) { (it * 7).toByte() })

    fun getAllServers(): List<NetworkServerConfig> {
        val jsonStr = prefs.getString(KEY_SERVERS, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<NetworkServerConfig>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val protocolStr = obj.optString("protocol", NetworkProtocol.FTP.name)
                val protocol = runCatching { NetworkProtocol.valueOf(protocolStr) }.getOrDefault(NetworkProtocol.FTP)
                list.add(
                    NetworkServerConfig(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        protocol = protocol,
                        host = obj.getString("host"),
                        port = obj.optInt("port", protocol.defaultPort),
                        username = obj.optString("username", ""),
                        remotePath = obj.optString("remotePath", "/"),
                        anonymous = obj.optBoolean("anonymous", false),
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getServerById(id: String): NetworkServerConfig? {
        return getAllServers().firstOrNull { it.id == id }
    }

    fun saveServer(config: NetworkServerConfig, password: String?) {
        val servers = getAllServers().filter { it.id != config.id }.toMutableList()
        servers.add(config)

        val jsonArray = JSONArray()
        for (server in servers) {
            val obj = JSONObject().apply {
                put("id", server.id)
                put("name", server.name)
                put("protocol", server.protocol.name)
                put("host", server.host)
                put("port", server.port)
                put("username", server.username)
                put("remotePath", server.remotePath)
                put("anonymous", server.anonymous)
            }
            jsonArray.put(obj)
        }

        prefs.edit().putString(KEY_SERVERS, jsonArray.toString()).apply()

        if (password != null) {
            savePassword(config.id, password)
        }
    }

    fun deleteServer(id: String) {
        val servers = getAllServers().filter { it.id != id }
        val jsonArray = JSONArray()
        for (server in servers) {
            val obj = JSONObject().apply {
                put("id", server.id)
                put("name", server.name)
                put("protocol", server.protocol.name)
                put("host", server.host)
                put("port", server.port)
                put("username", server.username)
                put("remotePath", server.remotePath)
                put("anonymous", server.anonymous)
            }
            jsonArray.put(obj)
        }
        prefs.edit()
            .putString(KEY_SERVERS, jsonArray.toString())
            .remove(KEY_PASS_PREFIX + id)
            .apply()
    }

    fun savePassword(serverId: String, pass: String) {
        try {
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
            val encrypted = cipher.doFinal(pass.toByteArray(StandardCharsets.UTF_8))
            val encoded = Base64.encodeToString(encrypted, Base64.NO_WRAP)
            prefs.edit().putString(KEY_PASS_PREFIX + serverId, encoded).apply()
        } catch (_: Exception) {
            // Fallback plain storage if encryption fails on legacy runtime
            prefs.edit().putString(KEY_PASS_PREFIX + serverId, pass).apply()
        }
    }

    fun getPassword(serverId: String): String? {
        val raw = prefs.getString(KEY_PASS_PREFIX + serverId, null) ?: return null
        return try {
            val decoded = Base64.decode(raw, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            val decrypted = cipher.doFinal(decoded)
            String(decrypted, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            raw
        }
    }

    companion object {
        private const val PREFS_NAME = "refract_network_credentials"
        private const val KEY_SERVERS = "saved_servers"
        private const val KEY_PASS_PREFIX = "server_pass_"
        private const val AES_TRANSFORMATION = "AES/CBC/PKCS5Padding"
    }
}
