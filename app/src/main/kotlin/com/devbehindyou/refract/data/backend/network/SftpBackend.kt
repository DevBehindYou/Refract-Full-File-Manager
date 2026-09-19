package com.devbehindyou.refract.data.backend.network

import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.repository.BackendType

/**
 * SFTP (SSH File Transfer Protocol) backend.
 *
 * **Not implemented.** SFTP runs over SSH, which cannot be spoken over a bare socket — it
 * needs a real SSH library (key exchange, host-key verification, cipher negotiation,
 * channel multiplexing). Until one is added, every operation fails visibly rather than
 * pretending to succeed. See [UnimplementedProtocolBackend] for what the previous
 * fabricating implementation did and why it was removed.
 *
 * The id scheme (`sftp:<serverId>:<path>`), [NetworkCredentialsStore] entry and UI wiring
 * all remain, so implementing this later is a matter of filling in the protocol.
 */
class SftpBackend(
    @Suppress("unused") private val credentialsStore: NetworkCredentialsStore,
) : UnimplementedProtocolBackend() {
    override val type: BackendType = BackendType.SFTP

    override val protocolName: String = "SFTP"

    override fun canHandle(id: FileNodeId): Boolean = id.prefix == FileNodeId.Prefix.SFTP
}
