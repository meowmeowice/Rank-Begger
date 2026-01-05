package best.spaghetcodes.catdueller.core

import best.spaghetcodes.catdueller.CatDueller
import gg.essential.api.EssentialAPI
import net.minecraft.client.settings.KeyBinding
import net.minecraftforge.fml.client.registry.ClientRegistry
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent
import org.lwjgl.input.Keyboard

/**
 * Manages custom key bindings for the CatDueller mod.
 *
 * Provides key bindings for toggling the bot and opening the configuration GUI.
 * Key bindings are registered with Forge and can be customized in Minecraft's controls menu.
 */
object KeyBindings {

    /**
     * Key binding for toggling the bot on/off.
     * Default key: Semicolon (;)
     */
    val toggleBotKeyBinding = KeyBinding("cat.toggleBot", Keyboard.KEY_SEMICOLON, "category.cat")

    /**
     * Key binding for opening the configuration GUI.
     * Default key: Right Shift
     */
    val configGuiKeyBinding = KeyBinding("cat.configGui", Keyboard.KEY_RSHIFT, "category.cat")

    /**
     * Registers all key bindings with the Forge client registry.
     * Called during mod initialization.
     */
    fun register() {
        ClientRegistry.registerKeyBinding(toggleBotKeyBinding)
        ClientRegistry.registerKeyBinding(configGuiKeyBinding)
    }

    /**
     * Handles key press events on each client tick.
     * Opens the configuration GUI when the config key binding is pressed.
     *
     * @param ev The client tick event from Forge.
     */
    @SubscribeEvent
    fun onTick(ev: ClientTickEvent) {
        if (configGuiKeyBinding.isPressed) {
            EssentialAPI.getGuiUtil().openScreen(CatDueller.config?.gui())
        }
    }
}
