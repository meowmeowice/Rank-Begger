package org.afterlike.catdueller.gui.components

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import org.afterlike.catdueller.gui.components.settings.Setting
import java.awt.Color

/**
 * Draggable panel containing settings.
 */
class Panel(
    val title: String,
    var x: Int,
    var y: Int,
    var width: Int,
    val headerHeight: Int,
    var expanded: Boolean = false  // 默認收起
) {
    
    private val settings = mutableListOf<Setting>()
    private var scrollOffset = 0
    private val maxVisibleHeight = 300
    
    // Smooth dragging animation
    private var targetX = x
    private var targetY = y
    private var animatedX = x.toFloat()
    private var animatedY = y.toFloat()
    private val smoothness = 0.3f  // 增加插值速度 (0.0 = 慢, 1.0 = 快)
    
    private val backgroundColor = Color(20, 20, 20, 200)
    private val headerColor = Color(40, 40, 40, 220)
    private val accentColor = Color(100, 150, 255, 255)
    
    fun addSetting(setting: Setting) {
        settings.add(setting)
    }
    
    fun render(mouseX: Int, mouseY: Int) {
        val mc = Minecraft.getMinecraft()
        
        // Update animation
        targetX = x
        targetY = y
        animatedX += (targetX - animatedX) * smoothness
        animatedY += (targetY - animatedY) * smoothness
        
        val renderX = animatedX.toInt()
        val renderY = animatedY.toInt()
        
        // Draw header
        Gui.drawRect(renderX, renderY, renderX + width, renderY + headerHeight, headerColor.rgb)
        
        // Draw header text
        mc.fontRendererObj.drawStringWithShadow(
            title,
            (renderX + 5).toFloat(),
            (renderY + (headerHeight - 8) / 2).toFloat(),
            Color.WHITE.rgb
        )
        
        // Draw expand/collapse indicator
        val indicator = if (expanded) "▼" else "▶"
        mc.fontRendererObj.drawStringWithShadow(
            indicator,
            (renderX + width - 15).toFloat(),
            (renderY + (headerHeight - 8) / 2).toFloat(),
            Color.WHITE.rgb
        )
        
        if (expanded) {
            // Calculate total content height
            val contentHeight = settings.sumOf { it.getHeight() }
            val visibleHeight = minOf(contentHeight, maxVisibleHeight)
            
            // Draw panel background
            Gui.drawRect(
                renderX,
                renderY + headerHeight,
                renderX + width,
                renderY + headerHeight + visibleHeight,
                backgroundColor.rgb
            )
            
            // Draw settings with scissor for scrolling
            var currentY = renderY + headerHeight - scrollOffset
            
            settings.forEach { setting ->
                if (currentY + setting.getHeight() > renderY + headerHeight && 
                    currentY < renderY + headerHeight + visibleHeight) {
                    setting.render(renderX, currentY, width, mouseX, mouseY)
                }
                currentY += setting.getHeight()
            }
            
            // Draw scrollbar if needed
            if (contentHeight > maxVisibleHeight) {
                drawScrollbar(contentHeight, visibleHeight, renderX, renderY)
            }
        }
    }
    
    private fun drawScrollbar(contentHeight: Int, visibleHeight: Int, renderX: Int, renderY: Int) {
        val scrollbarX = renderX + width - 3
        val scrollbarY = renderY + headerHeight
        val scrollbarHeight = visibleHeight
        
        // Background
        Gui.drawRect(
            scrollbarX,
            scrollbarY,
            scrollbarX + 3,
            scrollbarY + scrollbarHeight,
            Color(50, 50, 50, 150).rgb
        )
        
        // Thumb
        val thumbHeight = (visibleHeight * visibleHeight / contentHeight).coerceAtLeast(20)
        val thumbY = scrollbarY + (scrollOffset * visibleHeight / contentHeight)
        
        Gui.drawRect(
            scrollbarX,
            thumbY,
            scrollbarX + 3,
            thumbY + thumbHeight,
            accentColor.rgb
        )
    }
    
    fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int): Boolean {
        if (!expanded) return false
        
        val renderX = animatedX.toInt()
        val renderY = animatedY.toInt()
        
        val contentHeight = settings.sumOf { it.getHeight() }
        val visibleHeight = minOf(contentHeight, maxVisibleHeight)
        
        if (mouseX >= renderX && mouseX <= renderX + width &&
            mouseY >= renderY + headerHeight && mouseY <= renderY + headerHeight + visibleHeight) {
            
            var currentY = renderY + headerHeight - scrollOffset
            
            settings.forEach { setting ->
                if (mouseY >= currentY && mouseY <= currentY + setting.getHeight()) {
                    return setting.mouseClicked(mouseX, mouseY, mouseButton)
                }
                currentY += setting.getHeight()
            }
        }
        
        return false
    }
    
    fun mouseReleased(mouseX: Int, mouseY: Int, state: Int) {
        if (!expanded) return
        
        settings.forEach { it.mouseReleased(mouseX, mouseY, state) }
    }
    
    fun handleScroll(scroll: Int) {
        if (!expanded) return
        
        val contentHeight = settings.sumOf { it.getHeight() }
        val visibleHeight = minOf(contentHeight, maxVisibleHeight)
        
        if (contentHeight > visibleHeight) {
            scrollOffset -= scroll / 10
            scrollOffset = scrollOffset.coerceIn(0, contentHeight - visibleHeight)
        }
    }
    
    fun isMouseOverHeader(mouseX: Int, mouseY: Int): Boolean {
        return mouseX >= x && mouseX <= x + width &&
               mouseY >= y && mouseY <= y + headerHeight
    }
    
    fun isMouseOver(mouseX: Int, mouseY: Int): Boolean {
        val contentHeight = if (expanded) {
            minOf(settings.sumOf { it.getHeight() }, maxVisibleHeight)
        } else {
            0
        }
        
        return mouseX >= x && mouseX <= x + width &&
               mouseY >= y && mouseY <= y + headerHeight + contentHeight
    }
    
    fun toggleExpanded() {
        expanded = !expanded
        if (!expanded) {
            scrollOffset = 0
        }
    }
    
    fun keyTyped(typedChar: Char, keyCode: Int): Boolean {
        if (!expanded) return false
        
        settings.forEach { setting ->
            if (setting.keyTyped(typedChar, keyCode)) {
                return true
            }
        }
        
        return false
    }
}
