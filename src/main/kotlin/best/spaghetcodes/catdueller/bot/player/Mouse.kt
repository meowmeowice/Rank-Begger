package best.spaghetcodes.catdueller.bot.player

import best.spaghetcodes.catdueller.CatDueller
import best.spaghetcodes.catdueller.utils.client.ChatUtil
import best.spaghetcodes.catdueller.utils.client.TimerUtil
import best.spaghetcodes.catdueller.utils.game.EntityUtil
import best.spaghetcodes.catdueller.utils.system.RandomUtil
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

/**
 * Handles mouse input simulation including left/right clicks, auto-clicking,
 * target tracking, and aim rotation calculations for combat.
 */
object Mouse {

    /** Whether left auto-click is currently active. */
    private var leftAC = false

    /** Whether the right mouse button is currently held down. */
    var rClickDown = false

    /** Whether target tracking is currently active. */
    private var tracking = false

    /** AWT Robot instance for hardware-level mouse simulation. */
    private val robot by lazy {
        try {
            Robot()
        } catch (e: Exception) {
            ChatUtil.info("Failed to initialize Robot in Mouse: ${e.message}")
            null
        }
    }

    /** Whether a left click is being held via Robot. */
    private var isHoldingClick = false

    /** Whether a right click is being held via Robot. */
    private var isHoldingRightClick = false

    /** Whether the player is currently using a projectile (bow, fishing rod, etc.) */
    private var _usingProjectile = false

    /** Whether the player is currently using a splash potion. */
    private var _usingPotion = false

    /** Whether the player is currently using a golden apple (gap). */
    private var _usingGap = false

    /** Whether the player is currently running away from the opponent. */
    private var _runningAway = false

    /** Whether the player is currently blocking an incoming arrow. */
    private var _blockingArrow = false

    /** Whether the player is currently dodging arrows. */
    private var _dodgingArrow = false

    /** Target yaw for arrow dodging. */
    private var dodgeArrowTargetYaw = 0f

    /** Remaining ticks to hold the left click. */
    private var leftClickDur = 0

    /** Timestamp of the last left click for CPS calculation. */
    private var lastLeftClick = 0L

    /** Tick counter for CPS timing. */
    private var tickCounter = 0

    /** Cached rotation values when running away. */
    private var runningRotations: FloatArray? = null

    /** Target pitch for splash potion aiming. */
    private var splashAim = 0.0

    /** Whether game end view rotation is active. */
    private var gameEndViewRotationActive = false

    /** Target yaw for game end view rotation. */
    private var gameEndTargetYaw = 0f

    /** Target pitch for game end view rotation. */
    private var gameEndTargetPitch = 0f

    /** Reflection reference to Minecraft's clickMouse method. */
    private val clickMouseMethod: Method? by lazy {
        try {
            ReflectionHelper.findMethod(
                Minecraft::class.java,
                Minecraft.getMinecraft(),
                arrayOf("clickMouse", "func_147116_af")
            ).apply { isAccessible = true }
        } catch (e: Exception) {
            ChatUtil.error("Failed to find clickMouse method: ${e.message}")
            null
        }
    }

    /**
     * Invokes Minecraft's clickMouse method via reflection.
     * Falls back to manual attack simulation if reflection fails.
     */
    private fun invokeClickMouse() {
        try {
            clickMouseMethod?.invoke(Minecraft.getMinecraft())
        } catch (e: Exception) {
            ChatUtil.error("Failed to invoke clickMouse: ${e.message}")
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
     * Performs a left-click attack if conditions allow.
     * Respects bot toggle state, item usage, and canSwing() permissions.
     */
    fun leftClick() {
        val mc = CatDueller.mc
        if (CatDueller.bot?.toggled() == true && mc.thePlayer != null && !mc.thePlayer.isUsingItem) {
            // Only swing if canSwing allows it (BotBase handles all attack decision logic)
            if (CatDueller.bot?.canSwing() == true) {
                invokeClickMouse()
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("leftClick() executed")
                }
            } else {
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("leftClick() blocked - canSwing() returned false")
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
                ChatUtil.combatInfo("leftClick() blocked - $reason")
            }
        }
    }

    /**
     * Performs a right-click for the specified duration.
     *
     * @param duration Time in milliseconds to hold the right-click.
     */
    fun rClick(duration: Int) {
        if (CatDueller.bot?.toggled() == true) {
            if (!rClickDown) {
                rClickDown()
                TimerUtil.setTimeout(this::rClickUp, duration)
            }
        }
    }

    /**
     * Starts the left auto-click system.
     * Clicks are performed at a rate determined by the configured CPS.
     */
    fun startLeftAC() {
        if (CatDueller.bot?.toggled() == true) {
            leftAC = true
            tickCounter = 0
            ChatUtil.combatInfo("Started leftAC")
        }
    }


    /**
     * Stops the left auto-click system.
     */
    fun stopLeftAC() {
        leftAC = false
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
                ChatUtil.combatInfo("Started holding left click")
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
            ChatUtil.combatInfo("Stopped holding left click")
        }
    }


    /**
     * Stop holding right click (using Robot)
     */
    fun stopHoldRightClick() {
        if (isHoldingRightClick) {
            isHoldingRightClick = false
            robot?.mouseRelease(InputEvent.BUTTON3_DOWN_MASK)
            ChatUtil.combatInfo("Stopped holding right click")
        }
    }

    /**
     * Resets all mouse-related states to their default values.
     * Should be called when the bot is toggled off or reset.
     */
    fun resetAllStates() {
        stopLeftAC()
        stopHoldLeftClick()
        stopHoldRightClick()
        stopTracking()
        setUsingProjectile(false)
        setUsingPotion(false)
        setUsingGap(false)
        setRunningAway(false)
        setBlockingArrow(false)
        rClickDown = false

        gameEndViewRotationActive = false
        gameEndTargetYaw = 0f
        gameEndTargetPitch = 0f
    }

    /**
     * Starts tracking the opponent with the player's aim.
     */
    fun startTracking() {
        tracking = true
    }

    /**
     * Stops tracking the opponent.
     */
    fun stopTracking() {
        tracking = false
    }

    /**
     * Returns whether target tracking is currently active.
     */
    fun isTracking(): Boolean {
        return tracking
    }

    /**
     * Sets whether the player is currently blocking an arrow.
     *
     * @param blocking True if blocking an arrow, false otherwise.
     */
    fun setBlockingArrow(blocking: Boolean) {
        _blockingArrow = blocking
    }

    /**
     * Returns whether the player is currently blocking an arrow.
     */
    fun isBlockingArrow(): Boolean {
        return _blockingArrow
    }

    /**
     * Sets whether the player is currently dodging arrows.
     * @param dodging True if dodging arrows, false otherwise.
     */
    fun setDodgingArrow(dodging: Boolean) {
        _dodgingArrow = dodging
        if (dodging) {
            // Set random dodge direction (left or right 90 degrees)
            val currentYaw = CatDueller.mc.thePlayer?.rotationYaw ?: 0f
            val randomDirection = if (RandomUtil.randomIntInRange(1, 2) == 1) 90f else -90f
            dodgeArrowTargetYaw = currentYaw + randomDirection
            
            if (CatDueller.config?.combatLogs == true) {
                ChatUtil.combatInfo("Dodge Arrow: Started dodging - turning ${if (randomDirection > 0) "left" else "right"} 90 degrees")
            }
        } else {
            if (CatDueller.config?.combatLogs == true) {
                ChatUtil.combatInfo("Dodge Arrow: Stopped dodging")
            }
        }
    }

    /**
     * Returns whether the player is currently dodging arrows.
     */
    fun isDodgingArrow(): Boolean {
        return _dodgingArrow
    }

    /**
     * Sets whether the player is using a projectile weapon.
     *
     * @param proj True if using a projectile, false otherwise.
     */
    fun setUsingProjectile(proj: Boolean) {
        _usingProjectile = proj
    }

    /**
     * Returns whether the player is using a projectile weapon.
     */
    fun isUsingProjectile(): Boolean {
        return _usingProjectile
    }

    /**
     * Sets whether the player is using a splash potion.
     * Resets splash aim when usage ends.
     *
     * @param potion True if using a potion, false otherwise.
     */
    fun setUsingPotion(potion: Boolean) {
        _usingPotion = potion
        if (!_usingPotion) {
            splashAim = 0.0
        }
    }

    /**
     * Returns whether the player is using a splash potion.
     */
    fun isUsingPotion(): Boolean {
        return _usingPotion
    }

    /**
     * Sets whether the player is using a golden apple (gap).
     *
     * @param gap True if using a gap, false otherwise.
     */
    fun setUsingGap(gap: Boolean) {
        _usingGap = gap
    }

    /**
     * Returns whether the player is using a golden apple (gap).
     */
    fun isUsingGap(): Boolean {
        return _usingGap
    }

    /**
     * Sets whether the player is running away from the opponent.
     * Clears cached running rotations when state changes.
     *
     * @param runningAway True if running away, false otherwise.
     */
    fun setRunningAway(runningAway: Boolean) {
        _runningAway = runningAway
        runningRotations = null
    }

    /**
     * Returns whether the player is running away from the opponent.
     */
    fun isRunningAway(): Boolean {
        return _runningAway
    }

    /**
     * Starts the game end view rotation animation.
     * Levels the pitch to 0 and rotates yaw by a random +/- 45 degrees.
     */
    fun startGameEndViewRotation() {
        if (CatDueller.mc.thePlayer == null) return

        gameEndViewRotationActive = true
        gameEndTargetPitch = 0f

        val currentYaw = CatDueller.mc.thePlayer.rotationYaw
        val yawOffset = if (RandomUtil.randomBool()) 45f else -45f
        gameEndTargetYaw = currentYaw + yawOffset
    }

    /**
     * Stops the game end view rotation animation.
     */
    fun stopGameEndViewRotation() {
        gameEndViewRotationActive = false
    }

    /**
     * Handles the game end view rotation animation each tick.
     * Smoothly rotates toward the target yaw and pitch.
     */
    private fun handleGameEndViewRotation() {
        val player = CatDueller.mc.thePlayer ?: return

        val currentYaw = player.rotationYaw
        val currentPitch = player.rotationPitch

        var yawDiff = gameEndTargetYaw - currentYaw
        while (yawDiff > 180) yawDiff -= 360
        while (yawDiff < -180) yawDiff += 360

        val pitchDiff = gameEndTargetPitch - currentPitch

        val fixedSpeed = 10f

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

        player.rotationYaw += dyaw
        player.rotationPitch += dpitch

        if (abs(yawDiff) < 1f && abs(pitchDiff) < 1f) {
            gameEndViewRotationActive = false
        }
    }


    /**
     * Presses and holds the right mouse button.
     */
    private fun rClickDown() {
        if (CatDueller.bot?.toggled() == true) {
            rClickDown = true
            KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindUseItem.keyCode, true)
        }
    }

    /**
     * Starts holding the right mouse button.
     */
    fun startRightClick() {
        rClickDown()
    }

    /**
     * Releases the right mouse button.
     */
    fun rClickUp() {
        if (CatDueller.bot?.toggled() == true) {
            rClickDown = false
            KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindUseItem.keyCode, false)
        }
    }

    /**
     * Tick event handler for mouse operations.
     * Processes auto-clicking, key state management, and aim tracking.
     *
     * @param ev The client tick event.
     */
    @SubscribeEvent
    fun onTick(ev: TickEvent.ClientTickEvent) {
        if (CatDueller.mc.thePlayer == null) return

        if (ev.phase == TickEvent.Phase.START && CatDueller.bot?.toggled() == true) {
            if (leftAC) {
                tickCounter++

                if (!CatDueller.mc.thePlayer.isUsingItem) {
                    val targetCPS = CatDueller.config?.cps?.toDouble() ?: 12.0
                    val baseProbability = targetCPS / 20.0
                    val variance = 0.10
                    val randomFactor = 1.0 + RandomUtil.randomDoubleInRange(-variance, variance)
                    val finalProbability = (baseProbability * randomFactor).coerceIn(0.0, 1.0)

                    if (Math.random() < finalProbability) {
                        leftClick()
                        lastLeftClick = System.currentTimeMillis()

                        if (CatDueller.config?.combatLogs == true) {
                            ChatUtil.combatInfo(
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
                if (leftAC && CatDueller.mc.gameSettings.keyBindAttack.isKeyDown) {
                    KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindAttack.keyCode, false)
                }
            }
        } else {
            if (leftAC) {
                leftAC = false
                KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindAttack.keyCode, false)
            }
        }

        if (CatDueller.mc.thePlayer != null && CatDueller.bot?.toggled() == true) {
            if (gameEndViewRotationActive) {
                handleGameEndViewRotation()
            } else if (tracking && CatDueller.bot?.opponent() != null) {
                applyRotationsImmediate()
            }
        }
    }

    /**
     * Calculates the angle difference between current aim and the target entity.
     *
     * @param targetEntity The entity to calculate angle difference to.
     * @return The combined Euclidean angle difference in degrees.
     */
    private fun getAngleDifference(targetEntity: Entity?): Double {
        if (targetEntity == null) return 0.0

        val player = CatDueller.mc.thePlayer ?: return 0.0
        val rotations = EntityUtil.getRotations(player, targetEntity, false) ?: return 0.0

        val currentYaw = player.rotationYaw
        val currentPitch = player.rotationPitch
        val targetYaw = rotations[0]
        val targetPitch = rotations[1]

        var yawDiff = targetYaw - currentYaw
        while (yawDiff > 180) yawDiff -= 360
        while (yawDiff < -180) yawDiff += 360

        val pitchDiff = targetPitch - currentPitch

        return kotlin.math.sqrt((yawDiff * yawDiff + pitchDiff * pitchDiff).toDouble())
    }

    /**
     * Applies aim rotations immediately toward the opponent.
     * Handles special cases for running away, potion usage, projectile aiming, and arrow dodging.
     * Uses smooth rotation with distance and angle-based speed adjustments.
     */
    private fun applyRotationsImmediate() {
        if (_runningAway) {
            _usingProjectile = false
        }

        // Handle Dodge Arrow rotation - takes priority over normal tracking
        if (_dodgingArrow) {
            val player = CatDueller.mc.thePlayer ?: return
            val currentYaw = player.rotationYaw
            
            // Calculate yaw difference to target dodge direction
            var yawDiff = dodgeArrowTargetYaw - currentYaw
            while (yawDiff > 180) yawDiff -= 360
            while (yawDiff < -180) yawDiff += 360
            
            // Apply smooth rotation toward dodge direction
            val maxRotSpeed = 15.0f // Fast rotation for dodging
            val dyaw = if (abs(yawDiff) > maxRotSpeed) {
                if (yawDiff > 0) maxRotSpeed else -maxRotSpeed
            } else {
                yawDiff
            }
            
            // Keep pitch level for dodging
            val dpitch = -player.rotationPitch * 0.3f // Gradually level pitch
            
            player.rotationYaw += dyaw
            player.rotationPitch += dpitch
            
            if (CatDueller.config?.combatLogs == true && abs(dyaw) > 0.1f) {
                ChatUtil.combatInfo("Dodge Arrow rotation - dyaw: ${String.format("%.2f", dyaw)}, target: ${String.format("%.2f", dodgeArrowTargetYaw)}")
            }
            
            return // Skip normal tracking when dodging arrows
        }

        var rotations = EntityUtil.getRotations(CatDueller.mc.thePlayer, CatDueller.bot?.opponent(), false)

        if (rotations != null) {
            if (_runningAway) {
                if (runningRotations == null) {
                    runningRotations = rotations
                    runningRotations!![0] += 180 + RandomUtil.randomDoubleInRange(-5.0, 5.0).toFloat()
                }
                rotations = runningRotations!!
            }

            if (_usingPotion) {
                if (splashAim == 0.0) {
                    splashAim = RandomUtil.randomDoubleInRange(80.0, 90.0)
                }
                rotations[1] = splashAim.toFloat()
            } else if (!_usingProjectile && CatDueller.config?.verticalMultipoint == true) {
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
                        ?: ((opponentMinY + opponentMaxY) / 2.0)

                    // Compute target pitch
                    val deltaX = opponent.posX - player.posX
                    val deltaY = targetY - playerEyeY
                    val deltaZ = opponent.posZ - player.posZ
                    val distanceXZ = kotlin.math.sqrt(deltaX * deltaX + deltaZ * deltaZ)
                    val targetPitch = (-Math.toDegrees(kotlin.math.atan2(deltaY, distanceXZ))).toFloat()
                    rotations[1] = targetPitch
                }
            }

            val lookRand = (CatDueller.config?.lookRand ?: 0).toDouble()
            var dyaw = ((rotations[0] - CatDueller.mc.thePlayer.rotationYaw) + RandomUtil.randomDoubleInRange(
                -lookRand,
                lookRand
            )).toFloat()
            var dpitch = ((rotations[1] - CatDueller.mc.thePlayer.rotationPitch) + RandomUtil.randomDoubleInRange(
                -lookRand,
                lookRand
            )).toFloat()

            val opponent = CatDueller.bot?.opponent()
            val distanceFactor = if (_runningAway || _usingPotion || opponent == null) {
                1.0f
            } else {
                when (EntityUtil.getDistanceNoY(CatDueller.mc.thePlayer, opponent)) {
                    in 0f..10f -> 1.0f
                    in 10f..20f -> 0.6f
                    in 20f..30f -> 0.4f
                    else -> 0.2f
                }
            }

            val angleDifference = getAngleDifference(CatDueller.bot?.opponent())
            val angleFactor = when {
                angleDifference <= 1.0 -> 0.3f
                angleDifference <= 5.0 -> 0.5f
                angleDifference <= 15.0 -> 0.8f
                angleDifference <= 30.0 -> 1.0f
                else -> 1.2f
            }

            val onTarget = CatDueller.mc.objectMouseOver != null &&
                    CatDueller.mc.objectMouseOver.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.ENTITY &&
                    CatDueller.mc.objectMouseOver.entityHit == CatDueller.bot?.opponent()

            val onTargetFactor = if (onTarget) 0.85f else 1.0f

            val combinedFactor = distanceFactor * angleFactor * onTargetFactor

            val maxRotH = if (_runningAway) {
                30.0f
            } else {
                (CatDueller.config?.lookSpeedHorizontal ?: 10).toFloat() * combinedFactor
            }

            val maxRotV = if (_runningAway) {
                30.0f
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

            if (CatDueller.config?.combatLogs == true && (abs(dyaw) > 0.1f || abs(dpitch) > 0.1f)) {
                ChatUtil.combatInfo(
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
