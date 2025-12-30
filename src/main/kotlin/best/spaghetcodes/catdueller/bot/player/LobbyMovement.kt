package best.spaghetcodes.catdueller.bot.player

import best.spaghetcodes.catdueller.CatDueller
import best.spaghetcodes.catdueller.bot.MovementRecorder
import best.spaghetcodes.catdueller.bot.StateManager
import best.spaghetcodes.catdueller.utils.RandomUtils
import best.spaghetcodes.catdueller.utils.TimeUtils
import best.spaghetcodes.catdueller.utils.WorldUtils
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent
import java.util.Timer
import kotlin.math.pow

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
     * 只調整 pitch
     * @param pitch Target pitch angle
     * @param angleTolerance Angle tolerance (default 0.01)
     * @param onComplete Callback when pitch is reached
     */
    fun adjustPitch(
        pitch: Float,
        angleTolerance: Float = 0.01f,
        onComplete: (() -> Unit)? = null
    ) {
        val player = CatDueller.mc.thePlayer ?: return
        adjustRotation(player.rotationYaw, pitch, angleTolerance, onComplete)
    }
    
    /**
     * 只調整 yaw
     * @param yaw Target yaw angle
     * @param angleTolerance Angle tolerance (default 0.01)
     * @param onComplete Callback when yaw is reached
     */
    fun adjustYaw(
        yaw: Float,
        angleTolerance: Float = 0.01f,
        onComplete: (() -> Unit)? = null
    ) {
        val player = CatDueller.mc.thePlayer ?: return
        adjustRotation(yaw, player.rotationPitch, angleTolerance, onComplete)
    }
    
    /**
     * Check if rotation adjustment is currently active
     */
    fun isRotationAdjustmentActive(): Boolean {
        return rotationAdjustmentActive
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
            kotlin.math.abs(pitchDiff) <= angleTolerance) {
            
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
            println("Rotation adjustment: yaw=${String.format("%.2f", player.rotationYaw)}/${String.format("%.2f", targetYaw)}, pitch=${String.format("%.2f", player.rotationPitch)}/${String.format("%.2f", targetPitch)}")
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
                println("Expected: yaw=${String.format("%.3f", expectedYaw)}, pitch=${String.format("%.3f", expectedPitch)}")
                println("Actual: yaw=${String.format("%.3f", opponentYaw)}, pitch=${String.format("%.3f", opponentPitch)}")
                println("Raw yaw diff: ${String.format("%.3f", rawYawDiff)}")
                println("Normalized yaw diff: ${String.format("%.3f", normalizedYawDiff)}")
                println("Absolute differences: yaw=${String.format("%.3f", yawDiff)}, pitch=${String.format("%.3f", pitchDiff)}")
                println("Tolerance: ${String.format("%.3f", tolerance)}")
                if (tolerance < 0.1f) {
                    println("WARNING: Tolerance < 0.1 may not work reliably due to Minecraft network sync precision limits (~0.1 degrees)")
                }
                println("Matches: yaw=$yawMatches (${yawDiff} <= $tolerance), pitch=$pitchMatches (${pitchDiff} <= $tolerance)")
                
                if (!yawMatches || !pitchMatches) {
                    println("RESULT: Opponent rotation mismatch - dodging!")
                    
                    // Send queue command to dodge (with safer execution)
                    try {
                        best.spaghetcodes.catdueller.utils.TimeUtils.setTimeout({
                            try {
                                val queueCommand = (best.spaghetcodes.catdueller.CatDueller.bot as? best.spaghetcodes.catdueller.bot.BotBase)?.queueCommand ?: "/play duels_sumo_duel"
                                best.spaghetcodes.catdueller.utils.ChatUtils.sendAsPlayer(queueCommand)
                            } catch (e: Exception) {
                                println("Failed to send dodge command: ${e.message}")
                            }
                        }, best.spaghetcodes.catdueller.utils.RandomUtils.randomIntInRange(100, 300))
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
                println("Expected: yaw=${String.format("%.3f", expectedYaw)}, pitch=${String.format("%.3f", expectedPitch)}")
                println("Actual: yaw=${String.format("%.3f", opponentYaw)}, pitch=${String.format("%.3f", opponentPitch)}")
                println("Raw yaw diff: ${String.format("%.3f", rawYawDiff)}")
                println("Normalized yaw diff: ${String.format("%.3f", normalizedYawDiff)}")
                println("Absolute differences: yaw=${String.format("%.3f", yawDiff)}, pitch=${String.format("%.3f", pitchDiff)}")
                println("Tolerance: ${String.format("%.3f", tolerance)}")
                if (tolerance < 0.1f) {
                    println("WARNING: Tolerance < 0.1 may not work reliably due to Minecraft network sync precision limits (~0.1 degrees)")
                }
                println("Matches: yaw=$yawMatches (${yawDiff} <= $tolerance), pitch=$pitchMatches (${pitchDiff} <= $tolerance)")
                
                if (yawMatches && pitchMatches) {
                    println("RESULT: Opponent rotation MATCHES expected values - dodging bot!")
                    
                    // Send queue command to dodge (with safer execution)
                    try {
                        best.spaghetcodes.catdueller.utils.TimeUtils.setTimeout({
                            try {
                                val queueCommand = (best.spaghetcodes.catdueller.CatDueller.bot as? best.spaghetcodes.catdueller.bot.BotBase)?.queueCommand ?: "/play duels_sumo_duel"
                                best.spaghetcodes.catdueller.utils.ChatUtils.sendAsPlayer(queueCommand)
                            } catch (e: Exception) {
                                println("Failed to send dodge command: ${e.message}")
                            }
                        }, best.spaghetcodes.catdueller.utils.RandomUtils.randomIntInRange(100, 300))
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
    

    
    /**
     * Quick method to set rotation
     */
    fun setRotation(yaw: Float, pitch: Float, onComplete: (() -> Unit)? = null) {
        adjustRotation(yaw, pitch, onComplete = onComplete)
    }
    
    /**
     * Quick method to set pitch only
     */
    fun setPitch(pitch: Float, onComplete: (() -> Unit)? = null) {
        adjustPitch(pitch, onComplete = onComplete)
    }
    
    /**
     * Quick method to set yaw only
     */
    fun setYaw(yaw: Float, onComplete: (() -> Unit)? = null) {
        adjustYaw(yaw, onComplete = onComplete)
    }
    
    /**
     * Get current player position and rotation for debugging
     */
    fun getCurrentPositionInfo(): String {
        val player = CatDueller.mc.thePlayer ?: return "Player is null"
        return "Position: (${player.posX}, ${player.posZ}), Rotation: yaw=${player.rotationYaw}, pitch=${player.rotationPitch}"
    }
    

    
    /**
     * Data class to hold player position and rotation information
     */
    data class PlayerInfo(
        val name: String,
        val displayName: String,
        val x: Double,
        val z: Double,
        val y: Double,
        val yaw: Float,
        val pitch: Float,
        val isOnGround: Boolean,
        val health: Float,
        val isSneaking: Boolean,
        val isSprinting: Boolean
    )
    
    /**
     * Get information about all other players in the world (excluding self)
     * @return List of PlayerInfo for all other players
     */
    fun getOtherPlayersInfo(): List<PlayerInfo> {
        val world = CatDueller.mc.theWorld ?: return emptyList()
        val selfPlayer = CatDueller.mc.thePlayer ?: return emptyList()
        
        return try {
            // Safely get player entities to avoid ConcurrentModificationException
            val playerEntities = try {
                synchronized(world.playerEntities) {
                    world.playerEntities.toList()
                }
            } catch (e: ConcurrentModificationException) {
                // Fallback method
                world.loadedEntityList.filterIsInstance<net.minecraft.entity.player.EntityPlayer>()
            }
            
            playerEntities
                .filter { it != selfPlayer && it is net.minecraft.entity.player.EntityPlayer }
                .map { player ->
                    try {
                        PlayerInfo(
                            name = player.name,
                            displayName = player.displayNameString,
                            x = player.posX,
                            z = player.posZ,
                            y = player.posY,
                            yaw = player.rotationYaw,
                            pitch = player.rotationPitch,
                            isOnGround = player.onGround,
                            health = player.health,
                            isSneaking = player.isSneaking,
                            isSprinting = player.isSprinting
                        )
                    } catch (e: Exception) {
                        // If we can't get info for this player, skip it
                        null
                    }
                }.filterNotNull()
        } catch (e: Exception) {
            println("Error getting other players info: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Get information about the first other player found (usually the opponent in duels)
     * @return PlayerInfo of the first other player, or null if none found
     */
    fun getOpponentInfo(): PlayerInfo? {
        return getOtherPlayersInfo().firstOrNull()
    }
    
    /**
     * Get formatted string with all other players' information
     * @return Formatted string with player details
     */
    fun getOtherPlayersInfoString(): String {
        val players = getOtherPlayersInfo()
        if (players.isEmpty()) {
            return "No other players found in world"
        }
        
        return players.joinToString("\n") { player ->
            "Player: ${player.displayName}\n" +
            "  Position: (${String.format("%.3f", player.x)}, ${String.format("%.3f", player.z)})\n" +
            "  Y: ${String.format("%.3f", player.y)}\n" +
            "  Rotation: yaw=${String.format("%.1f", player.yaw)}, pitch=${String.format("%.1f", player.pitch)}\n" +
            "  Status: ${if (player.isOnGround) "OnGround" else "InAir"}, " +
            "${if (player.isSneaking) "Sneaking" else "Standing"}, " +
            "${if (player.isSprinting) "Sprinting" else "Walking"}\n" +
            "  Health: ${String.format("%.1f", player.health)}"
        }
    }
    
    /**
     * Get opponent's exact position and rotation for copying
     * @return Formatted string ready for config or null if no opponent
     */
    fun getOpponentPositionForConfig(): String? {
        val opponent = getOpponentInfo() ?: return null
        
        return "Opponent Position Data:\n" +
               "X: ${String.format("%.3f", opponent.x)}\n" +
               "Z: ${String.format("%.3f", opponent.z)}\n" +
               "Yaw: ${String.format("%.1f", opponent.yaw)}\n" +
               "Pitch: ${String.format("%.1f", opponent.pitch)}\n" +
               "\nConfig Values:\n" +
               "targetXPosition = ${String.format("%.3f", opponent.x)}f\n" +
               "targetZPosition = ${String.format("%.3f", opponent.z)}f\n" +
               "targetYaw = ${String.format("%.1f", opponent.yaw)}f\n" +
               "targetPitch = ${String.format("%.1f", opponent.pitch)}f"
    }
    
    /**
     * Copy opponent's exact position and rotation to current config
     * @return true if successful, false if no opponent found
     */
    fun copyOpponentPositionToConfig(): Boolean {
        val opponent = getOpponentInfo() ?: return false
        val config = CatDueller.config ?: return false
        
        // Note: This would require reflection or config modification methods
        // For now, just print the values that should be set
        println("To copy opponent position, set these config values:")
        println("Target X Position: ${String.format("%.3f", opponent.x)}")
        println("Target Z Position: ${String.format("%.3f", opponent.z)}")
        println("Target Yaw: ${String.format("%.1f", opponent.yaw)}")
        println("Target Pitch: ${String.format("%.1f", opponent.pitch)}")
        
        return true
    }

    private fun sumo1() {
        if (CatDueller.mc.thePlayer != null) {
            var left = RandomUtils.randomBool()

            val speed = RandomUtils.randomDoubleInRange(3.0, 9.0).toFloat()

            tickYawChange = if (left) -speed else speed
            TimeUtils.setTimeout(fun () {
                Movement.startForward()
                Movement.startSprinting()
                TimeUtils.setTimeout(fun () {
                    Movement.startJumping()
                }, RandomUtils.randomIntInRange(400, 800))
                intervals.add(TimeUtils.setInterval(fun () {
                    tickYawChange = if (WorldUtils.airInFront(CatDueller.mc.thePlayer, 7f)) {
                        if (WorldUtils.airInFront(CatDueller.mc.thePlayer, 3f)) {
                            RandomUtils.randomDoubleInRange(if (left) 9.5 else -9.5, if (left) 13.0 else -13.0).toFloat()
                        } else RandomUtils.randomDoubleInRange(if (left) 4.5 else -4.5, if (left) 7.0 else -7.0).toFloat()
                    } else {
                        0f
                    }
                }, 0, RandomUtils.randomIntInRange(50, 100)))
                intervals.add(TimeUtils.setTimeout(fun () {
                    intervals.add(TimeUtils.setInterval(fun () {
                        left = !left
                    }, 0, RandomUtils.randomIntInRange(5000, 10000)))
                }, RandomUtils.randomIntInRange(5000, 10000)))
            }, RandomUtils.randomIntInRange(100, 250))
        }
    }

    private fun twerk() {
        intervals.add(TimeUtils.setInterval(
            fun () {
                if (Movement.sneaking()) {
                    Movement.stopSneaking()
                } else {
                    Movement.startSneaking()
                }
        }, RandomUtils.randomIntInRange(500, 900), RandomUtils.randomIntInRange(200, 500)))
    }

    @SubscribeEvent
    fun onTick(ev: ClientTickEvent) {
        // Only run when bot is toggled on to prevent performance issues
        if (CatDueller.bot?.toggled() != true) return
        
        // Update MovementRecorder every tick
        MovementRecorder.onTick()
        
        // Also update simple recorder for testing
        best.spaghetcodes.catdueller.bot.MovementRecorderSimple.onTick()
        
        // Update rotation adjustment if active
        updateRotationAdjustment()
        
        // Only apply yaw changes if not using recorded movement and not using rotation adjustment
        // When rotation adjustment is active, it has control over rotation
        if (tickYawChange != 0f && CatDueller.mc.thePlayer != null && 
            StateManager.state != StateManager.States.PLAYING && !MovementRecorder.isPlaying() && !rotationAdjustmentActive) {
            CatDueller.mc.thePlayer.rotationYaw += tickYawChange
        }
    }

}
    /**
     * Utility object for quick access to player information and positioning
     */
    object PlayerUtils {
        
        /**
         * Print all players' information to console
         */
        fun printAllPlayersInfo() {
            println("=== All Players Information ===")
            println(LobbyMovement.getCurrentPositionInfo())
            println("\n=== Other Players ===")
            val otherInfo = LobbyMovement.getOtherPlayersInfoString()
            println(if (otherInfo.isNotEmpty()) otherInfo else "No other players found")
        }
        
        /**
         * Print opponent's detailed information
         */
        fun printOpponentInfo() {
            val opponentInfo = LobbyMovement.getOpponentInfo()
            if (opponentInfo != null) {
                println("=== Opponent Information ===")
                println("Name: ${opponentInfo.displayName}")
                println("Position: (${String.format("%.3f", opponentInfo.x)}, ${String.format("%.3f", opponentInfo.z)})")
                println("Y: ${String.format("%.3f", opponentInfo.y)}")
                println("Rotation: Yaw=${String.format("%.1f", opponentInfo.yaw)}, Pitch=${String.format("%.1f", opponentInfo.pitch)}")
                println("Status: ${if (opponentInfo.isOnGround) "OnGround" else "InAir"}")
                println("Health: ${String.format("%.1f", opponentInfo.health)}")
                println("Movement: ${if (opponentInfo.isSneaking) "Sneaking" else "Standing"}, ${if (opponentInfo.isSprinting) "Sprinting" else "Walking"}")
            } else {
                println("No opponent found")
            }
        }
        
        /**
         * Check opponent's rotation and dodge if it doesn't match expected values
         */
        fun checkAndDodgeOpponentRotation(expectedYaw: Float, expectedPitch: Float, tolerance: Float = 1.0f) {
            val dodged = LobbyMovement.checkOpponentRotationAndDodge(expectedYaw, expectedPitch, tolerance)
            if (dodged) {
                println("Dodged due to opponent rotation mismatch")
            } else {
                println("Opponent rotation check passed")
            }
        }
        
        /**
         * Check opponent's rotation for bot detection and dodge if it matches
         */
        fun checkAndDodgeBot(expectedYaw: Float, expectedPitch: Float, tolerance: Float = 1.0f) {
            val dodged = LobbyMovement.checkOpponentRotationMatchAndDodge(expectedYaw, expectedPitch, tolerance)
            if (dodged) {
                println("Dodged due to bot detection - opponent rotation matched expected values")
            } else {
                println("Bot detection check passed - opponent rotation doesn't match")
            }
        }
        
        /**
         * Set our rotation to specific values
         */
        fun setOurRotation(yaw: Float, pitch: Float) {
            println("Setting our rotation to yaw=$yaw, pitch=$pitch")
            LobbyMovement.adjustRotation(yaw, pitch) {
                println("Successfully set rotation!")
            }
        }
        
        /**
         * Get distance to opponent
         */
        fun getDistanceToOpponent(): Double? {
            val opponent = LobbyMovement.getOpponentInfo() ?: return null
            val player = CatDueller.mc.thePlayer ?: return null
            
            return kotlin.math.sqrt(
                (opponent.x - player.posX).pow(2.0) + 
                (opponent.z - player.posZ).pow(2.0)
            )
        }
        
        /**
         * Monitor opponent's position changes (call this repeatedly)
         */
        fun monitorOpponentPosition() {
            val opponent = LobbyMovement.getOpponentInfo()
            if (opponent != null) {
                val distance = getDistanceToOpponent() ?: 0.0
                println("Opponent ${opponent.displayName}: (${String.format("%.3f", opponent.x)}, ${String.format("%.3f", opponent.z)}) " +
                       "Yaw=${String.format("%.1f", opponent.yaw)} Distance=${String.format("%.2f", distance)}")
            }
        }
    }