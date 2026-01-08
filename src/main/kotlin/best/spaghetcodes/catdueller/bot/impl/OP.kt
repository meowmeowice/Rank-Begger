package best.spaghetcodes.catdueller.bot.impl

import best.spaghetcodes.catdueller.CatDueller
import best.spaghetcodes.catdueller.bot.BotBase
import best.spaghetcodes.catdueller.bot.features.*
import best.spaghetcodes.catdueller.bot.player.Combat
import best.spaghetcodes.catdueller.bot.player.Inventory
import best.spaghetcodes.catdueller.bot.player.Mouse
import best.spaghetcodes.catdueller.bot.player.Movement
import best.spaghetcodes.catdueller.utils.client.ChatUtil
import best.spaghetcodes.catdueller.utils.client.TimerUtil
import best.spaghetcodes.catdueller.utils.game.EntityUtil
import best.spaghetcodes.catdueller.utils.game.WorldUtil
import best.spaghetcodes.catdueller.utils.system.RandomUtil
import net.minecraft.init.Blocks
import net.minecraft.util.Vec3

/**
 * Bot implementation for OP Duels game mode.
 *
 * OP Duels features full diamond armor with protection enchantments,
 * along with potions, golden apples, bow, and fishing rod.
 * This bot handles:
 * - Speed and regeneration splash potion usage
 * - Golden apple consumption for healing
 * - Bow combat at long range
 * - Fishing rod tactics for combos
 * - Wall avoidance during combat
 */
class OP : BotBase("/play duels_op_duel"), Bow, Rod, MovePriority, Potion, Gap {

    /**
     * Returns the display name of this bot.
     * @return The string "OP"
     */
    override fun getName(): String {
        return "OP"
    }

    init {
        setStatKeys(
            mapOf(
                "wins" to "player.stats.Duels.op_duel_wins",
                "losses" to "player.stats.Duels.op_duel_losses",
                "ws" to "player.stats.Duels.current_op_winstreak",
            )
        )
    }

    /** Number of arrows fired this game. */
    var shotsFired = 0

    /** Maximum arrows allowed per game. */
    var maxArrows = 20

    /** Damage value for speed splash potion. */
    var speedDamage = 16386

    /** Damage value for regeneration splash potion. */
    var regenDamage = 16385

    /** Number of speed potions remaining. */
    var speedPotsLeft = 2

    /** Number of regeneration potions remaining. */
    var regenPotsLeft = 2

    /** Number of golden apples remaining. */
    var gapsLeft = 6

    /** Timestamp of last speed potion usage. */
    var lastSpeedUse = 0L

    /** Timestamp of last regeneration potion usage. */
    var lastRegenUse = 0L

    /** Timestamp of last potion usage (implements Potion interface). */
    override var lastPotion = 0L

    /** Timestamp of last golden apple usage (implements Gap interface). */
    override var lastGap = 0L

    /** Flag indicating W-tap is currently active. */
    var tapping = false

    /** Timestamp when current W-tap will end. */
    var tappingEndTime = 0L

    /** Current state of hold left click to avoid unnecessary calls. */
    private var shouldHoldLeftClick = false

    /** Flag indicating opponent just fired an arrow. */
    private var opponentJustFiredArrow = false

    /** Timestamp when opponent last fired an arrow. */
    private var lastOpponentArrowFireTime = 0L

    /** Previous tick's opponent bow drawing state. */
    private var lastTickOpponentDrawingBow = false

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

    /** Flag to continue retreating until rod hits opponent once. */
    private var shouldRetreatUntilRodHit = false

    /** Timestamp when retreat last ended for cooldown tracking. */
    private var lastRetreatEndTime = 0L

    /** Timestamp when rod last hit opponent. */
    private var lastRodHitTime = 0L

    /**
     * Called when the game starts.
     * Resets all consumable counts and state variables, then initiates movement.
     */
    override fun onGameStart() {
        super.onGameStart()

        shotsFired = 0
        speedPotsLeft = 2
        regenPotsLeft = 2
        gapsLeft = 6

        lastSpeedUse = 0L
        lastRegenUse = 0L
        lastPotion = 0L
        lastGap = 0L

        tapping = false
        tappingEndTime = 0L

        shouldHoldLeftClick = false

        opponentJustFiredArrow = false
        lastOpponentArrowFireTime = 0L
        lastTickOpponentDrawingBow = false

        rodHitNeedJump = false
        rodHitDistance = 0f
        lastRodUseTime = 0L
        opponentLastHurtTime = 0
        lastSwordHitTime = 0L
        isDodging = false
        shouldRetreatUntilRodHit = false
        lastRetreatEndTime = 0L
        lastRodHitTime = 0L

        Movement.startSprinting()
        Movement.startForward()
        TimerUtil.setTimeout(Movement::startJumping, RandomUtil.randomIntInRange(400, 1200))
    }

    /**
     * Called when opponent is found.
     * Starts tracking the opponent with the mouse.
     */
    override fun onFoundOpponent() {
        super.onFoundOpponent()
        Mouse.startTracking()
    }

    /**
     * Called when the game ends.
     * Resets all state variables and stops combat actions.
     */
    override fun onGameEnd() {
        super.onGameEnd()

        shotsFired = 0

        speedPotsLeft = 2
        regenPotsLeft = 2
        gapsLeft = 6

        lastSpeedUse = 0L
        lastRegenUse = 0L
        lastPotion = 0L
        lastGap = 0L

        tapping = false
        tappingEndTime = 0L

        shouldHoldLeftClick = false

        opponentJustFiredArrow = false
        lastOpponentArrowFireTime = 0L
        lastTickOpponentDrawingBow = false

        rodHitNeedJump = false
        rodHitDistance = 0f
        lastRodUseTime = 0L
        opponentLastHurtTime = 0
        lastSwordHitTime = 0L
        isDodging = false
        shouldRetreatUntilRodHit = false
        lastRetreatEndTime = 0L
        lastRodHitTime = 0L

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
                        ChatUtil.combatInfo("OP: Rod delayed jump EXECUTED after ${jumpDelay}ms - distance: $distance")
                    }
                }

                TimerUtil.setTimeout({
                    rodHitNeedJump = false
                }, 500)
            }, jumpDelay)

            if (CatDueller.config?.combatLogs == true) {
                ChatUtil.combatInfo("OP: Rod delayed jump SCHEDULED for ${jumpDelay}ms - distance: $distance")
            }
        } else if (CatDueller.config?.combatLogs == true) {
            val reason = when {
                !enableRodJump -> "rod jump disabled in config"
                distance >= 5f -> "distance: $distance (>= 5) "
                !mc.thePlayer.onGround -> "not on ground"
                else -> "unknown reason"
            }
            ChatUtil.combatInfo("OP: Rod jump SKIPPED - $reason")
        }

        if (CatDueller.config?.combatLogs == true) {
            ChatUtil.combatInfo("OP: Rod usage tracked - time: $lastRodUseTime, defensive: $isDefensive")
        }

        useRod(isDefensive)
    }

    /**
     * Called when the bot successfully attacks the opponent.
     *
     * Handles rod retraction, jump after rod hit, block-hitting at close range,
     * and W-tap for sprint reset.
     */
    override fun onAttack() {
        super.onAttack()

        val distance = EntityUtil.getDistanceNoY(mc.thePlayer, opponent())

        if (CatDueller.config?.combatLogs == true) {
            ChatUtil.combatInfo("OP: onAttack triggered - distance: $distance")
        }

        if (mc.thePlayer != null && mc.thePlayer.heldItem != null) {
            val n = mc.thePlayer.heldItem.unlocalizedName.lowercase()
            if (n.contains("rod")) { // wait after hitting with the rod
                // Immediately retract rod on hit
                immediateRetractRod()

                // Jump when rod hits (regardless of W-Tap setting)
                if (mc.thePlayer.onGround) {
                    Movement.singleJump(RandomUtil.randomIntInRange(100, 150))
                }

                if (CatDueller.config?.enableWTap == true) {
                    Combat.wTap(300)
                    tapping = true
                    tappingEndTime = System.currentTimeMillis() + 300
                }
                combo--
            } else if (n.contains("sword")) {
                lastSwordHitTime = System.currentTimeMillis()

                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("OP: Sword hit recorded - time: $lastSwordHitTime")
                }

                if (distance < 2 && CatDueller.config?.holdLeftClick != true) {
                    Mouse.rClick(RandomUtil.randomIntInRange(60, 90)) // otherwise just blockhit
                } else if (CatDueller.config?.enableWTap == true) {
                    Combat.wTap(100)
                    tapping = true
                    tappingEndTime = System.currentTimeMillis() + 100
                }
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
     * - Potion and golden apple usage
     * - Bow and rod combat
     * - Distance-based movement and attack management
     * - Wall avoidance during strafing
     */
    override fun onTick() {
        super.onTick()

        if (tapping && System.currentTimeMillis() >= tappingEndTime) {
            tapping = false
        }

        if (opponent() != null && mc.theWorld != null && mc.thePlayer != null) {
            val currentTime = System.currentTimeMillis()
            if (!lastTickOpponentDrawingBow && opponentIsDrawingBow) {
                // Opponent just started drawing bow
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("OP: Opponent started drawing bow")
                }
            } else if (lastTickOpponentDrawingBow && !opponentIsDrawingBow) {
                // Opponent stopped drawing bow (fired arrow)
                opponentJustFiredArrow = true
                lastOpponentArrowFireTime = currentTime

                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("OP: Opponent fired arrow - arrow fired flag set")
                }
            }

            // Update previous tick state for next comparison
            lastTickOpponentDrawingBow = opponentIsDrawingBow

            // Check for rod hit via opponent's hurtTime change (from Classic bot)
            val opponentCurrentHurtTime = opponent()!!.hurtTime

            // Detect rod hit: opponent's hurtTime increased AND we used rod recently AND it's NOT a sword hit
            val isRecentSwordHit = currentTime - lastSwordHitTime < 200  // 200ms window for sword hit
            val isRecentRodUse = currentTime - lastRodUseTime < 3000     // Extended to 3-second window for rod use

            // Debug rod hit detection conditions
            if (CatDueller.config?.combatLogs == true && shouldRetreatUntilRodHit) {
                ChatUtil.combatInfo("OP: Rod hit check - OpponentHurt: $opponentCurrentHurtTime (was: $opponentLastHurtTime), RecentRod: $isRecentRodUse (${currentTime - lastRodUseTime}ms ago), RecentSword: $isRecentSwordHit")
            }

            if (opponentCurrentHurtTime > opponentLastHurtTime && opponentCurrentHurtTime > 0 &&
                isRecentRodUse && !isRecentSwordHit
            ) {

                val distance = EntityUtil.getDistanceNoY(mc.thePlayer, opponent())

                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("OP: Rod hit detected via opponent hurtTime - opponent hurtTime: $opponentCurrentHurtTime, distance: $distance, time since rod: ${currentTime - lastRodUseTime}ms, sword hit excluded")
                }

                // Update last rod hit time
                lastRodHitTime = currentTime

                // Stop retreat when rod hits
                if (shouldRetreatUntilRodHit) {
                    shouldRetreatUntilRodHit = false
                    lastRetreatEndTime = System.currentTimeMillis()  // Record retreat end time for cooldown
                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtil.combatInfo("OP: Rod hit detected - stopping retreat, cooldown started")
                    }
                }

                // W-Tap logic for rod hit - only when distance < 4.0 blocks and not during rod jump
                if (!tapping && CatDueller.config?.enableWTap == true && distance in 3.0f..4.0f && !rodHitNeedJump) {
                    tapping = true
                    val delay = CatDueller.config?.wTapDelay ?: 100
                    TimerUtil.setTimeout(fun() {
                        val dur = 300  // Rod W-Tap duration
                        Combat.wTap(dur)
                        TimerUtil.setTimeout(fun() {
                            tapping = false
                        }, dur)
                    }, delay)
                } else if (CatDueller.config?.combatLogs == true && CatDueller.config?.enableWTap == true) {
                    val reason = when {
                        distance >= 4f -> "distance: $distance (>= 4 blocks)"
                        rodHitNeedJump -> "rod jump active"
                        else -> "unknown reason"
                    }
                    ChatUtil.combatInfo("OP: Rod W-Tap skipped - $reason")
                }
                combo--
            } else if (opponentCurrentHurtTime > opponentLastHurtTime && opponentCurrentHurtTime > 0) {
                // Debug why rod hit wasn't detected
                if (CatDueller.config?.combatLogs == true && shouldRetreatUntilRodHit) {
                    val reason = when {
                        !isRecentRodUse -> "no recent rod use (${currentTime - lastRodUseTime}ms ago)"
                        else -> "recent sword hit (${currentTime - lastSwordHitTime}ms ago)"
                    }
                    ChatUtil.combatInfo("OP: Opponent hurt but rod hit not detected - $reason")
                }
            }

            opponentLastHurtTime = opponentCurrentHurtTime

            // Reset arrow fired flag after 3 seconds
            if (opponentJustFiredArrow && currentTime - lastOpponentArrowFireTime > 3000) {
                opponentJustFiredArrow = false
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("OP: Arrow fired flag reset after 3 seconds")
                }
            }

            // Check for speed effect before using hasSpeed variable
            var hasSpeed = false
            for (effect in mc.thePlayer.activePotionEffects) {
                if (effect.effectName.lowercase().contains("speed")) {
                    hasSpeed = true
                }
            }

            // Check for block in front and jump if needed (copied from Classic)
            // But disable jumping if player has speed effect
            if (WorldUtil.blockInFront(mc.thePlayer, 2f, 0.5f) != Blocks.air && mc.thePlayer.onGround && !hasSpeed) {
                Movement.singleJump(RandomUtil.randomIntInRange(150, 250))
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("OP: Jumping over block in front")
                }
            } else if (WorldUtil.blockInFront(mc.thePlayer, 2f, 0.5f) != Blocks.air && hasSpeed) {
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("OP: Block in front but jumping disabled due to speed effect")
                }
            }

            if (!mc.thePlayer.isSprinting) {
                Movement.startSprinting()
            }

            val distance = EntityUtil.getDistanceNoY(mc.thePlayer, opponent())

            // Check if rod should be immediately retracted due to close distance
            // Only retract non-defensive rods due to distance
            if (distance < 3.3f && this.rodRetractTimeout != null && !this.isDefensiveRod) {
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
                val newShouldHoldLeftClick =
                    distance <= maxAttackDistance && mc.thePlayer.heldItem != null && mc.thePlayer.heldItem.unlocalizedName.lowercase()
                        .contains("sword")

                if (newShouldHoldLeftClick != shouldHoldLeftClick) {
                    shouldHoldLeftClick = newShouldHoldLeftClick
                    if (shouldHoldLeftClick) {
                        Mouse.startHoldLeftClick()
                    } else {
                        Mouse.stopHoldLeftClick()
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

            if (distance > 8.8) {
                // Jumping priority system: 1) RunningAway, 2) Using Gap, 3) Using Potion, 4) Speed Effect, 5) Normal combat
                if (Mouse.isRunningAway()) {
                    // Priority 1: RunningAway - always jump
                    Movement.startJumping()
                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtil.combatInfo("OP: Jumping enabled - runningAway priority")
                    }
                } else if (Mouse.isUsingGap()) {
                    // Priority 2: Using Gap - always jump
                    Movement.startJumping()
                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtil.combatInfo("OP: Jumping enabled - using gap priority")
                    }
                } else if (Mouse.isUsingPotion()) {
                    // Priority 3: Using Potion - always jump
                    Movement.startJumping()
                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtil.combatInfo("OP: Jumping enabled - using potion priority")
                    }
                } else if (hasSpeed) {
                    // Priority 4: Speed Effect - disable jumping
                    Movement.stopJumping()
                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtil.combatInfo("OP: Jumping disabled due to speed effect")
                    }
                } else if (opponent() != null && opponent()!!.heldItem != null && opponent()!!.heldItem.unlocalizedName.lowercase()
                        .contains("bow")
                ) {
                    // Priority 5: Normal combat - jump when opponent has bow to dodge arrows
                    Movement.stopJumping()
                } else {
                    // Priority 5: Normal combat - default jumping
                    Movement.startJumping()
                }
            } else {
                // Close range - check priorities but generally stop jumping
                if (Mouse.isRunningAway() || Mouse.isUsingGap()) {
                    // High priority actions still need jumping even at close range
                    Movement.startJumping()
                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtil.combatInfo("OP: Close range jumping enabled - high priority action")
                    }
                } else {
                    Movement.stopJumping()
                }
            }

            val movePriority = arrayListOf(0, 0)
            var clear = false
            var randomStrafe = false

            // Gap usage has highest priority for movement - always move backward during gap
            if (Mouse.isUsingGap()) {
                Movement.stopForward()
                Movement.startBackward()
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("OP: Gap usage - forcing backward movement (higher priority than normal logic)")
                }
            } else if (shouldStopForwardForCombo(distance, tapping)) {
                Movement.stopForward()
                Movement.startBackward()
            } else {
                Movement.stopBackward()
                if (!tapping) {
                    Movement.startForward()
                }
            }

            // Avoid switching to sword when recently used gap/potion to prevent inventory conflicts
            val recentlyUsedConsumable =
                System.currentTimeMillis() - lastGap < 3000 || System.currentTimeMillis() - lastPotion < 3000

            if (distance < 1.5 && mc.thePlayer.heldItem != null && !mc.thePlayer.heldItem.unlocalizedName.lowercase()
                    .contains("sword") &&
                !Mouse.isUsingPotion() && !Mouse.isUsingProjectile() && !Mouse.isUsingGap() && !recentlyUsedConsumable
            ) {
                Inventory.setInvItem("sword")
                if (CatDueller.config?.holdLeftClick != true) {
                    Mouse.rClickUp()
                }

                // Start attacking based on config
                if (CatDueller.config?.holdLeftClick == true) {
                    Mouse.startHoldLeftClick()
                } else {
                    Mouse.startLeftAC()
                }
            }

            if (!hasSpeed && speedPotsLeft > 0 && System.currentTimeMillis() - lastSpeedUse > 15000 && System.currentTimeMillis() - lastPotion > 3500) {
                if (Mouse.isUsingGap()) {
                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtil.combatInfo("OP: Speed potion blocked - using gap")
                    }
                } else {
                    Movement.stopJumping()  // Stop jumping when using speed potion
                    useSplashPotion(speedDamage, distance < 5, EntityUtil.entityFacingAway(mc.thePlayer, opponent()!!))
                    speedPotsLeft--
                    lastSpeedUse = System.currentTimeMillis()
                }
            }

            if (WorldUtil.blockInFront(mc.thePlayer, 3f, 1.5f) != Blocks.air) {
                // wall
                Mouse.setRunningAway(false)
            }

            if (((distance > 3 && mc.thePlayer.health < 12) || mc.thePlayer.health < 9) && combo < 2 && mc.thePlayer.health <= (opponent()!!.health + 10)) {
                // time to pot up
                if (!Mouse.isUsingProjectile() && !Mouse.isUsingPotion() && !Mouse.isUsingGap() && System.currentTimeMillis() - lastPotion > 3500) {
                    if (regenPotsLeft > 0 && System.currentTimeMillis() - lastRegenUse > 35000) {
                        Movement.stopJumping()  // Stop jumping when using regen potion
                        useSplashPotion(
                            regenDamage,
                            distance < 5,
                            EntityUtil.entityFacingAway(mc.thePlayer, opponent()!!)
                        )
                        regenPotsLeft--
                        lastRegenUse = System.currentTimeMillis()
                    } else {
                        if (gapsLeft > 0 && System.currentTimeMillis() - lastGap > 7000) {
                            if (CatDueller.config?.combatLogs == true) {
                                ChatUtil.combatInfo("OP: Using gap")
                            }
                            useGap(distance, distance < 8, EntityUtil.entityFacingAway(mc.thePlayer, opponent()!!))
                            gapsLeft--
                        }
                    }
                }
            }

            if (!Mouse.isUsingProjectile() && !Mouse.isRunningAway() && !Mouse.isUsingPotion() && !Mouse.isUsingGap() && !Mouse.rClickDown && System.currentTimeMillis() - lastGap > 2000) {
                // Calculate adjusted rod distances based on prediction ticks bonus
                val predictionTicksBonus = CatDueller.config?.predictionTicksBonus ?: 0
                val opponentActualSpeed = CatDueller.bot?.opponentActualSpeed ?: 0.13f  // Use opponent's actual speed
                val distanceAdjustment = predictionTicksBonus * opponentActualSpeed

                // Adjust rod usage distances based on prediction compensation
                val rodDistance1Min = 4.0f + distanceAdjustment
                val rodDistance1Max = 7.2f + distanceAdjustment
                val rodDistance2Min = 8.5f + distanceAdjustment
                val rodDistance2Max = 10.0f + distanceAdjustment

                // Check if opponent is actually drawing bow (not just holding it) to allow our bow usage
                if (opponent() != null && opponentIsDrawingBow) {
                    opponentUsedBow = true

                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtil.combatInfo("Opponent used bow - drawing detected")
                    }
                }

                if ((distance in rodDistance1Min..rodDistance1Max || distance in rodDistance2Min..rodDistance2Max) && !EntityUtil.entityFacingAway(
                        mc.thePlayer,
                        opponent()!!
                    )
                ) {
                    useRodWithTracking(false)  // OP bot only uses offensive rods
                } else {
                    // Situation 1: Enemy facing away (3.5-30 blocks) - no opponentUsedBow requirement
                    val situation1 = EntityUtil.entityFacingAway(mc.thePlayer, opponent()!!) && distance in 3.5f..30f
                    // Situation 2: Long distance (28-33 blocks) - requires opponentUsedBow
                    val situation2 = distance in 28.0..33.0 && !EntityUtil.entityFacingAway(mc.thePlayer, opponent()!!)

                    if (situation1 || situation2) {
                        val canUseBow = if (situation1) {
                            // Situation 1: No opponentUsedBow requirement
                            distance > 10 && shotsFired < maxArrows && System.currentTimeMillis() - lastPotion > 5000
                        } else {
                            // Situation 2: Requires opponentUsedBow
                            distance > 10 && shotsFired < maxArrows && System.currentTimeMillis() - lastPotion > 5000 && opponentUsedBow
                        }

                        if (canUseBow) {
                            clear = true
                            useBow(distance, fun() {
                                shotsFired++
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
                        if (opponent()!!.isInvisibleToPlayer(mc.thePlayer)) {
                            clear = false
                            if (WorldUtil.leftOrRightToPoint(mc.thePlayer, Vec3(0.0, 0.0, 0.0))) {
                                movePriority[0] += 4
                            } else {
                                movePriority[1] += 4
                            }
                        } else {
                            if (EntityUtil.entityFacingAway(mc.thePlayer, opponent()!!)) {
                                if (WorldUtil.leftOrRightToPoint(mc.thePlayer, Vec3(0.0, 0.0, 0.0))) {
                                    movePriority[0] += 4
                                } else {
                                    movePriority[1] += 4
                                }
                            } else {
                                if (distance in 15f..8f) {
                                    randomStrafe = true
                                } else {
                                    randomStrafe = false
                                    if (opponent() != null && opponent()!!.heldItem != null && (opponent()!!.heldItem.unlocalizedName.lowercase()
                                            .contains("bow") || opponent()!!.heldItem.unlocalizedName.lowercase()
                                            .contains("rod"))
                                    ) {
                                        randomStrafe = true
                                        if (distance < 15) {
                                            Movement.stopJumping()
                                        }
                                    } else {
                                        if (distance < 8) {
                                            // Dynamic strafe logic with randomization
                                            val rotations = EntityUtil.getRotations(opponent()!!, mc.thePlayer, false)
                                            if (rotations != null) {
                                                // Base direction preference
                                                val basePreference = if (rotations[0] < 0) 1 else 0  // 0=left, 1=right

                                                // Add randomization to prevent predictable movement
                                                val randomFactor = RandomUtil.randomIntInRange(1, 4)
                                                val shouldReverse =
                                                    RandomUtil.randomIntInRange(1, 100) <= 30 // 30% chance to reverse

                                                if (shouldReverse) {
                                                    // Reverse the preference occasionally
                                                    movePriority[1 - basePreference] += randomFactor
                                                } else {
                                                    movePriority[basePreference] += randomFactor
                                                }

                                                // Add slight preference to the opposite direction for balance
                                                movePriority[1 - basePreference] += 1
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (WorldUtil.blockInPath(
                        mc.thePlayer,
                        RandomUtil.randomIntInRange(3, 7),
                        1f
                    ) == Blocks.fire && !hasSpeed
                ) {
                    Movement.singleJump(RandomUtil.randomIntInRange(200, 400))
                } else if (WorldUtil.blockInPath(
                        mc.thePlayer,
                        RandomUtil.randomIntInRange(3, 7),
                        1f
                    ) == Blocks.fire && hasSpeed
                ) {
                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtil.combatInfo("OP: Fire detected but jumping disabled due to speed effect")
                    }
                }

                // Wall avoidance: simple wall detection using blockInFront logic (copied from Classic)
                val hasWallOnLeft =
                    hasWallInDirection(90f, 1f) || hasWallInDirection(90f, 2f) || hasWallInDirection(90f, 3f)
                val hasWallOnRight =
                    hasWallInDirection(-90f, 1f) || hasWallInDirection(-90f, 2f) || hasWallInDirection(-90f, 3f)

                // Wall avoidance priority adjustment (applies to all strafe logic)
                if (hasWallOnLeft && !hasWallOnRight) {
                    // Wall on left, prefer right movement
                    movePriority[1] += 20  // Higher priority than strafe
                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtil.combatInfo("OP: Wall on left - moving right")
                    }
                } else if (hasWallOnRight && !hasWallOnLeft) {
                    // Wall on right, prefer left movement
                    movePriority[0] += 20  // Higher priority than strafe
                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtil.combatInfo("OP: Wall on right - moving left")
                    }
                }

                // Check if gap usage is active - if so, skip handle() to avoid being overridden
                if (!Mouse.isUsingGap()) {
                    handle(clear, randomStrafe, movePriority)
                }
                // If gap usage is active, movement is already handled above, skip handle()
            }
        }
    }

    /**
     * Checks if there's a wall in the specified direction relative to the player.
     *
     * @param yaw The yaw offset in degrees (90f for left, -90f for right)
     * @param distance The distance to check in blocks
     * @return true if there's a wall in that direction, false otherwise
     */
    private fun hasWallInDirection(yaw: Float, distance: Float): Boolean {
        return try {
            val lookVec = EntityUtil.get2dLookVec(mc.thePlayer).rotateYaw(yaw)
            val checkPos = mc.thePlayer.position.add(lookVec.xCoord * distance, 0.0, lookVec.zCoord * distance)
            val block = mc.theWorld.getBlockState(checkPos).block
            block != Blocks.air
        } catch (e: Exception) {
            // If there's any error in wall detection, assume no wall to prevent crashes
            false
        }
    }
}