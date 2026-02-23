package org.afterlike.catdueller.bot.impl

import net.minecraft.init.Blocks
import net.minecraft.util.Vec3
import org.afterlike.catdueller.CatDueller
import org.afterlike.catdueller.bot.BotBase
import org.afterlike.catdueller.bot.features.Bow
import org.afterlike.catdueller.bot.features.MovePriority
import org.afterlike.catdueller.bot.features.Rod
import org.afterlike.catdueller.bot.player.*
import org.afterlike.catdueller.utils.client.ChatUtil
import org.afterlike.catdueller.utils.client.TimerUtil
import org.afterlike.catdueller.utils.game.EntityUtil
import org.afterlike.catdueller.utils.game.WorldUtil
import org.afterlike.catdueller.utils.system.RandomUtil

/**
 * Bot implementation for Blitz Duels game mode.
 *
 * This bot handles combat mechanics including sword fighting, bow usage,
 * fishing rod tactics, W-tapping, block-hitting, arrow blocking, and
 * strategic movement based on distance and opponent behavior.
 *
 * Features:
 * - Intelligent weapon switching between sword, bow, and rod
 * - Distance-based combat strategy with retreat mechanics
 * - Arrow blocking when opponent draws bow for extended periods
 * - Hurt strafe for evasion after taking damage
 * - Wall avoidance during strafing
 */
class Blitz : BotBase("/play duels_blitz_duel"), Bow, Rod, MovePriority {

    /**
     * Returns the display name of this bot.
     * @return The string "Blitz"
     */
    override fun getName(): String {
        return "Blitz"
    }

    /** Returns the melee weapon name based on kit and config. */
    private fun meleeWeapon(): String {
        val kit = CatDueller.config?.blitzKit ?: 0
        if (kit == 0) return "sword" // Fisherman always uses sword
        // Necromancer: check config
        return if ((CatDueller.config?.necromancerMelee ?: 0) == 0) "sword" else "shovel"
    }

    init {
        setStatKeys(
            mapOf(
                "wins" to "player.stats.Duels.blitz_duel_wins",
                "losses" to "player.stats.Duels.blitz_duel_losses",
                "ws" to "player.stats.Duels.current_blitz_winstreak",
            )
        )
    }

    /** Number of arrows fired this game. */
    var shotsFired = 0

    /** Maximum arrows allowed per game. */
    var maxArrows = 19

    /** Strafe direction after being hurt: 0=none, 1=left, 2=right. */
    private var hurtStrafeDirection = 0

    /** Current state of hold left click to avoid unnecessary calls. */
    private var shouldHoldLeftClick = false

    /** Flag to prevent jump interruption after rod hit. */
    private var rodHitNeedJump = false

    /** Distance stored when rod hit occurred for debugging purposes. */
    private var rodHitDistance = 0f

    /** Timestamp of last rod usage for accurate hit detection. */
    private var lastRodUseTime = 0L

    /** Previous hurtTime value of opponent for rod hit detection. */
    private var opponentLastHurtTime = 0

    /** Timestamp of last sword hit to exclude from rod hit detection. */
    private var lastSwordHitTime = 0L

    /** Flag indicating active dodge state to prevent block jump interference. */
    private var isDodging = false

    /** Timestamp when bot started using bow. */
    private var ourBowStartTime: Long = 0

    /** Flag indicating bot is currently using bow. */
    private var isUsingBow = false

    /** Timestamp of last weapon switch to add delay before attacking. */
    private var lastWeaponSwitchTime: Long = 0

    /** Flag to continue retreating until rod hits opponent once. */
    private var shouldRetreatUntilRodHit = false

    /** Timestamp when retreat last ended for cooldown tracking. */
    private var lastRetreatEndTime = 0L

    /** Flag indicating bow counter-attack is in progress. */
    private var bowCounterAttackActive = false

    /** Flag indicating spawn egg placement is in progress. */
    private var isPlacingSpawnEgg = false

    /** Timestamp of last bow shot for cooldown tracking. */
    private var lastBowShotTime = 0L

    /** Flag indicating arrow dodging is currently active. */
    private var isDodgingArrow = false

    /** Timer for switching between forward and backward movement during arrow dodge. */
    private var dodgeMovementTimer: java.util.Timer? = null

    /** Current dodge movement direction: true=forward, false=backward. */
    private var dodgeMovingForward = true

    /** Flag indicating post-bow strafe is active. */
    private var postBowStrafeActive = false

    /** Timestamp when post-bow strafe started. */
    private var postBowStrafeEndTime = 0L

    /** Previous tick's opponent bow drawing state. */
    private var lastTickOpponentDrawingBow = false

    /** Timestamp when opponent started drawing bow. */
    private var opponentBowStartTime = 0L

    /** Timestamp when opponent stopped drawing bow. */
    private var opponentBowStopTime = 0L

    /** Flag to prevent multiple scheduling of blocking end. */
    private var blockingEndScheduled = false

    /** Timestamp when dodge arrow last ended for cooldown tracking. */
    private var lastDodgeArrowEndTime = 0L

    /** Timestamp when dodge arrow started for timeout tracking. */
    private var dodgeArrowStartTime = 0L

    /** Previous tick's distance to opponent. */
    private var lastOpponentDistance = 0f

    /** Flag indicating opponent is moving closer. */
    private var isOpponentApproaching = false

    /** Flag indicating forward movement is paused due to opponent approach. */
    private var movementPausedForApproach = false

    /** Timestamp when rod last hit opponent. */
    private var lastRodHitTime = 0L

    /** Previous opponent strafe direction for detecting direction changes. */
    private var lastOpponentStrafeDirection = 0

    /** Timestamp when block breaking ended, for 1s strafe suppression. */
    private var blockBreakEndTime = 0L

    /** Timestamp of last random swing for random interval. */
    private var lastRandomSwing = 0L

    /** Whether random swing mode is currently active. */
    private var isRandomSwing = false

    /**
     * Called when the bot joins a game lobby.
     * Handles lobby movement and rotation setup.
     */
    override fun onJoinGame() {
        super.onJoinGame()
    }

    /**
     * Called when the game starts.
     * Resets all game-specific state variables and initiates movement.
     */
    override fun onGameStart() {
        super.onGameStart()
        shotsFired = 0

        hurtStrafeDirection = 0
        shouldHoldLeftClick = false
        rodHitNeedJump = false
        rodHitDistance = 0f
        lastRodUseTime = 0L
        opponentLastHurtTime = 0
        lastSwordHitTime = 0L
        isDodging = false
        ourBowStartTime = 0
        isUsingBow = false
        shouldRetreatUntilRodHit = false
        lastRetreatEndTime = 0L
        bowCounterAttackActive = false
        opponentBowStartTime = 0L
        opponentBowStopTime = 0L
        blockingEndScheduled = false
        lastWeaponSwitchTime = 0
        lastOpponentDistance = 0f
        isOpponentApproaching = false
        movementPausedForApproach = false
        lastRodHitTime = 0L
        lastDodgeArrowEndTime = 0L
        dodgeArrowStartTime = 0L
        isPlacingSpawnEgg = false
        postBowStrafeActive = false
        postBowStrafeEndTime = 0L
        lastBowShotTime = 0L
        lastRandomSwing = 0L
        isRandomSwing = false
        lastOpponentStrafeDirection = 0

        // Reset W-tap state
        tapping = false
        wTapStartTime = 0L
        wTapRecoveryTimer?.cancel()
        wTapRecoveryTimer = null

        // Reset Dodge Arrow state
        isDodgingArrow = false
        dodgeMovementTimer?.cancel()
        dodgeMovementTimer = null
        dodgeMovingForward = true
        Mouse.setDodgingArrow(false)

        Movement.startSprinting()
        Movement.startForward()
        TimerUtil.setTimeout(Movement::startJumping, RandomUtil.randomIntInRange(400, 1200))
    }

    /**
     * Called before the game starts.
     * Stops lobby movement.
     */
    override fun beforeStart() {
        
    }

    /**
     * Called when the game ends.
     * Stops all combat actions, cleans up resources, and prepares for next game.
     */
    override fun onGameEnd() {
        super.onGameEnd()

        shotsFired = 0
        shouldHoldLeftClick = false

        // Clean up block breaking state
        if (Mouse.lClickDown) {
            Mouse.lClickUp()
        }

        // Clean up random swing state
        if (isRandomSwing) {
            isRandomSwing = false
            Mouse.setBreakingBlock(false)
        }

        // Clean up Dodge Arrow state
        isDodgingArrow = false
        dodgeMovementTimer?.cancel()
        dodgeMovementTimer = null
        dodgeMovingForward = true
        Mouse.setDodgingArrow(false)

        // Clean up W-tap state
        tapping = false
        wTapStartTime = 0L
        wTapRecoveryTimer?.cancel()
        wTapRecoveryTimer = null

        cleanupGameResources()

        if (CatDueller.config?.holdLeftClick == true) {
            Mouse.stopHoldLeftClick()
        } else {
            Mouse.stopLeftAC()
        }

        val i = TimerUtil.setInterval({
            if (CatDueller.config?.holdLeftClick == true) {
                Mouse.stopHoldLeftClick()
            } else {
                Mouse.stopLeftAC()
            }
        }, 100, 100)

        TimerUtil.setTimeout(fun() {
            i?.cancel()
        }, RandomUtil.randomIntInRange(200, 400))

        if (CatDueller.bot?.toggled() == true) {
            Mouse.stopTracking()
            Movement.clearAll()
            Combat.stopRandomStrafe()
        }
    }

    /**
     * Resets all game-specific resources and state variables.
     * Called at game end to ensure clean state for next game.
     */
    private fun cleanupGameResources() {
        lastRodUseTime = 0L
        lastSwordHitTime = 0L
        ourBowStartTime = 0L
        lastWeaponSwitchTime = 0L

        rodHitNeedJump = false
        isDodging = false
        isUsingBow = false
        shouldRetreatUntilRodHit = false
        tapping = false
        wTapStartTime = 0L
        wTapRecoveryTimer?.cancel()
        wTapRecoveryTimer = null

        lastRetreatEndTime = 0L

        bowCounterAttackActive = false
        opponentBowStartTime = 0L
        opponentBowStopTime = 0L
        blockingEndScheduled = false

        rodHitDistance = 0f

        opponentLastHurtTime = 0

        hurtStrafeDirection = 0

        shouldHoldLeftClick = false

        // Clean up spawn egg state
        if (isPlacingSpawnEgg) {
            isPlacingSpawnEgg = false
            Mouse.setPlacingWater(false)
            Mouse.rClickUp()
        }

        // Clean up post-bow strafe
        postBowStrafeActive = false
        postBowStrafeEndTime = 0L

        // Clean up random swing
        if (isRandomSwing) {
            isRandomSwing = false
            Mouse.setBreakingBlock(false)
        }
        lastRandomSwing = 0L

        if (CatDueller.config?.combatLogs == true) {
            ChatUtil.combatInfo("Game resources cleaned up - memory leak prevention")
        }
    }

    /** Flag indicating W-tap is currently active. */
    var tapping = false

    /** Timestamp when W-tap started for early recovery detection. */
    private var wTapStartTime = 0L

    /** Timer reference for W-tap recovery to allow early cancellation. */
    private var wTapRecoveryTimer: java.util.Timer? = null

    /**
     * Uses the fishing rod with additional tracking and jump scheduling.
     *
     * Tracks rod usage time for hit detection and schedules a delayed jump
     * when using rod at close range (distance less than 5 blocks) if rod jump is enabled.
     *
     * @param isDefensive Whether this is a defensive rod usage (when being combo'd)
     */
    private fun useRodWithTracking(isDefensive: Boolean = false) {
        lastRodUseTime = System.currentTimeMillis()

        val distance = EntityUtil.getDistanceNoY(mc.thePlayer, opponent())
        val enableRodJump = CatDueller.config?.enableRodJump ?: true

        if (distance < 5f && mc.thePlayer.onGround && enableRodJump) {
            rodHitNeedJump = true

            val jumpDelay = CatDueller.config?.rodJumpDelay ?: 200
            TimerUtil.setTimeout({
                if (mc.thePlayer != null && mc.thePlayer.onGround && rodHitNeedJump) {
                    Movement.singleJump(RandomUtil.randomIntInRange(100, 150))
                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtil.combatInfo("Rod delayed jump EXECUTED after ${jumpDelay}ms - distance: $distance")
                    }
                }

                TimerUtil.setTimeout({
                    rodHitNeedJump = false
                }, 500)
            }, jumpDelay)

            if (CatDueller.config?.combatLogs == true) {
                ChatUtil.combatInfo("Rod delayed jump SCHEDULED for ${jumpDelay}ms - distance: $distance")
            }
        } else if (CatDueller.config?.combatLogs == true) {
            val reason = when {
                !enableRodJump -> "rod jump disabled in config"
                distance >= 5f -> "distance: $distance (>= 5) "
                !mc.thePlayer.onGround -> "not on ground"
                else -> "unknown reason"
            }
            ChatUtil.combatInfo("Rod jump SKIPPED - $reason")
        }

        if (CatDueller.config?.combatLogs == true) {
            ChatUtil.combatInfo("Rod usage tracked - time: $lastRodUseTime, defensive: $isDefensive")
        }

        useRod(isDefensive)
    }

    /**
     * Called when the bot successfully attacks the opponent.
     *
     * Handles block-hitting at close range, W-tapping at medium range,
     * and clears lateral movement when in a combo.
     */
    override fun onAttack() {
        val distance = EntityUtil.getDistanceNoY(mc.thePlayer, opponent())

        if (CatDueller.config?.combatLogs == true) {
            ChatUtil.combatInfo("onAttack triggered - distance: $distance")
        }

        lastSwordHitTime = System.currentTimeMillis()

        if (CatDueller.config?.combatLogs == true) {
            ChatUtil.combatInfo("Sword hit recorded - time: $lastSwordHitTime")
        }

        if (mc.thePlayer != null && mc.thePlayer.heldItem != null) {
            val n = mc.thePlayer.heldItem.unlocalizedName.lowercase()

            if (n.contains(meleeWeapon()) && distance < 3) {
                Mouse.rClick(RandomUtil.randomIntInRange(80, 100))
                
            }
        }

        if (distance >= 3) {
            if (!tapping && CatDueller.config?.enableWTap == true) {
                tapping = true
                val delay = CatDueller.config?.wTapDelay ?: 100
                TimerUtil.setTimeout(fun() {
                    val dur = 50
                    Combat.wTap(dur)
                    TimerUtil.setTimeout(fun() {
                        tapping = false
                    }, dur)
                }, delay)
            }
        }
        if (combo >= 2) {
            Movement.clearLeftRight()
        }
    }

    /**
     * Main game loop called every tick.
     *
     * Handles all combat logic including:
     * - Rod hit detection via opponent hurtTime
     * - Arrow blocking when opponent draws bow
     * - Weapon switching based on distance
     * - Bow usage in various tactical situations
     * - Movement control including retreat and approach management
     * - Hurt strafe and wall avoidance
     */
    override fun onTick() {
        super.onTick()
        var needJump = false

        // Block breaking: if looking at a plank or log block, switch to axe and hold left click
        if (toggled() && opponent() != null && mc.thePlayer != null && mc.objectMouseOver != null &&
            mc.objectMouseOver.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK) {
            val blockPos = mc.objectMouseOver.blockPos
            val block = mc.theWorld?.getBlockState(blockPos)?.block
            val isBreakable = block == Blocks.planks || block == Blocks.log || block == Blocks.log2
            if (isBreakable && !Mouse.lClickDown) {
                Inventory.setInvItem("hatchet")
                Mouse.startLeftClick()
                Combat.stopRandomStrafe()
                Movement.clearLeftRight()
            }
            if (!isBreakable && Mouse.lClickDown) {
                Mouse.lClickUp()
                Mouse.setBreakingBlock(false)
                Inventory.setInvItem(meleeWeapon())
                blockBreakEndTime = System.currentTimeMillis()
            }
        } else if (Mouse.lClickDown) {
            Mouse.lClickUp()
            Mouse.setBreakingBlock(false)
            Inventory.setInvItem(meleeWeapon())
            blockBreakEndTime = System.currentTimeMillis()
        }

        // Aim at feet-level block: if above head is NOT air, feet level has a block, and opponent distance > 1
        if (toggled() && mc.thePlayer != null && mc.theWorld != null && opponent() != null) {
            val opponentDist = EntityUtil.getDistanceNoY(mc.thePlayer, opponent())
            val aboveNotAir = (WorldUtil.blockInFront(mc.thePlayer, 4f, 2.5f) != Blocks.air || WorldUtil.blockInFront(mc.thePlayer, 3f, 2.5f) != Blocks.air || WorldUtil.blockInFront(mc.thePlayer, 2f, 2.5f) != Blocks.air || WorldUtil.blockInFront(mc.thePlayer, 1f, 2.5f) != Blocks.air)
            val feetBlock3Raw = WorldUtil.blockInFront(mc.thePlayer, 3f, 0.5f)
            val feetBlock2Raw = WorldUtil.blockInFront(mc.thePlayer, 2f, 0.5f)
            val feetBlock1Raw = WorldUtil.blockInFront(mc.thePlayer, 1f, 0.5f)
            val isBreakableFeet3 = feetBlock3Raw == Blocks.planks || feetBlock3Raw == Blocks.log || feetBlock3Raw == Blocks.log2
            val isBreakableFeet2 = feetBlock2Raw == Blocks.planks || feetBlock2Raw == Blocks.log || feetBlock2Raw == Blocks.log2
            val isBreakableFeet1 = feetBlock1Raw == Blocks.planks || feetBlock1Raw == Blocks.log || feetBlock1Raw == Blocks.log2
            val feetBlock3 = isBreakableFeet3
            val feetBlock2 = isBreakableFeet2
            val feetBlock1 = isBreakableFeet1

            if (aboveNotAir && (feetBlock1 || feetBlock2 || feetBlock3) && opponentDist > 1f) {
                // Calculate pitch to aim at the feet-level block
                // Block is at player.y + 0.3 (feet level = -0.2 + 0.5), eye is at player.y + 1.62
                // Vertical diff = 1.32 blocks down, horizontal = 1 or 2 blocks
                val horizDist = if (feetBlock1) 1.0 else 2.0
                val vertDiff = 1.32
                val pitch = Math.toDegrees(kotlin.math.atan2(vertDiff, horizDist)).toFloat()
                Mouse.setBreakingBlock(true, pitch)
            } else if (Mouse.isBreakingBlock()) {
                Mouse.setBreakingBlock(false)
            }

            // Jump if block directly above head (distance 0 = at player position) but NOT if there's a wall in front, and NOT during block breaking
            val headClearInFront = WorldUtil.blockInFront(mc.thePlayer, 0f, 1.5f) == Blocks.air
            if (!Mouse.lClickDown && WorldUtil.blockInFront(mc.thePlayer, 0f, 2.5f) != Blocks.air && mc.thePlayer.onGround && headClearInFront) {
                needJump = true
                ChatUtil.info("head hitter")
                Movement.singleJump(RandomUtil.randomIntInRange(100, 150))
            }
        }

        // Check for early W-tap recovery if player gets hurt during W-tap
        if (tapping && wTapStartTime > 0 && mc.thePlayer != null) {
            val currentHurtTime = mc.thePlayer.hurtTime
            if (currentHurtTime > 0) {
                // Player got hurt during W-tap, immediately recover forward movement
                wTapRecoveryTimer?.cancel()
                wTapRecoveryTimer = null
                Movement.startForward()
                tapping = false
                System.currentTimeMillis() - wTapStartTime
                wTapStartTime = 0L
            }
        }

        if (mc.thePlayer != null && opponent() != null) {
            // Check for rod hit via opponent's hurtTime change
            val opponentCurrentHurtTime = opponent()!!.hurtTime
            val currentTime = System.currentTimeMillis()

            // Detect rod hit: opponent's hurtTime increased AND we used rod recently AND it's NOT a sword hit
            val isRecentSwordHit = currentTime - lastSwordHitTime < 200  // 200ms window for sword hit
            val isRecentRodUse = currentTime - lastRodUseTime < 3000     // Extended to 3-second window for rod use

            // Debug rod hit detection conditions
            if (CatDueller.config?.combatLogs == true && shouldRetreatUntilRodHit) {
                ChatUtil.combatInfo("Rod hit check - OpponentHurt: $opponentCurrentHurtTime (was: $opponentLastHurtTime), RecentRod: $isRecentRodUse (${currentTime - lastRodUseTime}ms ago), RecentSword: $isRecentSwordHit")
            }

            if (opponentCurrentHurtTime > opponentLastHurtTime && opponentCurrentHurtTime > 0 &&
                isRecentRodUse && !isRecentSwordHit
            ) {

                val distance = EntityUtil.getDistanceNoY(mc.thePlayer, opponent())

                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("Rod hit detected via opponent hurtTime - opponent hurtTime: $opponentCurrentHurtTime, distance: $distance, time since rod: ${currentTime - lastRodUseTime}ms, sword hit excluded")
                }

                // Update last rod hit time
                lastRodHitTime = currentTime

                // Stop retreat when rod hits
                if (shouldRetreatUntilRodHit) {
                    shouldRetreatUntilRodHit = false
                    lastRetreatEndTime = System.currentTimeMillis()  // Record retreat end time for cooldown
                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtil.combatInfo("Rod hit detected - stopping retreat, cooldown started")
                    }
                }

                // W-Tap logic for rod hit - only when distance < 4.0 blocks and not during rod jump
                if (!tapping && CatDueller.config?.enableWTap == true && distance < 4.0f && !rodHitNeedJump) {
                    tapping = true
                    wTapStartTime = System.currentTimeMillis()
                    val delay = CatDueller.config?.wTapDelay ?: 100

                    TimerUtil.setTimeout(fun() {
                        val dur = 300  // Rod W-Tap duration

                        // Start W-tap (stop forward movement)
                        Movement.stopForward()

                        if (CatDueller.config?.combatLogs == true) {
                            ChatUtil.combatInfo("Rod W-Tap started - will recover in ${dur}ms or when hurt")
                        }

                        // Schedule recovery with cancellable timer
                        wTapRecoveryTimer = java.util.Timer("WTapRecovery", true)
                        wTapRecoveryTimer?.schedule(object : java.util.TimerTask() {
                            override fun run() {
                                if (tapping) {
                                    Movement.startForward()
                                    tapping = false
                                    wTapStartTime = 0L
                                    wTapRecoveryTimer = null

                                    if (CatDueller.config?.combatLogs == true) {
                                        ChatUtil.combatInfo("Rod W-Tap completed normally after ${dur}ms")
                                    }
                                }
                            }
                        }, dur.toLong())

                    }, delay)
                } else if (CatDueller.config?.combatLogs == true && CatDueller.config?.enableWTap == true) {
                    val reason = when {
                        distance >= 4f -> "distance: $distance (>= 4 blocks)"
                        rodHitNeedJump -> "rod jump active"
                        else -> "unknown reason"
                    }
                    ChatUtil.combatInfo("Rod W-Tap skipped - $reason")
                }
                combo--
            } else if (opponentCurrentHurtTime > opponentLastHurtTime && opponentCurrentHurtTime > 0) {
                // Debug why rod hit wasn't detected
                if (CatDueller.config?.combatLogs == true && shouldRetreatUntilRodHit) {
                    val reason = when {
                        !isRecentRodUse -> "no recent rod use (${currentTime - lastRodUseTime}ms ago)"
                        else -> "recent sword hit (${currentTime - lastSwordHitTime}ms ago)"
                    }
                    ChatUtil.combatInfo("Opponent hurt but rod hit not detected - $reason")
                }
            }

            opponentLastHurtTime = opponentCurrentHurtTime

            // Track opponent bow drawing time for 700ms blocking
            if (!lastTickOpponentDrawingBow && opponentIsDrawingBow) {
                // Opponent just started drawing bow
                opponentBowStartTime = currentTime
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("Opponent started drawing bow - tracking time for 700ms block")
                }
            } else if (lastTickOpponentDrawingBow && !opponentIsDrawingBow) {
                // Opponent stopped drawing bow (fired arrow)
                opponentBowStartTime = 0L  // Reset bow start time
                opponentBowStopTime = System.currentTimeMillis()  // Record when opponent stopped drawing bow

                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("Opponent stopped drawing bow - recorded stop time for Dodge Arrow delay")
                }
            }

            // Dodge Arrow logic
            val distance = EntityUtil.getDistanceNoY(mc.thePlayer, opponent())
            val cooldownRemaining = if (lastDodgeArrowEndTime > 0) {
                500 - (currentTime - lastDodgeArrowEndTime)
            } else {
                0
            }
            val isOnCooldown = cooldownRemaining > 0

            val shouldDodgeArrow = CatDueller.config?.dodgeArrow == true &&
                    opponentIsDrawingBow &&
                    distance > 10f &&
                    !isOnCooldown

            // Check if we should stop dodging with delay
            val shouldStopDodging = if (isDodgingArrow) {
                val dodgeArrowDuration = currentTime - dodgeArrowStartTime

                // Check for 3-second timeout first
                if (dodgeArrowDuration >= 3000) {
                    true
                } else if (!opponentIsDrawingBow && distance > 6f && opponentBowStopTime > 0) {
                    // If opponent stopped drawing bow and distance > 6, wait 500ms before stopping
                    currentTime - opponentBowStopTime >= 500
                } else if (!opponentIsDrawingBow && distance <= 6f) {
                    // If distance <= 6, stop immediately when opponent stops drawing
                    true
                } else if (distance <= 6f) {
                    // If distance becomes <= 6, stop immediately regardless of bow state
                    true
                } else {
                    // Continue dodging if opponent is still drawing bow
                    false
                }
            } else {
                false
            }

            if (shouldDodgeArrow && !isDodgingArrow) {
                // Check if we should complete our current bow usage before dodging
                val shouldCompleteOurBow = Mouse.isUsingProjectile() && isUsingBow &&
                        (System.currentTimeMillis() - ourBowStartTime) > 500 // If we've been drawing for more than 500ms

                if (shouldCompleteOurBow) {
                    // Delay dodge arrow until we finish our bow shot
                    if (CatDueller.config?.combatLogs == true) {
                        val drawTime = System.currentTimeMillis() - ourBowStartTime
                        ChatUtil.combatInfo("Dodge Arrow: Delaying dodge - completing our bow shot (drawn for ${drawTime}ms)")
                    }
                    // Don't start dodging yet, let the bow shot complete
                } else {
                    // Start dodging arrows immediately
                    isDodgingArrow = true
                    Mouse.setDodgingArrow(true)
                    dodgeArrowStartTime = System.currentTimeMillis()  // Record start time for timeout

                    // Stop all strafe movements
                    Combat.stopRandomStrafe()
                    Movement.clearLeftRight()
                    hurtStrafeDirection = 0  // Cancel any active hurt strafe

                    // Clear forward/backward movement to prevent interference from bow/rod
                    Movement.stopForward()
                    Movement.stopBackward()

                    // Stop any ongoing bow or rod usage
                    if (Mouse.isUsingProjectile()) {
                        Mouse.setUsingProjectile(false)
                        if (isUsingBow) {
                            isUsingBow = false
                            bowCounterAttackActive = false
                            if (CatDueller.config?.combatLogs == true) {
                                val drawTime = System.currentTimeMillis() - ourBowStartTime
                                ChatUtil.combatInfo("Dodge Arrow: Interrupted bow usage (drawn for ${drawTime}ms < 500ms)")
                            }
                        }
                        // Interrupt rod usage
                        immediateRetractRod()
                        if (CatDueller.config?.combatLogs == true) {
                            ChatUtil.combatInfo("Dodge Arrow: Interrupted rod usage")
                        }
                    }

                    // Switch to sword/shovel for safety
                    if (!Mouse.lClickDown) {
                        Inventory.setInvItem(meleeWeapon())
                    }
                    Mouse.rClickUp()

                    // Start movement switching timer (300-1000ms intervals)
                    startDodgeMovementTimer()

                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtil.combatInfo("Dodge Arrow: Started dodging - opponent drawing bow at distance $distance, all strafe cancelled")
                    }
                }
            } else if (shouldStopDodging) {
                // Stop dodging arrows
                isDodgingArrow = false
                Mouse.setDodgingArrow(false)

                // Record dodge arrow end time for cooldown
                lastDodgeArrowEndTime = System.currentTimeMillis()

                // Cancel movement timer
                dodgeMovementTimer?.cancel()
                dodgeMovementTimer = null

                // Resume normal movement
                Movement.stopBackward()
                if (!tapping) {
                    Movement.startForward()
                }

            }

            // Start arrow blocking after 700ms of opponent drawing bow
            // But only if distance is greater than 6 blocks (close range doesn't need blocking)
            if (CatDueller.config?.enableArrowBlocking == true && opponentIsDrawingBow && opponentBowStartTime > 0 &&
                currentTime - opponentBowStartTime >= 500 && !Mouse.isBlockingArrow() && distance > 6f
            ) {

                // Start arrow blocking - this will prevent other actions
                Mouse.setBlockingArrow(true)

                // Arrow block: interrupt any ongoing rod usage and switch to sword
                if (Mouse.isUsingProjectile() || this.rodRetractTimeout != null) {
                    // Interrupt rod usage for arrow blocking
                    immediateRetractRod()
                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtil.combatInfo("Interrupted rod usage for arrow blocking")
                    }
                }

                // Calculate block duration based on distance: distance(blocks) x 20ms
                val distance = EntityUtil.getDistanceNoY(mc.thePlayer, opponent())
                (distance * 20).toInt().coerceIn(200, 2000)  // Min 200ms, Max 2000ms

                // Check if we're currently using bow or rod (right-click active)
                val wasUsingProjectile = Mouse.isUsingProjectile() || Mouse.rClickDown

                // Ensure we have a sword for blocking - switch without releasing right click if we were using projectile
                if (!Mouse.lClickDown && (mc.thePlayer.heldItem == null || !mc.thePlayer.heldItem.unlocalizedName.lowercase()
                        .contains(meleeWeapon()))
                ) {
                    Inventory.setInvItem(meleeWeapon())

                    if (wasUsingProjectile) {
                        // If we were using projectile, continue holding right click for seamless blocking
                        if (!Mouse.rClickDown) {
                            Mouse.startRightClick()  // Start holding right click indefinitely
                        }
                        // If already holding right click, just continue holding

                        if (CatDueller.config?.combatLogs == true) {
                            ChatUtil.combatInfo("Seamless transition from projectile to sword blocking (no right-click release)")
                        }
                    } else {
                        // Not using projectile, start fresh block
                        Mouse.rClickUp()  // Release any ongoing right click
                        Mouse.startRightClick()  // Start holding right click indefinitely
                    }
                } else {
                    // Already have sword, start blocking
                    if (!wasUsingProjectile) {
                        Mouse.rClickUp()  // Release any ongoing right click only if not using projectile
                    }
                    if (!Mouse.rClickDown) {
                        Mouse.startRightClick()  // Start holding right click indefinitely
                    }
                }

                // Note: Block duration will be managed by checking opponent bow state each tick
                // No setTimeout for ending block - it will end when opponent stops drawing bow

                if (CatDueller.config?.combatLogs == true) {
                    val transitionType = if (wasUsingProjectile) "seamless" else "fresh"
                    ChatUtil.combatInfo(
                        "Started blocking arrow after 700ms draw time (${transitionType} transition, distance: ${
                            String.format(
                                "%.1f",
                                distance
                            )
                        } blocks) - will block until opponent stops drawing"
                    )
                }
            } else if (CatDueller.config?.enableArrowBlocking == true && opponentIsDrawingBow && opponentBowStartTime > 0 &&
                currentTime - opponentBowStartTime >= 500 && !Mouse.isBlockingArrow() && distance <= 5f
            ) {
                // Debug: explain why blocking is not started at close range
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo(
                        "Arrow blocking skipped - distance too close (${
                            String.format(
                                "%.1f",
                                distance
                            )
                        } blocks ≤ 6)"
                    )
                }
            }

            // Check if opponent has been drawing bow for more than 1.2 seconds - stop blocking if so
            // But don't interfere if we're using rod or bow
            if (Mouse.isBlockingArrow() && opponentIsDrawingBow && opponentBowStartTime > 0 &&
                currentTime - opponentBowStartTime > 1200 && !blockingEndScheduled &&
                !Mouse.isUsingProjectile()
            ) {  // Don't stop blocking if we're using rod/bow

                blockingEndScheduled = true  // Prevent multiple scheduling

                // Stop blocking immediately when opponent draws bow too long
                Mouse.setBlockingArrow(false)
                Mouse.rClickUp()  // Release right click immediately
                blockingEndScheduled = false  // Reset flag

                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("Arrow blocking ended - opponent drawing bow too long (${currentTime - opponentBowStartTime}ms > 1200ms)")
                }
            } else if (Mouse.isBlockingArrow() && opponentIsDrawingBow && opponentBowStartTime > 0 &&
                currentTime - opponentBowStartTime > 1200 && Mouse.isUsingProjectile()
            ) {
                // Debug: explain why timeout is skipped due to rod/bow usage
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("Arrow blocking timeout skipped - currently using rod/bow")
                }
            }

            // Check if distance became too close (≤6 blocks) - stop blocking if so
            if (Mouse.isBlockingArrow() && distance <= 6f && !blockingEndScheduled) {
                blockingEndScheduled = true  // Prevent multiple scheduling

                // Stop blocking immediately when distance is too close
                Mouse.setBlockingArrow(false)
                Mouse.rClickUp()  // Release right click immediately
                blockingEndScheduled = false  // Reset flag

                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo(
                        "Arrow blocking ended - distance too close (${
                            String.format(
                                "%.1f",
                                distance
                            )
                        } blocks ≤ 6)"
                    )
                }
            }

            // Check if we should stop blocking when opponent stops drawing bow
            // Use distance-based delay to account for arrow flight time
            if (Mouse.isBlockingArrow() && !opponentIsDrawingBow && !blockingEndScheduled) {
                // Calculate delay based on distance: longer distance = longer arrow flight time
                val distance = EntityUtil.getDistanceNoY(mc.thePlayer, opponent())
                val flightTimeDelay = when {
                    distance <= 5f -> 100   // Very close: 100ms delay
                    distance <= 10f -> 200  // Close: 200ms delay
                    distance <= 15f -> 300  // Medium: 300ms delay
                    distance <= 20f -> 400  // Far: 400ms delay
                    else -> 500             // Very far: 500ms delay
                }

                blockingEndScheduled = true  // Prevent multiple scheduling

                TimerUtil.setTimeout({
                    if (Mouse.isBlockingArrow()) {  // Double check we're still blocking
                        Mouse.setBlockingArrow(false)
                        Mouse.rClickUp()  // Release right click after delay
                        blockingEndScheduled = false  // Reset flag

                        if (CatDueller.config?.combatLogs == true) {
                            ChatUtil.combatInfo(
                                "Arrow blocking ended after ${flightTimeDelay}ms delay (distance: ${
                                    String.format(
                                        "%.1f",
                                        distance
                                    )
                                } blocks)"
                            )
                        }
                    }
                }, flightTimeDelay)

                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo(
                        "Opponent stopped drawing bow - scheduled blocking end in ${flightTimeDelay}ms (distance: ${
                            String.format(
                                "%.1f",
                                distance
                            )
                        } blocks)"
                    )
                }
            }

            // Update previous tick state for next comparison
            lastTickOpponentDrawingBow = opponentIsDrawingBow
        }

        if (mc.thePlayer != null && opponent() != null) {
            // Then check for block in front (always check, even when dodging)
            if ((WorldUtil.blockInFront(mc.thePlayer, 1f, 0.5f) != Blocks.air || WorldUtil.blockInFront(mc.thePlayer, 2f, 0.5f) != Blocks.air) && WorldUtil.blockInFront(mc.thePlayer, 1f, 2.5f) == Blocks.air && WorldUtil.blockInFront(mc.thePlayer, 2f, 2.5f) == Blocks.air && WorldUtil.blockInFront(mc.thePlayer, 3f, 2.5f) == Blocks.air && WorldUtil.blockInFront(mc.thePlayer, 0.5f, 1.5f) == Blocks.air && mc.thePlayer.onGround
            ) {
                needJump = true
                Movement.singleJump(RandomUtil.randomIntInRange(150, 250))
            }
        }

        if (opponent() != null && mc.theWorld != null && mc.thePlayer != null) {
            if (!mc.thePlayer.isSprinting) {
                Movement.startSprinting()
            }

            val distance = EntityUtil.getDistanceNoY(mc.thePlayer, opponent())

            // Track opponent approach for movement control
            if (lastOpponentDistance > 0f) {
                // Check if opponent is approaching (getting closer)
                isOpponentApproaching = distance < lastOpponentDistance - 0.1f  // 0.1 block threshold to avoid noise
            }

            // Movement control based on distance and opponent approach
            val timeSinceLastRodHit = System.currentTimeMillis() - lastRodHitTime

            // Check if we should pause movement due to opponent approach
            val shouldPauseMovement = distance in 7f..11f && isOpponentApproaching

            // Check if we should resume movement
            val shouldResumeMovement = movementPausedForApproach && (
                    distance !in 7f..11f || !isOpponentApproaching || timeSinceLastRodHit < 1000  // Resume if rod hit within 1 second
                    )

            if (shouldPauseMovement && !movementPausedForApproach) {
                // Start pausing movement
                movementPausedForApproach = true
                Movement.stopForward()
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo(
                        "Paused forward movement - opponent approaching at distance ${
                            String.format(
                                "%.1f",
                                distance
                            )
                        }"
                    )
                }
            } else if (shouldResumeMovement) {
                // Resume movement
                movementPausedForApproach = false
                Movement.startForward()
                if (CatDueller.config?.combatLogs == true) {
                    val reason = when {
                        distance < 7f -> "distance too close (${String.format("%.1f", distance)} < 7)"
                        distance > 11f -> "distance too far (${String.format("%.1f", distance)} > 11)"
                        !isOpponentApproaching -> "opponent not approaching"
                        timeSinceLastRodHit < 1000 -> "rod hit recently (${timeSinceLastRodHit}ms ago)"
                        else -> "unknown reason"
                    }
                    ChatUtil.combatInfo("Resumed forward movement - $reason")
                }
            }

            // Update last distance for next tick comparison
            lastOpponentDistance = distance

            // Check for opponent strafe direction change and retract rod
            if (this.rodRetractTimeout != null) {
                // Rod is currently active
                val currentOpponentStrafeDirection = opponentStrafeDirection
                
                // Check if opponent changed strafe direction (any change: left/none/right)
                val strafeDirectionChanged = lastOpponentStrafeDirection != currentOpponentStrafeDirection
                
                if (strafeDirectionChanged) {
                    if (CatDueller.config?.combatLogs == true) {
                        val oldDir = when (lastOpponentStrafeDirection) {
                            -1 -> "Left"
                            0 -> "None"
                            1 -> "Right"
                            else -> "Unknown"
                        }
                        val newDir = when (currentOpponentStrafeDirection) {
                            -1 -> "Left"
                            0 -> "None"
                            1 -> "Right"
                            else -> "Unknown"
                        }
                        ChatUtil.combatInfo("Opponent strafe direction changed: $oldDir -> $newDir, retracting rod (will auto re-throw)")
                    }
                    
                    // Immediately retract the current rod
                    // The rod logic will automatically re-throw on next tick if still in range
                    immediateRetractRod()
                }
                
                // Update last strafe direction
                lastOpponentStrafeDirection = currentOpponentStrafeDirection
            } else {
                // Rod is not active, just update the tracking
                lastOpponentStrafeDirection = opponentStrafeDirection
            }

            // Check if rod should be immediately retracted due to close distance
            // Only retract non-defensive rods due to distance
            if (distance < 3.0f && this.rodRetractTimeout != null && !this.isDefensiveRod) {
                immediateRetractRod()
            }

            if (distance < (CatDueller.config?.maxDistanceLook ?: 150)) {
                Mouse.startTracking()
            } else {
                Mouse.stopTracking()
            }

            if (CatDueller.config?.holdLeftClick == true) {
                // Hold Left Click mode: Distance-based control only (no crosshair check)
                val maxAttackDistance = CatDueller.config?.maxDistanceAttack ?: 5
                val hasSword =
                    mc.thePlayer.heldItem != null && mc.thePlayer.heldItem.unlocalizedName.lowercase().contains(meleeWeapon())
                val inRange = distance <= maxAttackDistance

                // Add delay after weapon switching to avoid immediate attack
                val timeSinceWeaponSwitch = System.currentTimeMillis() - lastWeaponSwitchTime
                val weaponSwitchDelay = 200  // 200ms delay after weapon switch
                val canAttackAfterSwitch = timeSinceWeaponSwitch > weaponSwitchDelay

                val newShouldHoldLeftClick = inRange && hasSword && canAttackAfterSwitch

                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("HoldLeftClick - Distance: $distance/$maxAttackDistance, HasSword: $hasSword, InRange: $inRange, CanAttack: $canAttackAfterSwitch (${timeSinceWeaponSwitch}ms), Should: $newShouldHoldLeftClick, Current: $shouldHoldLeftClick")
                }

                if (newShouldHoldLeftClick != shouldHoldLeftClick) {
                    shouldHoldLeftClick = newShouldHoldLeftClick
                    if (shouldHoldLeftClick) {
                        Mouse.startHoldLeftClick()
                        if (CatDueller.config?.combatLogs == true) {
                            ChatUtil.combatInfo("Started hold left click")
                        }
                    } else {
                        Mouse.stopHoldLeftClick()
                        if (CatDueller.config?.combatLogs == true) {
                            ChatUtil.combatInfo("Stopped hold left click")
                        }
                    }
                }
            } else {
                // Normal mode: Use shouldStartAttacking with crosshair checks
                if (shouldStartAttacking(distance)) {
                    if (mc.thePlayer.heldItem != null && mc.thePlayer.heldItem.unlocalizedName.lowercase()
                            .contains(meleeWeapon())
                    ) {
                        Mouse.startLeftAC()  // Start continuous attacking, hit select will handle cancellation
                    }
                } else {
                    Mouse.stopLeftAC()
                }
            }

            // Random swing: when distance > 20, look down 20° and randomly swing sword or use rod (lowest priority)
            if (distance > 20f && !Mouse.isUsingProjectile() && !Mouse.isBlockingArrow() && !Mouse.isDodgingArrow() && !Mouse.isRunningAway() && !Mouse.isUsingPotion() && !Mouse.isUsingGap() && !Mouse.isPlacingWater() && !Mouse.isPlacingPlank() && !Mouse.isPlacingBlockAtFeet() && !Mouse.lClickDown) {
                if (!isRandomSwing) {
                    isRandomSwing = true
                    Mouse.setBreakingBlock(true, 20f)
                    Inventory.setInvItem(meleeWeapon())
                }
                val now = System.currentTimeMillis()
                if (now - lastRandomSwing > RandomUtil.randomIntInRange(500, 1000)) {
                    lastRandomSwing = now
                    val isFisherman = (CatDueller.config?.blitzKit ?: 0) == 0
                    if (!isFisherman || RandomUtil.randomBool()) {
                        if (mc.thePlayer?.heldItem == null || !mc.thePlayer.heldItem.unlocalizedName.lowercase().contains(meleeWeapon())) {
                            Inventory.setInvItem(meleeWeapon())
                        }
                        mc.thePlayer?.swingItem()
                    } else {
                        useRodWithTracking(false)
                    }
                }
            } else if (isRandomSwing) {
                isRandomSwing = false
                Mouse.setBreakingBlock(false)
                Inventory.setInvItem(meleeWeapon())
            }

            if (distance > 11) {
                // Use opponentIsDrawingBow for more accurate dodge detection
                if (opponentIsDrawingBow) {
                    isDodging = true
                    if (!EntityUtil.entityFacingAway(
                            mc.thePlayer,
                            opponent()!!
                        ) && !needJump && !rodHitNeedJump && !isDodgingArrow
                    ) {
                        Movement.stopJumping()
                        if (CatDueller.config?.combatLogs == true) {
                            ChatUtil.combatInfo("Dodge: Opponent drawing bow - stopping jump")
                        }
                    } else if (!isDodgingArrow) {
                        Movement.startJumping()
                        if (CatDueller.config?.combatLogs == true) {
                            ChatUtil.combatInfo("Dodge: Opponent drawing bow - continuing jump (facing away or needJump or rodHitNeedJump)")
                        }
                    } else {
                        // Dodge Arrow is active - stop jumping
                        if (!needJump && !rodHitNeedJump) {
                            Movement.stopJumping()
                        }
                        if (CatDueller.config?.combatLogs == true) {
                            ChatUtil.combatInfo("Dodge: Stopping jump - Dodge Arrow active")
                        }
                    }
                } else if (!isDodgingArrow) {
                    Movement.startJumping()
                    isDodging = false
                } else {
                    // Dodge Arrow is active - stop jumping
                    if (!needJump && !rodHitNeedJump) {
                        Movement.stopJumping()
                    }
                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtil.combatInfo("Dodge: Stopping jump - Dodge Arrow active (no opponent bow)")
                    }
                }
            } else {
                if ((needJump || rodHitNeedJump || EntityUtil.entityFacingAway(
                        mc.thePlayer,
                        opponent()!!
                    )) && !isDodgingArrow
                ) {
                    Movement.startJumping()
                } else if (isDodgingArrow) {
                    // Dodge Arrow is active - stop jumping
                    if (!needJump && !rodHitNeedJump) {
                        Movement.stopJumping()
                    }
                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtil.combatInfo("Dodge: Stopping jump - Dodge Arrow active (close range)")
                    }
                } else {
                    if (!needJump && !rodHitNeedJump) {
                        Movement.stopJumping()
                    }
                }
                isDodging = false
            }

            // Check if post-bow strafe should end
            if (postBowStrafeActive && System.currentTimeMillis() >= postBowStrafeEndTime) {
                postBowStrafeActive = false
                Movement.clearLeftRight()
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("Post-bow strafe ended")
                }
            }

            val movePriority = arrayListOf(0, 0)
            var clear = false
            var randomStrafe = false

            // Height advantage distance keeping: if opponent is >3 blocks above us, keep 28-33 distance
            val heightDiff = opponent()!!.posY - mc.thePlayer.posY
            val opponentHighAbove = heightDiff > 3.0
            if (opponentHighAbove && !isDodgingArrow) {
                // Check if world border is behind us within 3 blocks — if so, force forward
                val border = mc.theWorld.worldBorder
                val behindVec = EntityUtil.get2dLookVec(mc.thePlayer).rotateYaw(180f)
                val behindX = mc.thePlayer.posX + behindVec.xCoord * 3.0
                val behindZ = mc.thePlayer.posZ + behindVec.zCoord * 3.0
                val borderBehind = behindX - border.minX() < 3.0 || border.maxX() - behindX < 3.0 ||
                        behindZ - border.minZ() < 3.0 || border.maxZ() - behindZ < 3.0

                if (borderBehind) {
                    // Border behind — force forward regardless of distance
                    Movement.stopBackward()
                    if (!tapping) Movement.startForward()
                } else if (distance < 28f) {
                    Movement.stopForward()
                    Movement.startBackward()
                } else if (distance > 33f) {
                    Movement.stopBackward()
                    if (!tapping) Movement.startForward()
                } else {
                    Movement.stopForward()
                    Movement.stopBackward()
                }
                if (!needJump && !rodHitNeedJump) {
                    Movement.stopJumping()
                }
                randomStrafe = true
            }

            // Retreat logic: low health + medium distance - retreat until rod hits once
            // Start retreat when: low health + health disadvantage + medium distance + cooldown expired + enabled in config
            // Allow re-triggering retreat after rod hit if player moves back into retreat range
            val currentTime = System.currentTimeMillis()
            val retreatCooldownExpired = currentTime - lastRetreatEndTime > 3000  // 3 second cooldown
            val enableRetreat = CatDueller.config?.enableRetreat ?: true

            val shouldStartRetreat = enableRetreat &&
                    mc.thePlayer.health < opponent()!!.health &&
                    distance in 7f..11f &&
                    !shouldRetreatUntilRodHit &&  // Only start if not already retreating
                    retreatCooldownExpired  // Must wait for cooldown

            // Continue retreat until rod hits (or conditions no longer met)
            // IMPORTANT: Only continue retreat if still in 7-10 block range (same as start condition)
            val shouldContinueRetreat = enableRetreat &&
                    shouldRetreatUntilRodHit &&
                    mc.thePlayer.health < 16f &&
                    mc.thePlayer.health < opponent()!!.health &&
                    distance in 7f..11f  // Must stay in retreat range

            // Defensive rod retreat: retreat when using defensive rod at close distance
            val shouldDefensiveRodRetreat = this.isDefensiveRod && Mouse.isUsingProjectile() && distance < 6f

            // Start retreat if conditions are met
            if (shouldStartRetreat) {
                shouldRetreatUntilRodHit = true
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("Starting retreat until rod hit - Low health (${mc.thePlayer.health}) vs opponent (${opponent()!!.health}), distance: $distance, cooldown: ${currentTime - lastRetreatEndTime}ms")
                }
            } else if (mc.thePlayer.health < 16f && mc.thePlayer.health < opponent()!!.health && distance in 7f..10f && !shouldRetreatUntilRodHit) {
                // Debug: retreat blocked by cooldown or disabled in config
                if (CatDueller.config?.combatLogs == true) {
                    if (!enableRetreat) {
                        ChatUtil.combatInfo("Retreat blocked - disabled in config")
                    } else if (!retreatCooldownExpired) {
                        val remainingCooldown = 3000 - (currentTime - lastRetreatEndTime)
                        ChatUtil.combatInfo("Retreat blocked by cooldown - ${remainingCooldown}ms remaining")
                    }
                }
            }

            if ((shouldContinueRetreat || shouldDefensiveRodRetreat) && !opponentHighAbove) {
                Movement.stopForward()
                Movement.startBackward()

                if (CatDueller.config?.combatLogs == true) {
                    val retreatReason = if (shouldContinueRetreat) {
                        "Continuing retreat until rod hit - Low health (${mc.thePlayer.health}) vs opponent (${opponent()!!.health}), distance: $distance"
                    } else {
                        "Defensive rod retreat - Using defensive rod at close distance: $distance"
                    }
                    ChatUtil.combatInfo(retreatReason)
                }
            } else if (!isDodgingArrow && !opponentHighAbove) {
                Movement.stopBackward()  // Stop retreating when not needed

                // Reset retreat state if conditions no longer met OR if distance is outside retreat range
                // BACKUP: Also cancel retreat if we recently used rod and opponent was hurt (backup rod hit detection)
                val currentTime = System.currentTimeMillis()
                val backupRodHitDetection = shouldRetreatUntilRodHit &&
                        (currentTime - lastRodUseTime < 4000) &&
                        (opponent()!!.hurtTime > 0) &&
                        (currentTime - lastSwordHitTime > 500)  // Make sure it's not a sword hit

                if (shouldRetreatUntilRodHit && (!enableRetreat || mc.thePlayer.health >= 16f || mc.thePlayer.health >= opponent()!!.health || distance < 7f || distance > 10f || backupRodHitDetection)) {
                    shouldRetreatUntilRodHit = false
                    lastRetreatEndTime = currentTime  // Record retreat end time for cooldown
                    if (CatDueller.config?.combatLogs == true) {
                        val reason = when {
                            !enableRetreat -> "retreat disabled in config"
                            mc.thePlayer.health >= 16f -> "health recovered"
                            mc.thePlayer.health >= opponent()!!.health -> "health advantage gained"
                            distance < 7f -> "moved too close (< 7 blocks)"
                            distance > 10f -> "moved too far (> 10 blocks)"
                            backupRodHitDetection -> "backup rod hit detection (opponent hurt: ${opponent()!!.hurtTime}, rod ${currentTime - lastRodUseTime}ms ago)"
                            else -> "conditions no longer met"
                        }
                        ChatUtil.combatInfo("Retreat cancelled - $reason, cooldown started")
                    }
                }

                if (distance < 0.5 || (distance < 2.5 && combo >= 3)) {
                    Movement.stopForward()
                } else {
                    if (!tapping) {
                        Movement.startForward()
                    }
                }
            }

            if (distance < 1.5 && !Mouse.lClickDown && mc.thePlayer.heldItem != null && !mc.thePlayer.heldItem.unlocalizedName.lowercase()
                    .contains(meleeWeapon())
            ) {
                Inventory.setInvItem(meleeWeapon())
                Mouse.rClickUp()
                // Don't start attacking here - let the distance control logic handle it
            }

            // Calculate adjusted rod distances based on prediction ticks bonus
            val predictionTicksBonus = CatDueller.config?.predictionTicksBonus ?: 0
            val opponentActualSpeed = CatDueller.bot?.opponentActualSpeed ?: 0.13f  // Use opponent's actual speed

            val basePredictionDistance = predictionTicksBonus * opponentActualSpeed
            val distanceAdjustment = basePredictionDistance

            // Rod logic - only for Fisherman kit (blitzKit == 0)
            val isFisherman = (CatDueller.config?.blitzKit ?: 0) == 0

            // Adjust rod usage distances based on prediction compensation
            // Extend minimum range when opponent is retreating
            val baseRodDistance1Min = if (opponentIsRetreating) 3.5f else 4.0f
            val rodDistance1Min = baseRodDistance1Min + distanceAdjustment
            val rodDistance1Max = 7.2f + distanceAdjustment
            val rodDistance2Min = 8.5f + distanceAdjustment
            val rodDistance2Max = 10.0f + distanceAdjustment

            // Rod usage variables - only relevant for Fisherman
            var shouldUseDefensiveRod = false
            var shouldUseOffensiveRod = false
            var shouldAvoidRodDueToCloseRangeBow = false

            // Close range + opponent drawing bow: jump to dodge (applies to ALL kits)
            if (distance <= 5f && opponentIsDrawingBow) {
                shouldAvoidRodDueToCloseRangeBow = true
                needJump = true
                Movement.startJumping()
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo(
                        "Jumping - close range (${String.format("%.1f", distance)} blocks ≤ 5) + opponent drawing bow"
                    )
                }
            }

            if (isFisherman) {
                // Check for defensive rod usage (opponent combo >= 3)
                shouldUseDefensiveRod = opponentCombo >= 3 && distance > 3
                // Check for offensive rod usage (distance-based)
                shouldUseOffensiveRod =
                    (distance in rodDistance1Min..rodDistance1Max || distance in rodDistance2Min..rodDistance2Max) &&
                            opponent() != null && !EntityUtil.entityFacingAway(mc.thePlayer, opponent()!!)
            }


            // Debug rod range extension
            if (CatDueller.config?.combatLogs == true && opponentIsRetreating && shouldUseOffensiveRod) {
                ChatUtil.combatInfo(
                    "Extended rod range activated - opponent retreating (min: ${
                        String.format(
                            "%.1f",
                            rodDistance1Min
                        )
                    })"
                )
            }


            if (isFisherman && (shouldUseDefensiveRod || shouldUseOffensiveRod) && !Mouse.isUsingProjectile() && !Mouse.isBlockingArrow() && !shouldAvoidRodDueToCloseRangeBow && !isDodgingArrow && !Mouse.lClickDown) {
                if (CatDueller.config?.combatLogs == true) {
                    val rodType = if (shouldUseDefensiveRod) "defensive" else "offensive"
                    val rangeInfo = if (distance in rodDistance2Min..rodDistance2Max) "Range2" else "Range1"
                    ChatUtil.combatInfo(
                        "Using rod ($rodType, $rangeInfo) at distance ${
                            String.format(
                                "%.2f",
                                distance
                            )
                        }"
                    )
                }
                useRodWithTracking(shouldUseDefensiveRod)  // Pass true if defensive, false if offensive
            } else if ((shouldUseDefensiveRod || shouldUseOffensiveRod) && (shouldAvoidRodDueToCloseRangeBow || isDodgingArrow)) {
                // Debug: explain why rod usage is skipped due to close range + opponent drawing bow or dodge arrow
                if (CatDueller.config?.combatLogs == true) {
                    val rodType = if (shouldUseDefensiveRod) "defensive" else "offensive"
                    val reason = if (shouldAvoidRodDueToCloseRangeBow) {
                        "close range (${String.format("%.1f", distance)} blocks ≤ 6) + opponent drawing bow"
                    } else {
                        "dodge arrow active"
                    }
                    ChatUtil.combatInfo("Rod usage skipped ($rodType) - $reason")
                }
            }

            // Smart weapon switching based on distance and situation
            // Combine offensive rod ranges with defensive rod condition
            val inRodRange = if (isFisherman) {
                (distance in rodDistance1Min..rodDistance1Max || distance in rodDistance2Min..rodDistance2Max) || shouldUseDefensiveRod
            } else false
            val shouldHaveSword = distance < 4f || (!inRodRange && distance < 6f)

            // Check if rod is currently in use (don't interrupt rod usage)
            // Also check if we're using projectile even if not holding rod (to prevent weapon switching during rod usage)
            val isRodInUse = Mouse.isUsingProjectile() && (
                    (mc.thePlayer.heldItem != null && mc.thePlayer.heldItem.unlocalizedName.lowercase()
                        .contains("rod")) ||
                            this.rodRetractTimeout != null  // Rod is still active even if we're not holding it
                    )

            if (mc.thePlayer.heldItem != null && !isRodInUse && !Mouse.lClickDown && !isUsingBow) {
                val currentItem = mc.thePlayer.heldItem.unlocalizedName.lowercase()

                // Switch to sword if we should have sword but don't
                if (shouldHaveSword && !currentItem.contains(meleeWeapon())) {
                    Inventory.setInvItem(meleeWeapon())
                    Mouse.rClickUp()
                    // Don't start attacking here - let the distance control logic handle it
                }
                // Switch to rod if we're in rod range but holding bow
                else if (inRodRange && currentItem.contains("bow") && !Mouse.isUsingProjectile()) {
                    Inventory.setInvItem(meleeWeapon())  // Switch to melee first, rod usage will switch to rod when needed
                    Mouse.rClickUp()
                }
            }

            // Debug rod usage protection
            if (CatDueller.config?.combatLogs == true && isRodInUse && shouldHaveSword) {
                ChatUtil.combatInfo("Rod in use - weapon switching blocked until rod completes")
            }

            // Check if opponent is actually drawing bow (not just holding it) to allow our bow usage
            if (opponent() != null && opponentIsDrawingBow) {
                opponentUsedBow = true

                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("Opponent used bow - drawing detected")
                }
            }

            // Bow situations - only for Necromancer kit (blitzKit == 1)
            // Situation 1: Enemy facing away (6-30 blocks) - wait for opponent to fire arrow
            val opponentHoldingBow = opponent()!!.heldItem != null && opponent()!!.heldItem.unlocalizedName.lowercase().contains("bow")
            // Situation 1: Enemy facing away (6-30 blocks) - don't start if opponent holding bow
            val situation1 = !isFisherman && (EntityUtil.entityFacingAway(
                mc.thePlayer,
                opponent()!!
            ) || (opponentIsRetreating)) && distance in 6f..30f &&
                    !opponentHoldingBow
            // Situation 2: Long distance - if opponent holding bow: 28-33 blocks, otherwise: 10-33 blocks
            val situation2Range = if (opponentHoldingBow) 28.0..33.0 else 10.0..33.0
            val situation2 = !isFisherman && distance in situation2Range && !EntityUtil.entityFacingAway(mc.thePlayer, opponent()!!) &&
                    !opponentHoldingBow
            // Situation 3: Low health opponent (< 2 hearts, distance > 8) - don't start if opponent holding bow
            val situation3 = !isFisherman &&
                opponent()!!.health < 4.0f && (distance > 10.0f || (distance > 8.0f && opponentIsRetreating)) &&
                        !opponentHoldingBow
            // Situation 4: Our health lower than opponent's health and distance > 10 - don't start if opponent holding bow
            val situation4 = !isFisherman && mc.thePlayer.health < opponent()!!.health && distance > 10.0f &&
                    !opponentHoldingBow
            // First, check if we should interrupt our bow usage when opponent starts drawing
            // Check if we should interrupt bow usage due to close distance (≤8 blocks)
            if (!isFisherman && isUsingBow && Mouse.isUsingProjectile() && distance <= 8f && !Mouse.lClickDown) {
                // Interrupt bow usage immediately when opponent gets too close
                Mouse.setUsingProjectile(false)
                Inventory.setInvItem(meleeWeapon())
                Mouse.rClickUp()
                isUsingBow = false
                bowCounterAttackActive = false  // Reset bow counter-attack

                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo(
                        "Bow usage interrupted - opponent too close (${
                            String.format(
                                "%.1f",
                                distance
                            )
                        } blocks ≤ 6)"
                    )
                }
            }

            // Spawn egg placement: when we have spawn eggs (Necromancer only)
            // If onlyWhenBowing=true: only when opponent is drawing bow
            // If onlyWhenBowing=false: also when distance < 20
            val onlyWhenBowing = CatDueller.config?.placeMobsOnlyWhenBowing ?: true
            val shouldPlaceEgg = if (onlyWhenBowing) opponentIsDrawingBow else (opponentIsDrawingBow || distance < 20)
            if (CatDueller.config?.placeMobs == true && !isFisherman && shouldPlaceEgg && !isPlacingSpawnEgg) {
                // Try to switch to spawn egg - if found, start placing
                // Try both unlocalizedName variants: "monsterPlacer" (item.monsterPlacer) and "spawn_egg" (registry name)
                if (Inventory.setInvItem("monsterPlacer")) {
                    isPlacingSpawnEgg = true

                    // Stop attacking
                    if (CatDueller.config?.holdLeftClick == true) {
                        Mouse.stopHoldLeftClick()
                    } else {
                        Mouse.stopLeftAC()
                    }

                    Mouse.setPlacingWater(true)  // Look straight down
                    Mouse.startRightClick()  // Hold right click to place

                    if (CatDueller.config?.combatLogs == true) {
                        val reason = if (opponentIsDrawingBow) "opponent drawing bow" else "distance < 20 ($distance)"
                        ChatUtil.combatInfo("Spawn egg: Started placing - $reason")
                    }
                }
            } else if (isPlacingSpawnEgg) {
                // Check if we should continue or stop
                val stillHasEgg = Inventory.setInvItem("monsterPlacer")
                val onlyBowing = CatDueller.config?.placeMobsOnlyWhenBowing ?: true
                val shouldContinue = if (onlyBowing) opponentIsDrawingBow else (opponentIsDrawingBow || distance < 20)
                if (!shouldContinue || !stillHasEgg) {
                    // Stop placing
                    isPlacingSpawnEgg = false
                    Mouse.setPlacingWater(false)
                    Mouse.rClickUp()
                    if (!Mouse.lClickDown) {
                        Inventory.setInvItem(meleeWeapon())
                    }

                    // Resume attacking
                    if (CatDueller.config?.holdLeftClick == true) {
                        Mouse.startHoldLeftClick()
                    } else {
                        Mouse.startLeftAC()
                    }

                    if (CatDueller.config?.combatLogs == true) {
                        val reason = if (!shouldContinue) "condition no longer met" else "no spawn eggs left"
                        ChatUtil.combatInfo("Spawn egg: Stopped placing - $reason")
                    }
                } else {
                    // Continue placing - keep right click held
                    if (!Mouse.rClickDown) {
                        Mouse.startRightClick()
                    }
                }
            }

            val canSeeOpponent = mc.thePlayer.canEntityBeSeen(opponent())

            if ((situation1 || situation2 || situation3 || situation4) && !Mouse.isBlockingArrow() && canSeeOpponent) {
                val bowOnCooldown = lastBowShotTime > 0 &&
                        System.currentTimeMillis() - lastBowShotTime < 500

                val canUseBow = if (bowOnCooldown && !Mouse.isUsingProjectile()) {
                    // Cooldown active - don't start new bow usage
                    if (CatDueller.config?.combatLogs == true) {
                        val remaining = 500 - (System.currentTimeMillis() - lastBowShotTime)
                        ChatUtil.combatInfo("Bow cooldown active - ${remaining}ms remaining (opponent drawing bow)")
                    }
                    false
                } else if (situation1) {
                    // Situation 1: Start bow usage if not already active
                    val canStart =
                        distance > 8 && !Mouse.isUsingProjectile() && shotsFired < maxArrows && !isDodgingArrow

                    if (canStart && !bowCounterAttackActive) {
                        bowCounterAttackActive = true
                        if (CatDueller.config?.combatLogs == true) {
                            ChatUtil.combatInfo("Situation1: Starting bow usage after opponent fired/not drawing")
                        }
                    }

                    canStart || (bowCounterAttackActive && Mouse.isUsingProjectile())
                } else if (situation2) {
                    // Situation 2: Start bow usage if not already active (requires opponentUsedBow)
                    val canStart =
                        distance > 8 && !Mouse.isUsingProjectile() && shotsFired < maxArrows && !isDodgingArrow

                    if (canStart && !bowCounterAttackActive) {
                        bowCounterAttackActive = true
                        if (CatDueller.config?.combatLogs == true) {
                            ChatUtil.combatInfo("Situation2: Starting bow usage after opponent fired/not drawing")
                        }
                    }

                    canStart || (bowCounterAttackActive && Mouse.isUsingProjectile())
                } else if (situation3) {
                    // Situation 3: Start bow usage if not already active
                    val canStart = !Mouse.isUsingProjectile() && shotsFired < maxArrows && !isDodgingArrow

                    if (canStart && !bowCounterAttackActive) {
                        bowCounterAttackActive = true
                        if (CatDueller.config?.combatLogs == true) {
                            ChatUtil.combatInfo("Situation3: Starting bow usage after opponent fired/not drawing")
                        }
                    }

                    canStart || (bowCounterAttackActive && Mouse.isUsingProjectile())
                } else run {
                    // Situation 4: Start bow usage if not already active
                    val canStart = !Mouse.isUsingProjectile() && shotsFired < maxArrows && !isDodgingArrow

                    if (canStart && !bowCounterAttackActive) {
                        bowCounterAttackActive = true
                        if (CatDueller.config?.combatLogs == true) {
                            ChatUtil.combatInfo("Situation4: Starting bow usage after opponent fired/not drawing")
                        }
                    }

                    canStart || (bowCounterAttackActive && Mouse.isUsingProjectile())
                }

                if (CatDueller.config?.combatLogs == true) {
                    if (opponentIsDrawingBow && !bowCounterAttackActive) {
                        ChatUtil.combatInfo("Bow usage waiting - opponent is drawing bow")
                    } else if (isDodgingArrow) {
                        ChatUtil.combatInfo("Bow usage blocked - dodge arrow active")
                    }
                }

                // Necromancer kit: actually use the bow
                if (!isFisherman && canUseBow && !Mouse.lClickDown) {
                    clear = true
                    // Track bow usage start time
                    if (!isUsingBow) {
                        ourBowStartTime = System.currentTimeMillis()
                        isUsingBow = true
                    }

                    if (isUsingBow) {
                        useBow(distance, fun() {
                        shotsFired++
                        isUsingBow = false
                        lastBowShotTime = System.currentTimeMillis()

                        // Start post-bow strafe: random left or right for 1 second (skip during block breaking and 1s after)
                        if (!Mouse.lClickDown && System.currentTimeMillis() - blockBreakEndTime >= 1000) {
                            postBowStrafeActive = true
                            postBowStrafeEndTime = System.currentTimeMillis() + 1000
                            Combat.stopRandomStrafe()
                            Movement.clearLeftRight()
                            val strafeDir = decideRandomStrafeDirection()
                            if (strafeDir == 1) Movement.startLeft() else Movement.startRight()

                            if (CatDueller.config?.combatLogs == true) {
                                val dirName = if (strafeDir == 1) "left" else "right"
                                ChatUtil.combatInfo("Post-bow strafe: moving $dirName for 1s")
                            }
                        }

                        if (bowCounterAttackActive) {
                            bowCounterAttackActive = false
                            if (CatDueller.config?.combatLogs == true) {
                                ChatUtil.combatInfo("Bow counter-attack completed - reset flag")
                            }
                        }

                        // Check if we need to start Dodge Arrow after completing our bow shot
                        val currentDistance = EntityUtil.getDistanceNoY(mc.thePlayer, opponent())
                        val shouldStartDodgeArrow = CatDueller.config?.dodgeArrow == true &&
                                opponentIsDrawingBow &&
                                currentDistance > 6f &&
                                !isDodgingArrow

                        if (shouldStartDodgeArrow) {
                            isDodgingArrow = true
                            Mouse.setDodgingArrow(true)
                            dodgeArrowStartTime = System.currentTimeMillis()
                            Combat.stopRandomStrafe()
                            Movement.clearLeftRight()
                            hurtStrafeDirection = 0
                            Movement.stopForward()
                            Movement.stopBackward()
                            if (!Mouse.lClickDown) {
                                Inventory.setInvItem(meleeWeapon())
                            }
                            Mouse.rClickUp()
                            this@Blitz.startDodgeMovementTimer()
                        }

                        if (CatDueller.config?.holdLeftClick == true) {
                            Mouse.startHoldLeftClick()
                        } else {
                            Mouse.startLeftAC()
                        }
                    })
                    }
                }

            } else {
                if (EntityUtil.entityFacingAway(mc.thePlayer, opponent()!!)) {
                    if (WorldUtil.leftOrRightToPoint(mc.thePlayer, Vec3(0.0, 0.0, 0.0))) {
                        movePriority[0] += 4
                    } else {
                        movePriority[1] += 4
                    }
                } else {
                    if (distance <= 11.0f) {
                        randomStrafe = true
                        if (!needJump && !rodHitNeedJump) {
                            Movement.stopJumping()
                        }

                        if (CatDueller.config?.combatLogs == true) {
                            ChatUtil.combatInfo(
                                "Random strafe activated - distance: ${
                                    String.format(
                                        "%.1f",
                                        distance
                                    )
                                } blocks (<= 11)"
                            )
                        }
                    } else {
                        // Long range (> 15 blocks) - dodge strafe when opponent is drawing bow or has rod
                        if (opponent() != null) {
                            val hasRod =
                                opponent()!!.heldItem != null && opponent()!!.heldItem.unlocalizedName.lowercase()
                                    .contains("rod")

                            // Use opponentIsDrawingBow for more accurate detection
                            if (hasRod || opponentIsDrawingBow) {
                                randomStrafe = true
                                if (!needJump && !rodHitNeedJump) {
                                    Movement.stopJumping()
                                }

                                if (CatDueller.config?.combatLogs == true && opponentIsDrawingBow) {
                                    ChatUtil.combatInfo("Dodge strafe activated - opponent drawing bow at distance ${String.format("%.1f", distance)}")
                                }
                            }
                        }
                    }
                }
            }

            // Wall avoidance: simple wall detection using blockInFront logic (used by all strafe logic)
            fun hasWallInDirection(yaw: Float, distance: Float): Boolean {
                val lookVec = EntityUtil.get2dLookVec(mc.thePlayer).rotateYaw(yaw)
                val checkPos = mc.thePlayer.position.add(lookVec.xCoord * distance, 0.0, lookVec.zCoord * distance)
                val block = mc.theWorld.getBlockState(checkPos).block
                return block != Blocks.air
            }

            // World border detection: check if a direction would be within 3 blocks of border
            fun hasBorderInDirection(yaw: Float, checkDist: Float): Boolean {
                val border = mc.theWorld.worldBorder
                val lookVec = EntityUtil.get2dLookVec(mc.thePlayer).rotateYaw(yaw)
                val checkX = mc.thePlayer.posX + lookVec.xCoord * checkDist
                val checkZ = mc.thePlayer.posZ + lookVec.zCoord * checkDist
                return checkX - border.minX() < 3.0 || border.maxX() - checkX < 3.0 ||
                       checkZ - border.minZ() < 3.0 || border.maxZ() - checkZ < 3.0
            }

            val hasWallOnLeft =
                hasWallInDirection(90f, 1f) || hasWallInDirection(90f, 2f) || hasWallInDirection(90f, 3f) ||
                hasBorderInDirection(90f, 3f)
            val hasWallOnRight =
                hasWallInDirection(-90f, 1f) || hasWallInDirection(-90f, 2f) || hasWallInDirection(-90f, 3f) ||
                hasBorderInDirection(-90f, 3f)
            val hasBorderBehind = hasBorderInDirection(180f, 3f)

            // Hurt strafe logic - only when hurt
            val player = mc.thePlayer
            val currentHurtTime = player?.hurtTime ?: 0

            // Check for hurt strafe activation (at hurtTime = 4, which is 400ms after hit)
            // But disable hurt strafe during Dodge Arrow
            if (currentHurtTime == 4 && CatDueller.config?.hurtStrafe == true && !isDodgingArrow && !Mouse.lClickDown && System.currentTimeMillis() - blockBreakEndTime >= 1000) {
                // Always activate hurt strafe - decide direction randomly
                hurtStrafeDirection = decideRandomStrafeDirection()

                // Auto stop after 400ms
                TimerUtil.setTimeout({
                    hurtStrafeDirection = 0
                }, 400)
            } else if (currentHurtTime == 4 && isDodgingArrow && CatDueller.config?.combatLogs == true) {
                ChatUtil.combatInfo("Hurt strafe blocked - Dodge Arrow active")
            }

            // Check if hurt strafe is active
            val hasActiveHurtStrafe = hurtStrafeDirection != 0 &&
                    opponent() != null &&
                    mc.thePlayer != null &&
                    !isDodgingArrow &&  // Disable hurt strafe during Dodge Arrow
                    !Mouse.lClickDown &&  // Disable hurt strafe during block breaking
                    System.currentTimeMillis() - blockBreakEndTime >= 1000  // Disable hurt strafe 1s after block breaking

            // HURT STRAFE HAS THE HIGHEST PRIORITY - but consider walls and Dodge Arrow
            if (hasActiveHurtStrafe) {
                // Force execute hurt strafe but avoid walls
                Combat.stopRandomStrafe()
                Movement.clearLeftRight()

                // Check if hurt strafe direction would hit a wall
                val wouldHitWall = when (hurtStrafeDirection) {
                    1 -> hasWallOnLeft  // Moving left, check left wall
                    2 -> hasWallOnRight // Moving right, check right wall
                    else -> false
                }

                if (wouldHitWall) {
                    // Reverse hurt strafe direction to avoid wall
                    when (hurtStrafeDirection) {
                        1 -> {
                            Movement.stopLeft()
                            Movement.startRight()
                            if (CatDueller.config?.combatLogs == true) {
                                ChatUtil.combatInfo("Hurt strafe reversed - avoiding left wall")
                            }
                        }

                        2 -> {
                            Movement.stopRight()
                            Movement.startLeft()
                            if (CatDueller.config?.combatLogs == true) {
                                ChatUtil.combatInfo("Hurt strafe reversed - avoiding right wall")
                            }
                        }
                    }
                } else {
                    // Normal hurt strafe execution
                    when (hurtStrafeDirection) {
                        1 -> {
                            Movement.stopRight()
                            Movement.startLeft()
                        }

                        2 -> {
                            Movement.stopLeft()
                            Movement.startRight()
                        }
                    }
                }
            }

            // Wall avoidance priority adjustment (applies to all strafe logic)
            if (hasWallOnLeft && !hasWallOnRight) {
                // Wall on left, prefer right movement
                movePriority[1] += 20  // Higher priority than strafe
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("Wall on left - moving right")
                }
            } else if (hasWallOnRight && !hasWallOnLeft) {
                // Wall on right, prefer left movement
                movePriority[0] += 20  // Higher priority than strafe
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("Wall on right - moving left")
                }
            }

            // Cancel all strafing during block breaking and 1s after
            val blockBreakCooldown = System.currentTimeMillis() - blockBreakEndTime < 1000
            val suppressStrafe = Mouse.lClickDown || blockBreakCooldown
            if (suppressStrafe) {
                Combat.stopRandomStrafe()
                Movement.clearLeftRight()
            }
            // Stop forward movement and jumping during block breaking
            if (Mouse.lClickDown) {
                Movement.stopJumping()
                if (opponent() != null) {
                    val breakDist = EntityUtil.getDistanceNoY(mc.thePlayer, opponent())
                    if (breakDist > 2.5f && (WorldUtil.blockInFront(mc.thePlayer, 4f, 2.5f) != Blocks.air || WorldUtil.blockInFront(mc.thePlayer, 3f, 2.5f) != Blocks.air || WorldUtil.blockInFront(mc.thePlayer, 2f, 2.5f) != Blocks.air || WorldUtil.blockInFront(mc.thePlayer, 1f, 2.5f) != Blocks.air)) {
                        Movement.stopForward()
                    }
                }
            }

            // Check if hurt strafe, Dodge Arrow, post-bow strafe, or block breaking is active - if so, skip handle()
            if (!hasActiveHurtStrafe && !isDodgingArrow && !postBowStrafeActive && !suppressStrafe) {
                handle(clear, randomStrafe, movePriority)
            } else if (isDodgingArrow && CatDueller.config?.combatLogs == true) {
                ChatUtil.combatInfo("Skipping handle() - Dodge Arrow movement takes priority")
            } else if (postBowStrafeActive && CatDueller.config?.combatLogs == true) {
                ChatUtil.combatInfo("Skipping handle() - Post-bow strafe takes priority")
            }
            // If hurt strafe or Dodge Arrow is active, movement is already handled above, skip handle()
        }
    }

    /**
     * Randomly decides strafe direction for hurt strafe.
     *
     * @return 1 for left, 2 for right
     */
    private fun decideRandomStrafeDirection(): Int {
        return if (RandomUtil.randomBool()) 1 else 2
    }

    /**
     * Checks if there's a wall in the forward or backward direction.
     * @param forward True to check forward, false to check backward
     * @return True if there's a wall in that direction
     */
    private fun hasWallInMovementDirection(forward: Boolean): Boolean {
        val player = mc.thePlayer ?: return false
        val yawOffset = if (forward) 0f else 180f
        val lookVec = EntityUtil.get2dLookVec(player).rotateYaw(yawOffset)
        val checkPos = player.position.add(lookVec.xCoord * 1.5, 0.0, lookVec.zCoord * 1.5)
        val block = mc.theWorld.getBlockState(checkPos).block
        return block != Blocks.air
    }

    /**
     * Switches the dodge arrow movement direction based on wall detection.
     */
    private fun switchDodgeMovement() {
        if (!isDodgingArrow) return

        val hasWallForward = hasWallInMovementDirection(true)
        val hasWallBackward = hasWallInMovementDirection(false)

        if (dodgeMovingForward) {
            // Currently moving forward, check if we should switch to backward
            if (hasWallForward && !hasWallBackward) {
                // Wall in front, switch to backward
                Movement.stopForward()
                Movement.startBackward()
                dodgeMovingForward = false

                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("Dodge Arrow: 前方有方塊，切換到後退")
                }
            } else if (!hasWallForward && !hasWallBackward) {
                // No walls, normal switch to backward
                Movement.stopForward()
                Movement.startBackward()
                dodgeMovingForward = false

                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("Dodge Arrow: 正常切換到後退")
                }
            } else if (hasWallForward && hasWallBackward) {
                // Walls in both directions, stay forward but log warning
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("Dodge Arrow: 前後都有方塊，保持前進")
                }
            }
            // If hasWallBackward && !hasWallForward, continue forward (don't switch)
        } else {
            // Currently moving backward, check if we should switch to forward
            if (hasWallBackward && !hasWallForward) {
                // Wall behind, switch to forward
                Movement.stopBackward()
                Movement.startForward()
                dodgeMovingForward = true

                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("Dodge Arrow: 後方有方塊，切換到前進")
                }
            } else if (!hasWallForward && !hasWallBackward) {
                // No walls, normal switch to forward
                Movement.stopBackward()
                Movement.startForward()
                dodgeMovingForward = true

                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("Dodge Arrow: 正常切換到前進")
                }
            } else if (hasWallForward && hasWallBackward) {
                // Walls in both directions, stay backward but log warning
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("Dodge Arrow: 前後都有方塊，保持後退")
                }
            }
            // If hasWallForward && !hasWallBackward, continue backward (don't switch)
        }
    }

    /**
     * Starts the dodge movement timer that switches between forward and backward movement.
     * Timer interval is randomized between 300-1000ms.
     * Includes wall detection to avoid moving into blocks.
     */
    private fun startDodgeMovementTimer() {
        dodgeMovementTimer?.cancel() // Cancel any existing timer

        // Start with initial movement, check for walls first
        val hasWallForward = hasWallInMovementDirection(true)
        val hasWallBackward = hasWallInMovementDirection(false)

        if (dodgeMovingForward && !hasWallForward) {
            Movement.startForward()
        } else if (!dodgeMovingForward && !hasWallBackward) {
            Movement.startBackward()
        } else if (dodgeMovingForward && hasWallForward && !hasWallBackward) {
            // Switch to backward if forward is blocked
            dodgeMovingForward = false
            Movement.startBackward()
            if (CatDueller.config?.combatLogs == true) {
                ChatUtil.combatInfo("Dodge Arrow: 初始前進被阻擋，改為後退")
            }
        } else if (!dodgeMovingForward && hasWallBackward && !hasWallForward) {
            // Switch to forward if backward is blocked
            dodgeMovingForward = true
            Movement.startForward()
            if (CatDueller.config?.combatLogs == true) {
                ChatUtil.combatInfo("Dodge Arrow: 初始後退被阻擋，改為前進")
            }
        } else {
            // Both directions blocked or no walls, proceed with original direction
            if (dodgeMovingForward) {
                Movement.startForward()
            } else {
                Movement.startBackward()
            }
        }

        // Create timer and start immediately
        dodgeMovementTimer = java.util.Timer("DodgeMovementTimer", true)

        // Start first movement switch immediately, then schedule random intervals
        scheduleNextMovementSwitch()
    }

    /**
     * Schedules the next movement switch with a random delay.
     * This creates a recursive scheduling pattern for truly random intervals.
     */
    private fun scheduleNextMovementSwitch() {
        if (!isDodgingArrow || dodgeMovementTimer == null) return

        val nextDelay = RandomUtil.randomIntInRange(300, 1000).toLong()
        dodgeMovementTimer?.schedule(object : java.util.TimerTask() {
            override fun run() {
                if (isDodgingArrow) {
                    switchDodgeMovement()
                    scheduleNextMovementSwitch() // Recursively schedule next switch
                }
            }
        }, nextDelay)
    }


}
