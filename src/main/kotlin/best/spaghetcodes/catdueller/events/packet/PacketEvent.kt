package best.spaghetcodes.catdueller.events.packet

import net.minecraft.network.Packet
import net.minecraftforge.fml.common.eventhandler.Cancelable
import net.minecraftforge.fml.common.eventhandler.Event

@Cancelable
open class PacketEvent(private var packet: Packet<*>) : Event() {

    fun getPacket(): Packet<*> {
        return packet
    }

    class Outgoing(packetIn: Packet<*>) : PacketEvent(packetIn)
    class Incoming(packetIn: Packet<*>) : PacketEvent(packetIn)
}
