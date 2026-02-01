package org.afterlike.catdueller.gui.components.settings

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import java.awt.Color

/**
 * Boolean toggle setting with sub-settings support.
 */
class BooleanSetting(
    name: String,
    description: String = "",
    private var value: Boolean,
    private val onChange: (Boolean) -> Unit = {},
    private val isSubSetting: Boolean = false  
) : Setting(name, description) {
    
    private val mainHeight = if (isSubSetting) 16 else 20  
    private val subSettings = mutableListOf<Setting>()
    private var isOpened = false  // 控制子設定是否展開（右鍵切換）  
    
    private val enabledColor = Color(255, 153, 204, 255) 
    private val enabledColorSub = Color(100, 255, 100, 255) 
    private val disabledColor = Color(120, 120, 120, 255)   
    private val hoverColor = Color(60, 60, 60, 200)
    
    // Store last render position for click detection
    private var lastRenderX = 0
    private var lastRenderY = 0
    private var lastRenderWidth = 0
    
    fun getValue(): Boolean = value
    
    fun setValue(newValue: Boolean) {
        if (value != newValue) {
            value = newValue
            onChange(value)
        }
    }
    
    fun addSubSetting(setting: Setting) {
        subSettings.add(setting)
    }
    
    override fun render(x: Int, y: Int, width: Int, mouseX: Int, mouseY: Int) {
        lastRenderX = x
        lastRenderY = y
        lastRenderWidth = width
        val mc = Minecraft.getMinecraft()
        
        // Highlight on hover (only for main setting area)
        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + mainHeight) {
            Gui.drawRect(x, y, x + width, y + mainHeight, hoverColor.rgb)
        }
        
        // Choose text color based on state and whether it's a sub-setting
        val textColor = if (value) {
            if (isSubSetting) enabledColorSub else enabledColor
        } else {
            disabledColor
        }
        
        // Use smaller font size for sub-settings by scaling
        val scale = if (isSubSetting) 0.85f else 1.0f
        val textY = if (isSubSetting) {
            y + (mainHeight - 7) / 2  // Adjust for smaller height
        } else {
            y + (mainHeight - 8) / 2
        }
        
        if (isSubSetting) {
            // Scale down for sub-settings
            net.minecraft.client.renderer.GlStateManager.pushMatrix()
            net.minecraft.client.renderer.GlStateManager.scale(scale, scale, scale)
            mc.fontRendererObj.drawStringWithShadow(
                name,
                ((x + 8) / scale),
                (textY / scale),
                textColor.rgb
            )
            net.minecraft.client.renderer.GlStateManager.popMatrix()
        } else {
            // Normal size for parent settings
            mc.fontRendererObj.drawStringWithShadow(
                name,
                (x + 8).toFloat(),
                textY.toFloat(),
                textColor.rgb
            )
        }
        
        // Render sub-settings if opened (regardless of enabled state)
        if (isOpened && subSettings.isNotEmpty()) {
            var currentY = y + mainHeight
            subSettings.forEach { setting ->
                setting.render(x + 10, currentY, width - 10, mouseX, mouseY)
                currentY += setting.getHeight()
            }
        }
    }
    
    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int): Boolean {
        // Check if clicking on main setting (with X bounds check)
        if (mouseX >= lastRenderX && mouseX <= lastRenderX + lastRenderWidth &&
            mouseY >= lastRenderY && mouseY <= lastRenderY + mainHeight) {
            
            if (mouseButton == 0) {
                // Left click: toggle value
                setValue(!value)
                return true
            } else if (mouseButton == 1 && subSettings.isNotEmpty()) {
                // Right click: toggle sub-settings opened state
                isOpened = !isOpened
                return true
            }
            return false
        }
        
        // Check if clicking on sub-settings (only if opened)
        if (isOpened && subSettings.isNotEmpty()) {
            var currentY = lastRenderY + mainHeight
            subSettings.forEach { setting ->
                val settingHeight = setting.getHeight()
                if (mouseY >= currentY && mouseY <= currentY + settingHeight) {
                    return setting.mouseClicked(mouseX, mouseY, mouseButton)
                }
                currentY += settingHeight
            }
        }
        
        return false
    }
    
    override fun keyTyped(typedChar: Char, keyCode: Int): Boolean {
        if (!isOpened || subSettings.isEmpty()) return false
        
        subSettings.forEach { setting ->
            if (setting.keyTyped(typedChar, keyCode)) {
                return true
            }
        }
        return false
    }
    
    override fun getHeight(): Int {
        var totalHeight = mainHeight
        if (isOpened && subSettings.isNotEmpty()) {
            totalHeight += subSettings.sumOf { it.getHeight() }
        }
        return totalHeight
    }
}
