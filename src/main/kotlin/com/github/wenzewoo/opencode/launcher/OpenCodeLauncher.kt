package com.github.wenzewoo.opencode.launcher

import com.github.wenzewoo.opencode.settings.OpenCodeSettings
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

sealed class SessionMode {
    data object New : SessionMode()
    data object Continue : SessionMode()
    data class Fork(val sessionId: String, val title: String) : SessionMode()
    data class History(val sessionId: String, val title: String) : SessionMode()
}

object OpenCodeLauncher {

    fun resolveBinaryPath(): String {
        val settings = OpenCodeSettings.getInstance()
        val settingsPath = settings.state.cliPath.trim()
        if (settingsPath.isNotEmpty() && File(settingsPath).canExecute()) return settingsPath


        val resolved = resolveAbsolutePath()
        val resolvedFile = File(resolved)
        if (resolvedFile.exists() && resolvedFile.canExecute()) {
            return resolvedFile.absolutePath
        }
        return "opencode"
    }

    fun buildLaunchCommand(sessionMode: SessionMode = SessionMode.Continue): List<String> {
        val binary = resolveBinaryPath()
        val port = findRandomPort()
        val args = mutableListOf(binary, "--port", port.toString())
        when (sessionMode) {
            is SessionMode.Continue -> args.add("--continue")
            is SessionMode.History -> { args.add("--session"); args.add(sessionMode.sessionId) }
            is SessionMode.Fork -> { args.add("--session"); args.add(sessionMode.sessionId); args.add("--fork") }
            is SessionMode.New -> {}
        }
        return args
    }

    private fun findRandomPort(): Int = try {
        ServerSocket(0).use { it.localPort }
    } catch (_: Exception) {
        4096
    }

    private inline fun <T> runQuietly(block: () -> T): T? =
        try { block() } catch (_: Throwable) { null }

    private fun resolveAbsolutePath(): String {
        val binary = "opencode"
        if (File(binary).isAbsolute) return binary
        val isWin = System.getProperty("os.name").lowercase().contains("win")
        val cmd = if (isWin) listOf("where", binary) else listOf("which", binary)
        return runQuietly {
            val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
            val result = proc.inputStream.bufferedReader().readLine()?.trim()
            proc.waitFor(5, TimeUnit.SECONDS)
            if (!result.isNullOrEmpty() && File(result).exists()) result else binary
        } ?: binary
    }
}
