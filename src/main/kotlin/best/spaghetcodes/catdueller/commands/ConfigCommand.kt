package best.spaghetcodes.catdueller.commands

import best.spaghetcodes.catdueller.CatDueller
import best.spaghetcodes.catdueller.bot.state.Session
import best.spaghetcodes.catdueller.core.DelayedTaskHandler
import best.spaghetcodes.catdueller.utils.client.ChatUtil
import gg.essential.universal.UScreen
import net.minecraft.command.CommandBase
import net.minecraft.command.ICommandSender
import net.minecraft.util.BlockPos
import net.minecraftforge.client.ClientCommandHandler

/**
 * Main command handler for CatDueller configuration and session management.
 *
 * Provides the `/catdueller` command with subcommands for opening the configuration GUI
 * and managing session statistics (wins, losses, winstreak).
 */
class ConfigCommand : CommandBase() {

    /**
     * Registers this command with the Minecraft Forge client command handler.
     */
    fun register() {
        ClientCommandHandler.instance.registerCommand(this)
    }

    override fun getCommandName(): String = "catdueller"

    override fun getCommandAliases(): List<String> = listOf("cd")

    override fun getCommandUsage(sender: ICommandSender?): String =
        "/catdueller <config|session>"

    override fun getRequiredPermissionLevel(): Int = 0

    override fun processCommand(sender: ICommandSender?, args: Array<out String>?) {
        when (args?.getOrNull(0)?.lowercase()) {
            "config", "cfg", "settings" -> openConfig()
            "session" -> handleSession(args.getOrNull(1))
            else -> handleDefault()
        }
    }

    override fun addTabCompletionOptions(
        sender: ICommandSender?,
        args: Array<out String>?,
        pos: BlockPos?
    ): List<String> {
        return when (args?.size) {
            1 -> getListOfStringsMatchingLastWord(args, "config", "session")
            2 -> if (args[0].equals("session", true))
                getListOfStringsMatchingLastWord(args, "reset", "show")
            else emptyList()
            else -> emptyList()
        }
    }

    /**
     * Opens the configuration GUI (delayed by 1 tick to allow chat screen to close).
     */
    private fun openConfig() {
        DelayedTaskHandler.schedule(1) {
            UScreen.displayScreen(CatDueller.config?.gui())
        }
    }

    /**
     * Default command handler that displays available commands.
     */
    private fun handleDefault() {
        ChatUtil.info("CatDueller Commands:")
        ChatUtil.info("/catdueller config - Open config GUI")
        ChatUtil.info("/catdueller session [reset|show] - Manage session statistics")
    }

    /**
     * Handles the session subcommand for viewing or resetting session statistics.
     */
    private fun handleSession(action: String?) {
        when (action?.lowercase()) {
            "reset" -> resetSession()
            "show", "stats", null -> showSession()
            else -> ChatUtil.info("Usage: /catdueller session [reset|show]")
        }
    }

    /**
     * Resets all session statistics to their initial values.
     */
    private fun resetSession() {
        Session.wins = 0
        Session.losses = 0
        Session.startTime = System.currentTimeMillis()
        ChatUtil.info("Session statistics have been reset!")
    }

    /**
     * Displays the current session statistics including wins, losses, and winstreak.
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

        ChatUtil.info(Session.getSession(winstreak))
    }
}
