package org.afterlike.catdueller.bot.impl

import org.afterlike.catdueller.CatDueller
import org.afterlike.catdueller.bot.BotBase
import org.afterlike.catdueller.bot.features.*
import org.afterlike.catdueller.bot.player.Combat
import org.afterlike.catdueller.bot.player.Inventory
import org.afterlike.catdueller.bot.player.Mouse
import org.afterlike.catdueller.bot.player.Movement
import org.afterlike.catdueller.utils.client.ChatUtil
import org.afterlike.catdueller.utils.client.TimerUtil
import org.afterlike.catdueller.utils.game.EntityUtil
import org.afterlike.catdueller.utils.game.WorldUtil
import org.afterlike.catdueller.utils.system.RandomUtil
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

    /** Previous opponent strafe direction for detecting direction changes. */
    private var lastOpponentStrafeDirection = 0

    /** Flag indicating post-bow strafe is active. */
    private var postBowStrafeActive = false

    /** Timestamp when post-bow strafe ends. */
    private var postBowStrafeEndTime = 0L

    /** Timestamp of last bow shot for cooldown tracking. */
    private var lastBowShotTime = 0L

    /** Timestamp of last random swing for random interval. */
    private var lastRandomSwing = 0L

    /** Whether random swing mode is currently active. */
    private var isRandomSwing = false

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

        lastRodUseTime = 0L
        opponentLastHurtTime = 0
        lastSwordHitTime = 0L
        isDodging = false
        shouldRetreatUntilRodHit = false
        lastRetreatEndTime = 0L
        lastRodHitTime = 0L
        lastOpponentStrafeDirection = 0
        postBowStrafeActive = false
        postBowStrafeEndTime = 0L
        lastBowShotTime = 0L
        lastRandomSwing = 0L
        isRandomSwing = false

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

        lastRodUseTime = 0L
        opponentLastHurtTime = 0
        lastSwordHitTime = 0L
        isDodging = false
        shouldRetreatUntilRodHit = false
        lastRetreatEndTime = 0L
        lastRodHitTime = 0L

        postBowStrafeActive = false
        postBowStrafeEndTime = 0L
        lastBowShotTime = 0L
        lastRandomSwing = 0L
        if (isRandomSwing) {
            isRandomSwing = false
            Mouse.setBreakingBlock(false)
        }

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
     * Uses the fishing rod with tracking for hit detection.
     *
     * @param isDefensive Whether this is a defensive rod usage (when being combo'd)
     */
    private fun useRodWithTracking(isDefensive: Boolean = false) {
        lastRodUseTime = System.currentTimeMillis()

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

        // Check if opponent has apple - if so, don't W-tap
        val opponentHasApple = opponent() != null && opponent()!!.heldItem != null && 
                               opponent()!!.heldItem.unlocalizedName.lowercase().contains("apple")

        if (mc.thePlayer != null && mc.thePlayer.heldItem != null) {
            val n = mc.thePlayer.heldItem.unlocalizedName.lowercase()
            if (n.contains("rod")) { // wait after hitting with the rod
                // Immediately retract rod on hit
                immediateRetractRod()

                // Don't W-tap if opponent has apple
                if (CatDueller.config?.enableWTap == true && !opponentHasApple) {
                    Combat.wTap(300)
                    tapping = true
                    tappingEndTime = System.currentTimeMillis() + 300
                } else if (CatDueller.config?.combatLogs == true && opponentHasApple) {
                    ChatUtil.combatInfo("OP: W-Tap skipped - opponent has apple")
                }
                combo--
            } else if (n.contains("sword")) {
                lastSwordHitTime = System.currentTimeMillis()

                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("OP: Sword hit recorded - time: $lastSwordHitTime")
                }

                if (distance < 2 && CatDueller.config?.holdLeftClick != true) {
                    Mouse.rClick(RandomUtil.randomIntInRange(60, 90)) // otherwise just blockhit
                } else if (CatDueller.config?.enableWTap == true && !opponentHasApple) {
                    Combat.wTap(100)
                    tapping = true
                    tappingEndTime = System.currentTimeMillis() + 100
                } else if (CatDueller.config?.combatLogs == true && opponentHasApple) {
                    ChatUtil.combatInfo("OP: W-Tap skipped - opponent has apple")
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

                // W-Tap logic for rod hit - only when distance is in range
                if (!tapping && CatDueller.config?.enableWTap == true && distance in 3.0f..4.0f) {
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
                        distance < 3.0f || distance > 4.0f -> "distance: $distance (not in 3.0-4.0 range)"
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
                        ChatUtil.combatInfo("OP: Opponent strafe direction changed: $oldDir -> $newDir, retracting rod (will auto re-throw)")
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


            // Jumping priority system: 1) RunningAway, 2) Using Gap, 3) Using Potion, 4) Opponent has apple, 5) Speed Effect, 6) Opponent facing away, 7) Normal combat
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
                // Priority 3: Using Potion - stop jumping
                Movement.stopJumping()
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("OP: Jumping disabled - using potion priority")
                }
            } else if (opponentIsUsingGap
            ) {
                // Priority 4: Opponent has apple - jump to apply pressure (higher priority than speed effect)
                Movement.startJumping()
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("OP: Jumping enabled - opponent has apple")
                }
            } else if (hasSpeed && WorldUtil.blockInPath(mc.thePlayer, RandomUtil.randomIntInRange(3, 7), 1f) != Blocks.fire ) {
                // Priority 5: Speed Effect - disable jumping
                Movement.stopJumping()
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("OP: Jumping disabled due to speed effect")
                }
            } else if ((WorldUtil.blockInPath(mc.thePlayer, RandomUtil.randomIntInRange(3, 7),1f) == Blocks.fire )){
                Movement.startJumping()
            } else if (opponent() != null && opponent()!!.heldItem != null && opponent()!!.heldItem.unlocalizedName.lowercase()
                    .contains("bow")
            ) {
                // Priority 7: Normal combat - stop jumping when opponent has bow to dodge arrows
                Movement.stopJumping()
            } else if (EntityUtil.entityFacingAway(mc.thePlayer, opponent()!!)) {
                // Priority 6: Opponent facing away - jump to catch up
                Movement.startJumping()
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("OP: Jumping enabled - opponent facing away")
                }
            } else if(distance < 10){
                // Priority 7: Normal combat - default jumping
                Movement.stopJumping()
            } else {
                Movement.startJumping()
            }
           

            // Random swing: when distance > 20, look down 20° and randomly swing sword or use rod (lowest priority)
            if (distance > 20f && !Mouse.isUsingProjectile() && !Mouse.isUsingPotion() && !Mouse.isUsingGap() && !Mouse.isRunningAway() && !Mouse.isBlockingArrow() && !Mouse.isDodgingArrow() && !Mouse.isPlacingWater() && !Mouse.isPlacingPlank() && !Mouse.isPlacingBlockAtFeet()) {
                if (!isRandomSwing) {
                    isRandomSwing = true
                    Mouse.setBreakingBlock(true, 20f)
                    Inventory.setInvItem("sword")
                }
                val now = System.currentTimeMillis()
                if (now - lastRandomSwing > RandomUtil.randomIntInRange(500, 1000)) {
                    lastRandomSwing = now
                    if (RandomUtil.randomBool()) {
                        if (mc.thePlayer?.heldItem == null || !mc.thePlayer.heldItem.unlocalizedName.lowercase().contains("sword")) {
                            Inventory.setInvItem("sword")
                        }
                        mc.thePlayer?.swingItem()
                    } else {
                        useRodWithTracking(false)
                    }
                }
            } else if (isRandomSwing) {
                isRandomSwing = false
                Mouse.setBreakingBlock(false)
                Inventory.setInvItem("sword")
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

                // Start attacking based on config
                if (CatDueller.config?.holdLeftClick == true) {
                    Mouse.startHoldLeftClick()
                } else {
                    Mouse.startLeftAC()
                }
            }

            if (!hasSpeed && speedPotsLeft > 0 && System.currentTimeMillis() - lastSpeedUse > 15000 && System.currentTimeMillis() - lastPotion > 3500 && distance > 5) {
                if (Mouse.isUsingGap()) {
                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtil.combatInfo("OP: Speed potion blocked - using gap")
                    }
                } else {
                    Movement.stopJumping()  // Stop jumping when using speed potion
                    useSplashPotion(speedDamage, distance < 8, EntityUtil.entityFacingAway(mc.thePlayer, opponent()!!))
                    speedPotsLeft--
                    lastSpeedUse = System.currentTimeMillis()
                }
            }

            if (WorldUtil.blockInFront(mc.thePlayer, 3f, 1.5f) != Blocks.air && Mouse.isRunningAway()) {
                Mouse.setRunningAway(false)
                useGap(distance, false, false) // Don't retreat since we're already at a wall
            
            }

            if ( mc.thePlayer.health < 15 && combo < 2) {
                // time to pot up
                if (!Mouse.isUsingProjectile() && !Mouse.isUsingPotion() && !Mouse.isUsingGap() && System.currentTimeMillis() - lastPotion > 3500 && System.currentTimeMillis() - lastGap > 7000 && distance > 5) {
                    if (regenPotsLeft > 0 && System.currentTimeMillis() - lastRegenUse > 35000) {
                        Movement.stopJumping()  // Stop jumping when using regen potion
                        useSplashPotion(
                            regenDamage,
                            distance < 8,
                            EntityUtil.entityFacingAway(mc.thePlayer, opponent()!!)
                        )
                        regenPotsLeft--
                        lastRegenUse = System.currentTimeMillis()
                    } else {
                        // Use gap if: distance > 5 OR opponent is using gap
                        if (gapsLeft > 0 && 
                            (System.currentTimeMillis() - lastPotion > 15000 || mc.thePlayer.health < 10) && 
                            ((mc.thePlayer.health <= (opponent()!!.health) && mc.thePlayer.health < 10) || opponentIsUsingGap) &&
                            (distance > 5 || opponentIsUsingGap)) {
                            // If player is on fire, never retreat - eat gap immediately
                            val shouldRetreat = distance < 4 && !mc.thePlayer.isBurning
                            useGap(distance, shouldRetreat, EntityUtil.entityFacingAway(mc.thePlayer, opponent()!!))
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
                        // 0.5s cooldown between bow shots
                        val bowOnCooldown = lastBowShotTime > 0 &&
                                System.currentTimeMillis() - lastBowShotTime < 500

                        val canUseBow = if (bowOnCooldown) {
                            false
                        } else if (situation1) {
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
                                lastBowShotTime = System.currentTimeMillis()

                                // Start post-bow strafe: random left or right for 1 second
                                postBowStrafeActive = true
                                postBowStrafeEndTime = System.currentTimeMillis() + 1000
                                Combat.stopRandomStrafe()
                                Movement.clearLeftRight()
                                val strafeDir = if (RandomUtil.randomBool()) 1 else 2
                                if (strafeDir == 1) Movement.startLeft() else Movement.startRight()
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
                            if (distance <= 11.0f) {
                                randomStrafe = true
                                Movement.stopJumping()

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
                                        Movement.stopJumping()

                                        if (CatDueller.config?.combatLogs == true && opponentIsDrawingBow) {
                                            ChatUtil.combatInfo("Dodge strafe activated - opponent drawing bow at distance ${String.format("%.1f", distance)}")
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
                    ) == Blocks.fire 
                ) {
                    Movement.singleJump(RandomUtil.randomIntInRange(200, 400))
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

                // Check if gap usage or potion usage is active - if so, skip handle() to avoid being overridden
                if (!Mouse.isUsingGap() && !Mouse.isUsingPotion()) {
                    // Check if post-bow strafe should end
                    if (postBowStrafeActive && System.currentTimeMillis() >= postBowStrafeEndTime) {
                        postBowStrafeActive = false
                        Movement.clearLeftRight()
                    }

                    if (!postBowStrafeActive) {
                        handle(clear, randomStrafe, movePriority)
                    }
                }
                // If gap usage or potion usage is active, movement is already handled above, skip handle()
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