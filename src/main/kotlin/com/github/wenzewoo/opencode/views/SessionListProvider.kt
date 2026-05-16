package com.github.wenzewoo.opencode.views

import com.github.wenzewoo.opencode.MessageBundle.message
import com.github.wenzewoo.opencode.services.OpenCodeService.fetchSessions
import com.github.wenzewoo.opencode.services.SessionInfo
import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import javax.swing.JComponent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.DefaultListCellRenderer
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel


object SessionListProvider {

    private fun formatDate(millis: Long): String {
        if (millis <= 0) return ""
        return Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }

    fun showSessionPicker(project: Project, onChosen: (sessionId: String, title: String) -> Unit, invoker: Component? = null) {
        val sessionsRef = arrayOf<List<SessionInfo>>(emptyList())
        val errorRef = arrayOf<String?>(null)

        val result = ProgressManager.getInstance().runProcessWithProgressSynchronously(
            {
                sessionsRef[0] = fetchSessions(project.basePath)
            },
            message("sessionList.progressTitle"),
            false,
            project,
            invoker as? JComponent
        )

        val sessions = sessionsRef[0]
        if (sessions.isEmpty()) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("OpenCode")
                .createNotification(message("sessionList.notFound"), NotificationType.WARNING)
                .notify(project)
            return
        }

        val popup = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(sessions)
            .setTitle(message("sessionList.pickerTitle"))
            .setItemChosenCallback { session -> onChosen(session.id, session.title) }
            .setNamerForFiltering { s: SessionInfo -> s.title.ifBlank { s.id } }
            .setRenderer(sessionRenderer())
            .setMovable(true)
            .setResizable(true)
            .setRequestFocus(true)
            .createPopup()
        if (invoker != null) popup.showUnderneathOf(invoker)
        else popup.showInFocusCenter()
    }

    private fun sessionRenderer(): DefaultListCellRenderer = object : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            if (value !is SessionInfo) {
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

            val displayTitle = value.title.ifBlank { message("sessionList.defaultTitle", value.id.take(8)) }
            val titleLabel = JLabel(displayTitle)
            titleLabel.font = titleLabel.font.deriveFont(Font.PLAIN, JBUIScale.scaleFontSize(13f).toFloat())
            titleLabel.foreground = if (isSelected) JBUI.CurrentTheme.List.Selection.foreground(list.hasFocus())
            else JBUI.CurrentTheme.Label.foreground()
            textPanel.add(titleLabel, BorderLayout.NORTH)

            val dateStr = formatDate(value.created)
            if (dateStr.isNotBlank()) {
                val dateLabel = JLabel(dateStr)
                dateLabel.font = dateLabel.font.deriveFont(Font.PLAIN, JBUIScale.scaleFontSize(11f).toFloat())
                dateLabel.foreground = if (isSelected) JBUI.CurrentTheme.List.Selection.foreground(list.hasFocus())
                else JBUI.CurrentTheme.Label.disabledForeground()
                dateLabel.border = JBUI.Borders.emptyTop(2)
                textPanel.add(dateLabel, BorderLayout.CENTER)
            }

            panel.add(textPanel, BorderLayout.CENTER)
            return panel
        }
    }
}
