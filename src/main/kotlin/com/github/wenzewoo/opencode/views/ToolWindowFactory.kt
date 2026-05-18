package com.github.wenzewoo.opencode.views

import com.github.wenzewoo.opencode.MessageBundle
import com.github.wenzewoo.opencode.launcher.SessionMode
import com.github.wenzewoo.opencode.settings.OpenCodeSettings
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import java.awt.BorderLayout
import javax.swing.JPanel

fun setupToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val typeName = OpenCodeSettings.getInstance().state.toolWindowType
    val targetType = try { ToolWindowType.valueOf(typeName) } catch (_: Exception) { ToolWindowType.FLOATING }
    toolWindow.setType(targetType) {}
    toolWindow.title = MessageBundle.message("toolWindow.title")
    toolWindow.stripeTitle = MessageBundle.message("toolWindow.title")
    val cm = toolWindow.contentManager

    val addTabAction = object : DumbAwareAction(
        MessageBundle.message("action.addTab.text"),
        MessageBundle.message("action.addTab.description"), AllIcons.General.Add
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            val addTabButton = e.inputEvent?.component
            val group = DefaultActionGroup(
                object : DumbAwareAction(
                    MessageBundle.message("action.newSession.text"),
                    MessageBundle.message("action.newSession.description"), AllIcons.Actions.AddFile
                ) {
                    override fun actionPerformed(event: AnActionEvent) {
                        addSessionTab(project, toolWindow, SessionMode.New)
                    }
                },
                object : DumbAwareAction(
                    MessageBundle.message("action.forkSession.text"),
                    MessageBundle.message("action.forkSession.description"), AllIcons.Vcs.Branch
                ) {
                    override fun getActionUpdateThread() = ActionUpdateThread.BGT
                    override fun update(e: AnActionEvent) {
                        val sessionId = cm.selectedContent?.getUserData(ToolWindowContent.OPENCODE_SESSION_ID_KEY)
                        e.presentation.isVisible = sessionId != null
                    }
                    override fun actionPerformed(event: AnActionEvent) {
                        val content = cm.selectedContent ?: return
                        val sessionId = content.getUserData(ToolWindowContent.OPENCODE_SESSION_ID_KEY) ?: return
                        val title = content.getUserData(ToolWindowContent.OPENCODE_SESSION_TITLE_KEY) ?: ""
                        addSessionTab(project, toolWindow, SessionMode.Fork(sessionId, title))
                    }
                },
                object : DumbAwareAction(
                    MessageBundle.message("action.resumeSession.text"),
                    MessageBundle.message("action.resumeSession.description"), AllIcons.Actions.Resume
                ) {
                    override fun actionPerformed(event: AnActionEvent) {
                        addSessionTab(project, toolWindow, SessionMode.Continue)
                    }
                },
                object : DumbAwareAction(
                    MessageBundle.message("action.selectSession.text"),
                    MessageBundle.message("action.selectSession.description"), AllIcons.Actions.SwapPanels
                ) {
                    override fun actionPerformed(event: AnActionEvent) {
                        SessionListProvider.showSessionPicker(
                            project = project,
                            onChosen = { sessionId, title -> addSessionTab(project, toolWindow, SessionMode.History(sessionId, title)) },
                            invoker = addTabButton
                        )
                    }
                }
            )
            val popup = JBPopupFactory.getInstance()
                .createActionGroupPopup(null, group, e.dataContext, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true)
            val invoker = e.inputEvent?.component
            if (invoker != null) popup.showUnderneathOf(invoker)
            else popup.showInBestPositionFor(e.dataContext)
        }
    }
// For idea 2026.x, No longer needed
//    val closeTabAction = object : DumbAwareAction(
//        MessageBundle.message("action.closeTab.text"),
//        MessageBundle.message("action.closeTab.description"), AllIcons.Actions.Cancel
//    ) {
//        override fun getActionUpdateThread() = ActionUpdateThread.BGT
//        override fun actionPerformed(e: AnActionEvent) {
//            val group = DefaultActionGroup(
//                object : DumbAwareAction(
//                    MessageBundle.message("action.closeTab.text"),
//                    MessageBundle.message("action.closeTab.description"), AllIcons.Actions.Cancel
//                ) {
//                    override fun actionPerformed(e: AnActionEvent) {
//                        val selected = cm.selectedContent ?: return
//                        val panel = selected.component
//                        if (panel is JPanel) ToolWindowContent.cleanupTab(panel)
//                        cm.removeContent(selected, true)
//                    }
//                }
//            )
//            val popup = JBPopupFactory.getInstance()
//                .createActionGroupPopup(null, group, e.dataContext, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true)
//            val invoker = e.inputEvent?.component
//            if (invoker != null) popup.showUnderneathOf(invoker)
//            else popup.showInBestPositionFor(e.dataContext)
//        }
//    }

    cm.addContentManagerListener(object : ContentManagerListener {
        override fun contentRemoved(event: ContentManagerEvent) {
            val panel = event.content.component
            if (panel is JPanel) ToolWindowContent.cleanupTab(panel)
             if (cm.contentCount == 0) toolWindow.show() // always show.
        }
    })

    toolWindow.setTitleActions(listOf(
        addTabAction//, closeTabAction
    ))

    addSessionTab(project, toolWindow, SessionMode.Continue)
}

fun addSessionTab(project: Project, toolWindow: ToolWindow, sessionMode: SessionMode) {
    val cm = toolWindow.contentManager
    val mainPanel = JPanel(BorderLayout())
    val content = ContentFactory.getInstance().createContent(mainPanel, null, false)
    cm.addContent(content)
    cm.setSelectedContent(content)

    ToolWindowContent(project, sessionMode, mainPanel, content).install()
}
