package org.afterlike.catdueller.gui.components.settings

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import java.awt.Color
import kotlin.math.roundToInt

/**
 * Slider setting for numeric values.
 */
class SliderSetting(
    name: String,
    description: String = "",
    private var value: Double,
    private val min: Double,
    private val max: Double,
    private val increment: Double = 1.0,
    private val onChange: (Double) -> Unit = {},
    private val scale: Float = 1.0f  // 縮放比例，子設定使用0.85
) : Setting(name, description) {
    
    private val height = (30 * scale).toInt()
    private var dragging = false
    
    private val sliderColor = Color(100, 150, 255, 255)
    private val trackColor = Color(80, 80, 80, 255)
    private val hoverColor = Color(60, 60, 60, 200)
    
    fun getValue(): Double = value
    
    fun setValue(newValue: Double) {
        val clamped = newValue.coerceIn(min, max)
        val rounded = (clamped / increment).roundToInt() * increment
        
        if (value != rounded) {
            value = rounded
            onChange(value)
        }
    }
    
    override fun render(x: Int, y: Int, width: Int, mouseX: Int, mouseY: Int) {
        val mc = Minecraft.getMinecraft()
        
        // Highlight on hover
        if (isMouseOver(x, y, width, mouseX, mouseY)) {
            Gui.drawRect(x, y, x + width, y + height, hoverColor.rgb)
        }
        
        // Draw setting name and value
        val displayValue = if (increment >= 1.0) {
            value.toInt().toString()
        } else {
            String.format("%.2f", value)
        }
        
        if (scale < 1.0f) {
            // Scale down for sub-settings
            net.minecraft.client.renderer.GlStateManager.pushMatrix()
            net.minecraft.client.renderer.GlStateManager.scale(scale, scale, scale)
            mc.fontRendererObj.drawStringWithShadow(
                "$name: $displayValue",
                ((x + 5) / scale),
                ((y + 3) / scale),
                Color.WHITE.rgb
            )
            net.minecraft.client.renderer.GlStateManager.popMatrix()
        } else {
            mc.fontRendererObj.drawStringWithShadow(
                "$name: $displayValue",
                (x + 5).toFloat(),
                (y + 3).toFloat(),
                Color.WHITE.rgb
            )
        }
        
        // Draw slider
        val sliderY = y + (15 * scale).toInt()
        val sliderWidth = width - 10
        val sliderHeight = (4 * scale).toInt()
        
        // Track
        Gui.drawRect(
            x + 5,
            sliderY,
            x + 5 + sliderWidth,
            sliderY + sliderHeight,
            trackColor.rgb
        )
        
        // Filled portion
        val percentage = ((value - min) / (max - min)).coerceIn(0.0, 1.0)
        val filledWidth = (sliderWidth * percentage).toInt()
        
        Gui.drawRect(
            x + 5,
            sliderY,
            x + 5 + filledWidth,
            sliderY + sliderHeight,
            sliderColor.rgb
        )
        
        // Thumb
        val thumbX = x + 5 + filledWidth
        val thumbSize = 8
        
        Gui.drawRect(
            thumbX - thumbSize / 2,
            sliderY - 2,
            thumbX + thumbSize / 2,
            sliderY + sliderHeight + 2,
            Color.WHITE.rgb
        )
        
        // Handle dragging
        if (dragging) {
            val relativeX = (mouseX - x - 5).coerceIn(0, sliderWidth)
            val newPercentage = relativeX.toDouble() / sliderWidth
            val newValue = min + (max - min) * newPercentage
            setValue(newValue)
        }
    }
    
    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int): Boolean {
        if (mouseButton == 0) {
            dragging = true
            return true
        }
        return false
    }
    
    override fun mouseReleased(mouseX: Int, mouseY: Int, state: Int) {
        if (state == 0) {
            dragging = false
        }
    }
    
    override fun getHeight(): Int = height
}
