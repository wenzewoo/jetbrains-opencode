package com.github.wenzewoo.opencode.utils

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

object FileRefUtils {
     fun get(project: Project, file: VirtualFile, editor: Editor?): String {
        val basePath = project.basePath ?: ""
        val relative = file.path.removePrefix(basePath).trimStart('/', '\\')

         if (editor != null) {
             val caret = editor.caretModel.currentCaret
             val sel = if (caret.hasSelection()) {
                 val start = editor.document.getLineNumber(caret.selectionStart) + 1
                 val end = editor.document.getLineNumber(caret.selectionEnd) + 1
                 if (start == end) ":$start" else ":$start-$end"
             } else ""
             return "@$relative$sel "
         }
         return "@$relative "
    }
}

