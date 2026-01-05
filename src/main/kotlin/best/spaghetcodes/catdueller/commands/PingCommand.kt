package best.spaghetcodes.catdueller.commands

import best.spaghetcodes.catdueller.CatDueller
import best.spaghetcodes.catdueller.utils.client.ChatUtil
import net.minecraft.command.CommandBase
import net.minecraft.command.ICommandSender
import net.minecraft.util.BlockPos
import net.minecraftforge.client.ClientCommandHandler

/**
 * Command handler for displaying the current server ping.
 *
 * Provides the `/ping` command which retrieves and displays the current
 * network latency to the server.
 */
class PingCommand : CommandBase() {

    /**
     * Registers this command with the Minecraft Forge client command handler.
     */
    fun register() {
        ClientCommandHandler.instance.registerCommand(this)
    }

    override fun getCommandName(): String = "ping"

    override fun getCommandUsage(sender: ICommandSender?): String =
        "/ping - Display current server latency"

    override fun getRequiredPermissionLevel(): Int = 0

    override fun processCommand(sender: ICommandSender?, args: Array<out String>?) {
        val bot = CatDueller.bot
        if (bot != null) {
            val pingStatus = bot.getPingStatus()
            val ping = bot.getServerPing()

            ChatUtil.info("Current ping: $pingStatus")

            if (!(ping >= 0)) {
                ChatUtil.info("Can't get ping")
            }
        } else {
            ChatUtil.info("Bot is not initialized")
        }
    }

    override fun addTabCompletionOptions(
        sender: ICommandSender?,
        args: Array<out String>?,
        pos: BlockPos?
    ): List<String> = emptyList()
}
