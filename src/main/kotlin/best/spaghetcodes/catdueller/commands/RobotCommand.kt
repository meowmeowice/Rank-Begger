package best.spaghetcodes.catdueller.commands

import best.spaghetcodes.catdueller.utils.ChatUtils
import gg.essential.api.commands.Command
import gg.essential.api.commands.DefaultHandler
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
class RobotCommand : Command("robot") {

    /**
     * Handles the robot command by performing a single left mouse click.
     *
     * Creates a new AWT Robot instance and simulates a left mouse button
     * press and release sequence. Reports success or failure to the player
     * via chat messages.
     */
    @DefaultHandler
    fun handle() {
        try {
            val robot = Robot()

            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)

            ChatUtils.info("Robot click executed successfully")
        } catch (e: SecurityException) {
            ChatUtils.error("Robot creation blocked by security policy: ${e.message}")
        } catch (e: Exception) {
            ChatUtils.error("Failed to execute robot click: ${e.message}")
        }
    }
}
