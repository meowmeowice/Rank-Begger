package best.spaghetcodes.catdueller.utils

import best.spaghetcodes.catdueller.CatDueller
import net.minecraft.util.ChatComponentText
import net.minecraft.util.EnumChatFormatting

object ChatUtils {

    fun removeFormatting(text: String): String{
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

    fun sendAsPlayer(message: String) {
        if (CatDueller.mc.thePlayer != null) {
            CatDueller.mc.thePlayer.sendChatMessage(message)
        }
    }

    fun info(message: String) {
        sendChatMessage("${EnumChatFormatting.GOLD}[${EnumChatFormatting.LIGHT_PURPLE}${EnumChatFormatting.BOLD}Cat${EnumChatFormatting.RESET}${EnumChatFormatting.DARK_PURPLE}Dueller${EnumChatFormatting.GOLD}] ${EnumChatFormatting.WHITE}$message", false)
    }

    fun error(message: String) {
        sendChatMessage("${EnumChatFormatting.GOLD}[${EnumChatFormatting.LIGHT_PURPLE}${EnumChatFormatting.BOLD}Cat${EnumChatFormatting.RESET}${EnumChatFormatting.DARK_PURPLE}Dueller${EnumChatFormatting.GOLD}] ${EnumChatFormatting.RED}$message", false)
    }

    fun combatInfo(message: String) {
        sendChatMessage("${EnumChatFormatting.GOLD}[${EnumChatFormatting.LIGHT_PURPLE}${EnumChatFormatting.BOLD}Cat${EnumChatFormatting.RESET}${EnumChatFormatting.DARK_PURPLE}Dueller${EnumChatFormatting.GOLD}] ${EnumChatFormatting.YELLOW}$message", true)
    }

    fun combatError(message: String) {
        sendChatMessage("${EnumChatFormatting.GOLD}[${EnumChatFormatting.LIGHT_PURPLE}${EnumChatFormatting.BOLD}Cat${EnumChatFormatting.RESET}${EnumChatFormatting.DARK_PURPLE}Dueller${EnumChatFormatting.GOLD}] ${EnumChatFormatting.RED}$message", true)
    }

    private fun sendChatMessage(message: String, isCombatMessage: Boolean = false) {
        if (CatDueller.mc.thePlayer != null) {
            // Always show non-combat messages, only show combat messages if combatLogs is enabled
            if (!isCombatMessage || CatDueller.config?.combatLogs == true) {
                CatDueller.mc.thePlayer.addChatMessage(ChatComponentText(message))
            }
        }
    }

}
