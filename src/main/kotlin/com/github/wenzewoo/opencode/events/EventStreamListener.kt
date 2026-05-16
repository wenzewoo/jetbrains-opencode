package com.github.wenzewoo.opencode.events

import com.intellij.openapi.diagnostic.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture

class EventStreamListener(
    private val port: Int, private val eventHandler: EventHandler
) {
    companion object {
        private val logger = Logger.getInstance(EventStreamListener::class.java)
    }

    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private val json = Json { ignoreUnknownKeys = true }

    private var future: CompletableFuture<*>? = null

    @Volatile
    private var stopped = false

    fun start() {
        if (port <= 0) return
        future = CompletableFuture.runAsync {
            try {
                connect()
            } catch (e: Exception) {
                if (!stopped) logger.warn("SSE connection error", e)
            }
        }
    }

    fun stop() {
        stopped = true
        future?.cancel(true)
        future = null
    }

    private fun connect() {
        val request = HttpRequest.newBuilder().uri(URI.create("http://localhost:$port/global/event")).GET().build()
        repeat(30) {
            if (stopped) return
            try {
                Thread.sleep(2000)
            } catch (_: InterruptedException) {
                return
            }

            try {
                val resp = client.send(request, HttpResponse.BodyHandlers.ofLines())
                if (resp.statusCode() == 200) {
                    this.eventHandler.notifyConnectionEstablished()
                    resp.body().use { stream ->
                        stream.forEach { line ->
                            if (stopped) return@forEach
                            processLine(line)
                        }
                    }
                    return
                }
                logger.warn("SSE attempt ${it + 1}: status ${resp.statusCode()}")
            } catch (_: java.net.ConnectException) {
                logger.warn("SSE attempt ${it + 1}: connection refused")
            }
        }
    }

    private var buffer = StringBuilder()
    private var braceDepth = 0
    private var inString = false
    private var sseMode = false

    private fun processLine(line: String) {
        val content = line.trimStart()
        if (content.startsWith("data:")) {
            sseMode = true
            processJson(content.removePrefix("data:").trim())
            return
        }
        if (sseMode) {
            if (line.isEmpty()) {
                flushBuffer()
            }
            return
        }
        processJson(line)
    }

    private fun processJson(text: String) {
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                ch == '"' && (i == 0 || text[i - 1] != '\\') -> inString = !inString
                !inString && ch == '{' -> braceDepth++
                !inString && ch == '}' -> braceDepth--
            }
            buffer.append(ch)
            i++
        }
        buffer.append('\n')
        if (braceDepth == 0 && buffer.isNotEmpty()) {
            flushBuffer()
        } else if (buffer.length > 100_000) {
            buffer.clear()
            braceDepth = 0
            inString = false
        }
    }

    private fun flushBuffer() {
        val text = buffer.toString().trim()
        if (text.startsWith("{") && text.endsWith("}")) dispatch(text)
        buffer.clear()
    }

    private fun dispatch(jsonText: String) {
        try {
            val root = json.parseToJsonElement(jsonText).jsonObject
            val eventObj = root["payload"]?.jsonObject ?: root
            val type = eventObj["type"]?.jsonPrimitive?.content ?: return
            val props = eventObj["properties"]?.jsonObject
            eventHandler.handle(type, props)
        } catch (e: Exception) {
            logger.warn("Failed to parse event", e)
        }
    }
}
