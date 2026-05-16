package com.github.wenzewoo.opencode.views

import com.github.wenzewoo.opencode.MessageBundle.message
import com.github.wenzewoo.opencode.services.OpenCodeService
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorEmbeddedComponentManager
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.EditorTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.KeyStroke

object InlineChat {
    private var activeDisposable: Disposable? = null

    fun show(project: Project, editor: Editor, initialText: String) {
        activeDisposable?.let { Disposer.dispose(it) }
        activeDisposable = null

        if (editor !is EditorEx) return
        val terminals = ToolWindowTabPicker.getTerminals(project)
        if (terminals.isEmpty()) return

        val arc = JBUI.scale(12)
        val arrow = JBUI.scale(16)
        val bgColor = UIUtil.getTextFieldBackground()
        val borderColor = com.intellij.ui.JBColor.border()

        val editorFont = java.awt.Font(editor.colorsScheme.editorFontName, java.awt.Font.PLAIN, editor.colorsScheme.editorFontSize)
        val editorTextField = EditorTextField(initialText, project, FileTypes.PLAIN_TEXT).apply {
            setOneLineMode(false)
            addSettingsProvider { it.settings.isUseSoftWraps = true }
            border = JBUI.Borders.empty(6)
        }
        editorTextField.setFont(editorFont)
        editorTextField.setPreferredSize(JBUI.size(0, editor.lineHeight * 3 + JBUI.scale(12)))

        val sendButton = JButton(message("inlineChat.sendButton"), AllIcons.Actions.Execute).apply {
            toolTipText = message("inlineChat.sendButton.tooltip")
            border = JBUI.Borders.empty(4, 12)
            isFocusPainted = false
        }

        val toolbar = JPanel(BorderLayout()).apply {
            add(sendButton, BorderLayout.EAST)
            border = JBUI.Borders.empty(4, 4, 4, 4)
            isOpaque = false
        }

        val panel = object : JPanel(BorderLayout()) {
            override fun paintComponent(g: Graphics) {
                val g2 = g as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val ins = insets
                val x = ins.left
                val y = ins.top
                val w = width - ins.left - ins.right - 1
                val h = height - ins.top - ins.bottom - 1
                val ax = x - arrow
                val arrowTipY = y + arrow

                g2.color = borderColor
                g2.fillRoundRect(x, y, w, h, arc, arc)
                g2.fillPolygon(intArrayOf(ax, x, x + arrow), intArrayOf(arrowTipY, arrowTipY - arrow / 2, arrowTipY), 3)
                g2.color = bgColor
                g2.fillRoundRect(x + 1, y + 1, w - 2, h - 2, arc - 1, arc - 1)
                g2.fillPolygon(intArrayOf(ax + 1, x + 1, x + arrow), intArrayOf(arrowTipY, arrowTipY - arrow / 2 + 1, arrowTipY), 3)
                super.paintComponent(g)
            }
        }
        panel.add(editorTextField, BorderLayout.CENTER)
        panel.add(toolbar, BorderLayout.SOUTH)
        panel.background = bgColor
        panel.border = JBUI.Borders.empty(8, arrow + 8, 8, 8)
        panel.isOpaque = false

        val offset = editor.caretModel.primaryCaret.offset
        val properties = EditorEmbeddedComponentManager.Properties(
            EditorEmbeddedComponentManager.ResizePolicy.none(),
            null,
            false,
            false,
            false,
            true,
            0,
            offset
        )

        val inlay = EditorEmbeddedComponentManager.getInstance()
            .addComponent(editor, panel, properties) ?: return

        editorTextField.requestFocusInWindow()

        fun close() {
            inlay.dispose()
            activeDisposable = null
        }

        fun performSend() {
            val text = editorTextField.text.trim()
            if (text.isEmpty()) return
            val term = if (terminals.size == 1) terminals.first()
            else {
                var chosen: ToolWindowTabPicker.TermInfo? = null
                ToolWindowTabPicker.showPicker(project, terminals) { chosen = it }
                chosen ?: return
            }
            OpenCodeService.sendPrompt(term.port, "$text ") { ok ->
                if (ok) {
                    inlay.dispose()
                    activeDisposable = null
                    ToolWindowTabPicker.navigateToTerminal(project, term.port)
                }
            }
        }

        sendButton.addActionListener { performSend() }

        val sendShortcut = CustomShortcutSet(
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, java.awt.event.InputEvent.SHIFT_DOWN_MASK)
        )
        object : AnAction(message("inlineChat.sendButton.tooltip")) {
            override fun actionPerformed(e: AnActionEvent) = performSend()
            override fun getActionUpdateThread() = ActionUpdateThread.BGT
        }.registerCustomShortcutSet(sendShortcut, editorTextField)

        object : AnAction() {
            override fun actionPerformed(e: AnActionEvent) = close()
        }.registerCustomShortcutSet(
            CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)),
            editorTextField
        )

        activeDisposable = Disposable {
            if (inlay.isValid) inlay.dispose()
        }
    }
}