package org.afterlike.catdueller.gui.components.settings

/**
 * Base class for all GUI settings.
 */
abstract class Setting(
    val name: String,
    val description: String = ""
) {
    
    /**
     * Render the setting at the specified position.
     */
    abstract fun render(x: Int, y: Int, width: Int, mouseX: Int, mouseY: Int)
    
    /**
     * Handle mouse click events.
     * @return true if the click was handled
     */
    abstract fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int): Boolean
    
    /**
     * Handle mouse release events.
     */
    open fun mouseReleased(mouseX: Int, mouseY: Int, state: Int) {}
    
    /**
     * Get the height of this setting component.
     */
    abstract fun getHeight(): Int
    
    /**
     * Check if mouse is over this setting.
     */
    fun isMouseOver(x: Int, y: Int, width: Int, mouseX: Int, mouseY: Int): Boolean {
        return mouseX >= x && mouseX <= x + width &&
               mouseY >= y && mouseY <= y + getHeight()
    }
    
    /**
     * Handle keyboard input events.
     * @return true if the input was handled
     */
    open fun keyTyped(typedChar: Char, keyCode: Int): Boolean {
        return false
    }
}
