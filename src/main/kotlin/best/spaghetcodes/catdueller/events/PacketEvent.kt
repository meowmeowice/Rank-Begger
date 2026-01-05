package best.spaghetcodes.catdueller.events

import net.minecraft.network.Packet
import net.minecraftforge.fml.common.eventhandler.Cancelable
import net.minecraftforge.fml.common.eventhandler.Event

/**
 * Base event class for network packet interception.
 *
 * This event is fired when packets are sent or received through the network manager.
 * It is cancelable, allowing listeners to prevent packets from being processed.
 *
 * @param packet The network packet associated with this event.
 * @see Outgoing
 * @see Incoming
 */
@Cancelable
open class PacketEvent(private var packet: Packet<*>) : Event() {

    /**
     * Retrieves the network packet associated with this event.
     *
     * @return The packet being sent or received.
     */
    fun getPacket(): Packet<*> {
        return packet
    }

    /**
     * Event fired when a packet is about to be sent to the server.
     *
     * Canceling this event will prevent the packet from being transmitted.
     *
     * @param packetIn The outgoing packet.
     */
    class Outgoing(packetIn: Packet<*>) : PacketEvent(packetIn)

    /**
     * Event fired when a packet is received from the server.
     *
     * Canceling this event will prevent the packet from being processed by the client.
     *
     * @param packetIn The incoming packet.
     */
    class Incoming(packetIn: Packet<*>) : PacketEvent(packetIn)
}
