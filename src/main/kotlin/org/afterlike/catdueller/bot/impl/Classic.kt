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
 * Bot implementation for Classic Duels game mode.
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
class Classic : BotBase("/play duels_classic_duel"), Bow, Rod, MovePriority {

    /**
     * Returns the display name of this bot.
     * @return The string "Classic"
     */
    override fun getName(): String {
        return "Classic"
    }

    init {
        setStatKeys(
            mapOf(
                "wins" to "player.stats.Duels.classic_duel_wins",
                "losses" to "player.stats.Duels.classic_duel_losses",
                "ws" to "player.stats.Duels.current_classic_winstreak",
            )
        )
    }

    /** Number of arrows fired this game. */
    var shotsFired = 0

    /** Maximum arrows allowed per game. */
    var maxArrows = 5

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

    /** Flag indicating arrow dodging is currently active. */
    private var isDodgingArrow = false

    /** Timer for switching between forward and backward movement during arrow dodge. */
    private var dodgeMovementTimer: java.util.Timer? = null

    /** Current dodge movement direction: true=forward, false=backward. */
    private var dodgeMovingForward = true

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

    /**
     * Called when the bot joins a game lobby.
     * Handles lobby movement and rotation setup.
     */
    override fun onJoinGame() {
        super.onJoinGame()

        if (CatDueller.config?.lobbyMovement == true) {
            LobbyMovement.generic()
        }
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
        LobbyMovement.stop()
    }

    /**
     * Called when the game ends.
     * Stops all combat actions, cleans up resources, and prepares for next game.
     */
    override fun onGameEnd() {
        super.onGameEnd()

        shotsFired = 0
        shouldHoldLeftClick = false

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

            if (n.contains("sword") && distance < 3) {
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
                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtil.combatInfo("Dodge Arrow: 3秒超時，自動結束閃避")
                    }
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

                    // Switch to sword for safety
                    Inventory.setInvItem("sword")
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

                if (CatDueller.config?.combatLogs == true) {
                    val dodgeArrowDuration = System.currentTimeMillis() - dodgeArrowStartTime
                    val reason = if (dodgeArrowDuration >= 3000) {
                        "3秒超時自動結束"
                    } else if (!opponentIsDrawingBow && distance > 6f && opponentBowStopTime > 0) {
                        "敵人停止拉弓後500ms延遲結束 (距離 > 6)"
                    } else if (!opponentIsDrawingBow && distance <= 6f) {
                        "敵人停止拉弓立即結束 (距離 <= 6)"
                    } else if (distance <= 6f) {
                        "距離變近立即結束 (<= 6格)"
                    } else {
                        "條件不再滿足"
                    }
                    ChatUtil.combatInfo("Dodge Arrow: 停止閃避 - $reason (持續時間: ${dodgeArrowDuration}ms)")
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
                if (mc.thePlayer.heldItem == null || !mc.thePlayer.heldItem.unlocalizedName.lowercase()
                        .contains("sword")
                ) {
                    Inventory.setInvItem("sword")

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

        if (mc.thePlayer != null) {
            // Then check for block in front (always check, even when dodging)
            if ((WorldUtil.blockInFront(mc.thePlayer, 2f, 0.5f) != Blocks.air || WorldUtil.blockInFront(
                    mc.thePlayer,
                    1f,
                    0.5f
                ) != Blocks.air) && mc.thePlayer.onGround
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
                    mc.thePlayer.heldItem != null && mc.thePlayer.heldItem.unlocalizedName.lowercase().contains("sword")
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
                            .contains("sword")
                    ) {
                        Mouse.startLeftAC()  // Start continuous attacking, hit select will handle cancellation
                    }
                } else {
                    Mouse.stopLeftAC()
                }
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
                        Movement.stopJumping()
                        if (CatDueller.config?.combatLogs == true) {
                            ChatUtil.combatInfo("Dodge: Stopping jump - Dodge Arrow active")
                        }
                    }
                } else if (!isDodgingArrow) {
                    Movement.startJumping()
                    isDodging = false
                } else {
                    // Dodge Arrow is active - stop jumping
                    Movement.stopJumping()
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
                    Movement.stopJumping()
                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtil.combatInfo("Dodge: Stopping jump - Dodge Arrow active (close range)")
                    }
                } else {
                    Movement.stopJumping()
                }
                isDodging = false
            }

            val movePriority = arrayListOf(0, 0)
            var clear = false
            var randomStrafe = false

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

            if (shouldContinueRetreat || shouldDefensiveRodRetreat) {
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
            } else if (!isDodgingArrow) {
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

            if (distance < 1.5 && mc.thePlayer.heldItem != null && !mc.thePlayer.heldItem.unlocalizedName.lowercase()
                    .contains("sword")
            ) {
                Inventory.setInvItem("sword")
                Mouse.rClickUp()
                // Don't start attacking here - let the distance control logic handle it
            }

            // Calculate adjusted rod distances based on prediction ticks bonus
            val predictionTicksBonus = CatDueller.config?.predictionTicksBonus ?: 0
            val opponentActualSpeed = CatDueller.bot?.opponentActualSpeed ?: 0.13f  // Use opponent's actual speed

            // Apply counter-strafe multiplier when counter-strafing (both moving away laterally)
            val counterStrafeMultiplier =
                if (isCounterStrafing) (CatDueller.config?.counterStrafeBonus ?: 1.5f) else 1.0f
            val basePredictionDistance = predictionTicksBonus * opponentActualSpeed
            val distanceAdjustment = basePredictionDistance * counterStrafeMultiplier

            if (CatDueller.config?.combatLogs == true && isCounterStrafing) {
                ChatUtil.combatInfo("Counter-strafe detected - applying ${counterStrafeMultiplier}x prediction multiplier")
            }

            // Adjust rod usage distances based on prediction compensation
            // Extend minimum range when opponent is retreating
            val baseRodDistance1Min = if (opponentIsRetreating) 3.5f else 4.0f
            val rodDistance1Min = baseRodDistance1Min + distanceAdjustment
            val rodDistance1Max = 7.2f + distanceAdjustment
            val rodDistance2Min = 8.5f + distanceAdjustment
            val rodDistance2Max = 10.0f + distanceAdjustment


            // Check for defensive rod usage (opponent combo >= 3)
            val shouldUseDefensiveRod = opponentCombo >= 3 && distance > 3
            // Check for offensive rod usage (distance-based)
            val shouldUseOffensiveRod =
                (distance in rodDistance1Min..rodDistance1Max || distance in rodDistance2Min..rodDistance2Max) &&
                        opponent() != null && !EntityUtil.entityFacingAway(mc.thePlayer, opponent()!!)

            // Check if we should avoid rod usage due to close range + opponent drawing bow
            val shouldAvoidRodDueToCloseRangeBow = distance <= 5f && opponentIsDrawingBow

            if (shouldAvoidRodDueToCloseRangeBow) {
                // Start jumping to dodge arrows at close range instead of using rod
                needJump = true
                Movement.startJumping()
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo(
                        "Avoiding rod usage and jumping - close range (${
                            String.format(
                                "%.1f",
                                distance
                            )
                        } blocks ≤ 6) + opponent drawing bow"
                    )
                }
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


            if ((shouldUseDefensiveRod || shouldUseOffensiveRod) && !Mouse.isUsingProjectile() && !Mouse.isBlockingArrow() && !shouldAvoidRodDueToCloseRangeBow && !isDodgingArrow) {
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
            val inRodRange =
                (distance in rodDistance1Min..rodDistance1Max || distance in rodDistance2Min..rodDistance2Max) || shouldUseDefensiveRod
            val shouldHaveSword = distance < 4f || (!inRodRange && distance < 6f)

            // Check if rod is currently in use (don't interrupt rod usage)
            // Also check if we're using projectile even if not holding rod (to prevent weapon switching during rod usage)
            val isRodInUse = Mouse.isUsingProjectile() && (
                    (mc.thePlayer.heldItem != null && mc.thePlayer.heldItem.unlocalizedName.lowercase()
                        .contains("rod")) ||
                            this.rodRetractTimeout != null  // Rod is still active even if we're not holding it
                    )

            if (mc.thePlayer.heldItem != null && !isRodInUse) {
                val currentItem = mc.thePlayer.heldItem.unlocalizedName.lowercase()

                // Switch to sword if we should have sword but don't
                if (shouldHaveSword && !currentItem.contains("sword")) {
                    Inventory.setInvItem("sword")
                    Mouse.rClickUp()
                    // Don't start attacking here - let the distance control logic handle it
                }
                // Switch to rod if we're in rod range but holding bow
                else if (inRodRange && currentItem.contains("bow") && !Mouse.isUsingProjectile()) {
                    Inventory.setInvItem("sword")  // Switch to sword first, rod usage will switch to rod when needed
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

            // Situation 1: Enemy facing away (6-30 blocks) - wait for opponent to fire arrow
            val situation1 = (EntityUtil.entityFacingAway(
                mc.thePlayer,
                opponent()!!
            ) || (opponentIsRetreating)) && distance in 6f..30f &&
                    !opponentIsDrawingBow
            // Situation 2: Long distance (28-33 blocks) - wait for opponent to fire arrow
            val situation2 = distance in 28.0..33.0 && !EntityUtil.entityFacingAway(mc.thePlayer, opponent()!!) &&
                    !opponentIsDrawingBow
            // Situation 3: Low health opponent (< 2 hearts, distance > 8) - wait for opponent to fire arrow
            val situation3 =
                opponent()!!.health < 4.0f && (distance > 10.0f || (distance > 8.0f && opponentIsRetreating)) &&
                        !opponentIsDrawingBow
            // Situation 4: Our health lower than opponent's health and distance > 10 - wait for opponent to fire arrow
            val situation4 = mc.thePlayer.health < opponent()!!.health && distance > 10.0f &&
                    !opponentIsDrawingBow

            // First, check if we should interrupt our bow usage when opponent starts drawing
            // All situations now wait for opponent to fire before starting, so only interrupt non-protected situations
            if (Mouse.isUsingProjectile() && isUsingBow && opponentIsDrawingBow) {
                val bowUsageTime = System.currentTimeMillis() - ourBowStartTime
                val isProtectedSituation = situation3 || situation4

                if (bowUsageTime < 2000 && !isProtectedSituation && !bowCounterAttackActive) {
                    // Interrupt bow usage to focus on dodging - switch to sword and release right click
                    Mouse.setUsingProjectile(false)
                    Inventory.setInvItem("sword")
                    Mouse.rClickUp()
                    isUsingBow = false
                    lastWeaponSwitchTime = System.currentTimeMillis()  // Record weapon switch time

                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtil.combatInfo("Interrupted bow usage to dodge - used for ${bowUsageTime}ms")
                    }
                } else if ((isProtectedSituation || bowCounterAttackActive) && CatDueller.config?.combatLogs == true) {
                    val situationType = when {
                        situation3 -> "situation 3 (low health opponent)"
                        situation4 -> "situation 4 (our health disadvantage)"
                        bowCounterAttackActive -> "bow counter-attack in progress"
                        else -> "protected situation"
                    }
                    ChatUtil.combatInfo("Bow interruption skipped - $situationType")
                }
            }

            // Check if we should interrupt bow usage due to close distance (≤8 blocks)
            if (isUsingBow && Mouse.isUsingProjectile() && distance <= 8f) {
                // Interrupt bow usage immediately when opponent gets too close
                Mouse.setUsingProjectile(false)
                Inventory.setInvItem("sword")
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

            if ((situation1 || situation2 || situation3 || situation4) && !Mouse.isBlockingArrow()) {
                val canUseBow = if (situation1) {
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
                        distance > 8 && !Mouse.isUsingProjectile() && shotsFired < maxArrows && opponentUsedBow && !isDodgingArrow

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

                if (canUseBow) {
                    clear = true
                    // Track bow usage start time
                    if (!isUsingBow) {
                        ourBowStartTime = System.currentTimeMillis()
                        isUsingBow = true
                    }

                    useBow(distance, fun() {
                        shotsFired++
                        isUsingBow = false  // Reset when bow usage completes

                        // Reset bow counter-attack when bow usage completes
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
                            dodgeArrowStartTime = System.currentTimeMillis()  // Record start time for timeout

                            // Stop all strafe movements
                            Combat.stopRandomStrafe()
                            Movement.clearLeftRight()
                            hurtStrafeDirection = 0  // Cancel any active hurt strafe

                            // Clear forward/backward movement to prevent interference from bow/rod
                            Movement.stopForward()
                            Movement.stopBackward()

                            // Switch to sword for safety
                            Inventory.setInvItem("sword")
                            Mouse.rClickUp()

                            // Start movement switching timer
                            startDodgeMovementTimer()

                            if (CatDueller.config?.combatLogs == true) {
                                ChatUtil.combatInfo("Dodge Arrow: Started dodging after completing bow shot - opponent still drawing at distance $currentDistance, all strafe cancelled")
                            }
                        }
                    })
                } else {
                    clear = false
                    if (WorldUtil.leftOrRightToPoint(mc.thePlayer, Vec3(0.0, 0.0, 0.0))) {
                        movePriority[0] += 4
                    } else {
                        movePriority[1] += 4
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
                    // Distance <= 8.8: Always use random strafe regardless of opponent's weapon or state
                    // But disable all strafe during Dodge Arrow
                    if (distance <= 11.0f && !isDodgingArrow) {
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
                                } blocks (≤ 8.8)"
                            )
                        }
                    } else if (distance in 15f..8.8f && !isDodgingArrow) {
                        randomStrafe = true
                    } else if (isDodgingArrow) {
                        // Dodge Arrow is active - stop jumping and disable strafe
                        randomStrafe = false
                        Movement.stopJumping()
                        if (CatDueller.config?.combatLogs == true) {
                            ChatUtil.combatInfo("Random strafe disabled and jumping stopped - Dodge Arrow active")
                        }
                    } else {
                        randomStrafe = false
                        // Dodge strafe when opponent is drawing bow, has rod, or when we're in dodge mode
                        // But disable all strafe during Dodge Arrow
                        if (opponent() != null && !isDodgingArrow) {
                            val hasRod =
                                opponent()!!.heldItem != null && opponent()!!.heldItem.unlocalizedName.lowercase()
                                    .contains("rod")

                            // Use opponentIsDrawingBow for more accurate detection
                            if (hasRod || opponentIsDrawingBow || isDodging) {
                                randomStrafe = true
                                if (distance < 15 && !needJump && !rodHitNeedJump && !isDodgingArrow) {
                                    Movement.stopJumping()
                                } else if (isDodgingArrow) {
                                    // Dodge Arrow is active - stop jumping
                                    Movement.stopJumping()
                                    if (CatDueller.config?.combatLogs == true) {
                                        ChatUtil.combatInfo("Dodge strafe jumping stopped - Dodge Arrow active")
                                    }
                                }

                                if (CatDueller.config?.combatLogs == true && (opponentIsDrawingBow || isDodging)) {
                                    ChatUtil.combatInfo("Dodge strafe activated - opponent drawing bow: $opponentIsDrawingBow, dodging: $isDodging")
                                }
                            }
                        } else if (isDodgingArrow && CatDueller.config?.combatLogs == true) {
                            ChatUtil.combatInfo("All strafe disabled - Dodge Arrow active")
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

            val hasWallOnLeft =
                hasWallInDirection(90f, 1f) || hasWallInDirection(90f, 2f) || hasWallInDirection(90f, 3f)
            val hasWallOnRight =
                hasWallInDirection(-90f, 1f) || hasWallInDirection(-90f, 2f) || hasWallInDirection(-90f, 3f)

            // Hurt strafe logic - only when hurt
            val player = mc.thePlayer
            val currentHurtTime = player?.hurtTime ?: 0

            // Check for hurt strafe activation (at hurtTime = 4, which is 400ms after hit)
            // But disable hurt strafe during Dodge Arrow
            if (currentHurtTime == 4 && CatDueller.config?.hurtStrafe == true && !isDodgingArrow) {
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
                    !isDodgingArrow  // Disable hurt strafe during Dodge Arrow

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

            // Check if hurt strafe or Dodge Arrow is active - if so, skip handle() to avoid being overridden
            if (!hasActiveHurtStrafe && !isDodgingArrow) {
                handle(clear, randomStrafe, movePriority)
            } else if (isDodgingArrow && CatDueller.config?.combatLogs == true) {
                ChatUtil.combatInfo("Skipping handle() - Dodge Arrow movement takes priority")
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
