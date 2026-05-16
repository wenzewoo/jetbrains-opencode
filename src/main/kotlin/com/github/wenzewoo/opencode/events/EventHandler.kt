package com.github.wenzewoo.opencode.events

import com.github.wenzewoo.opencode.MessageBundle.message
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Paths

class EventHandler(private val project: Project, private val widgetContent: Content) {

    companion object {
        private val logger = Logger.getInstance(EventHandler::class.java)
    }

    fun handle(type: String, properties: JsonObject?) {
//        logger.warn("[OPENCODE_EVENT]type: $type properties: $properties")
        when (type) {
//            "session.created" -> handleSessionUpdated(properties)
            "session.updated" -> handleSessionUpdated(properties)
            "file.edited" -> handleFileEdited(properties)
            "session.idle" -> handleSessionIdle(properties)
            "permission.asked" -> handlePermissionAsked(properties)
        }
    }

    private fun handlePermissionAsked(props: JsonObject?) {
        val permission = props?.get("permission")?.jsonPrimitive?.content ?: return
        val patterns = props["patterns"]?.jsonArray?.joinToString(", ") {
            it.jsonPrimitive.content
        } ?: ""
        val msg = if (patterns.isNotEmpty()) message(
            "notification.session.permissionAsked",
            "$permission/$patterns"
        ) else message("notification.session.permissionAsked", permission)
        notify(NotificationType.WARNING, msg)
    }

    private fun handleSessionUpdated(props: JsonObject?) {
        val info = props?.get("info")?.jsonObject ?: return
        widgetContent.displayName = info["title"]?.jsonPrimitive?.content
    }

    private fun handleSessionIdle(props: JsonObject?) {
        notify(NotificationType.INFORMATION, message("notification.session.idle"))
    }

    private fun handleFileEdited(props: JsonObject?) {
        val file = props?.get("file")?.jsonPrimitive?.content ?: return
        val parentPath = Paths.get(file).parent
        if (parentPath == null) {
            logger.warn("handleFileEdited: no parent for $file")
            return
        }
        val dir = VfsUtil.findFile(parentPath, true)
        if (dir == null) {
            logger.warn("handleFileEdited: VFS dir not found for $parentPath")
            return
        }
        ApplicationManager.getApplication().invokeLater {
            WriteAction.run<RuntimeException> {
                VfsUtil.markDirtyAndRefresh(false, true, true, dir)
            }
        }
        // notify(NotificationType.INFORMATION, message("notification.vfs.fileRefreshed", file))
    }

    fun notifyConnectionEstablished() {
        notify(NotificationType.INFORMATION, message("notification.sse.connected"))
    }

    private fun notify(type: NotificationType, content: String) {
        ApplicationManager.getApplication().invokeLater {
            val notification = NotificationGroupManager.getInstance().getNotificationGroup("OpenCode")
                .createNotification("${widgetContent.displayName}", content, type)

            notification.addAction(NotificationAction.createSimple(message("notification.navigateToTerminal")) {
                val toolWindow =
                    ToolWindowManager.getInstance(project).getToolWindow("OpenCode Agent") ?: return@createSimple
                toolWindow.activate(
                    Runnable { toolWindow.contentManager.setSelectedContent(widgetContent) }, true, true
                )
            })

            notification.notify(project)
        }
    }
}
