package org.afterlike.catdueller.utils.game

import org.afterlike.catdueller.CatDueller
import org.afterlike.catdueller.events.PacketEvent
import net.minecraft.client.Minecraft
import net.minecraft.network.play.server.S2APacketParticles
import net.minecraft.util.EnumParticleTypes
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

/**
 * Particle detection system for Forge 1.8.9.
 *
 * Intercepts S2APacketParticles packets to track particle spawns near the player.
 * Used for detecting combat-related particles such as hit indicators, critical hits,
 * and other server-spawned effects that may require dodge responses.
 */
object ParticleUtil {

    /** Thread-safe map storing recent particles with their spawn timestamps. */
    private val recentParticles = ConcurrentHashMap<ParticleInfo, Long>()

    /** Duration in milliseconds to retain particle information before cleanup. */
    private const val PARTICLE_LIFETIME_MS = 500L

    /** When enabled, outputs detailed packet parsing information to console. */
    var debugMode = false

    /**
     * Data class representing a detected particle with its type and position.
     *
     * @property type The Minecraft particle type.
     * @property x The X coordinate where the particle spawned.
     * @property y The Y coordinate where the particle spawned.
     * @property z The Z coordinate where the particle spawned.
     */
    data class ParticleInfo(
        val type: EnumParticleTypes,
        val x: Double,
        val y: Double,
        val z: Double
    )

    /**
     * Event handler for incoming packets.
     *
     * Processes S2APacketParticles packets using reflection to extract particle
     * type and coordinates. Only active when the bot is toggled on to minimize
     * performance impact.
     *
     * @param event The incoming packet event.
     */
    @SubscribeEvent
    fun onPacketReceive(event: PacketEvent.Incoming) {
        if (CatDueller.bot?.toggled() != true) return

        val packet = event.getPacket()

        if (packet is S2APacketParticles) {
            try {
                val packetClass = packet.javaClass

                if (debugMode) {
                    println("[ParticleDetector] Received S2APacketParticles")
                    println("[ParticleDetector] Packet class: ${packetClass.name}")
                }

                var particleType: EnumParticleTypes? = null
                var x = 0.0
                var y = 0.0
                var z = 0.0
                var coordCount = 0

                for (field in packetClass.declaredFields) {
                    field.isAccessible = true
                    val value = field.get(packet)

                    if (debugMode) {
                        println("[ParticleDetector]   Field: ${field.name} = $value (${value?.javaClass?.simpleName})")
                    }

                    when {
                        value is EnumParticleTypes -> {
                            particleType = value
                            if (debugMode) println("[ParticleDetector]     -> Particle type: ${value.particleName}")
                        }

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

                        value is Float || value is Double -> {
                            val doubleValue = when (value) {
                                is Float -> value.toDouble()
                                is Double -> value
                                else -> 0.0
                            }

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
     * Checks if a specific particle type exists near the given coordinates.
     *
     * @param x The X coordinate to check around.
     * @param y The Y coordinate to check around.
     * @param z The Z coordinate to check around.
     * @param particleType The particle type to search for.
     * @param radius The search radius in blocks.
     * @param debug If true, outputs debug information to console.
     * @return True if a matching particle exists within the radius.
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
     * Checks if a specific particle type exists within a distance range.
     *
     * @param x The X coordinate to check around.
     * @param y The Y coordinate to check around.
     * @param z The Z coordinate to check around.
     * @param particleType The particle type to search for.
     * @param minRadius The minimum distance in blocks.
     * @param maxRadius The maximum distance in blocks.
     * @param debug If true, outputs debug information to console.
     * @return True if a matching particle exists within the specified range.
     */
    fun hasParticleInRange(
        x: Double,
        y: Double,
        z: Double,
        particleType: EnumParticleTypes,
        minRadius: Double = 5.0,
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
     * Checks if any of the specified particle types exist near the given coordinates.
     *
     * @param x The X coordinate to check around.
     * @param y The Y coordinate to check around.
     * @param z The Z coordinate to check around.
     * @param particleTypes List of particle types to search for.
     * @param radius The search radius in blocks.
     * @param debug If true, outputs debug information to console.
     * @return True if any matching particle exists within the radius.
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
     * Checks for portal particles near the specified coordinates.
     *
     * Portal particles are used as hit indicators on some servers.
     *
     * @param x The X coordinate to check around.
     * @param y The Y coordinate to check around.
     * @param z The Z coordinate to check around.
     * @param radius The search radius in blocks.
     * @param debug If true, outputs debug information to console.
     * @return True if portal particles exist within the radius.
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
     * Checks for slime particles near the specified coordinates.
     *
     * Slime particles are used as hit indicators on some servers.
     *
     * @param x The X coordinate to check around.
     * @param y The Y coordinate to check around.
     * @param z The Z coordinate to check around.
     * @param radius The search radius in blocks.
     * @param debug If true, outputs debug information to console.
     * @return True if slime particles exist within the radius.
     */
    fun hasSlimeParticleNearby(x: Double, y: Double, z: Double, radius: Double = 3.0, debug: Boolean = false): Boolean {
        return hasParticleNearby(x, y, z, EnumParticleTypes.SLIME, radius, debug)
    }

    /**
     * Checks for redstone particles near the specified coordinates.
     *
     * Redstone particles are used as hit indicators on some servers.
     *
     * @param x The X coordinate to check around.
     * @param y The Y coordinate to check around.
     * @param z The Z coordinate to check around.
     * @param radius The search radius in blocks.
     * @param debug If true, outputs debug information to console.
     * @return True if redstone particles exist within the radius.
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
     * Checks for heart particles near the specified coordinates.
     *
     * Heart particles may indicate healing or hit effects on some servers.
     *
     * @param x The X coordinate to check around.
     * @param y The Y coordinate to check around.
     * @param z The Z coordinate to check around.
     * @param radius The search radius in blocks.
     * @param debug If true, outputs debug information to console.
     * @return True if heart particles exist within the radius.
     */
    fun hasHeartParticleNearby(x: Double, y: Double, z: Double, radius: Double = 3.0, debug: Boolean = false): Boolean {
        return hasParticleNearby(x, y, z, EnumParticleTypes.HEART, radius, debug)
    }

    /**
     * Checks for angry villager particles near the specified coordinates.
     *
     * Angry villager particles may indicate damage or hit effects on some servers.
     *
     * @param x The X coordinate to check around.
     * @param y The Y coordinate to check around.
     * @param z The Z coordinate to check around.
     * @param radius The search radius in blocks.
     * @param debug If true, outputs debug information to console.
     * @return True if angry villager particles exist within the radius.
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
     * Checks for any common hit indicator particles near the local player.
     *
     * Searches for portal, slime, redstone, crit, magic crit, heart,
     * and angry villager particles around the player's position.
     *
     * @param radius The search radius in blocks.
     * @param debug If true, outputs debug information to console.
     * @return True if any hit indicator particles exist near the player.
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
     * Removes particles that have exceeded the lifetime threshold.
     *
     * Called automatically before particle queries to ensure stale data is removed.
     */
    private fun cleanupOldParticles() {
        val currentTime = System.currentTimeMillis()
        recentParticles.entries.removeIf { (_, timestamp) ->
            currentTime - timestamp > PARTICLE_LIFETIME_MS
        }
    }

    /**
     * Clears all tracked particles from memory.
     */
    fun clear() {
        recentParticles.clear()
    }

    /**
     * Returns a formatted string containing debug information about tracked particles.
     *
     * Lists the total number of tracked particles and a breakdown by particle type.
     *
     * @return A multi-line string with particle tracking statistics.
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
