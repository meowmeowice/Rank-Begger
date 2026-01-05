package best.spaghetcodes.catdueller.commands

import best.spaghetcodes.catdueller.core.HWIDLock
import best.spaghetcodes.catdueller.utils.client.ChatUtil
import net.minecraft.command.CommandBase
import net.minecraft.command.ICommandSender
import net.minecraft.util.BlockPos
import net.minecraftforge.client.ClientCommandHandler

/**
 * Command handler for displaying Hardware ID (HWID) information and authorization status.
 *
 * Provides the `/hwid` command which shows the current machine's HWID and whether
 * it is authorized to use the mod.
 */
class HWIDCommand : CommandBase() {

    /**
     * Registers this command with the Minecraft Forge client command handler.
     */
    fun register() {
        ClientCommandHandler.instance.registerCommand(this)
    }

    /**
     * Returns the name of this command.
     *
     * @return The command name "hwid".
     */
    override fun getCommandName(): String {
        return "hwid"
    }

    /**
     * Returns the usage string for this command.
     *
     * @param sender The command sender requesting usage information.
     * @return A string describing how to use the command.
     */
    override fun getCommandUsage(sender: ICommandSender?): String {
        return "/hwid - Display HWID and authorization status"
    }

    /**
     * Executes the command, displaying the current HWID and authorization status.
     *
     * Outputs the information both to the in-game chat and the console.
     *
     * @param sender The entity that sent the command.
     * @param args Command arguments (unused).
     */
    override fun processCommand(sender: ICommandSender?, args: Array<out String>?) {
        println("[HWIDCommand] processCommand called")

        val isAuthorized = HWIDLock.isAuthorized()
        val currentHWID = HWIDLock.getCurrentHWID()

        ChatUtil.info("Your HWID: $currentHWID")
        ChatUtil.info("Authorized: ${if (isAuthorized) "§aTrue" else "§cFalse"}")

        println("[HWIDCommand] HWID: $currentHWID, Authorized: $isAuthorized")
    }

    /**
     * Returns tab completion options for this command.
     *
     * @param sender The command sender.
     * @param args Current command arguments.
     * @param pos The block position context.
     * @return An empty list as this command has no tab completions.
     */
    override fun addTabCompletionOptions(
        sender: ICommandSender?,
        args: Array<out String>?,
        pos: BlockPos?
    ): List<String> {
        return emptyList()
    }

    /**
     * Returns the required permission level to execute this command.
     *
     * @return 0, allowing all players to use this command.
     */
    override fun getRequiredPermissionLevel(): Int {
        return 0
    }
}