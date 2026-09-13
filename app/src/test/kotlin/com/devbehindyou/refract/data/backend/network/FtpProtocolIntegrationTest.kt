package com.devbehindyou.refract.data.backend.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Real loopback sockets and protocol replies; no public server or user credentials. */
class FtpProtocolIntegrationTest {
    @Test
    fun `login accepts multiline greeting and correct credentials`() {
        conversation(false, listOf("USER test" to "331 Password", "PASS fixture" to "230 Logged in")) { client ->
            client.connect()
            client.login("test", "fixture")
        }
    }

    @Test
    fun `authentication rejection is surfaced`() {
        conversation(false, listOf("USER test" to "331 Password", "PASS fixture" to "530 Rejected")) { client ->
            client.connect()
            assertThrows(IllegalStateException::class.java) { client.login("test", "fixture") }
        }
    }

    @Test
    fun `FTPS refuses plaintext fallback when server rejects TLS`() {
        conversation(true, listOf("AUTH TLS" to "500 TLS unavailable")) { client ->
            assertThrows(Exception::class.java) { client.connect() }
        }
    }

    private fun conversation(
        tls: Boolean,
        replies: List<Pair<String, String>>,
        action: (FtpProtocolClient) -> Unit,
    ) {
        val executor = Executors.newSingleThreadExecutor()
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 5000
            val future =
                executor.submit {
                    server.accept().use { socket ->
                        socket.soTimeout = 5000
                        val reader = socket.getInputStream().bufferedReader()
                        val writer = socket.getOutputStream().bufferedWriter()
                        writer.write("220-Fixture\r\n220 Ready\r\n")
                        writer.flush()
                        for ((expected, response) in replies) {
                            assertEquals(expected, reader.readLine())
                            writer.write("$response\r\n")
                            writer.flush()
                        }
                    }
                }
            val client = FtpProtocolClient("127.0.0.1", server.localPort, tls)
            try {
                action(client)
                future.get(10, TimeUnit.SECONDS)
            } finally {
                client.close()
                executor.shutdownNow()
            }
        }
    }
}
