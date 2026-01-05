package best.spaghetcodes.catdueller.utils

import best.spaghetcodes.catdueller.CatDueller
import best.spaghetcodes.catdueller.events.packet.PacketEvent
import net.minecraft.client.Minecraft
import net.minecraft.network.play.server.S2APacketParticles
import net.minecraft.util.EnumParticleTypes
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

/**
 * Particle detector using S2APacketParticles for Forge 1.8.9
 * Tracks particles near the player for dodge detection
 */
object ParticleDetector {

    private val recentParticles = ConcurrentHashMap<ParticleInfo, Long>()
    private const val PARTICLE_LIFETIME_MS = 500L // Keep particles for 500ms
    var debugMode = false // Enable to see packet structure details

    data class ParticleInfo(
        val type: EnumParticleTypes,
        val x: Double,
        val y: Double,
        val z: Double
    )

    @SubscribeEvent
    fun onPacketReceive(event: PacketEvent.Incoming) {
        // Only process particles when bot is toggled on to prevent performance issues
        if (CatDueller.bot?.toggled() != true) return

        val packet = event.getPacket()

        if (packet is S2APacketParticles) {
            try {
                // Use reflection to access S2APacketParticles fields
                // Field names may vary between different Forge versions
                val packetClass = packet.javaClass

                if (debugMode) {
                    println("[ParticleDetector] Received S2APacketParticles")
                    println("[ParticleDetector] Packet class: ${packetClass.name}")
                }

                // Try to get particle type/name
                var particleType: EnumParticleTypes? = null
                var x = 0.0
                var y = 0.0
                var z = 0.0
                var coordCount = 0

                // Try different possible field names for particle type
                for (field in packetClass.declaredFields) {
                    field.isAccessible = true
                    val value = field.get(packet)

                    if (debugMode) {
                        println("[ParticleDetector]   Field: ${field.name} = $value (${value?.javaClass?.simpleName})")
                    }

                    when {
                        // Check for EnumParticleTypes field
                        value is EnumParticleTypes -> {
                            particleType = value
                            if (debugMode) println("[ParticleDetector]     -> Particle type: ${value.particleName}")
                        }
                        // Check for particle name string
                        value is String && particleType == null -> {
                            particleType = try {
                                EnumParticleTypes.values().find {
                                    it.particleName.equals(value, ignoreCase = true)
                                }
                            } catch (_: Exception) {
                                null
                            }
                            if (debugMode && particleType != null) {
                                println("[ParticleDetector]     -> Particle type from string: ${particleType.particleName}")
                            }
                        }
                        // Check for coordinate fields (float or double)
                        value is Float || value is Double -> {
                            val doubleValue = when (value) {
                                is Float -> value.toDouble()
                                is Double -> value
                                else -> 0.0
                            }

                            // Assign coordinates in order: x, y, z
                            when (coordCount) {
                                0 -> {
                                    x = doubleValue; coordCount++
                                }

                                1 -> {
                                    y = doubleValue; coordCount++
                                }

                                2 -> {
                                    z = doubleValue; coordCount++
                                }
                            }

                            if (debugMode) {
                                val coord = when (coordCount - 1) {
                                    0 -> "X"
                                    1 -> "Y"
                                    2 -> "Z"
                                    else -> "?"
                                }
                                println("[ParticleDetector]     -> Coordinate $coord: $doubleValue")
                            }
                        }
                    }
                }

                // Only add if we successfully got particle type and coordinates
                if (particleType != null && coordCount >= 3) {
                    val particleInfo = ParticleInfo(particleType, x, y, z)
                    recentParticles[particleInfo] = System.currentTimeMillis()

                    if (debugMode) {
                        println("[ParticleDetector] Added particle: ${particleType.particleName} at ($x, $y, $z)")
                        println("[ParticleDetector] Total tracked: ${recentParticles.size}")
                    }
                } else if (debugMode) {
                    println("[ParticleDetector] Failed to parse particle: type=$particleType, coords=$coordCount")
                }
            } catch (e: Exception) {
                if (debugMode) {
                    println("[ParticleDetector] Error parsing packet: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Check if specific particle type exists near coordinates
     */
    fun hasParticleNearby(
        x: Double,
        y: Double,
        z: Double,
        particleType: EnumParticleTypes,
        radius: Double = 3.0,
        debug: Boolean = false
    ): Boolean {
        cleanupOldParticles()

        var found = false
        var nearbyCount = 0

        for ((particle, _) in recentParticles) {
            if (particle.type == particleType) {
                val dx = particle.x - x
                val dy = particle.y - y
                val dz = particle.z - z
                val distance = sqrt(dx * dx + dy * dy + dz * dz)

                if (distance <= radius) {
                    nearbyCount++
                    found = true
                    if (debug) {
                        println(
                            "[ParticleDetector] Found ${particleType.particleName} at distance ${
                                "%.2f".format(
                                    distance
                                )
                            }"
                        )
                    }
                }
            }
        }

        if (debug && !found) {
            println("[ParticleDetector] No ${particleType.particleName} particles within $radius blocks")
            println("[ParticleDetector] Total tracked particles: ${recentParticles.size}")
        }

        return found
    }

    /**
     * Check if specific particle type exists within a distance range
     */
    fun hasParticleInRange(
        x: Double,
        y: Double,
        z: Double,
        particleType: EnumParticleTypes,
        minRadius: Double = 1.0,
        maxRadius: Double = 20.0,
        debug: Boolean = false
    ): Boolean {
        cleanupOldParticles()

        var found = false
        var nearbyCount = 0

        for ((particle, _) in recentParticles) {
            if (particle.type == particleType) {
                val dx = particle.x - x
                val dy = particle.y - y
                val dz = particle.z - z
                val distance = sqrt(dx * dx + dy * dy + dz * dz)

                if (distance in minRadius..maxRadius) {
                    nearbyCount++
                    found = true
                    if (debug) {
                        println(
                            "[ParticleDetector] Found ${particleType.particleName} at distance ${
                                "%.2f".format(
                                    distance
                                )
                            } (within range ${minRadius}-${maxRadius})"
                        )
                    }
                }
            }
        }

        if (debug && !found) {
            println("[ParticleDetector] No ${particleType.particleName} particles within range ${minRadius}-${maxRadius} blocks")
            println("[ParticleDetector] Total tracked particles: ${recentParticles.size}")
        }

        return found
    }

    /**
     * Check if any of the specified particle types exist near coordinates
     */
    fun hasAnyParticleNearby(
        x: Double,
        y: Double,
        z: Double,
        particleTypes: List<EnumParticleTypes>,
        radius: Double = 3.0,
        debug: Boolean = false
    ): Boolean {
        cleanupOldParticles()

        for (particleType in particleTypes) {
            if (hasParticleNearby(x, y, z, particleType, radius, debug)) {
                return true
            }
        }
        return false
    }

    /**
     * Check for portal particles (used for player hits in some servers)
     */
    fun hasPortalParticleNearby(
        x: Double,
        y: Double,
        z: Double,
        radius: Double = 3.0,
        debug: Boolean = false
    ): Boolean {
        return hasParticleNearby(x, y, z, EnumParticleTypes.PORTAL, radius, debug)
    }

    /**
     * Check for slime particles (used for player hits in some servers)
     */
    fun hasSlimeParticleNearby(x: Double, y: Double, z: Double, radius: Double = 3.0, debug: Boolean = false): Boolean {
        return hasParticleNearby(x, y, z, EnumParticleTypes.SLIME, radius, debug)
    }

    /**
     * Check for redstone/reddust particles (used for player hits in some servers)
     */
    fun hasRedstoneParticleNearby(
        x: Double,
        y: Double,
        z: Double,
        radius: Double = 3.0,
        debug: Boolean = false
    ): Boolean {
        return hasParticleNearby(x, y, z, EnumParticleTypes.REDSTONE, radius, debug)
    }

    /**
     * Check for heart particles (used for player hits/healing in some servers)
     */
    fun hasHeartParticleNearby(x: Double, y: Double, z: Double, radius: Double = 3.0, debug: Boolean = false): Boolean {
        return hasParticleNearby(x, y, z, EnumParticleTypes.HEART, radius, debug)
    }

    /**
     * Check for angry villager particles (used for player hits/damage in some servers)
     */
    fun hasAngryVillagerParticleNearby(
        x: Double,
        y: Double,
        z: Double,
        radius: Double = 3.0,
        debug: Boolean = false
    ): Boolean {
        return hasParticleNearby(x, y, z, EnumParticleTypes.VILLAGER_ANGRY, radius, debug)
    }

    /**
     * Check for any hit indicator particles near player
     */
    fun hasHitParticleNearPlayer(radius: Double = 3.0, debug: Boolean = false): Boolean {
        val player = Minecraft.getMinecraft().thePlayer ?: return false

        val hitParticles = listOf(
            EnumParticleTypes.PORTAL,
            EnumParticleTypes.SLIME,
            EnumParticleTypes.REDSTONE,
            EnumParticleTypes.CRIT,
            EnumParticleTypes.CRIT_MAGIC,
            EnumParticleTypes.HEART,
            EnumParticleTypes.VILLAGER_ANGRY
        )

        return hasAnyParticleNearby(player.posX, player.posY, player.posZ, hitParticles, radius, debug)
    }

    /**
     * Remove particles older than PARTICLE_LIFETIME_MS
     */
    private fun cleanupOldParticles() {
        val currentTime = System.currentTimeMillis()
        recentParticles.entries.removeIf { (_, timestamp) ->
            currentTime - timestamp > PARTICLE_LIFETIME_MS
        }
    }

    /**
     * Clear all tracked particles
     */
    fun clear() {
        recentParticles.clear()
    }

    /**
     * Get debug information
     */
    fun getDebugInfo(): String {
        cleanupOldParticles()
        val particlesByType = recentParticles.keys.groupBy { it.type }
        return buildString {
            appendLine("Tracked particles: ${recentParticles.size}")
            for ((type, particles) in particlesByType) {
                appendLine("  ${type.particleName}: ${particles.size}")
            }
        }
    }
}
