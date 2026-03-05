package org.afterlike.catdueller.utils.debug

import net.minecraft.client.Minecraft
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import org.afterlike.catdueller.events.PacketEvent
import org.afterlike.catdueller.utils.client.ChatUtil
import kotlin.math.abs

/**
 * Debug utility that intercepts outgoing rotation packets and verifies
 * that every yaw/pitch delta is a valid multiple of the GCD interval.
 *
 * Register/unregister via [enable]/[disable]. Results are printed to chat.
 */
object GcdDebug {

    private var enabled = false
    private var lastYaw = Float.NaN
    private var lastPitch = Float.NaN
    private var totalPackets = 0
    private var failedPackets = 0

    fun enable() {
        enabled = true
        lastYaw = Float.NaN
        lastPitch = Float.NaN
        totalPackets = 0
        failedPackets = 0
        ChatUtil.info("§a[GCD Debug] Enabled — watching outgoing rotation packets")
    }

    fun disable() {
        enabled = false
        ChatUtil.info("§c[GCD Debug] Disabled — $totalPackets packets checked, $failedPackets failed")
    }

    fun isEnabled() = enabled

    @SubscribeEvent
    fun onOutgoing(event: PacketEvent.Outgoing) {
        if (!enabled) return

        val packet = event.getPacket()
        // C05 (look) and C06 (pos+look) both extend C03PacketPlayer
        if (packet !is C03PacketPlayer) return

        // C04 (pos only) also extends C03 but has rotating=false
        val rotating = try {
            val field = C03PacketPlayer::class.java.getDeclaredField("field_149481_i") // rotating
            field.isAccessible = true
            field.getBoolean(packet)
        } catch (_: Exception) {
            try {
                val field = C03PacketPlayer::class.java.getDeclaredField("rotating")
                field.isAccessible = true
                field.getBoolean(packet)
            } catch (_: Exception) {
                // If we can't read the field, assume it has rotation if yaw != 0
                true
            }
        }
        if (!rotating) return

        val yaw = packet.yaw
        val pitch = packet.pitch

        if (lastYaw.isNaN()) {
            lastYaw = yaw
            lastPitch = pitch
            return
        }

        val dyaw = yaw - lastYaw
        val dpitch = pitch - lastPitch
        lastYaw = yaw
        lastPitch = pitch

        // Skip zero-delta packets
        if (dyaw == 0f && dpitch == 0f) return

        totalPackets++

        val gcd = computeGcd()
        if (gcd <= 0f) return

        val yawRemainder = abs(dyaw % gcd)
        val pitchRemainder = abs(dpitch % gcd)
        // Allow small float tolerance
        val tolerance = gcd * 0.01f

        val yawOk = yawRemainder < tolerance || abs(yawRemainder - gcd) < tolerance
        val pitchOk = pitchRemainder < tolerance || abs(pitchRemainder - gcd) < tolerance

        if (!yawOk || !pitchOk) {
            failedPackets++
            ChatUtil.info(
                "§c[GCD FAIL] dyaw=%.4f dpitch=%.4f gcd=%.4f yawRem=%.6f pitchRem=%.6f".format(
                    dyaw, dpitch, gcd, yawRemainder, pitchRemainder
                )
            )
        }
    }

    private fun computeGcd(): Float {
        val sens = Minecraft.getMinecraft().gameSettings.mouseSensitivity
        val f = sens * 0.6f + 0.2f
        return f * f * f * 1.2f
    }
}
