package com.github.wenzewoo.opencode.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.wm.ToolWindowType

@State(
    name = "com.github.wenzewoo.opencode.settings.OpenCodeSettings",
    storages = [Storage("OpenCodeSettings.xml")]
)
@Service
class OpenCodeSettings : PersistentStateComponent<OpenCodeSettings.State> {
    data class State(
        var cliPath: String = "",
        var toolWindowType: String = ToolWindowType.DOCKED.name
    )

    private var myState = State()

    override fun getState(): State = myState
    override fun loadState(state: State) { myState = state }

    companion object {
        fun getInstance(): OpenCodeSettings =
            ApplicationManager.getApplication().getService(OpenCodeSettings::class.java)
    }
}
