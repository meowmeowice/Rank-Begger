package best.spaghetcodes.catdueller

import best.spaghetcodes.catdueller.bot.BotBase
import best.spaghetcodes.catdueller.bot.StateManager
import best.spaghetcodes.catdueller.bot.bots.Sumo
import best.spaghetcodes.catdueller.bot.player.LobbyMovement
import best.spaghetcodes.catdueller.bot.player.Mouse
import best.spaghetcodes.catdueller.commands.ConfigCommand
import best.spaghetcodes.catdueller.core.Config
import best.spaghetcodes.catdueller.core.KeyBindings
import best.spaghetcodes.catdueller.core.HWIDLock
import best.spaghetcodes.catdueller.events.packet.PacketListener
import best.spaghetcodes.catdueller.utils.ChatUtils
import com.google.gson.Gson
import net.minecraft.client.Minecraft
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLInitializationEvent

@Mod(
    modid = CatDueller.MOD_ID,
    name = CatDueller.MOD_NAME,
    version = CatDueller.VERSION
)
class CatDueller {

    companion object {
        const val MOD_ID = "catdueller"
        const val MOD_NAME = "CatDueller"
        const val VERSION = "0.1.0"
        const val configLocation = "./config/catdueller.toml"

        val mc: Minecraft = Minecraft.getMinecraft()
        val gson = Gson()
        var config: Config? = null
        var bot: BotBase? = null

        fun swapBot(b: BotBase) {
            if (bot != null) MinecraftForge.EVENT_BUS.unregister(bot) // make sure to unregister the current bot
            bot = b
            MinecraftForge.EVENT_BUS.register(bot) // register the new bot
        }
    }

    @Mod.EventHandler
    fun init(event: FMLInitializationEvent) {
        println("[CatDueller] Starting initialization...")
        
        // Initialize basic components first
        config = Config()
        config?.preload()

        // Register commands (including HWID command)
        ConfigCommand().register()
        best.spaghetcodes.catdueller.commands.MovementCommand().register()
        best.spaghetcodes.catdueller.commands.HWIDCommand().register()
        best.spaghetcodes.catdueller.commands.PingCommand().register()
        best.spaghetcodes.catdueller.commands.ParticleTestCommand().register()
        KeyBindings.register()
        
        // Initialize HWID lock system
        println("[CatDueller] Initializing HWID lock system...")
        if (!HWIDLock.initialize()) {
            println("[CatDueller] HWID VERIFICATION FAILED")
            println("[CatDueller] Your HWID: ${HWIDLock.getCurrentHWID()}")
            println("[CatDueller] This module is not authorized for your hardware")
            // Don't return - allow commands to work for HWID display
        } else {
            println("[CatDueller] HWID verification successful")
        }

        MinecraftForge.EVENT_BUS.register(PacketListener())
        MinecraftForge.EVENT_BUS.register(StateManager)
        MinecraftForge.EVENT_BUS.register(Mouse)
        MinecraftForge.EVENT_BUS.register(best.spaghetcodes.catdueller.bot.player.Movement)
        MinecraftForge.EVENT_BUS.register(LobbyMovement)
        MinecraftForge.EVENT_BUS.register(KeyBindings)
        MinecraftForge.EVENT_BUS.register(best.spaghetcodes.catdueller.utils.ParticleDetector)

        // Safely initialize bot after config is fully loaded
        val selectedBotIndex = config?.currentBot ?: 0
        val selectedBot = config?.bots?.get(selectedBotIndex) ?: Sumo()
        swapBot(selectedBot)
        
        // Add shutdown hook to clean up timers when game closes
        Runtime.getRuntime().addShutdownHook(Thread {
            println("CatDueller shutting down, cleaning up timers...")
            best.spaghetcodes.catdueller.utils.TimeUtils.cancelAllTimers()
        })
    }
}
