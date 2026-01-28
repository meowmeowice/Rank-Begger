package org.afterlike.catdueller.bot.impl

import org.afterlike.catdueller.CatDueller
import org.afterlike.catdueller.bot.BotBase
import org.afterlike.catdueller.bot.features.Bow
import org.afterlike.catdueller.bot.features.MovePriority
import org.afterlike.catdueller.bot.player.*
import org.afterlike.catdueller.utils.client.ChatUtil
import org.afterlike.catdueller.utils.client.TimerUtil
import org.afterlike.catdueller.utils.game.EntityUtil
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

    /** Previous tick's opponent bow drawing state. */
    private var lastTickOpponentDrawingBow = false

    /** Timestamp when opponent started drawing bow. */
    private var opponentBowStartTime = 0L

    /** Timestamp when opponent stopped drawing bow. */
    private var opponentBowStopTime = 0L

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
            if (distance < 28f) {
                // Too close - move backward
                Movement.stopForward()
                Movement.startBackward()
                if (CatDueller.config?.combatLogs == true && !initialPositioningComplete) {
                    ChatUtil.combatInfo("Distance < 28 blocks (${String.format("%.1f", distance)}), moving backward")
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

            // Bow usage logic - always use bow
            if (!isUsingBow) {
                ourBowStartTime = System.currentTimeMillis()
                isUsingBow = true
            }

            useBow(distance, fun() {
                isUsingBow = false  // Reset when bow usage completes
                
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("Shot arrow at distance ${String.format("%.1f", distance)}")
                }
            })

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

            // Handle movement priorities
            val movePriority = arrayListOf(0, 0)
            val clear = false
            handle(clear, randomStrafe, movePriority)
        }
    }
}
