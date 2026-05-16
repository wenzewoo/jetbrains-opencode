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

class EditorSendFileRefAction : AnAction() {
    init {
        templatePresentation.icon = AllIcons.Actions.Upload
    }

    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val vf = editor.virtualFile ?: return
        val terminals = ToolWindowTabPicker.getTerminals(project)
        if (terminals.isEmpty()) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("OpenCode")
                .createNotification(message("notification.noTerminals"), NotificationType.WARNING)
                .notify(project)
            return
        }

        val ref = FileRefUtils.get(project, vf, editor)
        ToolWindowTabPicker.showPicker(project, terminals) { term ->
            OpenCodeService.appendPrompt(term.port, ref) { ok ->
                if (!ok) {
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("OpenCode")
                        .createNotification(message("notification.sendFailed", ref), NotificationType.ERROR)
                        .notify(project)
                }
                ToolWindowTabPicker.navigateToTerminal(project, term.port)
            }
        }
    }
}
