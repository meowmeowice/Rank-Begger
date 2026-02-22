package org.afterlike.catdueller.bot.impl

import org.afterlike.catdueller.CatDueller
import org.afterlike.catdueller.bot.BotBase
import org.afterlike.catdueller.bot.features.Bow
import org.afterlike.catdueller.bot.features.MovePriority
import org.afterlike.catdueller.bot.player.*
import org.afterlike.catdueller.utils.client.ChatUtil
import org.afterlike.catdueller.utils.client.TimerUtil
import org.afterlike.catdueller.utils.game.EntityUtil
import org.afterlike.catdueller.utils.game.WorldUtil
import org.afterlike.catdueller.utils.system.RandomUtil

/**
 * Bot implementation for Bow Duels game mode.
 *
 * This bot focuses exclusively on bow combat mechanics including:
 * - Intelligent bow usage and arrow management
 * - Distance-based movement strategy
 * - Arrow dodging when opponent draws bow
 * - Strategic positioning and strafing
 */
class BowDuel : BotBase("/play duels_bow_duel"), Bow, MovePriority {

    /**
     * Returns the display name of this bot.
     * @return The string "BowDuel"
     */
    override fun getName(): String {
        return "Bow"
    }

    init {
        setStatKeys(
            mapOf(
                "wins" to "player.stats.Duels.bow_duel_wins",
                "losses" to "player.stats.Duels.bow_duel_losses",
                "ws" to "player.stats.Duels.current_bow_winstreak",
            )
        )
    }

    /** Timestamp when bot started using bow. */
    private var ourBowStartTime: Long = 0

    /** Flag indicating bot is currently using bow. */
    private var isUsingBow = false

    /** Flag indicating bow counter-attack is in progress. */
    private var bowCounterAttackActive = false

    /** Flag indicating post-bow strafe is active. */
    private var postBowStrafeActive = false

    /** Timestamp when post-bow strafe ends. */
    private var postBowStrafeEndTime = 0L

    /** Previous tick's opponent bow drawing state. */
    private var lastTickOpponentDrawingBow = false

    /** Timestamp when opponent started drawing bow. */
    private var opponentBowStartTime = 0L

    /** Timestamp when opponent stopped drawing bow. */
    private var opponentBowStopTime = 0L

    /** Timestamp of last bow shot for cooldown tracking. */
    private var lastBowShotTime = 0L

    /** Flag indicating if initial positioning is complete. */
    private var initialPositioningComplete = false

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
        ourBowStartTime = 0
        isUsingBow = false
        bowCounterAttackActive = false
        postBowStrafeActive = false
        postBowStrafeEndTime = 0L
        lastBowShotTime = 0L
        opponentBowStartTime = 0L
        opponentBowStopTime = 0L
        lastTickOpponentDrawingBow = false
        initialPositioningComplete = false

        Movement.startSprinting()
        Movement.startForward()  // Start moving forward initially
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

        isUsingBow = false
        bowCounterAttackActive = false
        postBowStrafeActive = false

        Mouse.stopLeftAC()

        if (CatDueller.bot?.toggled() == true) {
            Mouse.stopTracking()
            Movement.clearAll()
            Combat.stopRandomStrafe()
        }
    }

    /**
     * Main game loop called every tick.
     *
     * Handles all combat logic including:
     * - Bow usage based on distance
     * - Movement control
     * - Arrow dodging
     * - Strategic positioning
     */
    override fun onTick() {
        super.onTick()

        if (mc.thePlayer != null && opponent() != null) {
            val currentTime = System.currentTimeMillis()

            // Track opponent bow drawing time
            if (!lastTickOpponentDrawingBow && opponentIsDrawingBow) {
                // Opponent just started drawing bow
                opponentBowStartTime = currentTime
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("Opponent started drawing bow")
                }
            } else if (lastTickOpponentDrawingBow && !opponentIsDrawingBow) {
                // Opponent stopped drawing bow (fired arrow)
                opponentBowStartTime = 0L
                opponentBowStopTime = currentTime
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("Opponent stopped drawing bow")
                }
            }

            // Update previous tick state
            lastTickOpponentDrawingBow = opponentIsDrawingBow
        }

        if (opponent() != null && mc.theWorld != null && mc.thePlayer != null) {
            if (!mc.thePlayer.isSprinting) {
                Movement.startSprinting()
            }

            val distance = EntityUtil.getDistanceNoY(mc.thePlayer, opponent())

            // Mouse tracking
            if (distance < (CatDueller.config?.maxDistanceLook ?: 150)) {
                Mouse.startTracking()
            } else {
                Mouse.stopTracking()
            }

            // Distance control - maintain 28-33 blocks distance
            // Wall behind check takes priority over retreat
            val wallBehind = WorldUtil.wallBehind(mc.thePlayer, 5)

            if (distance < 28f) {
                if (wallBehind) {
                    // Wall behind within 3 blocks - stop retreating, just hold position
                    Movement.stopForward()
                    Movement.stopBackward()
                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtil.combatInfo("Wall behind within 3 blocks - stopping retreat at distance ${String.format("%.1f", distance)}")
                    }
                } else {
                    // No wall behind - safe to move backward
                    Movement.stopForward()
                    Movement.startBackward()
                }
                initialPositioningComplete = true
            } else if (distance > 33f) {
                // Too far - move forward
                Movement.stopBackward()
                Movement.startForward()
                if (CatDueller.config?.combatLogs == true && !initialPositioningComplete) {
                    ChatUtil.combatInfo("Distance > 33 blocks (${String.format("%.1f", distance)}), moving forward")
                }
                initialPositioningComplete = true
            } else {
                // In optimal range (28-33 blocks) - stop all forward/backward movement
                Movement.stopForward()
                Movement.stopBackward()
                if (CatDueller.config?.combatLogs == true && !initialPositioningComplete) {
                    ChatUtil.combatInfo("In optimal distance range: ${String.format("%.1f", distance)} blocks (28-33)")
                    initialPositioningComplete = true
                }
            }

            // Bow usage logic - normal useBow, but early release if opponent stops drawing bow
            val bowOnCooldown = lastBowShotTime > 0 &&
                    System.currentTimeMillis() - lastBowShotTime < 500

            if (!isUsingBow && !bowOnCooldown) {
                ourBowStartTime = System.currentTimeMillis()
                isUsingBow = true
            }

            if (isUsingBow) {
                useBow(distance, fun() {
                    isUsingBow = false
                    lastBowShotTime = System.currentTimeMillis()

                    // Start post-bow strafe
                    postBowStrafeActive = true
                    postBowStrafeEndTime = System.currentTimeMillis() + 1000
                    Combat.stopRandomStrafe()
                    Movement.clearLeftRight()
                    val strafeDir = if (RandomUtil.randomBool()) 1 else 2
                    if (strafeDir == 1) Movement.startLeft() else Movement.startRight()
                })
            }

            // Early release: if opponent just stopped drawing bow and we're charging, release immediately
            val opponentJustStoppedDrawing = lastTickOpponentDrawingBow && !opponentIsDrawingBow
            if (opponentJustStoppedDrawing && isUsingBow && Mouse.isUsingProjectile() && Mouse.rClickDown) {
                Mouse.rClickUp()
            }

            // Jumping logic - dodge when opponent draws bow
            if (distance > 11) {
                if (opponentIsDrawingBow) {
                    Movement.stopJumping()
                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtil.combatInfo("Dodge: Opponent drawing bow - stopping jump")
                    }
                } else {
                    Movement.startJumping()
                }
            } else {
                if (EntityUtil.entityFacingAway(mc.thePlayer, opponent()!!)) {
                    Movement.startJumping()
                } else {
                    Movement.stopJumping()
                }
            }

            // Strafing logic - always strafe
            val randomStrafe = true

            // Check if post-bow strafe should end
            if (postBowStrafeActive && System.currentTimeMillis() >= postBowStrafeEndTime) {
                postBowStrafeActive = false
                Movement.clearLeftRight()
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("Post-bow strafe ended")
                }
            }

            // Handle movement priorities (skip if post-bow strafe is active)
            val movePriority = arrayListOf(0, 0)
            val clear = false
            if (!postBowStrafeActive) {
                handle(clear, randomStrafe, movePriority)
            }
        }
    }
}
