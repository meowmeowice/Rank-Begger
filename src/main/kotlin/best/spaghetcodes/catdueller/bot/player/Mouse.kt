package best.spaghetcodes.catdueller.bot.player

import best.spaghetcodes.catdueller.CatDueller
import best.spaghetcodes.catdueller.utils.ChatUtils
import best.spaghetcodes.catdueller.utils.EntityUtils
import best.spaghetcodes.catdueller.utils.RandomUtils
import best.spaghetcodes.catdueller.utils.TimeUtils
import net.minecraft.client.Minecraft
import net.minecraft.client.settings.KeyBinding
import net.minecraft.entity.Entity
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.relauncher.ReflectionHelper
import java.awt.Robot
import java.awt.event.InputEvent
import java.lang.reflect.Method
import kotlin.math.abs

object Mouse {

    private var leftAC = false
    var rClickDown = false

    private var tracking = false

    // Variables for Hold Left Click
    private val robot by lazy {
        try {
            Robot()
        } catch (e: Exception) {
            ChatUtils.info("Failed to initialize Robot in Mouse: ${e.message}")
            null
        }
    }
    private var isHoldingClick = false
    private var isHoldingRightClick = false

    private var _usingProjectile = false
    private var _usingPotion = false
    private var _runningAway = false
    private var _blockingArrow = false  // Prevent other actions during arrow blocking

    private var leftClickDur = 0

    private var lastLeftClick = 0L

    // Simple tick-based CPS variables
    private var tickCounter = 0

    private var runningRotations: FloatArray? = null

    private var splashAim = 0.0

    // Game end view rotation variables
    private var gameEndViewRotationActive = false
    private var gameEndTargetYaw = 0f
    private var gameEndTargetPitch = 0f


    // Reflection method for clickMouse
    private val clickMouseMethod: Method? by lazy {
        try {
            ReflectionHelper.findMethod(
                Minecraft::class.java,
                Minecraft.getMinecraft(),
                arrayOf("clickMouse", "func_147116_af")
            ).apply { isAccessible = true }
        } catch (e: Exception) {
            ChatUtils.error("Failed to find clickMouse method: ${e.message}")
            null
        }
    }

    /**
     * Invoke clickMouse using direct method calls
     */
    private fun invokeClickMouse() {
        try {
            clickMouseMethod?.invoke(Minecraft.getMinecraft())
        } catch (e: Exception) {
            ChatUtils.error("Failed to invoke clickMouse: ${e.message}")
            // Fallback to direct method calls
            CatDueller.mc.thePlayer.swingItem()
            KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindAttack.keyCode, true)
            if (CatDueller.mc.objectMouseOver != null && CatDueller.mc.objectMouseOver.entityHit != null) {
                CatDueller.mc.playerController.attackEntity(
                    CatDueller.mc.thePlayer,
                    CatDueller.mc.objectMouseOver.entityHit
                )
            }
        }
    }

    /**
     * Traditional left-click using KeyBinding (fallback method)
     */
    fun leftClickKeyBinding() {
        if (CatDueller.bot?.toggled() == true && CatDueller.mc.thePlayer != null && !CatDueller.mc.thePlayer.isUsingItem) {
            KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindAttack.keyCode, true)
            CatDueller.mc.thePlayer.swingItem()
            if (CatDueller.mc.objectMouseOver != null && CatDueller.mc.objectMouseOver.entityHit != null) {
                CatDueller.mc.playerController.attackEntity(
                    CatDueller.mc.thePlayer,
                    CatDueller.mc.objectMouseOver.entityHit
                )
            }
        }
    }

    fun leftClick() {
        val mc = CatDueller.mc
        if (CatDueller.bot?.toggled() == true && mc.thePlayer != null && !mc.thePlayer.isUsingItem) {
            // Only swing if canSwing allows it (BotBase handles all attack decision logic)
            if (CatDueller.bot?.canSwing() == true) {
                // Use invokeClickMouse which handles swing, keybind, and packet logic
                invokeClickMouse()
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtils.combatInfo("leftClick() executed - invokeClickMouse() called")
                }
            } else {
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtils.combatInfo("leftClick() blocked - canSwing() returned false")
                }
            }
        } else {
            if (CatDueller.config?.combatLogs == true) {
                val reason = when {
                    CatDueller.bot?.toggled() != true -> "bot not toggled"
                    mc.thePlayer == null -> "player is null"
                    mc.thePlayer.isUsingItem -> "player is using item"
                    else -> "unknown reason"
                }
                ChatUtils.combatInfo("leftClick() blocked - $reason")
            }
        }
    }


    /**
     * Left-click using Robot (safer than KeyBinding)
     */
    private fun leftClickRobot() {
        try {
            // Use Robot to perform hardware-level click
            robot?.let {
                it.mousePress(InputEvent.BUTTON1_DOWN_MASK)
                it.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
            }
            // Robot click triggers in-game attack logic automatically; no extra calls needed
        } catch (e: Exception) {
            ChatUtils.error("Robot left click failed, falling back to KeyBinding: ${e.message}")
            leftClickKeyBinding()
        }
    }

    fun rClick(duration: Int) {
        if (CatDueller.bot?.toggled() == true) {
            if (!rClickDown) {
                rClickDown()
                TimeUtils.setTimeout(this::rClickUp, duration)
            }
        }
    }

    fun startLeftAC() {
        if (CatDueller.bot?.toggled() == true) {
            leftAC = true
            // Reset tick counter
            tickCounter = 0
            ChatUtils.combatInfo("Started leftAC - simple tick-based CPS system initialized")
        }
    }

    /**
     * Force immediate left click, bypassing probability delay
     */
    fun forceImmediateLeftClick() {
        if (CatDueller.bot?.toggled() == true) {
            leftClick()
            lastLeftClick = System.currentTimeMillis()
            if (CatDueller.config?.combatLogs == true) {
                ChatUtils.combatInfo("Force immediate left click executed - bypassed probability delay")
            }
        }
    }

    fun stopLeftAC() {
        // no need to check for toggled state here
        leftAC = false
        // Reset tick counter
        tickCounter = 0
    }


    /**
     * Start holding left click (using Robot)
     */
    fun startHoldLeftClick() {
        if (CatDueller.bot?.toggled() == true && CatDueller.config?.holdLeftClick == true) {
            if (!isHoldingClick) {
                isHoldingClick = true
                robot?.mousePress(InputEvent.BUTTON1_DOWN_MASK)
                ChatUtils.combatInfo("Started holding left click")
            }
        }
    }

    /**
     * Stop holding left click (using Robot)
     */
    fun stopHoldLeftClick() {
        if (isHoldingClick) {
            isHoldingClick = false
            robot?.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
            ChatUtils.combatInfo("Stopped holding left click")
        }
    }

    /**
     * Start holding right click (using Robot)
     */
    fun startHoldRightClick() {
        if (CatDueller.bot?.toggled() == true && CatDueller.config?.holdLeftClick == true) {
            if (!isHoldingRightClick) {
                isHoldingRightClick = true
                robot?.mousePress(InputEvent.BUTTON3_DOWN_MASK)
                ChatUtils.combatInfo("Started holding right click")
            }
        }
    }

    /**
     * Stop holding right click (using Robot)
     */
    fun stopHoldRightClick() {
        if (isHoldingRightClick) {
            isHoldingRightClick = false
            robot?.mouseRelease(InputEvent.BUTTON3_DOWN_MASK)
            ChatUtils.combatInfo("Stopped holding right click")
        }
    }

    /**
     * Reset all mouse states (useful when bot is toggled off/on)
     */
    fun resetAllStates() {
        stopLeftAC()
        stopHoldLeftClick()
        stopHoldRightClick()
        stopTracking()
        setUsingProjectile(false)
        setUsingPotion(false)
        setRunningAway(false)
        setBlockingArrow(false)
        rClickDown = false

        // Reset game end view rotation variables
        gameEndViewRotationActive = false
        gameEndTargetYaw = 0f
        gameEndTargetPitch = 0f
    }


    fun startTracking() {
        tracking = true
    }

    fun stopTracking() {
        tracking = false
    }

    fun isTracking(): Boolean {
        return tracking
    }

    fun setBlockingArrow(blocking: Boolean) {
        _blockingArrow = blocking
    }

    fun isBlockingArrow(): Boolean {
        return _blockingArrow
    }

    fun setUsingProjectile(proj: Boolean) {
        _usingProjectile = proj
    }

    fun isUsingProjectile(): Boolean {
        return _usingProjectile
    }

    fun setUsingPotion(potion: Boolean) {
        _usingPotion = potion
        if (!_usingPotion) {
            splashAim = 0.0
        }
    }

    fun isUsingPotion(): Boolean {
        return _usingPotion
    }

    fun setRunningAway(runningAway: Boolean) {
        _runningAway = runningAway
        runningRotations = null // make sure to clear this, otherwise running away gets buggy asf
    }

    fun isRunningAway(): Boolean {
        return _runningAway
    }

    /**
     * Start game end view rotation: pitch to 0 (level), yaw random ±45 degrees
     */
    fun startGameEndViewRotation() {
        if (CatDueller.mc.thePlayer == null) return

        gameEndViewRotationActive = true
        gameEndTargetPitch = 0f  // Level view

        // Random yaw rotation: current yaw ± 45 degrees
        val currentYaw = CatDueller.mc.thePlayer.rotationYaw
        val yawOffset = if (RandomUtils.randomBool()) 45f else -45f
        gameEndTargetYaw = currentYaw + yawOffset
    }

    /**
     * Stop game end view rotation
     */
    fun stopGameEndViewRotation() {
        gameEndViewRotationActive = false
    }

    /**
     * Handle game end view rotation during tick
     */
    private fun handleGameEndViewRotation() {
        val player = CatDueller.mc.thePlayer ?: return

        val currentYaw = player.rotationYaw
        val currentPitch = player.rotationPitch

        // Calculate yaw difference (handle wrapping around 360 degrees)
        var yawDiff = gameEndTargetYaw - currentYaw
        while (yawDiff > 180) yawDiff -= 360
        while (yawDiff < -180) yawDiff += 360

        // Calculate pitch difference
        val pitchDiff = gameEndTargetPitch - currentPitch

        // Use fixed speed of 10 for game end view rotation
        val fixedSpeed = 10f

        // Apply rotation limits
        val dyaw = if (abs(yawDiff) > fixedSpeed) {
            if (yawDiff > 0) fixedSpeed else -fixedSpeed
        } else {
            yawDiff
        }

        val dpitch = if (abs(pitchDiff) > fixedSpeed) {
            if (pitchDiff > 0) fixedSpeed else -fixedSpeed
        } else {
            pitchDiff
        }

        // Apply rotation
        player.rotationYaw += dyaw
        player.rotationPitch += dpitch

        // Stop rotation when close enough to target
        if (abs(yawDiff) < 1f && abs(pitchDiff) < 1f) {
            gameEndViewRotationActive = false
        }
    }

    /**
     * 檢查?�否??GUI ?��?（�?天室?�ESC ?�單等�?
     */
    private fun isGuiOpen(): Boolean {
        return CatDueller.mc.currentScreen != null
    }

    private fun rClickDown() {
        if (CatDueller.bot?.toggled() == true) {
            rClickDown = true
            KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindUseItem.keyCode, true)
        }
    }

    fun startRightClick() {
        rClickDown()
    }

    fun rClickUp() {
        if (CatDueller.bot?.toggled() == true) {
            rClickDown = false
            KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindUseItem.keyCode, false)
        }
    }

    @SubscribeEvent
    fun onTick(ev: TickEvent.ClientTickEvent) {
        if (CatDueller.mc.thePlayer == null) return

        // Execute clicks at tick START to align with vanilla reduce/motion
        if (ev.phase == TickEvent.Phase.START && CatDueller.bot?.toggled() == true) {
            if (leftAC) {
                // Update tick counter
                tickCounter++

                // Simple tick-based left auto-click logic
                if (!CatDueller.mc.thePlayer.isUsingItem) {
                    val targetCPS = CatDueller.config?.cps?.toDouble() ?: 12.0

                    // Calculate base probability of clicking this tick
                    // 20 TPS means each tick has targetCPS/20 chance of clicking
                    val baseProbability = targetCPS / 20.0

                    // Add some randomness: ±25% variance
                    val variance = 0.10
                    val randomFactor = 1.0 + RandomUtils.randomDoubleInRange(-variance, variance)
                    val finalProbability = (baseProbability * randomFactor).coerceIn(0.0, 1.0)

                    // Decide whether to click this tick
                    if (Math.random() < finalProbability) {
                        leftClick()
                        lastLeftClick = System.currentTimeMillis()

                        if (CatDueller.config?.combatLogs == true) {
                            ChatUtils.combatInfo(
                                "Tick START click: probability=${
                                    String.format(
                                        "%.3f",
                                        finalProbability
                                    )
                                }, targetCPS=${targetCPS}"
                            )
                        }
                    }
                }
            }
        }

        if (CatDueller.bot?.toggled() == true) {

            if (leftClickDur > 0) {
                leftClickDur--
            } else {
                // Only clear left click if bot is actively controlling it (leftAC is active)
                if (leftAC && CatDueller.mc.gameSettings.keyBindAttack.isKeyDown) {
                    KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindAttack.keyCode, false)
                }
            }
        } else {
            // When bot is disabled, don't interfere with normal left click functionality
            // Only clear if we were previously controlling the key state
            if (leftAC) {
                leftAC = false
                KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindAttack.keyCode, false)
            }
        }

        if (CatDueller.mc.thePlayer != null && CatDueller.bot?.toggled() == true) {
            // Handle game end view rotation (highest priority)
            if (gameEndViewRotationActive) {
                handleGameEndViewRotation()
            } else if (tracking && CatDueller.bot?.opponent() != null) {
                // Normal tracking logic (original behavior)
                applyRotationsImmediate()
            }
        }
    }

    /**
     * Calculate the angle difference between current aim and target
     * Returns the absolute angle difference in degrees
     */
    private fun getAngleDifference(targetEntity: Entity?): Double {
        if (targetEntity == null) return 0.0

        val player = CatDueller.mc.thePlayer ?: return 0.0
        val rotations = EntityUtils.getRotations(player, targetEntity, false) ?: return 0.0

        val currentYaw = player.rotationYaw
        val currentPitch = player.rotationPitch
        val targetYaw = rotations[0]
        val targetPitch = rotations[1]

        // Calculate yaw difference (handle wrapping around 360 degrees)
        var yawDiff = targetYaw - currentYaw
        while (yawDiff > 180) yawDiff -= 360
        while (yawDiff < -180) yawDiff += 360

        // Calculate pitch difference
        val pitchDiff = targetPitch - currentPitch

        // Return the combined angle difference (Euclidean distance in angle space)
        return kotlin.math.sqrt((yawDiff * yawDiff + pitchDiff * pitchDiff).toDouble())
    }

    /**
     * Apply rotations immediately (original behavior for non-interpolation mode)
     */
    private fun applyRotationsImmediate() {
        if (_runningAway) {
            _usingProjectile = false
        }

        var rotations = EntityUtils.getRotations(CatDueller.mc.thePlayer, CatDueller.bot?.opponent(), false)

        if (rotations != null) {
            if (_runningAway) {
                if (runningRotations == null) {
                    runningRotations = rotations
                    runningRotations!![0] += 180 + RandomUtils.randomDoubleInRange(-5.0, 5.0).toFloat()
                }
                rotations = runningRotations!!
            }

            if (_usingPotion) {
                if (splashAim == 0.0) {
                    splashAim = RandomUtils.randomDoubleInRange(80.0, 90.0)
                }
                rotations[1] = splashAim.toFloat()
            } else if (!_usingProjectile && CatDueller.config?.verticalMultipoint == true) {
                // --- vertical multipoint logic (50 points) - only when enabled and NOT using projectiles ---
                val player = CatDueller.mc.thePlayer
                val opponent = CatDueller.bot?.opponent()
                if (player != null && opponent != null) {
                    val playerEyeY = player.posY + player.eyeHeight
                    val opponentMinY = opponent.entityBoundingBox.minY
                    val opponentMaxY = opponent.entityBoundingBox.maxY
                    val candidateHeights = List(50) { i ->
                        opponentMinY + i * (opponentMaxY - opponentMinY) / 49.0
                    }
                    val targetY = candidateHeights.minByOrNull { h -> abs(playerEyeY - h) }
                        ?: (opponentMinY + opponentMaxY) / 2.0

                    // Compute target pitch
                    val deltaX = opponent.posX - player.posX
                    val deltaY = targetY - playerEyeY
                    val deltaZ = opponent.posZ - player.posZ
                    val distanceXZ = kotlin.math.sqrt(deltaX * deltaX + deltaZ * deltaZ)
                    val targetPitch = (-Math.toDegrees(kotlin.math.atan2(deltaY, distanceXZ))).toFloat()
                    rotations[1] = targetPitch // pitch
                }
            }
            // When vertical multipoint is disabled or using projectiles, keep the pitch from EntityUtils.getRotations() (original behavior)
            // When using projectiles, keep the pitch calculated by EntityUtils.getRotations() for proper trajectory compensation

            // --- horizontal + vertical smoothing with distance-based slowdown ---
            val lookRand = (CatDueller.config?.lookRand ?: 0).toDouble()
            var dyaw = ((rotations[0] - CatDueller.mc.thePlayer.rotationYaw) + RandomUtils.randomDoubleInRange(
                -lookRand,
                lookRand
            )).toFloat()
            var dpitch = ((rotations[1] - CatDueller.mc.thePlayer.rotationPitch) + RandomUtils.randomDoubleInRange(
                -lookRand,
                lookRand
            )).toFloat()

            // Distance-based slowing factor (skip for runningAway and splashAim)
            val opponent = CatDueller.bot?.opponent()
            var distanceFactor = if (_runningAway || _usingPotion || opponent == null) {
                1.0f  // No distance penalty for runningAway, splashAim, or null opponent
            } else {
                when (EntityUtils.getDistanceNoY(CatDueller.mc.thePlayer, opponent)) {
                    in 0f..10f -> 1.0f
                    in 10f..20f -> 0.6f
                    in 20f..30f -> 0.4f
                    else -> 0.2f
                }
            }

            // Angle-based speed factor (closer to target = slower movement)
            val angleDifference = getAngleDifference(CatDueller.bot?.opponent())
            val angleFactor = when {
                angleDifference <= 1.0 -> 0.3f      // Very close to target: 30% speed
                angleDifference <= 5.0 -> 0.5f      // Close to target: 50% speed
                angleDifference <= 15.0 -> 0.8f     // Medium distance: 80% speed
                angleDifference <= 30.0 -> 1.0f     // Far from target: 100% speed
                else -> 1.2f                        // Very far from target: 120% speed
            }

            // On-target slowdown factor (always enabled)
            val onTarget = CatDueller.mc.objectMouseOver != null &&
                    CatDueller.mc.objectMouseOver.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.ENTITY &&
                    CatDueller.mc.objectMouseOver.entityHit == CatDueller.bot?.opponent()

            val onTargetFactor = if (onTarget) 0.85f else 1.0f

            // Combine all factors: distance * angle * onTarget
            val combinedFactor = distanceFactor * angleFactor * onTargetFactor

            // Use fixed speed for running away, otherwise use config speed
            val maxRotH = if (_runningAway) {
                30.0f  // Fixed horizontal speed for running away
            } else {
                (CatDueller.config?.lookSpeedHorizontal ?: 10).toFloat() * combinedFactor
            }

            val maxRotV = if (_runningAway) {
                30.0f  // Fixed vertical speed for running away
            } else {
                (CatDueller.config?.lookSpeedVertical ?: 5).toFloat() * combinedFactor
            }

            if (abs(dyaw) > maxRotH) {
                dyaw = if (dyaw > 0) {
                    maxRotH
                } else {
                    -maxRotH
                }
            }

            if (abs(dpitch) > maxRotV) {
                dpitch = if (dpitch > 0) {
                    maxRotV
                } else {
                    -maxRotV
                }
            }

            // Debug: Log rotation values to check if they're too high
            if (CatDueller.config?.combatLogs == true && (abs(dyaw) > 0.1f || abs(dpitch) > 0.1f)) {
                ChatUtils.combatInfo(
                    "Immediate Mode - dyaw: ${
                        String.format(
                            "%.2f",
                            dyaw
                        )
                    }, dpitch: ${String.format("%.2f", dpitch)}, maxRotH: ${
                        String.format(
                            "%.2f",
                            maxRotH
                        )
                    }, maxRotV: ${String.format("%.2f", maxRotV)}, combinedFactor: ${
                        String.format(
                            "%.2f",
                            combinedFactor
                        )
                    }"
                )
            }

            CatDueller.mc.thePlayer.rotationYaw += dyaw
            CatDueller.mc.thePlayer.rotationPitch += dpitch
        }
    }

}
