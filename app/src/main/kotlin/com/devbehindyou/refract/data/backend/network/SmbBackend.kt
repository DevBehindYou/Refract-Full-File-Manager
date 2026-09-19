package com.devbehindyou.refract.data.backend.network

import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.repository.BackendType

/**
 * SMB / CIFS backend for Windows network shares and Samba.
 *
 * **Not implemented.** SMB2/3 requires a real protocol library (NTLM/Kerberos
 * authentication, session setup, tree connect, message signing); it cannot be spoken over
 * a bare socket. Until one is added, every operation fails visibly rather than pretending
 * to succeed. See [UnimplementedProtocolBackend] for what the previous fabricating
 * implementation did and why it was removed.
 *
 * The id scheme (`smb:<serverId>:<path>`), [NetworkCredentialsStore] entry and UI wiring
 * all remain, so implementing this later is a matter of filling in the protocol.
 */
class SmbBackend(
    @Suppress("unused") private val credentialsStore: NetworkCredentialsStore,
) : UnimplementedProtocolBackend() {
    override val type: BackendType = BackendType.SMB

    override val protocolName: String = "SMB"

    override fun canHandle(id: FileNodeId): Boolean = id.prefix == FileNodeId.Prefix.SMB
}
