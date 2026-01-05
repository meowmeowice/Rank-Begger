package best.spaghetcodes.catdueller.commands

import best.spaghetcodes.catdueller.CatDueller
import best.spaghetcodes.catdueller.bot.Session
import best.spaghetcodes.catdueller.utils.ChatUtils
import gg.essential.api.EssentialAPI
import gg.essential.api.commands.Command
import gg.essential.api.commands.DefaultHandler
import gg.essential.api.commands.SubCommand

class ConfigCommand : Command("CatDueller") {

    @DefaultHandler
    fun handle() {
        ChatUtils.info("CatDueller Commands:")
        ChatUtils.info("/catdueller - Open config GUI")
        ChatUtils.info("/catdueller session [reset|show] - Manage session statistics")
        ChatUtils.info("Opening config GUI...")
        EssentialAPI.getGuiUtil().openScreen(CatDueller.config?.gui())
    }

    @SubCommand("session")
    fun sessionCommand(action: String? = null) {
        when (action?.lowercase()) {
            "reset" -> {
                resetSession()
            }

            "show", "stats", null -> {
                showSession()
            }

            else -> {
                ChatUtils.info("Usage: /catdueller session [reset|show]")
            }
        }
    }

    private fun resetSession() {
        Session.wins = 0
        Session.losses = 0
        Session.startTime = System.currentTimeMillis()
        ChatUtils.info("Session statistics have been reset!")
    }

    private fun showSession() {
        val currentBot = CatDueller.bot
        val winstreak = if (currentBot != null) {
            // Get current winstreak from bot's currentWinstreak field
            try {
                val field = currentBot.javaClass.superclass.getDeclaredField("currentWinstreak")
                field.isAccessible = true
                field.getInt(currentBot)
            } catch (e: Exception) {
                0 // Default to 0 if we can't access the field
            }
        } else {
            0
        }

        ChatUtils.info(Session.getSession(winstreak))
    }
}
