package com.github.wenzewoo.opencode.views

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.util.IconLoader

class ToolWindowRegistrar : StartupActivity.DumbAware {
    override fun runActivity(project: Project) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            val twm = ToolWindowManager.getInstance(project)
            if (twm.getToolWindow("OpenCode Agent") != null) return@invokeLater

            twm.registerToolWindow("OpenCode Agent") {
                anchor = ToolWindowAnchor.RIGHT
                icon = IconLoader.getIcon("/icons/opencode.svg", ToolWindowRegistrar::class.java)
            }

            var initialized = false
            project.messageBus.connect(project).subscribe(
                ToolWindowManagerListener.TOPIC,
                object : ToolWindowManagerListener {
                    override fun toolWindowShown(toolWindow: ToolWindow) {
                        if (toolWindow.id == "OpenCode Agent" && !initialized) {
                            initialized = true
                            setupToolWindowContent(project, toolWindow)
                        }
                    }
                }
            )
        }
    }
}
