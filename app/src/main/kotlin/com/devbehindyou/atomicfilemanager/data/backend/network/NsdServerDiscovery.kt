package com.devbehindyou.atomicfilemanager.data.backend.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.devbehindyou.atomicfilemanager.domain.model.NetworkProtocol
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import kotlin.coroutines.resume

/**
 * Finds file servers that announce themselves over mDNS / DNS-SD (ALL_IN_ONE_PLAN.md 3.2):
 * NAS boxes, Macs and Linux machines with Samba, SSH or WebDAV. Nothing is sent but the standard
 * multicast queries, and nothing is saved; a found server only fills in the add-server form.
 *
 * Browsing runs while the flow is collected. Answers are resolved one at a time, because older
 * Android versions refuse a second resolve while one is running.
 */
class NsdServerDiscovery(context: Context) {
    private val nsd: NsdManager? = context.getSystemService(NsdManager::class.java)

    fun discover(): Flow<DiscoveredServer> =
        callbackFlow {
            val manager =
                nsd ?: run {
                    close()
                    return@callbackFlow
                }
            val toResolve = Channel<Pair<NsdServiceInfo, NetworkProtocol>>(Channel.UNLIMITED)
            val listeners =
                ServerDiscovery.SERVICE_TYPES.map { (type, protocol) ->
                    listener { found -> toResolve.trySend(found to protocol) }.also {
                        runCatching { manager.discoverServices(type.removeSuffix("."), NsdManager.PROTOCOL_DNS_SD, it) }
                    }
                }
            launch {
                for ((info, protocol) in toResolve) {
                    resolve(manager, info, protocol)?.let { send(it) }
                }
            }
            awaitClose {
                listeners.forEach { runCatching { manager.stopServiceDiscovery(it) } }
                toResolve.close()
            }
        }

    private fun listener(onFound: (NsdServiceInfo) -> Unit) =
        object : NsdManager.DiscoveryListener {
            override fun onServiceFound(serviceInfo: NsdServiceInfo) = onFound(serviceInfo)

            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(
                serviceType: String,
                errorCode: Int,
            ) = Unit

            override fun onStopDiscoveryFailed(
                serviceType: String,
                errorCode: Int,
            ) = Unit
        }

    // resolveService and host are deprecated on API 34 in favour of callbacks that need API 34;
    // they still work there, and minSdk is 27.
    @Suppress("DEPRECATION")
    private suspend fun resolve(
        manager: NsdManager,
        info: NsdServiceInfo,
        protocol: NetworkProtocol,
    ): DiscoveredServer? {
        val resolved =
            withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                suspendCancellableCoroutine<NsdServiceInfo?> { cont ->
                    val callback =
                        object : NsdManager.ResolveListener {
                            override fun onResolveFailed(
                                serviceInfo: NsdServiceInfo,
                                errorCode: Int,
                            ) {
                                if (cont.isActive) cont.resume(null)
                            }

                            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                if (cont.isActive) cont.resume(serviceInfo)
                            }
                        }
                    runCatching { manager.resolveService(info, callback) }
                        .onFailure { if (cont.isActive) cont.resume(null) }
                }
            } ?: return null
        val address = resolved.host?.takeIf { it is Inet4Address } ?: resolved.host ?: return null
        val path = resolved.attributes["path"]?.let { String(it, Charsets.UTF_8) }
        return DiscoveredServer(
            name = resolved.serviceName,
            protocol = protocol,
            host = ServerDiscovery.hostFrom(address.hostAddress ?: return null),
            port = resolved.port,
            path = ServerDiscovery.pathFrom(path),
        )
    }

    private companion object {
        const val RESOLVE_TIMEOUT_MS = 5_000L
    }
}
