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

    private val isWin by lazy { System.getProperty("os.name").lowercase().contains("win") }
    private val binaryName by lazy { if (isWin) "opencode.exe" else "opencode" }

    private val shellPath by lazy {
        val shell = System.getenv("SHELL") ?: "/bin/zsh"
        runQuietly {
            val proc = ProcessBuilder(shell, "-l", "-c", "echo \$PATH")
                .redirectErrorStream(true).start()
            val result = proc.inputStream.bufferedReader().readLine()?.trim()
            proc.waitFor(5, TimeUnit.SECONDS)
            result
        }?.ifBlank { null }
    }

    fun resolveBinaryPath(): String {
        val settings = OpenCodeSettings.getInstance()
        val settingsPath = settings.state.cliPath.trim()
        if (settingsPath.isNotEmpty() && File(settingsPath).canExecute()) return settingsPath

        val resolved = resolveFromShell()
        if (resolved != null && File(resolved).canExecute()) return resolved

        throw IllegalStateException("opencode binary not found. Please configure the path in Settings → Tools → OpenCode Agent")
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

    fun createProcessBuilder(cmd: List<String>): ProcessBuilder {
        val pb = ProcessBuilder(cmd)
        val path = shellPath
        if (path != null) {
            val current = pb.environment()["PATH"] ?: System.getenv("PATH") ?: ""
            pb.environment()["PATH"] = "$path:$current"
        }
        return pb
    }

    private fun findRandomPort(): Int = try {
        ServerSocket(0).use { it.localPort }
    } catch (_: Exception) {
        4096
    }

    private inline fun <T> runQuietly(block: () -> T): T? =
        try { block() } catch (_: Throwable) { null }

    private fun resolveFromShell(): String? {
        val shell = System.getenv("SHELL") ?: "/bin/zsh"
        val cmd = if (isWin) listOf("where", binaryName) else listOf(shell, "-l", "-c", "which $binaryName")
        return runQuietly {
            val proc = createProcessBuilder(cmd).redirectErrorStream(true).start()
            val result = proc.inputStream.bufferedReader().readLine()?.trim()
            proc.waitFor(5, TimeUnit.SECONDS)
            if (!result.isNullOrEmpty() && File(result).canExecute()) result else null
        }
    }
}