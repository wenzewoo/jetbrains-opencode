package com.github.wenzewoo.opencode.settings

import com.github.wenzewoo.opencode.MessageBundle.message
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.openapi.ui.ComboBox
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class OpenCodeConfigurable : Configurable {
    private var cliPath: TextFieldWithBrowseButton? = null
    private var typeCombo: ComboBox<String>? = null

    override fun getDisplayName(): String = message("configurable.displayName")

    override fun createComponent(): JComponent {
        cliPath = TextFieldWithBrowseButton().apply {
            addBrowseFolderListener(
                null,
                FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
                    .withTitle(message("fileChooser.selectBinary.title"))
                    .withDescription(message("fileChooser.selectBinary.description"))
            )
        }
        typeCombo = ComboBox(arrayOf(
            ToolWindowType.DOCKED.name,
            ToolWindowType.SLIDING.name,
            ToolWindowType.FLOATING.name,
            ToolWindowType.WINDOWED.name
        ))

        return FormBuilder.createFormBuilder()
            .addLabeledComponent(message("settings.label.binaryPath"), cliPath!!)
            .addTooltip(message("settings.tooltip.binaryPath"))
            .addLabeledComponent(message("settings.label.windowMode"), typeCombo!!)
            .addTooltip(message("settings.tooltip.windowMode"))
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    override fun isModified(): Boolean {
        val s = OpenCodeSettings.getInstance().state
        return cliPath?.text != s.cliPath ||
                typeCombo?.selectedItem != s.toolWindowType
    }

    override fun apply() {
        val s = OpenCodeSettings.getInstance().state
        s.cliPath = cliPath?.text?.trim() ?: ""
        s.toolWindowType = typeCombo?.selectedItem as? String ?: ToolWindowType.FLOATING.name
    }

    override fun reset() {
        val s = OpenCodeSettings.getInstance().state
        cliPath?.text = s.cliPath
        typeCombo?.selectedItem = s.toolWindowType
    }

    override fun disposeUIResources() {
        cliPath = null
        typeCombo = null
    }
}
