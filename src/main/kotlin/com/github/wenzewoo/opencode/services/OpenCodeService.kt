package com.github.wenzewoo.opencode.services

import com.github.wenzewoo.opencode.launcher.OpenCodeLauncher
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.text.trim

@Serializable
data class SessionInfo(
    val id: String,
    val title: String,
    val created: Long,
    val updated: Long,
    val projectId: String,
    val directory: String
)

object OpenCodeService {
    private val logger = Logger.getInstance(OpenCodeService::class.java)
    private val app = ApplicationManager.getApplication()

    private fun newClient(): HttpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(3))
        .build()

    fun fetchSessions(workingDir: String? = null, limit: Int = 50): List<SessionInfo> {
        val binary = OpenCodeLauncher.resolveBinaryPath()
        val cmd = listOf(binary, "session", "list", "--format", "json", "-n", limit.toString())
        return try {
            val proc = OpenCodeLauncher.createProcessBuilder(cmd)
                .directory(if (workingDir != null) File(workingDir) else null)
                .start()
            val output = proc.inputStream.bufferedReader().readText()
            proc.waitFor(10, TimeUnit.SECONDS)

            val trimmed = output.trim()
            if (!trimmed.startsWith("[")) {
                logger.warn("fetchSessions: unexpected output prefix: ${trimmed.take(80)}")
                return emptyList()
            }
            return try {
                Json.decodeFromString<List<SessionInfo>>(trimmed)
            } catch (ex: Exception) {
                logger.warn("Decode fetch sessions", ex)
                emptyList()
            }
        } catch (e: Exception) {
            logger.warn("Error fetch sessions", e)
            emptyList()
        }
    }

    private fun clearPromptSync(port: Int): Boolean = try {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port/tui/clear-prompt"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.noBody())
            .timeout(Duration.ofSeconds(5))
            .build()
        newClient().send(request, HttpResponse.BodyHandlers.ofString()).statusCode() == 200
    } catch (ex: Exception) {
        logger.warn("Error clear prompt", ex)
        false
    }

    fun clearPrompt(port: Int, onResult: ((Boolean) -> Unit)? = null) {
        runOnPooled {
            val ok = clearPromptSync(port)
            onResult?.let { invokeOnEdt { it(ok) } }
        }
    }

    private fun appendPromptSync(port: Int, text: String): Boolean = try {
        val body = """{"text":${Json.encodeToString(text)}}"""
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port/tui/append-prompt"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .timeout(Duration.ofSeconds(5))
            .build()
        newClient().send(request, HttpResponse.BodyHandlers.ofString()).statusCode() == 200
    } catch (ex: Exception) {
        logger.warn("Error append prompt", ex)
        false
    }

    fun appendPrompt(port: Int, text: String, onResult: ((Boolean) -> Unit)? = null) {
        runOnPooled {
            val ok = appendPromptSync(port, text)
            onResult?.let { invokeOnEdt { it(ok) } }
        }
    }

    private fun submitPromptSync(port: Int): Boolean = try {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port/tui/submit-prompt"))
            .POST(HttpRequest.BodyPublishers.noBody())
            .timeout(Duration.ofSeconds(5))
            .build()
        newClient().send(request, HttpResponse.BodyHandlers.ofString()).statusCode() == 200
    } catch (ex: Exception) {
        logger.warn("Error submit prompt", ex)
        false
    }

    fun submitPrompt(port: Int, onResult: ((Boolean) -> Unit)? = null) {
        runOnPooled {
            val ok = submitPromptSync(port)
            onResult?.let { invokeOnEdt { it(ok) } }
        }
    }

    private fun runOnPooled(action: () -> Unit) = app.executeOnPooledThread(action)
    private fun invokeOnEdt(action: () -> Unit) = app.invokeLater(action)

    fun sendPrompt(port: Int, text: String, onResult: (Boolean) -> Unit) {
        if (text.isBlank()) {
            invokeOnEdt { onResult(false) }
            return
        }
        runOnPooled {
            try {
                clearPromptSync(port)
                appendPromptSync(port, text)
                submitPromptSync(port)
                invokeOnEdt { onResult(true) }
            } catch (ex: Exception) {
                logger.warn("Error sending prompt", ex)
                invokeOnEdt { onResult(false) }
            }
        }
    }
}