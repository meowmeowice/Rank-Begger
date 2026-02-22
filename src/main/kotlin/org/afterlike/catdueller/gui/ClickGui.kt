package org.afterlike.catdueller.gui

import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.ScaledResolution
import org.afterlike.catdueller.gui.components.Panel
import org.afterlike.catdueller.gui.components.settings.TextSetting
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import java.awt.Color
import java.io.IOException

/**
 * Main ClickGUI screen for CatDueller mod.
 * 
 * Features:
 * - Draggable panels
 * - Expandable/collapsible categories
 * - Smooth animations
 * - Custom rendering
 * - Persistent panel positions and states
 */
class ClickGui : GuiScreen() {
    
    private val panels = mutableListOf<Panel>()
    private var draggingPanel: Panel? = null
    private var dragOffsetX = 0
    private var dragOffsetY = 0
    
    init {
        // Initialize panels with saved states or defaults
        val panelConfigs = listOf(
            Triple("General", 20, 20),
            Triple("Combat", 220, 20),
            Triple("Classic", 420, 20),
            Triple("Blitz", 620, 20),
            Triple("Sumo", 820, 20),
            Triple("Toggling", 20, 300),
            Triple("Queue Dodging", 220, 300),
            Triple("Auto Requeue", 420, 300),
            Triple("AutoGG", 620, 300),
            Triple("Chat Messages", 20, 500),
            Triple("Webhook", 220, 500),
            Triple("Bot Crasher", 420, 500)
        )
        
        panelConfigs.forEach { (name, defaultX, defaultY) ->
            val state = GuiConfig.getPanelState(name, defaultX, defaultY)
            val panel = Panel(name, state.x, state.y, 180, 15, state.expanded)
            
            // Build panel content
            when (name) {
                "General" -> ConfigPanelBuilder.buildGeneralPanel(panel)
                "Combat" -> ConfigPanelBuilder.buildCombatPanel(panel)
                "Classic" -> ConfigPanelBuilder.buildClassicPanel(panel)
                "Blitz" -> ConfigPanelBuilder.buildBlitzPanel(panel)
                "Sumo" -> ConfigPanelBuilder.buildSumoPanel(panel)
                "Toggling" -> ConfigPanelBuilder.buildTogglingPanel(panel)
                "Queue Dodging" -> ConfigPanelBuilder.buildQueueDodgingPanel(panel)
                "Auto Requeue" -> ConfigPanelBuilder.buildAutoRequeuePanel(panel)
                "AutoGG" -> ConfigPanelBuilder.buildAutoGGPanel(panel)
                "Chat Messages" -> ConfigPanelBuilder.buildChatMessagesPanel(panel)
                "Webhook" -> ConfigPanelBuilder.buildWebhookPanel(panel)
                "Bot Crasher" -> ConfigPanelBuilder.buildBotCrasherPanel(panel)
            }
            
            panels.add(panel)
        }
    }
    
    override fun initGui() {
        super.initGui()
    }
    
    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        // Draw semi-transparent background
        drawRect(0, 0, width, height, Color(0, 0, 0, 100).rgb)
        
        // Draw all panels
        panels.forEach { panel ->
            panel.render(mouseX, mouseY)
        }
        
        super.drawScreen(mouseX, mouseY, partialTicks)
    }
    
    @Throws(IOException::class)
    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        super.mouseClicked(mouseX, mouseY, mouseButton)
        
        if (mouseButton == 0) { // Left click
            // Check if clicking on any panel header
            for (panel in panels.reversed()) { // Reverse to handle top panel first
                if (panel.isMouseOverHeader(mouseX, mouseY)) {
                    // Start dragging
                    draggingPanel = panel
                    dragOffsetX = mouseX - panel.x
                    dragOffsetY = mouseY - panel.y
                    
                    // Bring panel to front
                    panels.remove(panel)
                    panels.add(panel)
                    return
                }
                
                // Check if clicking on panel content
                if (panel.mouseClicked(mouseX, mouseY, mouseButton)) {
                    return
                }
            }
        } else if (mouseButton == 1) { // Right click
            // First check if clicking on panel content (for sub-settings)
            for (panel in panels.reversed()) {
                if (panel.mouseClicked(mouseX, mouseY, mouseButton)) {
                    return
                }
            }
            
            // Then toggle panel expansion if clicking on header
            panels.forEach { panel ->
                if (panel.isMouseOverHeader(mouseX, mouseY)) {
                    panel.toggleExpanded()
                    return
                }
            }
        }
    }
    
    override fun mouseReleased(mouseX: Int, mouseY: Int, state: Int) {
        super.mouseReleased(mouseX, mouseY, state)
        
        if (state == 0) {
            draggingPanel = null
        }
        
        panels.forEach { it.mouseReleased(mouseX, mouseY, state) }
    }
    
    override fun mouseClickMove(mouseX: Int, mouseY: Int, clickedMouseButton: Int, timeSinceLastClick: Long) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick)
        
        // Handle panel dragging
        draggingPanel?.let { panel ->
            panel.x = mouseX - dragOffsetX
            panel.y = mouseY - dragOffsetY
        }
    }
    
    override fun handleMouseInput() {
        super.handleMouseInput()
        
        val mouseX = Mouse.getEventX() * width / mc.displayWidth
        val mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1
        val scroll = Mouse.getEventDWheel()
        
        if (scroll != 0) {
            panels.forEach { panel ->
                if (panel.isMouseOver(mouseX, mouseY)) {
                    panel.handleScroll(scroll)
                }
            }
        }
    }
    
    @Throws(IOException::class)
    override fun keyTyped(typedChar: Char, keyCode: Int) {
        // Handle text input for TextSettings
        var handled = false
        panels.forEach { panel ->
            if (panel.keyTyped(typedChar, keyCode)) {
                handled = true
            }
        }
        
        if (!handled) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                mc.displayGuiScreen(null)
            } else {
                super.keyTyped(typedChar, keyCode)
            }
        }
    }
    
    override fun doesGuiPauseGame(): Boolean {
        return false
    }
    
    override fun onGuiClosed() {
        super.onGuiClosed()
        // Save panel positions and states to memory
        panels.forEach { panel ->
            GuiConfig.setPanelState(panel.title, panel.x, panel.y, panel.expanded)
        }
    }
}
