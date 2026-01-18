package org.afterlike.catdueller.commands

import net.minecraft.command.CommandBase
import net.minecraft.command.ICommandSender
import net.minecraft.util.BlockPos
import net.minecraftforge.client.ClientCommandHandler
import org.afterlike.catdueller.utils.client.ChatUtil
import java.awt.Robot
import java.awt.event.InputEvent

/**
 * Command handler for performing a single mouse click using Java's AWT Robot.
 *
 * Provides the `/robot` command which creates a Robot instance and executes
 * a left mouse click. Useful for testing Robot functionality and verifying
 * that AWT input simulation is working correctly on the current system.
 *
 * Note: Robot functionality may be restricted on some systems due to security
 * policies or accessibility permissions.
 */
class RobotCommand : CommandBase() {

    /**
     * Registers this command with the Minecraft Forge client command handler.
     */
    fun register() {
        ClientCommandHandler.instance.registerCommand(this)
    }

    override fun getCommandName(): String = "robot"

    override fun getCommandUsage(sender: ICommandSender?): String =
        "/robot - Test AWT Robot mouse click"

    override fun getRequiredPermissionLevel(): Int = 0

    override fun processCommand(sender: ICommandSender?, args: Array<out String>?) {
        try {
            val robot = Robot()

            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)

            ChatUtil.info("Robot click executed successfully")
        } catch (e: SecurityException) {
            ChatUtil.error("Robot creation blocked by security policy: ${e.message}")
        } catch (e: Exception) {
            ChatUtil.error("Failed to execute robot click: ${e.message}")
        }
    }

    override fun addTabCompletionOptions(
        sender: ICommandSender?,
        args: Array<out String>?,
        pos: BlockPos?
    ): List<String> = emptyList()
}
