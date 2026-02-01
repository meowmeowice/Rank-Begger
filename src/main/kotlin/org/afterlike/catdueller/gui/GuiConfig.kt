package org.afterlike.catdueller.gui

/**
 * In-memory configuration for ClickGUI panel positions and states.
 * State persists during the game session but resets on restart.
 */
data class PanelState(
    var x: Int,
    var y: Int,
    var expanded: Boolean = false
)

object GuiConfig {
    private val panels = mutableMapOf<String, PanelState>()
    
    fun getPanelState(name: String, defaultX: Int, defaultY: Int): PanelState {
        return panels.getOrPut(name) { PanelState(defaultX, defaultY, false) }
    }
    
    fun setPanelState(name: String, x: Int, y: Int, expanded: Boolean) {
        panels[name] = PanelState(x, y, expanded)
    }
}
