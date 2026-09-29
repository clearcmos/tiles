package com.clearcmos.tiles.calls

import org.junit.Assert.assertTrue
import org.junit.Test

class SshFailureTest {
    private fun describe(detail: String) = describeConnectFailure("ws.example.test", "alice", detail)

    @Test
    fun `a rejected key tells the user to authorize it`() {
        assertTrue(describe("Auth fail").contains("authorized_keys"))
        assertTrue(describe("Auth cancel for methods 'publickey'").contains("alice@ws.example.test refused"))
    }

    @Test
    fun `a changed host key tells the user to clear storage`() {
        assertTrue(describe("HostKey has been changed: ws.example.test").contains("clear the app's storage"))
        assertTrue(describe("connection was rejected").contains("different host key"))
    }

    @Test
    fun `anything else is reported as unreachable with the original detail`() {
        val message = describe("timeout: socket is not established")
        assertTrue(message.contains("Could not reach alice@ws.example.test"))
        assertTrue(message.contains("timeout: socket is not established"))
    }
}
