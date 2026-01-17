package org.afterlike.catdueller.bot.player

import org.afterlike.catdueller.CatDueller
import org.afterlike.catdueller.bot.player.LobbyMovement.checkOpponentRotationAndDodge
import org.afterlike.catdueller.bot.state.StateManager
import org.afterlike.catdueller.utils.client.ChatUtil
import org.afterlike.catdueller.utils.client.TimerUtil
import org.afterlike.catdueller.utils.game.WorldUtil
import org.afterlike.catdueller.utils.system.RandomUtil
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent
import java.util.*

/**
 * Handles player movement patterns during lobby/pre-game phases.
 * Provides automated movement behaviors like circling the arena, rotation adjustments,
 * and opponent detection for queue dodging.
 */
object LobbyMovement {

    /** Yaw rotation change applied each tick during lobby movement. */
    private var tickYawChange = 0f

    /** Collection of active timer intervals for movement patterns. */
    private var intervals: ArrayList<Timer?> = ArrayList()

    /** Whether rotation adjustment is currently active. */
    private var rotationAdjustmentActive = false

    /** Target pitch angle for rotation adjustment. */
    private var targetPitch = 0f

    /** Target yaw angle for rotation adjustment. */
    private var targetYaw = 0f

    /** Tolerance in degrees for considering rotation target reached. */
    private var angleTolerance = 0.01f

    /** Callback invoked when target rotation is reached. */
    private var onRotationReached: (() -> Unit)? = null

    /**
     * Initiates the sumo lobby movement pattern.
     * Attempts to use recorded movement patterns if configured, otherwise falls back to default behavior.
     */
    fun sumo() {
        if (CatDueller.config?.useRecordedMovement == true) {
            if (MovementRecorder.startRandomPlayback()) {
                return
            }
        }
        sumo1()
    }

    /**
     * Stops all lobby movement, clears timers, and resets rotation state.
     * Also stops any recorded movement playback.
     */
    fun stop() {
        Movement.clearAll()
        tickYawChange = 0f
        intervals.forEach { it?.cancel() }
        intervals.clear()

        rotationAdjustmentActive = false
        onRotationReached = null

        MovementRecorder.stopPlayback()
    }

    /**
     * Smoothly adjusts the player's rotation to the specified target angles.
     * The adjustment is applied gradually each tick until the target is reached.
     *
     * @param yaw Target yaw angle in degrees.
     * @param pitch Target pitch angle in degrees.
     * @param angleTolerance Tolerance in degrees for considering target reached (default 0.01).
     * @param onComplete Optional callback invoked when target rotation is reached.
     */
    fun adjustRotation(
        yaw: Float,
        pitch: Float,
        angleTolerance: Float = 0.01f,
        onComplete: (() -> Unit)? = null
    ) {
        if (CatDueller.mc.thePlayer == null) return

        targetYaw = yaw
        targetPitch = pitch
        this.angleTolerance = angleTolerance
        onRotationReached = onComplete

        rotationAdjustmentActive = true
    }

    /**
     * Updates the rotation adjustment each tick, smoothly moving toward the target angles.
     * Applies speed limiting to ensure smooth rotation and calls the completion callback when done.
     */
    private fun updateRotationAdjustment() {
        if (!rotationAdjustmentActive) return

        val player = CatDueller.mc.thePlayer ?: return

        val yawDiff = normalizeAngle(targetYaw - player.rotationYaw)
        val pitchDiff = targetPitch - player.rotationPitch

        if (kotlin.math.abs(yawDiff) <= angleTolerance &&
            kotlin.math.abs(pitchDiff) <= angleTolerance
        ) {
            rotationAdjustmentActive = false

            player.rotationYaw = targetYaw
            player.rotationPitch = targetPitch
            player.rotationYawHead = targetYaw

            onRotationReached?.invoke()
            return
        }

        if (kotlin.math.abs(yawDiff) > angleTolerance) {
            val yawSpeed = kotlin.math.min(kotlin.math.abs(yawDiff), 2f) * if (yawDiff > 0) 1 else -1
            player.rotationYaw += yawSpeed
            player.rotationYawHead = player.rotationYaw
        } else if (kotlin.math.abs(yawDiff) > 0.001f) {
            player.rotationYaw = targetYaw
            player.rotationYawHead = targetYaw
        }

        if (kotlin.math.abs(pitchDiff) > angleTolerance) {
            val pitchSpeed = kotlin.math.min(kotlin.math.abs(pitchDiff), 2f) * if (pitchDiff > 0) 1 else -1
            player.rotationPitch += pitchSpeed
        } else if (kotlin.math.abs(pitchDiff) > 0.001f) {
            player.rotationPitch = targetPitch
        }
    }

    /**
     * Normalizes an angle to the range of -180 to 180 degrees.
     *
     * @param angle The angle to normalize.
     * @return The normalized angle within -180 to 180 degrees.
     */
    private fun normalizeAngle(angle: Float): Float {
        var normalized = angle % 360f
        if (normalized > 180f) normalized -= 360f
        if (normalized < -180f) normalized += 360f
        return normalized
    }

    /**
     * Checks if the opponent's rotation differs from expected values and initiates a queue dodge if so.
     * Used to detect and avoid suspected bot opponents based on their rotation patterns.
     *
     * @param expectedYaw Expected opponent yaw angle in degrees.
     * @param expectedPitch Expected opponent pitch angle in degrees.
     * @param tolerance Angle tolerance for matching (default 1.0 degrees).
     * @return True if a dodge was initiated, false if rotation matches or no opponent found.
     */
    fun checkOpponentRotationAndDodge(
        expectedYaw: Float,
        expectedPitch: Float,
        tolerance: Float = 1.0f
    ): Boolean {
        val world = CatDueller.mc.theWorld ?: return false
        val player = CatDueller.mc.thePlayer ?: return false

        try {
            val otherPlayers = try {
                synchronized(world.playerEntities) {
                    world.playerEntities.toList().filter {
                        it != player && it is net.minecraft.entity.player.EntityPlayer
                    }
                }
            } catch (_: ConcurrentModificationException) {
                try {
                    world.loadedEntityList.filterIsInstance<net.minecraft.entity.player.EntityPlayer>().filter {
                        it != player
                    }
                } catch (_: Exception) {
                    return false
                }
            }

            if (otherPlayers.size == 1) {
                val opponent = otherPlayers[0] as net.minecraft.entity.player.EntityPlayer

                val opponentYaw = try {
                    opponent.rotationYaw
                } catch (_: Exception) {
                    return false
                }

                val opponentPitch = try {
                    opponent.rotationPitch
                } catch (_: Exception) {
                    return false
                }

                val rawYawDiff = opponentYaw - expectedYaw
                val normalizedYawDiff = normalizeAngle(rawYawDiff)
                val yawDiff = kotlin.math.abs(normalizedYawDiff)
                val pitchDiff = kotlin.math.abs(opponentPitch - expectedPitch)

                val yawMatches = yawDiff <= tolerance
                val pitchMatches = pitchDiff <= tolerance

                if (!yawMatches || !pitchMatches) {
                    try {
                        TimerUtil.setTimeout({
                            try {
                                val queueCommand =
                                    CatDueller.bot?.queueCommand
                                        ?: "/play duels_sumo_duel"
                                ChatUtil.sendAsPlayer(queueCommand)
                            } catch (_: Exception) {
                            }
                        }, RandomUtil.randomIntInRange(100, 300))
                    } catch (_: Exception) {
                    }

                    return true
                } else {
                    return false
                }
            }
        } catch (_: ConcurrentModificationException) {
            return false
        } catch (_: Exception) {
        }

        return false
    }

    /**
     * Checks if the opponent's rotation matches expected values and initiates a queue dodge if so.
     * Opposite behavior of [checkOpponentRotationAndDodge] - dodges when rotation MATCHES expected values.
     * Used to detect and avoid suspected bot opponents based on their rotation patterns.
     *
     * @param expectedYaw Expected opponent yaw angle in degrees.
     * @param expectedPitch Expected opponent pitch angle in degrees.
     * @param tolerance Angle tolerance for matching (default 1.0 degrees).
     * @return True if a dodge was initiated, false if rotation does not match or no opponent found.
     */
    fun checkOpponentRotationMatchAndDodge(
        expectedYaw: Float,
        expectedPitch: Float,
        tolerance: Float = 1.0f
    ): Boolean {
        val world = CatDueller.mc.theWorld ?: return false
        val player = CatDueller.mc.thePlayer ?: return false

        try {
            val otherPlayers = try {
                synchronized(world.playerEntities) {
                    world.playerEntities.toList().filter {
                        it != player && it is net.minecraft.entity.player.EntityPlayer
                    }
                }
            } catch (_: ConcurrentModificationException) {
                try {
                    world.loadedEntityList.filterIsInstance<net.minecraft.entity.player.EntityPlayer>().filter {
                        it != player
                    }
                } catch (_: Exception) {
                    return false
                }
            }

            if (otherPlayers.size == 1) {
                val opponent = otherPlayers[0] as net.minecraft.entity.player.EntityPlayer

                val opponentYaw = try {
                    opponent.rotationYaw
                } catch (_: Exception) {
                    return false
                }

                val opponentPitch = try {
                    opponent.rotationPitch
                } catch (_: Exception) {
                    return false
                }

                val rawYawDiff = opponentYaw - expectedYaw
                val normalizedYawDiff = normalizeAngle(rawYawDiff)
                val yawDiff = kotlin.math.abs(normalizedYawDiff)
                val pitchDiff = kotlin.math.abs(opponentPitch - expectedPitch)

                val yawMatches = yawDiff <= tolerance
                val pitchMatches = pitchDiff <= tolerance

                if (yawMatches && pitchMatches) {
                    try {
                        TimerUtil.setTimeout({
                            try {
                                val queueCommand =
                                    CatDueller.bot?.queueCommand
                                        ?: "/play duels_sumo_duel"
                                ChatUtil.sendAsPlayer(queueCommand)
                            } catch (_: Exception) {
                            }
                        }, RandomUtil.randomIntInRange(100, 300))
                    } catch (_: Exception) {
                    }

                    return true
                } else {
                    return false
                }
            }
        } catch (_: ConcurrentModificationException) {
            return false
        } catch (_: Exception) {
        }

        return false
    }

    /**
     * Default sumo lobby movement pattern.
     * Moves forward while sprinting, jumping, and rotating to avoid falling off the arena.
     * Automatically adjusts rotation speed based on proximity to edges.
     */
    private fun sumo1() {
        if (CatDueller.mc.thePlayer != null) {
            var left = RandomUtil.randomBool()

            val speed = RandomUtil.randomDoubleInRange(3.0, 9.0).toFloat()

            tickYawChange = if (left) -speed else speed
            TimerUtil.setTimeout(fun() {
                Movement.startForward()
                Movement.startSprinting()
                TimerUtil.setTimeout(fun() {
                    Movement.startJumping()
                }, RandomUtil.randomIntInRange(400, 800))
                intervals.add(TimerUtil.setInterval(fun() {
                    tickYawChange = if (WorldUtil.airInFront(CatDueller.mc.thePlayer, 7f)) {
                        if (WorldUtil.airInFront(CatDueller.mc.thePlayer, 3f)) {
                            RandomUtil.randomDoubleInRange(if (left) 9.5 else -9.5, if (left) 13.0 else -13.0)
                                .toFloat()
                        } else RandomUtil.randomDoubleInRange(if (left) 4.5 else -4.5, if (left) 7.0 else -7.0)
                            .toFloat()
                    } else {
                        0f
                    }
                }, 0, RandomUtil.randomIntInRange(50, 100)))
                intervals.add(TimerUtil.setTimeout(fun() {
                    intervals.add(TimerUtil.setInterval(fun() {
                        left = !left
                    }, 0, RandomUtil.randomIntInRange(5000, 10000)))
                }, RandomUtil.randomIntInRange(5000, 10000)))
            }, RandomUtil.randomIntInRange(100, 250))
        }
    }

    /**
     * Tick event handler for lobby movement updates.
     * Processes movement recorder playback, rotation adjustments, and yaw changes.
     *
     * @param ev The client tick event.
     */
    @SubscribeEvent
    fun onTick(ev: ClientTickEvent) {
        if (CatDueller.bot?.toggled() != true) return

        MovementRecorder.onTick()
        updateRotationAdjustment()

        if (tickYawChange != 0f && CatDueller.mc.thePlayer != null &&
            StateManager.state != StateManager.States.PLAYING && !MovementRecorder.isPlaying() && !rotationAdjustmentActive
        ) {
            CatDueller.mc.thePlayer.rotationYaw += tickYawChange
        }
    }
}