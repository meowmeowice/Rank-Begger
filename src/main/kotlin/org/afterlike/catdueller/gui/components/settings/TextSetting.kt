package org.afterlike.catdueller.gui.components.settings

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import org.lwjgl.input.Keyboard
import java.awt.Color

/**
 * Text input setting.
 */
class TextSetting(
    name: String,
    description: String = "",
    private var value: String,
    private val onChange: (String) -> Unit = {},
    private val scale: Float = 1.0f  // 縮放比例，子設定使用0.85
) : Setting(name, description) {
    
    private val height = (35 * scale).toInt()
    private var focused = false
    private var cursorPosition = value.length
    
    private val hoverColor = Color(60, 60, 60, 200)
    private val focusColor = Color(80, 80, 80, 220)
    private val inputBgColor = Color(40, 40, 40, 255)
    
    fun getValue(): String = value
    
    fun setValue(newValue: String) {
        if (value != newValue) {
            value = newValue
            onChange(value)
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
            (y + 3).toFloat(),
            Color.WHITE.rgb
        )
        
        // Draw input box
        val inputY = y + 15
        val inputHeight = 16
        
        val bgColor = if (focused) focusColor else inputBgColor
        Gui.drawRect(
            x + 5,
            inputY,
            x + width - 5,
            inputY + inputHeight,
            bgColor.rgb
        )
        
        // Draw border if focused
        if (focused) {
            Gui.drawRect(x + 5, inputY, x + width - 5, inputY + 1, Color(100, 150, 255).rgb)
            Gui.drawRect(x + 5, inputY + inputHeight - 1, x + width - 5, inputY + inputHeight, Color(100, 150, 255).rgb)
        }
        
        // Draw text
        val displayText = if (value.isEmpty()) "..." else value
        val textColor = if (value.isEmpty()) Color.GRAY.rgb else Color.WHITE.rgb
        
        mc.fontRendererObj.drawString(
            displayText,
            x + 8,
            inputY + 4,
            textColor
        )
        
        // Draw cursor if focused
        if (focused && System.currentTimeMillis() % 1000 < 500) {
            val cursorX = x + 8 + mc.fontRendererObj.getStringWidth(value.substring(0, cursorPosition.coerceIn(0, value.length)))
            Gui.drawRect(cursorX, inputY + 2, cursorX + 1, inputY + inputHeight - 2, Color.WHITE.rgb)
        }
    }
    
    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int): Boolean {
        if (mouseButton == 0) {
            focused = true
            cursorPosition = value.length
            return true
        }
        return false
    }
    
    override fun keyTyped(typedChar: Char, keyCode: Int): Boolean {
        if (!focused) return false
        
        when (keyCode) {
            Keyboard.KEY_BACK -> {
                if (value.isNotEmpty() && cursorPosition > 0) {
                    value = value.substring(0, cursorPosition - 1) + value.substring(cursorPosition)
                    cursorPosition--
                    onChange(value)
                }
            }
            Keyboard.KEY_DELETE -> {
                if (cursorPosition < value.length) {
                    value = value.substring(0, cursorPosition) + value.substring(cursorPosition + 1)
                    onChange(value)
                }
            }
            Keyboard.KEY_LEFT -> {
                if (cursorPosition > 0) cursorPosition--
            }
            Keyboard.KEY_RIGHT -> {
                if (cursorPosition < value.length) cursorPosition++
            }
            Keyboard.KEY_HOME -> {
                cursorPosition = 0
            }
            Keyboard.KEY_END -> {
                cursorPosition = value.length
            }
            Keyboard.KEY_RETURN, Keyboard.KEY_ESCAPE -> {
                focused = false
            }
            Keyboard.KEY_V -> {
                // Handle Ctrl+V paste
                if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) {
                    try {
                        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                        val contents = clipboard.getContents(null)
                        if (contents != null && contents.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor)) {
                            val pastedText = contents.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor) as String
                            // Insert pasted text at cursor position
                            value = value.substring(0, cursorPosition) + pastedText + value.substring(cursorPosition)
                            cursorPosition += pastedText.length
                            onChange(value)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            Keyboard.KEY_A -> {
                // Handle Ctrl+A select all (move cursor to end)
                if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) {
                    cursorPosition = value.length
                }
            }
            else -> {
                if (typedChar.toInt() >= 32 && typedChar.toInt() <= 126) {
                    value = value.substring(0, cursorPosition) + typedChar + value.substring(cursorPosition)
                    cursorPosition++
                    onChange(value)
                }
            }
        }
        
        return true
    }
    
    fun unfocus() {
        focused = false
    }
    
    override fun getHeight(): Int = height
}
