package best.spaghetcodes.catdueller.bot.player

import best.spaghetcodes.catdueller.CatDueller
import best.spaghetcodes.catdueller.bot.MovementRecorder
import best.spaghetcodes.catdueller.bot.StateManager
import best.spaghetcodes.catdueller.utils.RandomUtils
import best.spaghetcodes.catdueller.utils.TimeUtils
import best.spaghetcodes.catdueller.utils.WorldUtils
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent
import java.util.*

object LobbyMovement {

    private var tickYawChange = 0f
    private var intervals: ArrayList<Timer?> = ArrayList()

    // Rotation adjustment variables (pitch and yaw)
    private var rotationAdjustmentActive = false
    private var targetPitch = 0f
    private var targetYaw = 0f
    private var angleTolerance = 0.01f
    private var onRotationReached: (() -> Unit)? = null

    fun sumo() {
        // Check if we should use recorded movement patterns
        if (CatDueller.config?.useRecordedMovement == true) {
            // Try to start random recorded movement
            if (MovementRecorder.startRandomPlayback()) {
                return // Successfully started recorded movement
            }
            // If no recorded patterns available, fall back to default movement
        }

        /*val opt = RandomUtils.randomIntInRange(0, 1)
        when (opt) {
            0 -> sumo1()
            1 -> twerk()
        }*/
        sumo1()
    }

    fun stop() {
        Movement.clearAll()
        tickYawChange = 0f
        intervals.forEach { it?.cancel() }
        intervals.clear()  // 清空 intervals 列表

        // Stop rotation adjustment
        rotationAdjustmentActive = false
        onRotationReached = null

        // Also stop any recorded movement playback
        MovementRecorder.stopPlayback()
    }

    /**
     * 調整玩家的 pitch 和 yaw 到指定角度
     * @param yaw Target yaw angle
     * @param pitch Target pitch angle
     * @param angleTolerance Angle tolerance (default 0.01)
     * @param onComplete Callback when rotation is reached
     */
    fun adjustRotation(
        yaw: Float,
        pitch: Float,
        angleTolerance: Float = 0.01f,
        onComplete: (() -> Unit)? = null
    ) {
        if (CatDueller.mc.thePlayer == null) return

        // Set target values
        targetYaw = yaw
        targetPitch = pitch
        this.angleTolerance = angleTolerance
        onRotationReached = onComplete

        // Start rotation adjustment
        rotationAdjustmentActive = true

        println("Starting rotation adjustment: yaw=$yaw, pitch=$pitch")
        println("Angle tolerance: $angleTolerance")
    }

    /**
     * Update rotation adjustment logic
     */
    private fun updateRotationAdjustment() {
        if (!rotationAdjustmentActive) return

        val player = CatDueller.mc.thePlayer ?: return

        // Calculate angle differences
        val yawDiff = normalizeAngle(targetYaw - player.rotationYaw)
        val pitchDiff = targetPitch - player.rotationPitch

        // Check if we've reached the target rotation
        if (kotlin.math.abs(yawDiff) <= angleTolerance &&
            kotlin.math.abs(pitchDiff) <= angleTolerance
        ) {

            // Target rotation reached!
            rotationAdjustmentActive = false

            // Set exact final rotation
            player.rotationYaw = targetYaw
            player.rotationPitch = targetPitch
            player.rotationYawHead = targetYaw

            println("Target rotation reached! Final rotation: yaw=${player.rotationYaw}, pitch=${player.rotationPitch}")

            // Call completion callback
            onRotationReached?.invoke()

            return
        }

        // Update yaw with smooth adjustment
        if (kotlin.math.abs(yawDiff) > angleTolerance) {
            val yawSpeed = kotlin.math.min(kotlin.math.abs(yawDiff), 2f) * if (yawDiff > 0) 1 else -1
            player.rotationYaw += yawSpeed
            player.rotationYawHead = player.rotationYaw
        } else if (kotlin.math.abs(yawDiff) > 0.001f) {
            player.rotationYaw = targetYaw
            player.rotationYawHead = targetYaw
        }

        // Update pitch with smooth adjustment
        if (kotlin.math.abs(pitchDiff) > angleTolerance) {
            val pitchSpeed = kotlin.math.min(kotlin.math.abs(pitchDiff), 2f) * if (pitchDiff > 0) 1 else -1
            player.rotationPitch += pitchSpeed
        } else if (kotlin.math.abs(pitchDiff) > 0.001f) {
            player.rotationPitch = targetPitch
        }

        // Debug output for rotation adjustment
        if (CatDueller.mc.thePlayer.ticksExisted % 20 == 0) {
            println(
                "Rotation adjustment: yaw=${String.format("%.2f", player.rotationYaw)}/${
                    String.format(
                        "%.2f",
                        targetYaw
                    )
                }, pitch=${String.format("%.2f", player.rotationPitch)}/${String.format("%.2f", targetPitch)}"
            )
        }
    }

    /**
     * Normalize angle to -180 to 180 range
     */
    private fun normalizeAngle(angle: Float): Float {
        var normalized = angle % 360f
        if (normalized > 180f) normalized -= 360f
        if (normalized < -180f) normalized += 360f
        return normalized
    }

    /**
     * 檢查對手的 pitch 和 yaw 是否在指定角度範圍內，如果不是就 dodge
     * @param expectedYaw Expected opponent yaw angle
     * @param expectedPitch Expected opponent pitch angle
     * @param tolerance Angle tolerance for matching
     * @return true if dodged, false if opponent rotation matches
     */
    fun checkOpponentRotationAndDodge(
        expectedYaw: Float,
        expectedPitch: Float,
        tolerance: Float = 1.0f
    ): Boolean {
        val world = CatDueller.mc.theWorld ?: return false
        val player = CatDueller.mc.thePlayer ?: return false

        try {
            // Safely get player entities with multiple fallback methods
            val otherPlayers = try {
                // Method 1: Try to get a safe copy of the player entities
                synchronized(world.playerEntities) {
                    world.playerEntities.toList().filter {
                        it != player && it is net.minecraft.entity.player.EntityPlayer
                    }
                }
            } catch (e: ConcurrentModificationException) {
                try {
                    // Method 2: Fallback - try again with a different approach
                    world.loadedEntityList.filterIsInstance<net.minecraft.entity.player.EntityPlayer>().filter {
                        it != player
                    }
                } catch (e2: Exception) {
                    // Method 3: Final fallback - return false to avoid crash
                    println("Failed to safely access player entities: ${e2.message}")
                    return false
                }
            }

            // Check if there's exactly one other player (our opponent)
            if (otherPlayers.size == 1) {
                val opponent = otherPlayers[0] as net.minecraft.entity.player.EntityPlayer

                // Safely get opponent rotation values
                val opponentYaw = try {
                    opponent.rotationYaw
                } catch (e: Exception) {
                    println("Failed to get opponent yaw: ${e.message}")
                    return false
                }

                val opponentPitch = try {
                    opponent.rotationPitch
                } catch (e: Exception) {
                    println("Failed to get opponent pitch: ${e.message}")
                    return false
                }

                // Calculate angle differences with detailed debugging
                val rawYawDiff = opponentYaw - expectedYaw
                val normalizedYawDiff = normalizeAngle(rawYawDiff)
                val yawDiff = kotlin.math.abs(normalizedYawDiff)
                val pitchDiff = kotlin.math.abs(opponentPitch - expectedPitch)

                // Check if opponent's rotation matches expected values within tolerance
                val yawMatches = yawDiff <= tolerance
                val pitchMatches = pitchDiff <= tolerance

                // Always print detailed debug info
                println("=== Rotation Check Debug ===")
                println(
                    "Expected: yaw=${String.format("%.3f", expectedYaw)}, pitch=${
                        String.format(
                            "%.3f",
                            expectedPitch
                        )
                    }"
                )
                println(
                    "Actual: yaw=${String.format("%.3f", opponentYaw)}, pitch=${
                        String.format(
                            "%.3f",
                            opponentPitch
                        )
                    }"
                )
                println("Raw yaw diff: ${String.format("%.3f", rawYawDiff)}")
                println("Normalized yaw diff: ${String.format("%.3f", normalizedYawDiff)}")
                println(
                    "Absolute differences: yaw=${String.format("%.3f", yawDiff)}, pitch=${
                        String.format(
                            "%.3f",
                            pitchDiff
                        )
                    }"
                )
                println("Tolerance: ${String.format("%.3f", tolerance)}")
                if (tolerance < 0.1f) {
                    println("WARNING: Tolerance < 0.1 may not work reliably due to Minecraft network sync precision limits (~0.1 degrees)")
                }
                println("Matches: yaw=$yawMatches (${yawDiff} <= $tolerance), pitch=$pitchMatches (${pitchDiff} <= $tolerance)")

                if (!yawMatches || !pitchMatches) {
                    println("RESULT: Opponent rotation mismatch - dodging!")

                    // Send queue command to dodge (with safer execution)
                    try {
                        TimeUtils.setTimeout({
                            try {
                                val queueCommand =
                                    CatDueller.bot?.queueCommand
                                        ?: "/play duels_sumo_duel"
                                best.spaghetcodes.catdueller.utils.ChatUtils.sendAsPlayer(queueCommand)
                            } catch (e: Exception) {
                                println("Failed to send dodge command: ${e.message}")
                            }
                        }, RandomUtils.randomIntInRange(100, 300))
                    } catch (e: Exception) {
                        println("Failed to schedule dodge command: ${e.message}")
                    }

                    return true // Dodged
                } else {
                    println("RESULT: Opponent rotation matches expected values - no dodge needed")
                    return false // No dodge needed
                }
            } else {
                println("Found ${otherPlayers.size} other players, expected exactly 1 opponent")
            }
        } catch (e: ConcurrentModificationException) {
            println("ConcurrentModificationException in rotation check - retrying safely")
            // Don't crash, just return false and let the game continue
            return false
        } catch (e: Exception) {
            println("Unexpected error checking opponent rotation: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
        }

        return false // No opponent found or error occurred
    }

    /**
     * 檢查對手的 pitch 和 yaw 是否匹配指定角度，如果匹配就 dodge（與 checkOpponentRotationAndDodge 相反）
     * @param expectedYaw Expected opponent yaw angle
     * @param expectedPitch Expected opponent pitch angle
     * @param tolerance Angle tolerance for matching
     * @return true if dodged, false if opponent rotation doesn't match
     */
    fun checkOpponentRotationMatchAndDodge(
        expectedYaw: Float,
        expectedPitch: Float,
        tolerance: Float = 1.0f
    ): Boolean {
        val world = CatDueller.mc.theWorld ?: return false
        val player = CatDueller.mc.thePlayer ?: return false

        try {
            // Safely get player entities with multiple fallback methods
            val otherPlayers = try {
                // Method 1: Try to get a safe copy of the player entities
                synchronized(world.playerEntities) {
                    world.playerEntities.toList().filter {
                        it != player && it is net.minecraft.entity.player.EntityPlayer
                    }
                }
            } catch (e: ConcurrentModificationException) {
                try {
                    // Method 2: Fallback - try again with a different approach
                    world.loadedEntityList.filterIsInstance<net.minecraft.entity.player.EntityPlayer>().filter {
                        it != player
                    }
                } catch (e2: Exception) {
                    // Method 3: Final fallback - return false to avoid crash
                    println("Failed to safely access player entities: ${e2.message}")
                    return false
                }
            }

            // Check if there's exactly one other player (our opponent)
            if (otherPlayers.size == 1) {
                val opponent = otherPlayers[0] as net.minecraft.entity.player.EntityPlayer

                // Safely get opponent rotation values
                val opponentYaw = try {
                    opponent.rotationYaw
                } catch (e: Exception) {
                    println("Failed to get opponent yaw: ${e.message}")
                    return false
                }

                val opponentPitch = try {
                    opponent.rotationPitch
                } catch (e: Exception) {
                    println("Failed to get opponent pitch: ${e.message}")
                    return false
                }

                // Calculate angle differences with detailed debugging
                val rawYawDiff = opponentYaw - expectedYaw
                val normalizedYawDiff = normalizeAngle(rawYawDiff)
                val yawDiff = kotlin.math.abs(normalizedYawDiff)
                val pitchDiff = kotlin.math.abs(opponentPitch - expectedPitch)

                // Check if opponent's rotation matches expected values within tolerance
                val yawMatches = yawDiff <= tolerance
                val pitchMatches = pitchDiff <= tolerance

                // Always print detailed debug info
                println("=== Bot Detection Debug ===")
                println(
                    "Expected: yaw=${String.format("%.3f", expectedYaw)}, pitch=${
                        String.format(
                            "%.3f",
                            expectedPitch
                        )
                    }"
                )
                println(
                    "Actual: yaw=${String.format("%.3f", opponentYaw)}, pitch=${
                        String.format(
                            "%.3f",
                            opponentPitch
                        )
                    }"
                )
                println("Raw yaw diff: ${String.format("%.3f", rawYawDiff)}")
                println("Normalized yaw diff: ${String.format("%.3f", normalizedYawDiff)}")
                println(
                    "Absolute differences: yaw=${String.format("%.3f", yawDiff)}, pitch=${
                        String.format(
                            "%.3f",
                            pitchDiff
                        )
                    }"
                )
                println("Tolerance: ${String.format("%.3f", tolerance)}")
                if (tolerance < 0.1f) {
                    println("WARNING: Tolerance < 0.1 may not work reliably due to Minecraft network sync precision limits (~0.1 degrees)")
                }
                println("Matches: yaw=$yawMatches (${yawDiff} <= $tolerance), pitch=$pitchMatches (${pitchDiff} <= $tolerance)")

                if (yawMatches && pitchMatches) {
                    println("RESULT: Opponent rotation MATCHES expected values - dodging bot!")

                    // Send queue command to dodge (with safer execution)
                    try {
                        TimeUtils.setTimeout({
                            try {
                                val queueCommand =
                                    CatDueller.bot?.queueCommand
                                        ?: "/play duels_sumo_duel"
                                best.spaghetcodes.catdueller.utils.ChatUtils.sendAsPlayer(queueCommand)
                            } catch (e: Exception) {
                                println("Failed to send dodge command: ${e.message}")
                            }
                        }, RandomUtils.randomIntInRange(100, 300))
                    } catch (e: Exception) {
                        println("Failed to schedule dodge command: ${e.message}")
                    }

                    return true // Dodged
                } else {
                    println("RESULT: Opponent rotation doesn't match expected values - no dodge needed")
                    return false // No dodge needed
                }
            } else {
                println("Found ${otherPlayers.size} other players, expected exactly 1 opponent")
            }
        } catch (e: ConcurrentModificationException) {
            println("ConcurrentModificationException in bot detection - retrying safely")
            // Don't crash, just return false and let the game continue
            return false
        } catch (e: Exception) {
            println("Unexpected error checking opponent rotation for bot detection: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
        }

        return false // No opponent found or error occurred
    }

    private fun sumo1() {
        if (CatDueller.mc.thePlayer != null) {
            var left = RandomUtils.randomBool()

            val speed = RandomUtils.randomDoubleInRange(3.0, 9.0).toFloat()

            tickYawChange = if (left) -speed else speed
            TimeUtils.setTimeout(fun() {
                Movement.startForward()
                Movement.startSprinting()
                TimeUtils.setTimeout(fun() {
                    Movement.startJumping()
                }, RandomUtils.randomIntInRange(400, 800))
                intervals.add(TimeUtils.setInterval(fun() {
                    tickYawChange = if (WorldUtils.airInFront(CatDueller.mc.thePlayer, 7f)) {
                        if (WorldUtils.airInFront(CatDueller.mc.thePlayer, 3f)) {
                            RandomUtils.randomDoubleInRange(if (left) 9.5 else -9.5, if (left) 13.0 else -13.0)
                                .toFloat()
                        } else RandomUtils.randomDoubleInRange(if (left) 4.5 else -4.5, if (left) 7.0 else -7.0)
                            .toFloat()
                    } else {
                        0f
                    }
                }, 0, RandomUtils.randomIntInRange(50, 100)))
                intervals.add(TimeUtils.setTimeout(fun() {
                    intervals.add(TimeUtils.setInterval(fun() {
                        left = !left
                    }, 0, RandomUtils.randomIntInRange(5000, 10000)))
                }, RandomUtils.randomIntInRange(5000, 10000)))
            }, RandomUtils.randomIntInRange(100, 250))
        }
    }

    @SubscribeEvent
    fun onTick(ev: ClientTickEvent) {
        // Only run when bot is toggled on to prevent performance issues
        if (CatDueller.bot?.toggled() != true) return

        // Update MovementRecorder every tick
        MovementRecorder.onTick()

        // Update rotation adjustment if active
        updateRotationAdjustment()

        // Only apply yaw changes if not using recorded movement and not using rotation adjustment
        // When rotation adjustment is active, it has control over rotation
        if (tickYawChange != 0f && CatDueller.mc.thePlayer != null &&
            StateManager.state != StateManager.States.PLAYING && !MovementRecorder.isPlaying() && !rotationAdjustmentActive
        ) {
            CatDueller.mc.thePlayer.rotationYaw += tickYawChange
        }
    }
}