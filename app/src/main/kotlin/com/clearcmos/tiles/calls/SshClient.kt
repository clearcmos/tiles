package com.clearcmos.tiles.calls

import android.content.Context
import android.util.Log
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.KeyPair
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/** What an SSH command did. [exitStatus] is -1 when the channel closed without reporting one. */
data class SshResult(
    val exitStatus: Int,
    val output: String,
)

/** A failure worth showing as is: the message names the fix. */
class SshException(
    message: String,
) : IOException(message)

/**
 * Runs one command on the workstation. Adapted from kata's client.
 *
 * The key is generated on the device on first use and never leaves it; only the public half is
 * exported, to the app's external files directory where `adb shell` can read it.
 *
 * ECDSA rather than ed25519 on purpose. jsch ships as a multi-release jar whose ed25519 support
 * lives in the Java 15+ classes, and Android ignores META-INF/versions entirely, so only the
 * Java 8 base is present at runtime.
 */
class SshClient(
    private val context: Context,
) {
    private val sshDir = File(context.filesDir, "ssh")
    private val privateKey = File(sshDir, "id_ecdsa")
    private val publicKey = File(sshDir, "id_ecdsa.pub")
    private val knownHosts = File(sshDir, "known_hosts")

    /** Generates the keypair if absent, and returns the public key line. */
    fun ensureKey(): String {
        if (!privateKey.exists()) {
            sshDir.mkdirs()
            val pair = KeyPair.genKeyPair(JSch(), KeyPair.ECDSA, KEY_BITS)
            pair.writePrivateKey(privateKey.absolutePath)
            pair.writePublicKey(publicKey.absolutePath, COMMENT)
            pair.dispose()
            privateKey.setReadable(false, false)
            privateKey.setReadable(true, true)
            Log.i(TAG, "generated ssh key at ${privateKey.absolutePath}")
        }
        val line = publicKey.readText().trim()
        mirrorPublicKey(line)
        return line
    }

    /** Puts the public key where `adb shell cat` can reach it, so setup needs no typing. */
    private fun mirrorPublicKey(line: String) {
        val external = context.getExternalFilesDir(null) ?: return
        runCatching {
            val target = File(external, "id_ecdsa.pub")
            if (!target.exists() || target.readText().trim() != line) target.writeText(line + "\n")
        }.onFailure { Log.w(TAG, "could not mirror public key", it) }
    }

    fun run(
        host: String,
        user: String,
        command: String,
        connectTimeoutMs: Int,
        commandTimeoutMs: Int,
    ): SshResult {
        ensureKey()
        if (!knownHosts.exists()) {
            sshDir.mkdirs()
            knownHosts.createNewFile()
        }

        val jsch = JSch()
        jsch.addIdentity(privateKey.absolutePath)
        jsch.setKnownHosts(knownHosts.absolutePath)

        val session = jsch.getSession(user, host, PORT)
        // Trust on first use, then pinned: the first connection records the host key and a later
        // change is refused. A tile has nobody to prompt.
        session.setConfig("StrictHostKeyChecking", "accept-new")
        session.setConfig("PreferredAuthentications", "publickey")

        try {
            session.connect(connectTimeoutMs)
        } catch (e: JSchException) {
            throw SshException(describeConnectFailure(host, user, e))
        }

        try {
            val channel = session.openChannel("exec") as ChannelExec
            channel.setCommand(command)
            val captured = ByteArrayOutputStream()
            channel.outputStream = captured
            channel.setErrStream(captured)
            channel.connect(connectTimeoutMs)

            val deadline = System.currentTimeMillis() + commandTimeoutMs
            while (!channel.isClosed && System.currentTimeMillis() < deadline) {
                Thread.sleep(POLL_MS)
            }
            val timedOut = !channel.isClosed
            val status = if (timedOut) -1 else channel.exitStatus
            channel.disconnect()
            if (timedOut) {
                throw SshException("$user@$host: '$command' was still running after ${commandTimeoutMs}ms")
            }
            return SshResult(status, captured.toString(Charsets.UTF_8.name()).trim())
        } finally {
            session.disconnect()
        }
    }

    /**
     * jsch reports every failure as one exception type, so the message is the only signal. An
     * unreachable host and a rejected key need completely different fixes.
     */
    private fun describeConnectFailure(
        host: String,
        user: String,
        e: JSchException,
    ): String {
        val detail = e.message.orEmpty()
        return when {
            detail.contains("Auth fail", ignoreCase = true) ||
                detail.contains("Auth cancel", ignoreCase = true) ->
                "$user@$host refused this app's key. Add it to ~/.ssh/authorized_keys there."

            detail.contains("HostKey", ignoreCase = true) || detail.contains("reject", ignoreCase = true) ->
                "$host presented a different host key than the one recorded. If the machine was " +
                    "rebuilt, clear the app's storage and let it pin again."

            else ->
                "Could not reach $user@$host ($detail). The workstation is probably off or this " +
                    "phone is not on the home network."
        }
    }

    private companion object {
        const val TAG = "TilesSsh"
        const val PORT = 22
        const val KEY_BITS = 256
        const val POLL_MS = 50L
        const val COMMENT = "tiles@android"
    }
}
