package best.spaghetcodes.catdueller.commands

import best.spaghetcodes.catdueller.CatDueller
import best.spaghetcodes.catdueller.bot.Session
import best.spaghetcodes.catdueller.utils.ChatUtils
import gg.essential.api.EssentialAPI
import gg.essential.api.commands.Command
import gg.essential.api.commands.DefaultHandler
import gg.essential.api.commands.SubCommand

/**
 * Main command handler for CatDueller configuration and session management.
 *
 * Provides the `/catdueller` command with subcommands for opening the configuration GUI
 * and managing session statistics (wins, losses, winstreak).
 */
class ConfigCommand : Command("CatDueller") {

    /**
     * Default command handler that displays available commands and opens the configuration GUI.
     *
     * Invoked when the user runs `/catdueller` without any subcommands.
     */
    @DefaultHandler
    fun handle() {
        ChatUtils.info("CatDueller Commands:")
        ChatUtils.info("/catdueller - Open config GUI")
        ChatUtils.info("/catdueller session [reset|show] - Manage session statistics")
        ChatUtils.info("Opening config GUI...")
        EssentialAPI.getGuiUtil().openScreen(CatDueller.config?.gui())
    }

    /**
     * Handles the session subcommand for viewing or resetting session statistics.
     *
     * @param action The action to perform: "reset" to clear statistics, "show"/"stats" or null to display them.
     */
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

    /**
     * Resets all session statistics to their initial values.
     *
     * Clears wins and losses counters, and resets the session start time to the current time.
     */
    private fun resetSession() {
        Session.wins = 0
        Session.losses = 0
        Session.startTime = System.currentTimeMillis()
        ChatUtils.info("Session statistics have been reset!")
    }

    /**
     * Displays the current session statistics including wins, losses, and winstreak.
     *
     * Retrieves the current winstreak from the bot instance via reflection if available,
     * otherwise defaults to zero.
     */
    private fun showSession() {
        val currentBot = CatDueller.bot
        val winstreak = if (currentBot != null) {
            try {
                val field = currentBot.javaClass.superclass.getDeclaredField("currentWinstreak")
                field.isAccessible = true
                field.getInt(currentBot)
            } catch (e: Exception) {
                0
            }
        } else {
            0
        }

        ChatUtils.info(Session.getSession(winstreak))
    }
}
