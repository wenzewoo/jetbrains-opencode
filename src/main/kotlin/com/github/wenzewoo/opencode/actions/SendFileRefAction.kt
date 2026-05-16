package com.github.wenzewoo.opencode.actions

import com.github.wenzewoo.opencode.MessageBundle.message
import com.github.wenzewoo.opencode.services.OpenCodeService
import com.github.wenzewoo.opencode.views.ToolWindowTabPicker
import com.github.wenzewoo.opencode.utils.FileRefUtils
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.icons.AllIcons

class SendFileRefAction : AnAction() {
    init {
        templatePresentation.icon = AllIcons.Actions.Upload
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabled = file != null && file.isValid
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val terminals = ToolWindowTabPicker.getTerminals(project)
        if (terminals.isEmpty()) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("OpenCode")
                .createNotification(message("notification.noTerminals"), NotificationType.WARNING)
                .notify(project)
            return
        }

        val fileRef = FileRefUtils.get(project, file, null)
        ToolWindowTabPicker.showPicker(project, terminals) { term ->
            OpenCodeService.appendPrompt(term.port, fileRef) { ok ->
                if (!ok) {
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("OpenCode")
                        .createNotification(message("notification.sendFailed", fileRef), NotificationType.ERROR)
                        .notify(project)
                }
                ToolWindowTabPicker.navigateToTerminal(project, term.port)
            }
        }
    }
}
