package best.spaghetcodes.catdueller.utils.client

import best.spaghetcodes.catdueller.CatDueller
import net.minecraft.util.ChatComponentText
import net.minecraft.util.EnumChatFormatting

/**
 * Utility object for chat-related operations in the Minecraft client.
 *
 * Provides methods for sending formatted chat messages to the player,
 * sending messages as the player, and removing formatting codes from text.
 */
object ChatUtil {

    /**
     * Removes Minecraft formatting codes from a string.
     *
     * Strips all section sign (paragraph symbol) formatting codes and their associated
     * format characters from the input text.
     *
     * @param text The input string potentially containing formatting codes.
     * @return The text with all formatting codes removed.
     */
    fun removeFormatting(text: String): String {
        var t = ""
        var skip = false
        for (i in text.indices) {
            if (!skip) {
                if (text[i] == '§') {
                    skip = true
                } else {
                    t += text[i]
                }
            } else {
                skip = false
            }
        }
        return t
    }

    /**
     * Sends a chat message as the player to the server.
     *
     * This sends the message through the player's chat system, meaning it will
     * be visible to other players on the server.
     *
     * @param message The message to send.
     */
    fun sendAsPlayer(message: String) {
        if (CatDueller.mc.thePlayer != null) {
            CatDueller.mc.thePlayer.sendChatMessage(message)
        }
    }

    /**
     * Displays an informational message to the player in chat.
     *
     * The message is prefixed with the CatDueller branding and displayed in white text.
     * This message is only visible to the local player.
     *
     * @param message The informational message to display.
     */
    fun info(message: String) {
        sendChatMessage(
            "${EnumChatFormatting.GOLD}[${EnumChatFormatting.LIGHT_PURPLE}${EnumChatFormatting.BOLD}Cat${EnumChatFormatting.RESET}${EnumChatFormatting.DARK_PURPLE}Dueller${EnumChatFormatting.GOLD}] ${EnumChatFormatting.WHITE}$message",
            false
        )
    }

    /**
     * Displays an error message to the player in chat.
     *
     * The message is prefixed with the CatDueller branding and displayed in red text
     * to indicate an error condition. This message is only visible to the local player.
     *
     * @param message The error message to display.
     */
    fun error(message: String) {
        sendChatMessage(
            "${EnumChatFormatting.GOLD}[${EnumChatFormatting.LIGHT_PURPLE}${EnumChatFormatting.BOLD}Cat${EnumChatFormatting.RESET}${EnumChatFormatting.DARK_PURPLE}Dueller${EnumChatFormatting.GOLD}] ${EnumChatFormatting.RED}$message",
            false
        )
    }

    /**
     * Displays a combat-related informational message to the player in chat.
     *
     * The message is prefixed with the CatDueller branding and displayed in yellow text.
     * This message is only shown if combat logging is enabled in the configuration.
     *
     * @param message The combat information message to display.
     */
    fun combatInfo(message: String) {
        sendChatMessage(
            "${EnumChatFormatting.GOLD}[${EnumChatFormatting.LIGHT_PURPLE}${EnumChatFormatting.BOLD}Cat${EnumChatFormatting.RESET}${EnumChatFormatting.DARK_PURPLE}Dueller${EnumChatFormatting.GOLD}] ${EnumChatFormatting.YELLOW}$message",
            true
        )
    }

    /**
     * Internal method that sends a formatted chat message to the local player.
     *
     * Non-combat messages are always displayed. Combat messages are only displayed
     * when the combatLogs configuration option is enabled.
     *
     * @param message The formatted message to display.
     * @param isCombatMessage If true, the message is only shown when combat logging is enabled.
     */
    private fun sendChatMessage(message: String, isCombatMessage: Boolean = false) {
        if (CatDueller.mc.thePlayer != null) {
            if (!isCombatMessage || CatDueller.config?.combatLogs == true) {
                CatDueller.mc.thePlayer.addChatMessage(ChatComponentText(message))
            }
        }
    }

}
