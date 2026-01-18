package org.afterlike.catdueller.mixins;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.network.handshake.FMLHandshakeMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mixin for {@link FMLHandshakeMessage.ModList} to modify the mod list sent during server handshake.
 *
 * <p>This mixin removes the catdueller mod from the handshake message when connecting
 * to multiplayer servers, preventing servers from detecting the mod's presence.</p>
 */
@Mixin(FMLHandshakeMessage.ModList.class)
public class MixinModList {

    /**
     * Shadow field containing the map of mod IDs to their version tags.
     */
    @Shadow(remap = false)
    private Map<String, String> modTags;

    /**
     * Injects into the ModList constructor to remove the catdueller mod from the handshake.
     *
     * <p>This injection runs after the constructor completes and removes the "catdueller"
     * entry from the mod tags map. This only occurs when connecting to multiplayer servers,
     * not in singleplayer worlds.</p>
     *
     * @param modContainerList The list of mod containers being processed.
     * @param ci               Callback information for the injection.
     */
    @Inject(method = "<init>(Ljava/util/List;)V", at = @At("RETURN"), remap = false)
    public void removeMod(List<ModContainer> modContainerList, CallbackInfo ci) {
        if (!Minecraft.getMinecraft().isSingleplayer()) {
            System.out.println("Removing mod from handshake...");
            this.modTags.keySet().removeIf(key -> Objects.equals(key, "catdueller"));
        }
    }

}