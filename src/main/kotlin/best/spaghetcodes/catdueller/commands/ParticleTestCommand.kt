package best.spaghetcodes.catdueller.commands

import best.spaghetcodes.catdueller.utils.ChatUtils
import best.spaghetcodes.catdueller.utils.ParticleDetector
import net.minecraft.client.Minecraft
import net.minecraft.command.CommandBase
import net.minecraft.command.ICommandSender
import net.minecraft.util.EnumParticleTypes
import net.minecraftforge.client.ClientCommandHandler

class ParticleTestCommand : CommandBase() {
    
    override fun getCommandName(): String = "particletest"
    
    override fun getCommandUsage(sender: ICommandSender?): String = "/particletest [radius] [debug]"
    
    override fun getRequiredPermissionLevel(): Int = 0
    
    override fun processCommand(sender: ICommandSender?, args: Array<out String>?) {
        val player = Minecraft.getMinecraft().thePlayer ?: return
        val radius = args?.getOrNull(0)?.toDoubleOrNull() ?: 10.0
        val enableDebug = args?.getOrNull(1)?.equals("debug", ignoreCase = true) ?: false
        
        // Toggle debug mode if requested
        if (enableDebug) {
            ParticleDetector.debugMode = !ParticleDetector.debugMode
            ChatUtils.info("Particle debug mode: ${if (ParticleDetector.debugMode) "ON" else "OFF"}")
            if (ParticleDetector.debugMode) {
                ChatUtils.info("Debug output will appear in console")
            }
            return
        }
        
        ChatUtils.info("=== Particle Detection Test ===")
        ChatUtils.info("Testing within $radius blocks of player")
        ChatUtils.info("Use '/particletest debug' to toggle debug mode")
        ChatUtils.info("")
        
        // Show debug info
        ChatUtils.info(ParticleDetector.getDebugInfo())
        ChatUtils.info("")
        
        // Test for common particle types
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
            val found = ParticleDetector.hasParticleNearby(
                player.posX, player.posY, player.posZ, type, radius, true
            )
            if (found) {
                ChatUtils.info("✓ Found $name particles")
                foundAny = true
            }
        }
        
        if (!foundAny) {
            ChatUtils.info("No particles detected within $radius blocks")
        }
        
        ChatUtils.info("")
        ChatUtils.info("=== Test Complete ===")
    }
    
    fun register() {
        ClientCommandHandler.instance.registerCommand(this)
    }
}
