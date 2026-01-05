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
 * - Arrow blocking when opponent draws bow
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

    /** Timestamp when opponent started drawing bow. */
    private var opponentBowStartTime = 0L

    /** Flag to prevent multiple scheduling of blocking end. */
    private var blockingEndScheduled = false

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
        opponentBowStartTime = 0L
        blockingEndScheduled = false

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
        opponentBowStartTime = 0L
        blockingEndScheduled = false

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
     * Called when the bot successfully attacks the opponent.
     *
     * Handles rod retraction, jump after rod hit, block-hitting at close range,
     * and W-tap for sprint reset.
     */
    override fun onAttack() {
        super.onAttack()

        val distance = EntityUtil.getDistanceNoY(mc.thePlayer, opponent())
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
                if (distance < 2 && CatDueller.config?.holdLeftClick != true) {
                    Mouse.rClick(RandomUtil.randomIntInRange(60, 90)) // otherwise just blockhit
                } else if (CatDueller.config?.enableWTap == true) {
                    Combat.wTap(100)
                    tapping = true
                    tappingEndTime = System.currentTimeMillis() + 100
                }
            }
        }
    }

    /**
     * Main game loop called every tick.
     *
     * Handles all combat logic including:
     * - Arrow blocking when opponent draws bow
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
                opponentBowStartTime = currentTime
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("OP: Opponent started drawing bow - tracking time for 700ms block")
                }
            } else if (lastTickOpponentDrawingBow && !opponentIsDrawingBow) {
                // Opponent stopped drawing bow (fired arrow)
                opponentJustFiredArrow = true
                lastOpponentArrowFireTime = currentTime
                opponentBowStartTime = 0L  // Reset bow start time

                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("OP: Opponent fired arrow - arrow fired flag set")
                }
            }

            // Start arrow blocking after 700ms of opponent drawing bow
            if (opponentIsDrawingBow && opponentBowStartTime > 0 &&
                currentTime - opponentBowStartTime >= 700 && !Mouse.isBlockingArrow()
            ) {

                // Start arrow blocking - this will prevent other actions
                Mouse.setBlockingArrow(true)

                // Arrow block: interrupt any ongoing rod usage and switch to sword
                if (Mouse.isUsingProjectile() || this.rodRetractTimeout != null) {
                    // Interrupt rod usage for arrow blocking
                    immediateRetractRod()
                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtil.combatInfo("OP: Interrupted rod usage for arrow blocking")
                    }
                }

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
                            ChatUtil.combatInfo("OP: Seamless transition from projectile to sword blocking (no right-click release)")
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
                    ChatUtil.combatInfo("OP: Started blocking arrow after 700ms draw time (${transitionType} transition) - will block until opponent stops drawing")
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
                                "OP: Arrow blocking ended after ${flightTimeDelay}ms delay (distance: ${
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
                        "OP: Opponent stopped drawing bow - scheduled blocking end in ${flightTimeDelay}ms (distance: ${
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
                // Check if player has speed effect - if so, disable jumping
                if (hasSpeed) {
                    Movement.stopJumping()
                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtil.combatInfo("OP: Jumping disabled due to speed effect")
                    }
                } else if (opponent() != null && opponent()!!.heldItem != null && opponent()!!.heldItem.unlocalizedName.lowercase()
                        .contains("bow")
                ) {
                    // Always jump when opponent has bow to dodge arrows (unless has speed)
                    Movement.stopJumping()
                } else {
                    Movement.startJumping()
                }
            } else {
                Movement.stopJumping()
            }

            val movePriority = arrayListOf(0, 0)
            var clear = false
            var randomStrafe = false

            // Simple forward movement logic (like Sumo but adapted for OP)
            if (shouldStopForwardForCombo(distance, tapping)) {
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
                !Mouse.isUsingPotion() && !Mouse.isUsingProjectile() && !recentlyUsedConsumable
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
                Movement.stopJumping()  // Stop jumping when using speed potion
                useSplashPotion(speedDamage, distance < 3.5, EntityUtil.entityFacingAway(mc.thePlayer, opponent()!!))
                speedPotsLeft--
                lastSpeedUse = System.currentTimeMillis()
            }

            if (WorldUtil.blockInFront(mc.thePlayer, 3f, 1.5f) != Blocks.air) {
                // wall
                Mouse.setRunningAway(false)
            }

            if (((distance > 3 && mc.thePlayer.health < 12) || mc.thePlayer.health < 9) && combo < 2 && mc.thePlayer.health <= (opponent()!!.health + 10)) {
                // time to pot up
                if (!Mouse.isUsingProjectile() && !Mouse.isRunningAway() && !Mouse.isUsingPotion() && System.currentTimeMillis() - lastPotion > 3500) {
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
                        if (gapsLeft > 0 && System.currentTimeMillis() - lastGap > 6000) {
                            useGap(distance, distance < 5, EntityUtil.entityFacingAway(mc.thePlayer, opponent()!!))
                            gapsLeft--
                        }

                    }
                }
            }

            if (!Mouse.isUsingProjectile() && !Mouse.isRunningAway() && !Mouse.isUsingPotion() && !Mouse.rClickDown && System.currentTimeMillis() - lastGap > 2500) {
                // Calculate adjusted rod distances based on prediction ticks bonus
                val predictionTicksBonus = CatDueller.config?.predictionTicksBonus ?: 0
                val opponentActualSpeed = CatDueller.bot?.opponentActualSpeed ?: 0.13f  // Use opponent's actual speed
                val distanceAdjustment = predictionTicksBonus * opponentActualSpeed

                // Adjust rod usage distances based on prediction compensation
                val rodDistance1Min = 5.7f + distanceAdjustment
                val rodDistance1Max = 6.5f + distanceAdjustment
                val rodDistance2Min = 9.0f + distanceAdjustment
                val rodDistance2Max = 9.5f + distanceAdjustment

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
                    useRod(false)  // OP bot only uses offensive rods
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

                handle(clear, randomStrafe, movePriority)
            }
        }
    }
}