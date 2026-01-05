package best.spaghetcodes.catdueller.commands

import best.spaghetcodes.catdueller.utils.client.ChatUtil
import best.spaghetcodes.catdueller.utils.game.ParticleUtil
import net.minecraft.client.Minecraft
import net.minecraft.command.CommandBase
import net.minecraft.command.ICommandSender
import net.minecraft.util.EnumParticleTypes
import net.minecraftforge.client.ClientCommandHandler

/**
 * Command handler for testing and debugging particle detection functionality.
 *
 * Provides the `/particletest` command to scan for nearby particles and toggle debug mode.
 * Useful for verifying that particle detection is working correctly for game mechanics
 * that rely on particle effects.
 */
class ParticleTestCommand : CommandBase() {

    /**
     * Returns the name of this command.
     *
     * @return The command name "particletest".
     */
    override fun getCommandName(): String = "particletest"

    /**
     * Returns the usage string for this command.
     *
     * @param sender The command sender requesting usage information.
     * @return A string describing the command syntax.
     */
    override fun getCommandUsage(sender: ICommandSender?): String = "/particletest [radius] [debug]"

    /**
     * Returns the required permission level to execute this command.
     *
     * @return 0, allowing all players to use this command.
     */
    override fun getRequiredPermissionLevel(): Int = 0

    /**
     * Executes the particle test command.
     *
     * Scans for common particle types within the specified radius of the player and reports
     * which particles were detected. Can also toggle debug mode for detailed console output.
     *
     * @param sender The entity that sent the command.
     * @param args Optional arguments: [0] = search radius in blocks, [1] = "debug" to toggle debug mode.
     */
    override fun processCommand(sender: ICommandSender?, args: Array<out String>?) {
        val player = Minecraft.getMinecraft().thePlayer ?: return
        val radius = args?.getOrNull(0)?.toDoubleOrNull() ?: 10.0
        val enableDebug = args?.getOrNull(1)?.equals("debug", ignoreCase = true) ?: false

        if (enableDebug) {
            ParticleUtil.debugMode = !ParticleUtil.debugMode
            ChatUtil.info("Particle debug mode: ${if (ParticleUtil.debugMode) "ON" else "OFF"}")
            if (ParticleUtil.debugMode) {
                ChatUtil.info("Debug output will appear in console")
            }
            return
        }

        ChatUtil.info("=== Particle Detection Test ===")
        ChatUtil.info("Testing within $radius blocks of player")
        ChatUtil.info("Use '/particletest debug' to toggle debug mode")
        ChatUtil.info("")

        ChatUtil.info(ParticleUtil.getDebugInfo())
        ChatUtil.info("")

        val particleTypes = listOf(
            EnumParticleTypes.PORTAL to "Portal",
            EnumParticleTypes.SLIME to "Slime",
            EnumParticleTypes.REDSTONE to "Redstone",
            EnumParticleTypes.CRIT to "Crit",
            EnumParticleTypes.CRIT_MAGIC to "Magic Crit",
            EnumParticleTypes.FLAME to "Flame",
            EnumParticleTypes.SMOKE_NORMAL to "Smoke"
        )

        var foundAny = false
        for ((type, name) in particleTypes) {
            val found = ParticleUtil.hasParticleNearby(
                player.posX, player.posY, player.posZ, type, radius, true
            )
            if (found) {
                ChatUtil.info("[checkmark] Found $name particles")
                foundAny = true
            }
        }

        if (!foundAny) {
            ChatUtil.info("No particles detected within $radius blocks")
        }

        ChatUtil.info("")
        ChatUtil.info("=== Test Complete ===")
    }

    /**
     * Registers this command with the Minecraft Forge client command handler.
     */
    fun register() {
        ClientCommandHandler.instance.registerCommand(this)
    }
}
