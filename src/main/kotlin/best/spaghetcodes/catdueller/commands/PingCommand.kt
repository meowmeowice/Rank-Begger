package best.spaghetcodes.catdueller.commands

import best.spaghetcodes.catdueller.CatDueller
import best.spaghetcodes.catdueller.utils.ChatUtils
import gg.essential.api.commands.Command
import gg.essential.api.commands.DefaultHandler

class PingCommand : Command("ping") {

    @DefaultHandler
    fun handle() {
        val bot = CatDueller.bot
        if (bot != null) {
            val pingStatus = bot.getPingStatus()
            val ping = bot.getServerPing()
            
            ChatUtils.info("Current ping: $pingStatus")
            
            if (!(ping >= 0)) {
                ChatUtils.info("Cant get ping")
            }
        } else {
            ChatUtils.info("Bot is not initialized")
        }
    }
}