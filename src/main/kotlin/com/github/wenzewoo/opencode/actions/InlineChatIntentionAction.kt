package com.github.wenzewoo.opencode.actions

import com.github.wenzewoo.opencode.MessageBundle.message
import com.github.wenzewoo.opencode.utils.FileRefUtils
import com.github.wenzewoo.opencode.views.InlineChat
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

class InlineChatIntentionAction : IntentionAction {
    override fun getFamilyName() = message("intention.inlineChat.familyName")
    override fun getText() = message("intention.inlineChat.text")

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        return editor != null
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null) return
        val vf = editor.virtualFile ?: file?.virtualFile ?: return
        val ref = FileRefUtils.get(project, vf, editor)

        InlineChat.show(project, editor, ref)
    }
    override fun startInWriteAction() = false
}
