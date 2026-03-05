package org.afterlike.catdueller.bot.player

import net.minecraft.client.Minecraft
import net.minecraft.client.settings.KeyBinding
import net.minecraft.entity.Entity
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.relauncher.ReflectionHelper
import org.afterlike.catdueller.CatDueller
import org.afterlike.catdueller.utils.client.ChatUtil
import org.afterlike.catdueller.utils.client.TimerUtil
import org.afterlike.catdueller.utils.game.EntityUtil
import org.afterlike.catdueller.utils.system.RandomUtil
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

    /** Whether the left mouse button is currently held down via KeyBinding. */
    var lClickDown = false

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

    // ==================== GCD Rotation Snap ====================
    // Minecraft's mouse system quantizes rotation deltas to multiples of a
    // "GCD interval" derived from the sensitivity slider. Anticheat checks
    // that every yaw/pitch delta is divisible by this interval.

    /** Cached GCD interval in degrees, recalculated when sensitivity changes. */
    private var gcdInterval = 0.0f
    /** Last known sensitivity value to detect changes. */
    private var lastSensitivity = -1.0f
    private var yawRemainder = 0f
    private var pitchRemainder = 0f

    private fun updateGcd() {
        val sens = Minecraft.getMinecraft().gameSettings.mouseSensitivity
        if (sens != lastSensitivity) {
            lastSensitivity = sens
            val f = sens * 0.6f + 0.2f
            gcdInterval = f * f * f * 1.2f
        }
    }

    fun snapToGcd(delta: Float, isYaw: Boolean = true): Float {
        updateGcd()
        if (gcdInterval <= 0f) return delta
        val remainder = if (isYaw) yawRemainder else pitchRemainder
        val total = delta + remainder
        val steps = Math.round(total / gcdInterval)
        val snapped = steps * gcdInterval
        if (isYaw) yawRemainder = total - snapped
        else pitchRemainder = total - snapped
        return snapped
    }

    /** Whether the player is currently using a splash potion. */
    private var _usingPotion = false

    /** Whether the player is currently using a golden apple (gap). */
    private var _usingGap = false

    /** Whether the player is currently using a golden head. */
    private var _usingHead = false

    /** Whether the player is currently running away from the opponent. */
    private var _runningAway = false

    /** Whether the player is currently blocking an incoming arrow. */
    private var _blockingArrow = false

    /** Whether the player is currently dodging arrows. */
    private var _dodgingArrow = false

    /** Target yaw for arrow dodging. */
    private var dodgeArrowTargetYaw = 0f

    /** Whether the player is currently placing water at feet. */
    private var _placingWater = false

    /** Whether the player is currently placing plank at feet. */
    private var _placingPlank = false

    /** Whether the player is currently placing block at feet (water or plank). */
    private var _placingBlockAtFeet = false

    /** Whether the player is currently aiming at a block to break. */
    private var _breakingBlock = false

    /** Target pitch when breaking a block. */
    private var _breakingBlockPitch = 0f

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
        }
    }

    /**
     * Performs a left-click attack if conditions allow.
     * Respects bot toggle state, item usage, and canSwing() permissions.
     */
    fun leftClick() {
        val mc = CatDueller.mc
        if (lClickDown) return  // Don't interrupt block breaking with auto-click
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
     * Start holding right click (using Robot)
     */
    fun startHoldRightClick() {
        if (CatDueller.bot?.toggled() == true) {
            if (!isHoldingRightClick) {
                isHoldingRightClick = true
                robot?.mousePress(InputEvent.BUTTON3_DOWN_MASK)
                ChatUtil.combatInfo("Started holding right click (Robot)")
            }
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
        setUsingHead(false)
        setRunningAway(false)
        setBlockingArrow(false)
        setBreakingBlock(false)
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
     * Sets whether the player is placing water at feet.
     * @param placing True if placing water, false otherwise.
     */
    fun setPlacingWater(placing: Boolean) {
        _placingWater = placing
        _placingBlockAtFeet = placing
    }

    /**
     * Returns whether the player is currently placing water.
     */
    fun isPlacingWater(): Boolean {
        return _placingWater
    }

    /**
     * Sets whether the player is placing plank at feet.
     * @param placing True if placing plank, false otherwise.
     */
    fun setPlacingPlank(placing: Boolean) {
        _placingPlank = placing
        _placingBlockAtFeet = placing
    }

    /**
     * Returns whether the player is currently placing plank.
     */
    fun isPlacingPlank(): Boolean {
        return _placingPlank
    }

    /**
     * Sets whether the player is placing any block at feet (water or plank).
     * @param placing True if placing block, false otherwise.
     */
    fun setPlacingBlockAtFeet(placing: Boolean) {
        _placingBlockAtFeet = placing
    }

    /**
     * Returns whether the player is currently placing any block at feet.
     */
    fun isPlacingBlockAtFeet(): Boolean {
        return _placingBlockAtFeet
    }

    /**
     * Sets block breaking aim state with target pitch.
     */
    fun setBreakingBlock(breaking: Boolean, pitch: Float = 0f) {
        _breakingBlock = breaking
        _breakingBlockPitch = pitch
    }

    /**
     * Returns whether the player is currently aiming at a block to break.
     */
    fun isBreakingBlock(): Boolean {
        return _breakingBlock
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
     * Sets whether the player is currently using a golden head.
     *
     * @param head True if using a head, false otherwise.
     */
    fun setUsingHead(head: Boolean) {
        _usingHead = head
    }

    /**
     * Returns whether the player is using a golden head.
     */
    fun isUsingHead(): Boolean {
        return _usingHead
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

        player.rotationYaw += snapToGcd(dyaw)
        player.rotationPitch += snapToGcd(dpitch, isYaw = false)

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
     * Presses and holds the left mouse button via KeyBinding.
     */
    fun startLeftClick() {
        if (CatDueller.bot?.toggled() == true) {
            lClickDown = true
            // Initiate block breaking via PlayerControllerMP
            val mc = CatDueller.mc
            if (mc.objectMouseOver != null &&
                mc.objectMouseOver.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK) {
                val pos = mc.objectMouseOver.blockPos
                val side = mc.objectMouseOver.sideHit
                mc.playerController.clickBlock(pos, side)
                mc.thePlayer?.swingItem()
            }
        }
    }

    /**
     * Releases the left mouse button via KeyBinding.
     */
    fun lClickUp() {
        lClickDown = false
        CatDueller.mc.playerController?.resetBlockRemoving()
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
            // Progress block breaking every tick (works when tabbed out)
            if (lClickDown) {
                val mc = CatDueller.mc
                if (mc.objectMouseOver != null &&
                    mc.objectMouseOver.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK) {
                    val pos = mc.objectMouseOver.blockPos
                    val side = mc.objectMouseOver.sideHit
                    mc.playerController.onPlayerDamageBlock(pos, side)
                    mc.thePlayer?.swingItem()
                }
            }

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
                if (leftAC && CatDueller.mc.gameSettings.keyBindAttack.isKeyDown && !lClickDown) {
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
     * Handles special cases for running away, potion usage, projectile aiming, arrow dodging, and block placement at feet.
     * Uses smooth rotation with distance and angle-based speed adjustments.
     */
    private fun applyRotationsImmediate() {
        if (_runningAway) {
            _usingProjectile = false
        }

        // Handle Block Placement at Feet rotation - takes highest priority (water or plank)
        if (_placingBlockAtFeet) {
            val player = CatDueller.mc.thePlayer ?: return

            // Look straight down (pitch = 90)
            val targetPitch = 90f
            val currentPitch = player.rotationPitch
            val pitchDiff = targetPitch - currentPitch

            // Apply fast rotation downward
            val maxRotSpeed = 30.0f
            val dpitch = if (abs(pitchDiff) > maxRotSpeed) {
                if (pitchDiff > 0) maxRotSpeed else -maxRotSpeed
            } else {
                pitchDiff
            }

            player.rotationPitch += snapToGcd(dpitch, isYaw = false)

            if (CatDueller.config?.combatLogs == true && abs(dpitch) > 0.1f) {
                val blockType = if (_placingPlank) "Plank" else if (_placingWater) "Water" else "Block"
                ChatUtil.combatInfo(
                    "$blockType Placement rotation - dpitch: ${
                        String.format(
                            "%.2f",
                            dpitch
                        )
                    }, target: 90°"
                )
            }

            return // Skip normal tracking when placing blocks at feet
        }

        // Handle Block Breaking rotation - aim at target block
        if (_breakingBlock) {
            val player = CatDueller.mc.thePlayer ?: return

            val currentPitch = player.rotationPitch
            val pitchDiff = _breakingBlockPitch - currentPitch

            val maxRotSpeed = 20.0f
            val dpitch = if (abs(pitchDiff) > maxRotSpeed) {
                if (pitchDiff > 0) maxRotSpeed else -maxRotSpeed
            } else {
                pitchDiff
            }

            player.rotationPitch += snapToGcd(dpitch, isYaw = false)

            return // Skip normal tracking when breaking blocks
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

            player.rotationYaw += snapToGcd(dyaw)
            player.rotationPitch += snapToGcd(dpitch, isYaw = false)

            if (CatDueller.config?.combatLogs == true && abs(dyaw) > 0.1f) {
                ChatUtil.combatInfo(
                    "Dodge Arrow rotation - dyaw: ${
                        String.format(
                            "%.2f",
                            dyaw
                        )
                    }, target: ${String.format("%.2f", dodgeArrowTargetYaw)}"
                )
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

                    // Find closest Y coordinate without creating a list
                    // Sample 50 points along opponent's height
                    var targetY = (opponentMinY + opponentMaxY) / 2.0
                    var minDiff = abs(playerEyeY - targetY)

                    for (i in 0..49) {
                        val candidateY = opponentMinY + i * (opponentMaxY - opponentMinY) / 49.0
                        val diff = abs(playerEyeY - candidateY)
                        if (diff < minDiff) {
                            minDiff = diff
                            targetY = candidateY
                        }
                    }

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

            val combinedFactor = distanceFactor * angleFactor

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

            CatDueller.mc.thePlayer.rotationYaw += snapToGcd(dyaw)
            CatDueller.mc.thePlayer.rotationPitch += snapToGcd(dpitch, isYaw = false)
        }
    }

}
