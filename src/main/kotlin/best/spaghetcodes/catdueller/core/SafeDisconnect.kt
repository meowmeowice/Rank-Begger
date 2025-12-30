package best.spaghetcodes.catdueller.core

import best.spaghetcodes.catdueller.utils.ChatUtils
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiMainMenu
import net.minecraft.client.gui.GuiMultiplayer

/**
 * Safe disconnect utilities to prevent crashes during disconnection
 */
object SafeDisconnect {
    
    private val mc = Minecraft.getMinecraft()
    
    /**
     * Attempt safe disconnect with multiple fallback methods
     */
    fun attemptSafeDisconnect(reason: String = "Safe Disconnect"): Boolean {
        return try {
            ChatUtils.info("Attempting safe disconnect for: $reason")
            
            // Method 1: Standard disconnect
            if (tryStandardDisconnect()) {
                ChatUtils.info("Standard disconnect successful")
                return true
            }
            
            // Method 2: Force world clear
            if (tryForceWorldClear()) {
                ChatUtils.info("Force world clear successful")
                return true
            }
            
            // Method 3: Emergency main menu
            if (tryEmergencyMainMenu()) {
                ChatUtils.info("Emergency main menu successful")
                return true
            }
            
            ChatUtils.error("All disconnect methods failed")
            false
        } catch (e: Exception) {
            ChatUtils.error("Safe disconnect failed: ${e.message}")
            false
        }
    }
    
    /**
     * Try standard disconnect method
     */
    private fun tryStandardDisconnect(): Boolean {
        return try {
            if (mc.theWorld != null) {
                mc.theWorld.sendQuittingDisconnectingPacket()
                mc.loadWorld(null)
                mc.displayGuiScreen(GuiMultiplayer(GuiMainMenu()))
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Try force world clear without quit packet
     */
    private fun tryForceWorldClear(): Boolean {
        return try {
            mc.loadWorld(null)
            mc.displayGuiScreen(GuiMainMenu())
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Emergency method: just show main menu
     */
    private fun tryEmergencyMainMenu(): Boolean {
        return try {
            mc.displayGuiScreen(GuiMainMenu())
            true
        } catch (e: Exception) {
            false
        }
    }
}