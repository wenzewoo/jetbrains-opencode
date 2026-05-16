package com.github.wenzewoo.opencode.actions

import com.github.wenzewoo.opencode.MessageBundle.message
import com.github.wenzewoo.opencode.services.OpenCodeService
import com.github.wenzewoo.opencode.views.ToolWindowTabPicker
import com.github.wenzewoo.opencode.utils.FileRefUtils
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

class SendFileRefIntentionAction : IntentionAction {
    override fun getFamilyName() = message("intention.fileRef.familyName")
    override fun getText() = message("intention.fileRef.text")

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        return editor != null;
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null) return
        val vf = editor.virtualFile ?: file?.virtualFile ?: return
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
    override fun startInWriteAction() = false

}


