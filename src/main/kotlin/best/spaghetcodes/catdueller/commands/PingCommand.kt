package best.spaghetcodes.catdueller.commands

import best.spaghetcodes.catdueller.CatDueller
import best.spaghetcodes.catdueller.utils.ChatUtils
import gg.essential.api.commands.Command
import gg.essential.api.commands.DefaultHandler

/**
 * Command handler for displaying the current server ping.
 *
 * Provides the `/ping` command which retrieves and displays the current
 * network latency to the server.
 */
class PingCommand : Command("ping") {

    /**
     * Handles the ping command by displaying the current server latency.
     *
     * Retrieves ping information from the bot instance if available.
     * Displays an error message if the bot is not initialized or ping cannot be retrieved.
     */
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