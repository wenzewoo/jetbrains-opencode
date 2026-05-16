package com.github.wenzewoo.opencode.views

import com.github.wenzewoo.opencode.MessageBundle.message
import com.github.wenzewoo.opencode.launcher.OpenCodeLauncher
import com.github.wenzewoo.opencode.launcher.SessionMode
import com.github.wenzewoo.opencode.events.EventHandler
import com.github.wenzewoo.opencode.events.EventStreamListener
import com.github.wenzewoo.opencode.services.OpenCodeService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.ui.content.Content
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import java.awt.BorderLayout
import java.io.File
import java.util.WeakHashMap
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.text.iterator

class ToolWindowContent(

    private val project: Project,
    private val sessionMode: SessionMode,
    private val mainPanel: JPanel,
    private val content: Content
) {
    companion object {
        private val logger = Logger.getInstance(ToolWindowContent::class.java)
        private val cleanupKey = Any()
        private val widgets = WeakHashMap<Project, TerminalWidget>()
        val OPENCODE_PORT_KEY: Key<Int> = Key.create("opencode.port")
        val OPENCODE_SESSION_ID_KEY: Key<String> = Key.create("opencode.sessionId")
        val OPENCODE_SESSION_TITLE_KEY: Key<String> = Key.create("opencode.sessionTitle")

        fun cleanupTab(panel: JPanel) {
            val disposable = panel.getClientProperty(cleanupKey) as? Disposable
            if (disposable != null) {
                Disposer.dispose(disposable)
                panel.putClientProperty(cleanupKey, null)
            }
        }
    }

    fun install() {
        val parentDisposable = Disposer.newDisposable("opencode-tab")
        mainPanel.putClientProperty(cleanupKey, parentDisposable)
        val baseDir = project.basePath ?: System.getProperty("user.dir")

        ApplicationManager.getApplication().executeOnPooledThread {
            val launchCommand: List<String>?
            try {
                launchCommand = OpenCodeLauncher.buildLaunchCommand(sessionMode)
            } catch (e: Exception) {
                logger.warn("Failed to build launch command", e)
                invokeOnEdt { showError(message("terminal.error.buildCommand", e.message ?: "")) }
                return@executeOnPooledThread
            }

            if (launchCommand.firstOrNull().isNullOrBlank()) {
                invokeOnEdt { showError(message("terminal.error.binaryNotConfigured")) }
                return@executeOnPooledThread
            }

            invokeOnEdt { installTerminal(baseDir, parentDisposable, launchCommand) }
        }
    }

    private fun installTerminal(baseDir: String, parentDisposable: Disposable, launchCommand: List<String>) {
        val terminalWidget = try {
            val runner = TerminalToolWindowManager.getInstance(project).terminalRunner
            val options = ShellStartupOptions.Builder()
                .workingDirectory(baseDir)
                .build()
            runner.startShellTerminalWidget(parentDisposable, options, true)
        } catch (e: Exception) {
            logger.warn("Failed to create terminal widget", e)
            showError(message("terminal.error.createWidget", e.message ?: ""))
            return
        }

        widgets[project] = terminalWidget
        mainPanel.add(terminalWidget.component, BorderLayout.CENTER)

        val portStr = parsePort(launchCommand)
        val port = portStr.toIntOrNull() ?: 0
        content.putUserData(OPENCODE_PORT_KEY, port)
        content.displayName = when (sessionMode) {
            is SessionMode.New -> message("terminal.displayName.newSession")
            is SessionMode.Continue -> message("terminal.displayName.resumeSession")
            is SessionMode.Fork -> if (sessionMode.title.isNotBlank()) "Fork: ${sessionMode.title}" else message("terminal.displayName.forkSession")
            is SessionMode.History -> sessionMode.title.ifBlank { message("terminal.displayName.historySession") }
        }
        if (sessionMode is SessionMode.Continue) fetchSessionTitle(baseDir)

        val handler = EventHandler(project, content)
        val listener = EventStreamListener(port, handler)
        listener.start()
        Disposer.register(parentDisposable) { listener.stop() }


        Disposer.register(parentDisposable) {
            widgets.remove(project)
            runQuietly {
                val process = terminalWidget.ttyConnector?.let {
                    ShellTerminalWidget.getProcessTtyConnector(it)?.process
                }
                process?.destroy() ?: terminalWidget.ttyConnector?.write("\u0003")
            }
            runQuietly { (terminalWidget as? Disposable)?.let(Disposer::dispose) }
        }

        try {
            executeCommand(terminalWidget, launchCommand.toShellCommand())
        } catch (e: Exception) {
            logger.warn("Failed to start command", e)
            mainPanel.removeAll()
            mainPanel.revalidate()
            mainPanel.repaint()
            showError(message("terminal.error.startCommand", e.message ?: ""))
        }
    }

    private inline fun <T> runQuietly(block: () -> T): T? =
        try { block() } catch (_: Throwable) { null }

    private fun invokeOnEdt(action: () -> Unit) =
        ApplicationManager.getApplication().invokeLater(action)

    private fun parsePort(cmd: List<String>): String =
        cmd.indexOf("--port").let { i ->
            if (i >= 0 && i + 1 < cmd.size) cmd[i + 1] else "?"
        }

    private fun executeCommand(widget: TerminalWidget, command: String) {
        val shellWidget = widget as? ShellTerminalWidget
        if (shellWidget != null) {
            shellWidget.executeCommand(command)
        } else {
            widget.sendCommandToExecute(command)
        }
    }

    private fun isUsableBinary(binary: String, resolvedBinary: String): Boolean {
        val resolvedFile = File(resolvedBinary)
        if (resolvedFile.isAbsolute) return resolvedFile.exists() && resolvedFile.canExecute()
        val rawFile = File(binary)
        return rawFile.isAbsolute && rawFile.exists() && rawFile.canExecute()
    }

    private fun List<String>.toShellCommand(): String = joinToString(" ") { it.shellQuote() }

    private fun String.shellQuote(): String {
        val windows = System.getProperty("os.name").lowercase().contains("win")
        return if (windows) {
            if (isEmpty()) "\"\"" else if (any { it.isWhitespace() || it == '"' }) {
                "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
            } else {
                this
            }
        } else {
            if (isEmpty()) "''" else if (any { it.isWhitespace() || it in "'\\\"\$`()[]{}*?&;|<>" }) {
                "'" + replace("'", "'\"'\"'") + "'"
            } else {
                this
            }
        }
    }

    private fun escapeHtml(value: String): String = buildString(value.length) {
        for (ch in value) {
            append(
                when (ch) {
                    '<' -> "&lt;"
                    '>' -> "&gt;"
                    '&' -> "&amp;"
                    '"' -> "&quot;"
                    else -> ch
                }
            )
        }
    }

    private fun fetchSessionTitle(baseDir: String) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val sessions = OpenCodeService.fetchSessions(baseDir, limit = 1)
                val session = sessions.firstOrNull()
                if (session != null) {
                    ApplicationManager.getApplication().invokeLater {
                        content.putUserData(OPENCODE_SESSION_ID_KEY, session.id)
                        content.putUserData(OPENCODE_SESSION_TITLE_KEY, session.title)
                        if (session.title.isNotBlank()) content.displayName = session.title
                    }
                }
            } catch (_: Exception) { }
        }
    }

    private fun showError(message: String) {
        mainPanel.removeAll()
        mainPanel.add(JPanel(BorderLayout()).apply {
            add(JLabel("<html><center>$message</center></html>"), BorderLayout.CENTER)
        }, BorderLayout.CENTER)
        mainPanel.revalidate()
        mainPanel.repaint()
    }
}
