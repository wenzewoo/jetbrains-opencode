package com.github.wenzewoo.opencode.views

import com.github.wenzewoo.opencode.MessageBundle.message
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.Consumer
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import javax.swing.DefaultListCellRenderer
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel

object ToolWindowTabPicker {
    data class TermInfo(val port: Int, val title: String) {
        override fun toString() = title
    }

    fun getTerminals(project: Project): List<TermInfo> {
        val tw = ToolWindowManager.getInstance(project).getToolWindow("OpenCode") ?: return emptyList()
        return tw.contentManager.contents.mapNotNull { content ->
            val port = content.getUserData(ToolWindowContent.OPENCODE_PORT_KEY)
            if (port != null) TermInfo(port, content.displayName?.ifEmpty { message("terminal.picker.defaultTitle", port) } ?: message("terminal.picker.defaultTitle", port))
            else null
        }
    }

    fun navigateToTerminal(project: Project, port: Int) {
        val tw = ToolWindowManager.getInstance(project).getToolWindow("OpenCode") ?: return
        val content = tw.contentManager.contents.find { it.getUserData(ToolWindowContent.OPENCODE_PORT_KEY) == port } ?: return
        tw.activate(Runnable { tw.contentManager.setSelectedContent(content) }, true, true)
    }

    fun showPicker(project: Project, terminals: List<TermInfo>, onChosen: (TermInfo) -> Unit) {
        if (terminals.isEmpty()) return
        if (terminals.size == 1) {
            onChosen(terminals.first())
            return
        }

        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(terminals)
            .setTitle(message("terminal.picker.title"))
            .setItemChosenCallback(Consumer<TermInfo> { onChosen(it) })
            .setNamerForFiltering { t: TermInfo -> t.title }
            .setRenderer(terminalRenderer())
            .setMovable(true)
            .setResizable(true)
            .createPopup()
            .showInFocusCenter()
    }

    private fun terminalRenderer(): DefaultListCellRenderer = object : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            if (value !is TermInfo) {
                return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            }
            val panel = JPanel(BorderLayout())
            panel.isOpaque = true
            panel.border = JBUI.Borders.empty(2, 8, 2, 8)
            panel.background = if (isSelected) JBUI.CurrentTheme.List.Selection.background(list.hasFocus())
            else list.background

            val iconLabel = JLabel(AllIcons.Actions.RealIntentionBulb)
            iconLabel.verticalAlignment = TOP
            iconLabel.border = JBUI.Borders.emptyRight(8)
            panel.add(iconLabel, BorderLayout.WEST)

            val textPanel = JPanel(BorderLayout())
            textPanel.isOpaque = false

            val titleLabel = JLabel(value.title)
            titleLabel.font = titleLabel.font.deriveFont(Font.PLAIN, JBUIScale.scaleFontSize(13f).toFloat())
            titleLabel.foreground = if (isSelected) JBUI.CurrentTheme.List.Selection.foreground(list.hasFocus())
            else JBUI.CurrentTheme.Label.foreground()
            textPanel.add(titleLabel, BorderLayout.NORTH)
            panel.add(textPanel, BorderLayout.CENTER)
            return panel
        }
    }
}
