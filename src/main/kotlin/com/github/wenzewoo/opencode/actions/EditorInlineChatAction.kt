package com.github.wenzewoo.opencode.actions

import com.github.wenzewoo.opencode.utils.FileRefUtils
import com.github.wenzewoo.opencode.views.InlineChat
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.icons.AllIcons

class EditorInlineChatAction : AnAction() {
    init {
        templatePresentation.icon = AllIcons.General.Balloon
    }

    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val vf = editor.virtualFile ?: return
        val ref = FileRefUtils.get(project, vf, editor)
        InlineChat.show(project, editor, ref)
    }
}
