package best.spaghetcodes.catdueller.core

import best.spaghetcodes.catdueller.CatDueller
import gg.essential.api.EssentialAPI
import net.minecraft.client.settings.KeyBinding
import net.minecraftforge.fml.client.registry.ClientRegistry
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent
import org.lwjgl.input.Keyboard

object KeyBindings {

    val toggleBotKeyBinding = KeyBinding("cat.toggleBot", Keyboard.KEY_SEMICOLON, "category.cat")
    val configGuiKeyBinding = KeyBinding("cat.configGui", Keyboard.KEY_RSHIFT, "category.cat")

    fun register() {
        ClientRegistry.registerKeyBinding(toggleBotKeyBinding)
        ClientRegistry.registerKeyBinding(configGuiKeyBinding)
    }

    @SubscribeEvent
    fun onTick(ev: ClientTickEvent) {
        if (configGuiKeyBinding.isPressed) {
            EssentialAPI.getGuiUtil().openScreen(CatDueller.config?.gui())
        }
    }

}
