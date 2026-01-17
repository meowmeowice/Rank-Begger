package org.afterlike.catdueller.bot.impl

import org.afterlike.catdueller.CatDueller
import org.afterlike.catdueller.bot.BotBase
import org.afterlike.catdueller.bot.features.MovePriority
import org.afterlike.catdueller.bot.player.Combat
import org.afterlike.catdueller.bot.player.Inventory
import org.afterlike.catdueller.bot.player.Mouse
import org.afterlike.catdueller.bot.player.Movement
import org.afterlike.catdueller.bot.state.StateManager
import org.afterlike.catdueller.utils.client.ChatUtil
import org.afterlike.catdueller.utils.client.TimerUtil
import org.afterlike.catdueller.utils.game.EntityUtil
import org.afterlike.catdueller.utils.game.WorldUtil
import org.afterlike.catdueller.utils.system.RandomUtil
import net.minecraft.init.Blocks
import net.minecraft.util.Vec3
import java.util.*

/**
 * Bot implementation for Boxing Duels game mode.
 *
 * Boxing Duels is a fist-fighting mode where the goal is to land 100 hits.
 * This bot focuses on:
 * - W-tapping for sprint reset advantage
 * - Strategic strafing to avoid opponent hits
 * - Distance-based attack management
 * - Optional fish item holding for style
 */
class Boxing : BotBase("/play duels_boxing_duel"), MovePriority {

    /**
     * Returns the display name of this bot.
     * @return The string "Boxing"
     */
    override fun getName(): String {
        return "Boxing"
    }

    /**
     * Determines whether the bot should start attacking.
     *
     * Adds Boxing-specific distance logic that reduces attack range
     * when in a combo (3+ hits) to maintain control.
     *
     * @param distance Current horizontal distance to opponent
     * @return True if attacking should begin, false otherwise
     */
    override fun shouldStartAttacking(distance: Float): Boolean {
        val player = mc.thePlayer ?: return false
        val opponent = opponent() ?: return false

        // If player hurtTime > 6, immediately allow attack (highest priority)
        if (player.hurtTime > 6) {
            return true
        }

        // Boxing-specific distance check based on combo
        val maxDistance = if (combo < 3) {
            (CatDueller.config?.maxDistanceAttack ?: 10).toFloat()
        } else {
            3.5f
        }

        if (distance > maxDistance) {
            return false
        }

        // Use base class checks for other conditions (crosshair, visibility, etc.)
        val mouseOver = mc.objectMouseOver
        if (mouseOver == null || mouseOver.entityHit != opponent) {
            return false
        }

        if (!player.canEntityBeSeen(opponent)) {
            return false
        }

        if (player.isUsingItem) {
            return false
        }

        return true
    }

    init {
        setStatKeys(
            mapOf(
                "wins" to "player.stats.Duels.boxing_duel_wins",
                "losses" to "player.stats.Duels.boxing_duel_losses",
                "ws" to "player.stats.Duels.current_boxing_winstreak",
            )
        )
    }

    /** Flag indicating W-tap is currently active. */
    private var tapping = false

    /** Timer for fish item switching (cosmetic feature). */
    private var fishTimer: Timer? = null

    /**
     * Called when the game starts.
     * Initiates sprinting and forward movement.
     */
    override fun onGameStart() {
        super.onGameStart()
        Movement.startSprinting()
        Movement.startForward()
    }

    /**
     * Alternates between holding fish and sword items for cosmetic effect.
     *
     * @param fish True to switch to fish, false to switch to sword
     */
    private fun fishFunc(fish: Boolean = true) {
        if (StateManager.state == StateManager.States.PLAYING) {
            if (fish) {
                Inventory.setInvItem("fish")
            } else {
                Inventory.setInvItem("sword")
            }
            fishTimer = TimerUtil.setTimeout(fun() {
                fishFunc(!fish)
            }, RandomUtil.randomIntInRange(10000, 20000))
        }
    }

    /**
     * Called when the game ends.
     * Stops all movement and cancels the fish timer.
     */
    override fun onGameEnd() {
        TimerUtil.setTimeout(fun() {
            Movement.clearAll()
            Mouse.stopLeftAC()
            Combat.stopRandomStrafe()
            fishTimer?.cancel()
        }, RandomUtil.randomIntInRange(100, 300))
    }

    /**
     * Called when the bot successfully attacks the opponent.
     * Performs W-tap and clears lateral movement when in a combo.
     */
    override fun onAttack() {
        if (CatDueller.config?.enableWTap == true) {
            tapping = true
            ChatUtil.info("W-Tap")
            Combat.wTap(100)
            TimerUtil.setTimeout(fun() {
                tapping = false
            }, 100)
        }
        if (combo >= 3) {
            Movement.clearLeftRight()
        }
    }

    /**
     * Main game loop called every tick.
     *
     * Handles obstacle jumping, attack initiation, distance-based
     * forward movement control, and strategic strafing.
     */
    override fun onTick() {
        if (mc.thePlayer != null) {
            if (WorldUtil.blockInFront(mc.thePlayer, 2f, 0.5f) != Blocks.air && mc.thePlayer.onGround) {
                Movement.singleJump(RandomUtil.randomIntInRange(150, 250))
            }
        }
        if (opponent() != null && mc.theWorld != null && mc.thePlayer != null) {
            val distance = EntityUtil.getDistanceNoY(mc.thePlayer, opponent())

            if (distance < (CatDueller.config?.maxDistanceLook ?: 150)) {
                Mouse.startTracking()
            } else {
                Mouse.stopTracking()
            }

            if (shouldStartAttacking(distance)) {
                Mouse.startLeftAC()  // Start continuous attacking, hit select will handle cancellation
            } else {
                Mouse.stopLeftAC()
            }

            if (combo >= 3 && distance >= 3.2 && mc.thePlayer.onGround) {
                Movement.singleJump(RandomUtil.randomIntInRange(100, 150))
            }

            if (distance < 1.5 || (distance < 2.7 && combo >= 1)) {
                Movement.stopForward()
            } else {
                if (!tapping) {
                    Movement.startForward()
                }
            }

            val movePriority = arrayListOf(0, 0)
            val clear = false
            var randomStrafe = false

            if (!EntityUtil.entityFacingAway(mc.thePlayer, opponent()!!)) {
                if (distance in 15f..8f) {
                    randomStrafe = true
                } else {
                    if (distance in 4f..8f) {
                        if (EntityUtil.entityMovingLeft(mc.thePlayer, opponent()!!)) {
                            movePriority[1] += 1
                        } else {
                            movePriority[0] += 1
                        }
                    } else if (distance < 4) {
                        val rotations = EntityUtil.getRotations(opponent()!!, mc.thePlayer, false)
                        if (rotations != null) {
                            if (rotations[0] < 0) {
                                movePriority[1] += 5
                            } else {
                                movePriority[0] += 5
                            }
                        }
                    }
                }
            } else {
                if (WorldUtil.leftOrRightToPoint(mc.thePlayer, Vec3(0.0, 0.0, 0.0))) {
                    movePriority[0] += 4
                } else {
                    movePriority[1] += 4
                }
            }

            handle(clear, randomStrafe, movePriority)
        }
    }

}
