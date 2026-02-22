package org.afterlike.catdueller.commands

import net.minecraft.command.CommandBase
import net.minecraft.command.ICommandSender
import net.minecraft.util.BlockPos
import net.minecraftforge.client.ClientCommandHandler
import org.afterlike.catdueller.CatDueller
import org.afterlike.catdueller.utils.client.ChatUtil

class InvCommand : CommandBase() {

    fun register() {
        ClientCommandHandler.instance.registerCommand(this)
    }

    override fun getCommandName(): String = "hotbar"

    override fun getCommandAliases(): List<String> = listOf("cathotbar")

    override fun getCommandUsage(sender: ICommandSender?): String =
        "/hotbar - Display hotbar item names"

    override fun getRequiredPermissionLevel(): Int = 0

    override fun processCommand(sender: ICommandSender?, args: Array<out String>?) {
        try {
            val player = CatDueller.mc.thePlayer
            if (player == null || player.inventory == null) {
                ChatUtil.info("Player not available")
                return
            }

            ChatUtil.info("=== Hotbar Items ===")
            for (i in 0..8) {
                val stack = player.inventory.getStackInSlot(i)
                if (stack != null) {
                    val registryName = net.minecraft.item.Item.itemRegistry.getNameForObject(stack.item) ?: "unknown"
                    ChatUtil.info("Slot $i: unlocalized=${stack.unlocalizedName} registry=$registryName (${stack.displayName}) x${stack.stackSize}")
                } else {
                    ChatUtil.info("Slot $i: [empty]")
                }
            }
        } catch (e: Exception) {
            ChatUtil.info("Error: ${e.message}")
        }
    }

    override fun addTabCompletionOptions(
        sender: ICommandSender?,
        args: Array<out String>?,
        pos: BlockPos?
    ): List<String> = emptyList()
}
