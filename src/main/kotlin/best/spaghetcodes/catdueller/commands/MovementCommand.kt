package best.spaghetcodes.catdueller.commands

import best.spaghetcodes.catdueller.bot.player.MovementRecorder
import best.spaghetcodes.catdueller.utils.client.ChatUtil
import net.minecraft.command.CommandBase
import net.minecraft.command.ICommandSender
import net.minecraft.util.BlockPos
import net.minecraftforge.client.ClientCommandHandler

/**
 * Command handler for managing lobby movement pattern recording.
 *
 * Provides the `/movement` command with subcommands to record, stop, list,
 * and delete movement patterns used for automated lobby navigation.
 */
class MovementCommand : CommandBase() {

    /**
     * Registers this command with the Minecraft Forge client command handler.
     */
    fun register() {
        ClientCommandHandler.instance.registerCommand(this)
    }

    override fun getCommandName(): String = "movement"

    override fun getCommandUsage(sender: ICommandSender?): String =
        "/movement <record|stop|list|delete> [name]"

    override fun getRequiredPermissionLevel(): Int = 0

    override fun processCommand(sender: ICommandSender?, args: Array<out String>?) {
        when (args?.getOrNull(0)?.lowercase()) {
            "record" -> MovementRecorder.enableAutoRecord()
            "stop" -> MovementRecorder.disableAutoRecord()
            "list" -> MovementRecorder.listPatterns()
            "delete" -> {
                val name = args.getOrNull(1)
                if (name.isNullOrBlank()) {
                    ChatUtil.info("Please provide a pattern name to delete!")
                } else {
                    MovementRecorder.deletePattern(name)
                }
            }
            else -> showHelp()
        }
    }

    override fun addTabCompletionOptions(
        sender: ICommandSender?,
        args: Array<out String>?,
        pos: BlockPos?
    ): List<String> {
        return when (args?.size) {
            1 -> getListOfStringsMatchingLastWord(args, "record", "stop", "list", "delete")
            else -> emptyList()
        }
    }

    /**
     * Displays all available movement subcommands.
     */
    private fun showHelp() {
        ChatUtil.info("Lobby Movement Recording Commands:")
        ChatUtil.info("/movement record - Enable auto record lobby movement")
        ChatUtil.info("/movement stop - Disable auto record lobby movement")
        ChatUtil.info("/movement list - List all recorded patterns")
        ChatUtil.info("/movement delete <name> - Delete a pattern")
    }
}
