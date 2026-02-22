package org.afterlike.catdueller.bot.impl

import net.minecraft.init.Blocks
import net.minecraft.util.Vec3
import org.afterlike.catdueller.CatDueller
import org.afterlike.catdueller.bot.BotBase
import org.afterlike.catdueller.bot.features.Bow
import org.afterlike.catdueller.bot.features.Gap
import org.afterlike.catdueller.bot.features.MovePriority
import org.afterlike.catdueller.bot.features.Rod
import org.afterlike.catdueller.bot.player.Combat
import org.afterlike.catdueller.bot.player.Inventory
import org.afterlike.catdueller.bot.player.Mouse
import org.afterlike.catdueller.bot.player.Movement
import org.afterlike.catdueller.utils.client.ChatUtil
import org.afterlike.catdueller.utils.client.TimerUtil
import org.afterlike.catdueller.utils.game.EntityUtil
import org.afterlike.catdueller.utils.game.WorldUtil
import org.afterlike.catdueller.utils.system.RandomUtil

/**
 * Bot implementation for UHC Duels game mode.
 *
 * UHC Duels features diamond armor with protection enchantments,
 * along with 6 golden apples, 3 golden heads, bow, and fishing rod.
 * This bot handles:
 * - Golden apple consumption for healing (6 gaps available)
 * - Golden head usage for emergency close-range healing (3 heads available)
 * - Bow combat at long range
 * - Fishing rod tactics for combos
 * - Wall avoidance during combat
 * - No potion usage (UHC mode doesn't have potions)
 */
class UHC : BotBase("/play duels_uhc_duel"), Bow, Rod, MovePriority, Gap {

    /**
     * Returns the display name of this bot.
     * @return The string "UHC"
     */
    override fun getName(): String {
        return "UHC"
    }

    init {
        setStatKeys(
            mapOf(
                "wins" to "player.stats.Duels.uhc_duel_wins",
                "losses" to "player.stats.Duels.uhc_duel_losses",
                "ws" to "player.stats.Duels.current_uhc_winstreak",
            )
        )
    }

    /** Number of arrows fired this game. */
    var shotsFired = 0

    /** Maximum arrows allowed per game. */
    var maxArrows = 20

    /** Number of golden apples remaining. */
    var gapsLeft = 6

    /** Number of golden heads remaining. */
    var headsLeft = 3

    /** Timestamp of last golden apple usage (implements Gap interface). */
    override var lastGap = 0L

    /** Timestamp of last golden head usage. */
    private var lastHead = 0L

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

    /** Flag to continue retreating until rod hits opponent once. */
    private var shouldRetreatUntilRodHit = false

    /** Timestamp when retreat last ended for cooldown tracking. */
    private var lastRetreatEndTime = 0L

    /** Timestamp when rod last hit opponent. */
    private var lastRodHitTime = 0L

    /** Timestamp when water was last placed to prevent spam. */
    private var lastWaterPlacement = 0L

    /** Timestamp when plank was last placed to prevent spam. */
    private var lastPlankPlacement = 0L

    /** Flag indicating plank placement is in progress. */
    private var isPlacingPlank = false

    /** Flag indicating water placement is in progress. */
    private var isPlacingWater = false

    /** Previous opponent strafe direction for detecting direction changes. */
    private var lastOpponentStrafeDirection = 0

    /** Flag indicating post-bow strafe is active. */
    private var postBowStrafeActive = false

    /** Timestamp when post-bow strafe ends. */
    private var postBowStrafeEndTime = 0L

    /** Timestamp of last bow shot for cooldown tracking. */
    private var lastBowShotTime = 0L

    /**
     * Called when the game starts.
     * Resets all consumable counts and state variables, then initiates movement.
     */
    override fun onGameStart() {
        super.onGameStart()

        shotsFired = 0
        gapsLeft = 6
        headsLeft = 3
        lastGap = 0L
        lastHead = 0L

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
        shouldRetreatUntilRodHit = false
        lastRetreatEndTime = 0L
        lastRodHitTime = 0L
        lastWaterPlacement = 0L
        lastPlankPlacement = 0L
        isPlacingPlank = false
        isPlacingWater = false
        lastOpponentStrafeDirection = 0
        postBowStrafeActive = false
        postBowStrafeEndTime = 0L
        lastBowShotTime = 0L

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
        gapsLeft = 6
        headsLeft = 3
        lastGap = 0L
        lastHead = 0L

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
        shouldRetreatUntilRodHit = false
        lastRetreatEndTime = 0L
        lastRodHitTime = 0L
        lastWaterPlacement = 0L
        lastPlankPlacement = 0L
        isPlacingPlank = false
        isPlacingWater = false
        lastOpponentStrafeDirection = 0
        postBowStrafeActive = false
        postBowStrafeEndTime = 0L
        lastBowShotTime = 0L

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

        

        if (CatDueller.config?.combatLogs == true) {
            ChatUtil.combatInfo("UHC: Rod usage tracked - time: $lastRodUseTime, defensive: $isDefensive")
        }

        useRod(isDefensive)
    }

    /**
     * Uses a golden head for quick healing at close range.
     * 
     * Golden heads provide instant health regeneration and are ideal for close-range combat
     * when the player needs immediate healing but doesn't have time for a full gap retreat.
     */
    private fun useHead() {
        lastHead = System.currentTimeMillis()
        ChatUtil.info("about to use head")

        if (CatDueller.config?.combatLogs == true) {
            ChatUtil.combatInfo("UHC: Using head - health: ${mc.thePlayer.health}, heads left: $headsLeft")
        }

        // Stop any current attacks
        Mouse.stopLeftAC()

        // Switch to head and use it
        if (Inventory.setInvItem("skull")) {
            // Single right click to consume head
            TimerUtil.setTimeout({
                Mouse.rClick(50)
            }, 100)
             // Very short click duration for quick consumption

            // Switch back to sword after a short delay
            TimerUtil.setTimeout({
                Inventory.setInvItem("sword")
            }, 200)

            headsLeft--
        } else {
            if (CatDueller.config?.combatLogs == true) {
                ChatUtil.combatInfo("UHC: Failed to switch to head")
            }
        }
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
            ChatUtil.combatInfo("UHC: onAttack triggered - distance: $distance")
        }

        if (mc.thePlayer != null && mc.thePlayer.heldItem != null) {
            val n = mc.thePlayer.heldItem.unlocalizedName.lowercase()
            if (n.contains("rod")) { // wait after hitting with the rod
                // Immediately retract rod on hit
                immediateRetractRod()

                if (CatDueller.config?.enableWTap == true) {
                    Combat.wTap(300)
                    tapping = true
                    tappingEndTime = System.currentTimeMillis() + 300
                }
                combo--
            } else if (n.contains("sword")) {
                lastSwordHitTime = System.currentTimeMillis()

                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("UHC: Sword hit recorded - time: $lastSwordHitTime")
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
     * - Golden apple usage
     * - Bow and rod combat
     * - Distance-based movement and attack management
     * - Wall avoidance during strafing
     */
    override fun onTick() {
        super.onTick()
        var needJump = false

        // Block breaking: if looking at a plank or log block, switch to axe and hold left click
        if (toggled() && opponent() != null && mc.thePlayer != null && !isPlacingWater && mc.objectMouseOver != null &&
            mc.objectMouseOver.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK) {
            val blockPos = mc.objectMouseOver.blockPos
            val block = mc.theWorld?.getBlockState(blockPos)?.block
            val isBreakable = block == Blocks.planks || block == Blocks.log || block == Blocks.log2
            if (isBreakable && !Mouse.lClickDown) {
                Inventory.setInvItem("hatchet")
                Mouse.startLeftClick()
            } else if (!isBreakable && Mouse.lClickDown) {
                Mouse.lClickUp()
                Inventory.setInvItem("sword")
            }
        } else if (Mouse.lClickDown) {
            Mouse.lClickUp()
            Inventory.setInvItem("sword")
        }

        if (tapping && System.currentTimeMillis() >= tappingEndTime) {
            tapping = false
        }

        // Check if there's lava in front and place plank (HIGHEST PRIORITY)
        if (mc.thePlayer != null && mc.theWorld != null) {
            val currentTime = System.currentTimeMillis()

            // Check blocks in front for lava
            val lavaInFront = (1..3).any { distance ->
                val block = WorldUtil.blockInFront(mc.thePlayer, distance.toFloat(), 0f)
                block == Blocks.lava || block == Blocks.flowing_lava
            }

            if (lavaInFront && currentTime - lastPlankPlacement > 2000) {
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("UHC: Lava in front - placing 4 planks and jumping")
                }

                lastPlankPlacement = currentTime
                needJump = true  // Allow jumping to continue

                // Stop attacking
                Mouse.stopLeftAC()

                // Start jumping to avoid lava
                Movement.startJumping()

                // Switch to plank
                if (Inventory.setInvItem("plank")) {
                    // Enable plank placement rotation mode
                    Mouse.setPlacingPlank(true)

                    // Wait for rotation to apply, then place 4 planks rapidly
                    TimerUtil.setTimeout({
                        // Place plank 1
                        Mouse.rClick(50)

                        TimerUtil.setTimeout({
                            // Place plank 2
                            Mouse.rClick(50)

                            TimerUtil.setTimeout({
                                // Place plank 3
                                Mouse.rClick(50)

                                TimerUtil.setTimeout({
                                    // Place plank 4
                                    Mouse.rClick(50)

                                    // Disable plank placement rotation and switch back to sword
                                    TimerUtil.setTimeout({
                                        Mouse.setPlacingPlank(false)
                                        Inventory.setInvItem("sword")
                                    }, 100)
                                }, 250)
                            }, 250)
                        }, 250)
                    }, 50)
                }
            }
        }

        // Check if player is standing in lava or on fire and place water if needed
        if (mc.thePlayer != null && mc.theWorld != null && !isPlacingWater) {
            val playerPos = mc.thePlayer.position
            val blockAtFeet = mc.theWorld.getBlockState(playerPos).block
            val currentTime = System.currentTimeMillis()
            val isInLava = blockAtFeet == Blocks.lava || blockAtFeet == Blocks.flowing_lava
            val isOnFire = mc.thePlayer.isBurning

            if ((isInLava || isOnFire) && currentTime - lastWaterPlacement > 3000) {
                val reason = if (isInLava) "in lava" else "on fire"
                ChatUtil.info("$reason - placing water")

                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("UHC: Player $reason - placing water")
                }
                TimerUtil.setTimeout(fun() {
                    lastWaterPlacement = currentTime
                    isPlacingWater = true

                    // Stop attacking
                    Mouse.stopLeftAC()

                    // Switch to water bucket
                    if (Inventory.setInvItem("water")) {
                        // Enable water placement rotation mode
                        Mouse.setPlacingWater(true)
                        Mouse.rClickUp()
                        // Wait for rotation to apply, then hold right click for 500ms
                        TimerUtil.setTimeout({
                            Mouse.startRightClick()

                            // Release after 500ms and switch back to sword
                            TimerUtil.setTimeout({
                                Mouse.rClickUp()
                                Mouse.setPlacingWater(false)
                                Inventory.setInvItem("sword")
                                isPlacingWater = false
                            }, 400)
                        }, 100)
                    }
                }, RandomUtil.randomIntInRange(100, 300))

                
            }
        }

        if (opponent() != null && mc.theWorld != null && mc.thePlayer != null) {
            val currentTime = System.currentTimeMillis()
            if (!lastTickOpponentDrawingBow && opponentIsDrawingBow) {
                // Opponent just started drawing bow
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("UHC: Opponent started drawing bow")
                }
            } else if (lastTickOpponentDrawingBow && !opponentIsDrawingBow) {
                // Opponent stopped drawing bow (fired arrow)
                opponentJustFiredArrow = true
                lastOpponentArrowFireTime = currentTime

                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("UHC: Opponent fired arrow - arrow fired flag set")
                }
            }

            // Update previous tick state for next comparison
            lastTickOpponentDrawingBow = opponentIsDrawingBow

        }

        if (mc.thePlayer != null) {
            // Check for block in front (always check, similar to Classic)
            if ((WorldUtil.blockInFront(mc.thePlayer, 2f, 0.5f) != Blocks.air || WorldUtil.blockInFront(mc.thePlayer, 1f, 0.5f) != Blocks.air) && WorldUtil.blockInFront(mc.thePlayer, 1f, 2.5f) == Blocks.air && WorldUtil.blockInFront(mc.thePlayer, 2f, 2.5f) == Blocks.air && mc.thePlayer.onGround
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

            // Check for rod hit via opponent's hurtTime change
            val currentTime = System.currentTimeMillis()
            val opponentCurrentHurtTime = opponent()!!.hurtTime

            // Detect rod hit: opponent's hurtTime increased AND we used rod recently AND it's NOT a sword hit
            val isRecentSwordHit = currentTime - lastSwordHitTime < 200  // 200ms window for sword hit
            val isRecentRodUse = currentTime - lastRodUseTime < 3000     // Extended to 3-second window for rod use

            // Debug rod hit detection conditions
            if (CatDueller.config?.combatLogs == true && shouldRetreatUntilRodHit) {
                ChatUtil.combatInfo("UHC: Rod hit check - OpponentHurt: $opponentCurrentHurtTime (was: $opponentLastHurtTime), RecentRod: $isRecentRodUse (${currentTime - lastRodUseTime}ms ago), RecentSword: $isRecentSwordHit")
            }

            if (opponentCurrentHurtTime > opponentLastHurtTime && opponentCurrentHurtTime > 0 &&
                isRecentRodUse && !isRecentSwordHit
            ) {

                val distance = EntityUtil.getDistanceNoY(mc.thePlayer, opponent())

                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("UHC: Rod hit detected via opponent hurtTime - opponent hurtTime: $opponentCurrentHurtTime, distance: $distance, time since rod: ${currentTime - lastRodUseTime}ms, sword hit excluded")
                }

                // Update last rod hit time
                lastRodHitTime = currentTime

                // Stop retreat when rod hits
                if (shouldRetreatUntilRodHit) {
                    shouldRetreatUntilRodHit = false
                    lastRetreatEndTime = System.currentTimeMillis()  // Record retreat end time for cooldown
                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtil.combatInfo("UHC: Rod hit detected - stopping retreat, cooldown started")
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
                    ChatUtil.combatInfo("UHC: Rod W-Tap skipped - $reason")
                }
                combo--
            } else if (opponentCurrentHurtTime > opponentLastHurtTime && opponentCurrentHurtTime > 0) {
                // Debug why rod hit wasn't detected
                if (CatDueller.config?.combatLogs == true && shouldRetreatUntilRodHit) {
                    val reason = when {
                        !isRecentRodUse -> "no recent rod use (${currentTime - lastRodUseTime}ms ago)"
                        else -> "recent sword hit (${currentTime - lastSwordHitTime}ms ago)"
                    }
                    ChatUtil.combatInfo("UHC: Opponent hurt but rod hit not detected - $reason")
                }
            }

            opponentLastHurtTime = opponentCurrentHurtTime

            // Reset arrow fired flag after 3 seconds
            if (opponentJustFiredArrow && currentTime - lastOpponentArrowFireTime > 3000) {
                opponentJustFiredArrow = false
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("UHC: Arrow fired flag reset after 3 seconds")
                }
            }

            if (!mc.thePlayer.isSprinting) {
                Movement.startSprinting()
            }

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
                        ChatUtil.combatInfo("UHC: Opponent strafe direction changed: $oldDir -> $newDir, retracting rod (will auto re-throw)")
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

            // Jumping priority system (copied from OP): 1) RunningAway, 2) Using Gap, 3) Using Potion, 4) Opponent has apple, 5) Speed Effect, 6) Opponent facing away, 7) Normal combat
            // PRESERVE: needJump and rodHitNeedJump for UHC block detection
            if (needJump || rodHitNeedJump) {
                // UHC-specific: Always jump when block detection triggers
                Movement.startJumping()
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("UHC: Jumping enabled - block detection (needJump: $needJump, rodHitNeedJump: $rodHitNeedJump)")
                }
            } else if (Mouse.isRunningAway()) {
                // Priority 1: RunningAway - always jump
                Movement.startJumping()
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("UHC: Jumping enabled - runningAway priority")
                }
            } else if (Mouse.isUsingGap()) {
                // Priority 2: Using Gap - always jump
                Movement.startJumping()
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("UHC: Jumping enabled - using gap priority")
                }
            } else if (Mouse.isPlacingWater()) {
                // UHC-specific: Jump when placing water
                Movement.startJumping()
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("UHC: Jumping enabled - placing water")
                }
            } else if (opponentIsUsingGap) {
                // Priority 4: Opponent has apple - jump to apply pressure
                Movement.startJumping()
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("UHC: Jumping enabled - opponent has apple")
                }
            } else if ((WorldUtil.blockInPath(mc.thePlayer, RandomUtil.randomIntInRange(3, 7), 1f) == Blocks.fire)) {
                // UHC-specific: Jump over fire
                Movement.startJumping()
            } else if (opponent() != null && opponent()!!.heldItem != null && opponent()!!.heldItem.unlocalizedName.lowercase()
                    .contains("bow") && !needJump && !rodHitNeedJump
            ) {
                // Priority 5: Normal combat - stop jumping when opponent has bow to dodge arrows
                Movement.stopJumping()
            } else if (EntityUtil.entityFacingAway(mc.thePlayer, opponent()!!)) {
                // Priority 6: Opponent facing away - jump to catch up
                Movement.startJumping()
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("UHC: Jumping enabled - opponent facing away")
                }
            } else if (distance < 10) {
                // Priority 7: Normal combat - default jumping
                Movement.stopJumping()
            } else {
                Movement.startJumping()
            }

            val movePriority = arrayListOf(0, 0)
            var clear = false
            var randomStrafe = false

            // Height advantage distance keeping: if opponent is >3 blocks above us, keep 28-33 distance
            val heightDiff = opponent()!!.posY - mc.thePlayer.posY
            val opponentHighAbove = heightDiff > 3.0

            // Gap usage has highest priority for movement - always move backward during gap
            if (opponentHighAbove && !Mouse.isUsingGap()) {
                if (distance < 28f) {
                    Movement.stopForward()
                    Movement.startBackward()
                } else if (distance > 33f) {
                    Movement.stopBackward()
                    if (!tapping) Movement.startForward()
                } else {
                    Movement.stopForward()
                    Movement.stopBackward()
                }
            } else if (Mouse.isUsingGap()) {
                Movement.stopForward()
                Movement.startBackward()
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("UHC: Gap usage - forcing backward movement (higher priority than normal logic)")
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

            // Avoid switching to sword when recently used gap to prevent inventory conflicts
            val recentlyUsedGap = System.currentTimeMillis() - lastGap < 3000

            if (distance < 1.5 && !Mouse.lClickDown && !isPlacingWater && mc.thePlayer.heldItem != null && !mc.thePlayer.heldItem.unlocalizedName.lowercase()
                    .contains("sword") &&
                !Mouse.isUsingProjectile() && !Mouse.isUsingGap() && !recentlyUsedGap
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

            // Check for wall behind - if retreating into a wall, use gap immediately
            if (WorldUtil.blockInFront(mc.thePlayer, 3f, 1.5f) != Blocks.air && Mouse.isRunningAway()) {
                Mouse.setRunningAway(false)
                useGap(distance, false, false) // Don't retreat since we're already at a wall

            }

            // UHC Head usage logic - for close range emergency healing
            if (mc.thePlayer.health < 10 && combo < 2 && headsLeft > 0 && !Mouse.lClickDown && !isPlacingWater &&
                System.currentTimeMillis() - lastHead > 3000 && System.currentTimeMillis() - lastGap > 7000 && !Mouse.isUsingGap() && !Mouse.isUsingProjectile()
            ) {
                lastHead = System.currentTimeMillis()  // Update immediately to prevent multiple triggers
                TimerUtil.setTimeout(fun() {
                    useHead()
                }, RandomUtil.randomIntInRange(100, 300))
                
            }

            // UHC Gap usage logic - similar to OP mode
            if (mc.thePlayer.health < 12 && combo < 2) {
                // Use gap for healing since no potions available in UHC
                if (!Mouse.isUsingProjectile() && !Mouse.isUsingGap() && !Mouse.lClickDown && !isPlacingWater && gapsLeft > 0 && 
                    System.currentTimeMillis() - lastGap > 7000 && System.currentTimeMillis() - lastHead > 3000 &&
                    (System.currentTimeMillis() - lastGap > 15000 || mc.thePlayer.health < 10) &&
                    (mc.thePlayer.health <= (opponent()!!.health) || opponentIsUsingGap) &&
                    (distance > 5 || opponentIsUsingGap)) {
                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtil.combatInfo("UHC: Using gap - health: ${mc.thePlayer.health}, distance: ${String.format("%.1f", distance)}, opponentUsingGap: $opponentIsUsingGap, gaps left: $gapsLeft")
                    }
                    // If player is on fire, never retreat - eat gap immediately
                    val shouldRetreat = distance < 4 && !mc.thePlayer.isBurning
                    useGap(distance, shouldRetreat, EntityUtil.entityFacingAway(mc.thePlayer, opponent()!!))
                    gapsLeft--
                }
            }

            if (!Mouse.isUsingProjectile() && !Mouse.isRunningAway() && !Mouse.isUsingGap() && !Mouse.rClickDown && System.currentTimeMillis() - lastGap > 2000) {
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

                if ((distance in rodDistance1Min..rodDistance1Max || distance in rodDistance2Min..rodDistance2Max) && !Mouse.lClickDown && !isPlacingWater && !EntityUtil.entityFacingAway(
                        mc.thePlayer,
                        opponent()!!
                    )
                ) {
                    useRodWithTracking(false)  // UHC bot only uses offensive rods
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
                            distance > 10 && shotsFired < maxArrows
                        } else {
                            // Situation 2: Requires opponentUsedBow
                            distance > 10 && shotsFired < maxArrows && opponentUsedBow
                        }

                        if (canUseBow && !Mouse.lClickDown && !isPlacingWater) {
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
                                // Strafe logic (copied from OP with distance-based approach)
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

                // Wall avoidance: simple wall detection using blockInFront logic
                val hasWallOnLeft =
                    hasWallInDirection(90f, 1f) || hasWallInDirection(90f, 2f) || hasWallInDirection(90f, 3f)
                val hasWallOnRight =
                    hasWallInDirection(-90f, 1f) || hasWallInDirection(-90f, 2f) || hasWallInDirection(-90f, 3f)

                // Wall avoidance priority adjustment (applies to all strafe logic)
                if (hasWallOnLeft && !hasWallOnRight) {
                    // Wall on left, prefer right movement
                    movePriority[1] += 20  // Higher priority than strafe
                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtil.combatInfo("UHC: Wall on left - moving right")
                    }
                } else if (hasWallOnRight && !hasWallOnLeft) {
                    // Wall on right, prefer left movement
                    movePriority[0] += 20  // Higher priority than strafe
                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtil.combatInfo("UHC: Wall on right - moving left")
                    }
                }

                // Check if gap usage or running away is active - if so, skip handle() to avoid being overridden
                if (!Mouse.isUsingGap() && !Mouse.isRunningAway()) {
                    // Check if post-bow strafe should end
                    if (postBowStrafeActive && System.currentTimeMillis() >= postBowStrafeEndTime) {
                        postBowStrafeActive = false
                        Movement.clearLeftRight()
                    }

                    if (!postBowStrafeActive) {
                        if (CatDueller.config?.combatLogs == true) {
                            ChatUtil.combatInfo("UHC: handle() - clear: $clear, randomStrafe: $randomStrafe, movePriority: [${movePriority[0]}, ${movePriority[1]}]")
                        }
                        handle(clear, randomStrafe, movePriority)
                    }
                } else {
                    // Clear strafe when running away or using gap to avoid residual movement
                    Movement.clearLeftRight()
                }
                // If gap usage or running away is active, movement is already handled above, skip handle()
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
