package best.spaghetcodes.catdueller.commands

import best.spaghetcodes.catdueller.core.HWIDLock
import best.spaghetcodes.catdueller.utils.ChatUtils
import net.minecraft.command.CommandBase
import net.minecraft.command.ICommandSender
import net.minecraft.util.BlockPos
import net.minecraftforge.client.ClientCommandHandler

class HWIDCommand : CommandBase() {

    fun register() {
        ClientCommandHandler.instance.registerCommand(this)
    }

    override fun getCommandName(): String {
        return "hwid"
    }

    override fun getCommandUsage(sender: ICommandSender?): String {
        return "/hwid - Display HWID and authorization status"
    }

    override fun processCommand(sender: ICommandSender?, args: Array<out String>?) {
        println("[HWIDCommand] processCommand called")

        val isAuthorized = HWIDLock.isAuthorized()
        val currentHWID = HWIDLock.getCurrentHWID()

        ChatUtils.info("Your HWID: $currentHWID")
        ChatUtils.info("Authorized: ${if (isAuthorized) "§aTrue" else "§cFalse"}")

        // Also print to console for debugging
        println("[HWIDCommand] HWID: $currentHWID, Authorized: $isAuthorized")
    }

    override fun addTabCompletionOptions(
        sender: ICommandSender?,
        args: Array<out String>?,
        pos: BlockPos?
    ): List<String> {
        return emptyList()
    }

    override fun getRequiredPermissionLevel(): Int {
        return 0
    }
}