package best.spaghetcodes.catdueller

import best.spaghetcodes.catdueller.bot.BotBase
import best.spaghetcodes.catdueller.bot.impl.Sumo
import best.spaghetcodes.catdueller.bot.player.LobbyMovement
import best.spaghetcodes.catdueller.bot.player.Mouse
import best.spaghetcodes.catdueller.bot.player.Movement
import best.spaghetcodes.catdueller.bot.state.StateManager
import best.spaghetcodes.catdueller.commands.*
import best.spaghetcodes.catdueller.core.Config
import best.spaghetcodes.catdueller.core.DelayedTaskHandler
import best.spaghetcodes.catdueller.core.HWIDLock
import best.spaghetcodes.catdueller.core.KeyBindings
import best.spaghetcodes.catdueller.irc.IRCDodgeClient
import best.spaghetcodes.catdueller.utils.client.TimerUtil
import best.spaghetcodes.catdueller.utils.game.ParticleUtil
import net.minecraft.client.Minecraft
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLInitializationEvent

/**
 * Main mod class for CatDueller, a Minecraft Forge mod for automated dueling.
 *
 * This class serves as the entry point for the mod, handling initialization
 * of all core systems including configuration, commands, key bindings,
 * HWID verification, and bot management.
 */
@Mod(
    modid = CatDueller.MOD_ID,
    name = CatDueller.MOD_NAME,
    version = CatDueller.VERSION
)
class CatDueller {

    companion object {
        /** Unique identifier for the mod used by Forge. */
        const val MOD_ID = "catdueller"

        /** Display name of the mod. */
        const val MOD_NAME = "CatDueller"

        /** Current version of the mod. */
        const val VERSION = "1.0.0"

        /** File path for the mod configuration file. */
        const val CONFIG_LOCATION = "./config/catdueller.toml"

        /** Reference to the Minecraft client instance. */
        val mc: Minecraft = Minecraft.getMinecraft()

        /** The mod configuration instance containing all user settings. */
        var config: Config? = null

        /** The currently active bot instance handling automated gameplay. */
        var bot: BotBase? = null

        /**
         * Swaps the currently active bot with a new bot instance.
         *
         * Properly unregisters the current bot from the event bus before
         * registering the new one to prevent duplicate event handling.
         *
         * @param b The new bot instance to activate.
         */
        fun swapBot(b: BotBase) {
            if (bot != null) MinecraftForge.EVENT_BUS.unregister(bot)
            bot = b
            MinecraftForge.EVENT_BUS.register(bot)
        }
    }

    /**
     * Mod initialization handler called by Forge during startup.
     *
     * Initializes all mod components in the following order:
     * 1. Configuration loading
     * 2. Command registration
     * 3. Key binding registration
     * 4. HWID verification
     * 5. Event handler registration
     * 6. Bot initialization
     * 7. Shutdown hook setup
     *
     * @param event The FML initialization event provided by Forge.
     */
    @Mod.EventHandler
    fun init(event: FMLInitializationEvent) {
        println("[CatDueller] Starting initialization...")

        config = Config()
        config?.initialize()

        ConfigCommand().register()
        MovementCommand().register()
        HWIDCommand().register()
        PingCommand().register()
        ParticleTestCommand().register()
        RobotCommand().register()
        KeyBindings.register()

        println("[CatDueller] Initializing HWID lock system...")
        if (!HWIDLock.initialize()) {
            println("[CatDueller] HWID generation failed")
            println("[CatDueller] Your HWID: ${HWIDLock.getCurrentHWID()}")
        }

        // Always initialize IRC client - it handles HWID authentication
        println("[CatDueller] Initializing IRC client for auth...")
        IRCDodgeClient.initialize()

        MinecraftForge.EVENT_BUS.register(StateManager)
        MinecraftForge.EVENT_BUS.register(Mouse)
        MinecraftForge.EVENT_BUS.register(Movement)
        MinecraftForge.EVENT_BUS.register(LobbyMovement)
        MinecraftForge.EVENT_BUS.register(KeyBindings)
        MinecraftForge.EVENT_BUS.register(ParticleUtil)
        MinecraftForge.EVENT_BUS.register(DelayedTaskHandler)

        val selectedBotIndex = config?.currentBot ?: 0
        val selectedBot = config?.bots?.get(selectedBotIndex) ?: Sumo()
        swapBot(selectedBot)

        Runtime.getRuntime().addShutdownHook(Thread {
            println("CatDueller shutting down, cleaning up...")
            TimerUtil.cancelAllTimers()
            IRCDodgeClient.disconnect()
        })
    }
}
