package org.afterlike.catdueller.gui.components.settings

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import java.awt.Color

/**
 * Selector/dropdown setting for choosing from multiple options.
 */
class SelectorSetting(
    name: String,
    description: String = "",
    private val options: List<String>,
    private var selectedIndex: Int,
    private val onChange: (Int) -> Unit = {},
    private val scale: Float = 1.0f  // 縮放比例，子設定使用0.85
) : Setting(name, description) {
    
    private val height = (20 * scale).toInt()
    private val hoverColor = Color(60, 60, 60, 200)
    private val selectedColor = Color(100, 150, 255, 255)
    
    fun getSelectedIndex(): Int = selectedIndex
    fun getSelectedOption(): String = options.getOrNull(selectedIndex) ?: ""
    
    fun setSelectedIndex(index: Int) {
        if (index in options.indices && selectedIndex != index) {
            selectedIndex = index
            onChange(index)
        }
    }
    
    override fun render(x: Int, y: Int, width: Int, mouseX: Int, mouseY: Int) {
        val mc = Minecraft.getMinecraft()
        
        // Highlight on hover
        if (isMouseOver(x, y, width, mouseX, mouseY)) {
            Gui.drawRect(x, y, x + width, y + height, hoverColor.rgb)
        }
        
        // Draw setting name
        mc.fontRendererObj.drawStringWithShadow(
            name,
            (x + 5).toFloat(),
            (y + (height - 8) / 2).toFloat(),
            Color.WHITE.rgb
        )
        
        // Draw current selection
        val selectedText = getSelectedOption()
        val textWidth = mc.fontRendererObj.getStringWidth(selectedText)
        
        // Background for selected option 
        val optionX = x + width - textWidth - 15
        Gui.drawRect(
            optionX - 15,
            y + 2,
            x + width - 2,
            y + height - 2,
            selectedColor.rgb
        )
        
        mc.fontRendererObj.drawStringWithShadow(
            selectedText,
            (optionX).toFloat(),
            (y + (height - 8) / 2).toFloat(),
            Color.WHITE.rgb
        )
        
        // Draw arrows
        mc.fontRendererObj.drawStringWithShadow(
            "<",
            (optionX - 12).toFloat(),
            (y + (height - 8) / 2).toFloat(),
            Color.WHITE.rgb
        )
        
        mc.fontRendererObj.drawStringWithShadow(
            ">",
            (x + width - 10).toFloat(),
            (y + (height - 8) / 2).toFloat(),
            Color.WHITE.rgb
        )
    }
    
    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int): Boolean {
        if (mouseButton == 0) { // Left click - next option
            val nextIndex = (selectedIndex + 1) % options.size
            setSelectedIndex(nextIndex)
            return true
        } else if (mouseButton == 1) { // Right click - previous option
            val prevIndex = if (selectedIndex - 1 < 0) options.size - 1 else selectedIndex - 1
            setSelectedIndex(prevIndex)
            return true
        }
        return false
    }
    
    override fun getHeight(): Int = height
}
