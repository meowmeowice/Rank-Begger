package org.afterlike.catdueller.mixins;

import org.afterlike.catdueller.events.PacketEvent;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin for {@link NetworkManager} to intercept network packet traffic.
 *
 * <p>This mixin hooks into both outgoing and incoming packet handlers to fire
 * {@link PacketEvent} instances on the Forge event bus. This allows other parts
 * of the mod to monitor and optionally cancel packet transmission or processing.</p>
 *
 * @see PacketEvent.Outgoing
 * @see PacketEvent.Incoming
 */
@Mixin(NetworkManager.class)
public class MixinNetworkManager {

    /**
     * Intercepts outgoing packets before they are sent to the server.
     *
     * <p>Posts a {@link PacketEvent.Outgoing} to the Forge event bus. If the event
     * is canceled by a listener, the packet transmission is prevented.</p>
     *
     * @param packet The packet being sent.
     * @param ci     Callback information for the injection.
     */
    @Inject(method = "sendPacket*", at = @At("HEAD"), cancellable = true)
    private void catdueller$onSend(Packet<?> packet, CallbackInfo ci) {
        PacketEvent.Outgoing event = new PacketEvent.Outgoing(packet);
        MinecraftForge.EVENT_BUS.post(event);

        if (event.isCanceled()) {
            ci.cancel();
        }
    }

    /**
     * Intercepts incoming packets before they are processed by the client.
     *
     * <p>Posts a {@link PacketEvent.Incoming} to the Forge event bus. If the event
     * is canceled by a listener, the packet processing is prevented.</p>
     *
     * @param ctx    The Netty channel handler context.
     * @param packet The packet being received.
     * @param ci     Callback information for the injection.
     */
    @Inject(method = "channelRead0*", at = @At("HEAD"), cancellable = true)
    private void catdueller$onReceive(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
        PacketEvent.Incoming event = new PacketEvent.Incoming(packet);
        MinecraftForge.EVENT_BUS.post(event);

        if (event.isCanceled()) {
            ci.cancel();
        }
    }
}