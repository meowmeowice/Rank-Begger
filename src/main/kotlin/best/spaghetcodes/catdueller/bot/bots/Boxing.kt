package best.spaghetcodes.catdueller.bot.bots

import best.spaghetcodes.catdueller.CatDueller
import best.spaghetcodes.catdueller.bot.BotBase
import best.spaghetcodes.catdueller.bot.StateManager
import best.spaghetcodes.catdueller.bot.features.MovePriority
import best.spaghetcodes.catdueller.bot.player.Combat
import best.spaghetcodes.catdueller.bot.player.Inventory
import best.spaghetcodes.catdueller.bot.player.Mouse
import best.spaghetcodes.catdueller.bot.player.Movement
import best.spaghetcodes.catdueller.utils.*
import net.minecraft.init.Blocks
import net.minecraft.util.Vec3
import java.util.*

class Boxing : BotBase("/play duels_boxing_duel"), MovePriority {

    override fun getName(): String {
        return "Boxing"
    }

    /**
     * Override shouldStartAttacking to add Boxing-specific distance logic
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

    private var tapping = false
    private var fishTimer: Timer? = null

    override fun onGameStart() {
        super.onGameStart()  // Call parent to check scoreboard
        Movement.startSprinting()
        Movement.startForward()
        /* 
        if (CatDueller.config?.boxingFish == true) {
            TimeUtils.setTimeout(this::fishFunc, RandomUtils.randomIntInRange(10000, 20000))
        }
        */
    }

    private fun fishFunc(fish: Boolean = true) {
        if (StateManager.state == StateManager.States.PLAYING) {
            if (fish) {
                Inventory.setInvItem("fish")
            } else {
                Inventory.setInvItem("sword")
            }
            fishTimer = TimeUtils.setTimeout(fun() {
                fishFunc(!fish)
            }, RandomUtils.randomIntInRange(10000, 20000))
        }
    }

    override fun onGameEnd() {
        TimeUtils.setTimeout(fun() {
            Movement.clearAll()
            Mouse.stopLeftAC()
            Combat.stopRandomStrafe()
            fishTimer?.cancel()
        }, RandomUtils.randomIntInRange(100, 300))
    }

    override fun onAttack() {
        if (CatDueller.config?.enableWTap == true) {
            tapping = true
            ChatUtils.info("W-Tap")
            Combat.wTap(100)
            TimeUtils.setTimeout(fun() {
                tapping = false
            }, 100)
        }
        if (combo >= 3) {
            Movement.clearLeftRight()
        }
    }

    override fun onTick() {
        if (mc.thePlayer != null) {
            if (WorldUtils.blockInFront(mc.thePlayer, 2f, 0.5f) != Blocks.air && mc.thePlayer.onGround) {
                Movement.singleJump(RandomUtils.randomIntInRange(150, 250))
            }
        }
        if (opponent() != null && mc.theWorld != null && mc.thePlayer != null) {
            val distance = EntityUtils.getDistanceNoY(mc.thePlayer, opponent())

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
                Movement.singleJump(RandomUtils.randomIntInRange(100, 150))
            }

            if (distance < 1.5 || (distance < 2.7 && combo >= 1)) {
                Movement.stopForward()
            } else {
                if (!tapping) {
                    Movement.startForward()
                }
            }

            val movePriority = arrayListOf(0, 0)
            var clear = false
            var randomStrafe = false

            if (!EntityUtils.entityFacingAway(mc.thePlayer, opponent()!!)) {
                if (distance in 15f..8f) {
                    randomStrafe = true
                } else {
                    if (distance in 4f..8f) {
                        if (EntityUtils.entityMovingLeft(mc.thePlayer, opponent()!!)) {
                            movePriority[1] += 1
                        } else {
                            movePriority[0] += 1
                        }
                    } else if (distance < 4) {
                        val rotations = EntityUtils.getRotations(opponent()!!, mc.thePlayer, false)
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
                // runner
                if (WorldUtils.leftOrRightToPoint(mc.thePlayer, Vec3(0.0, 0.0, 0.0))) {
                    movePriority[0] += 4
                } else {
                    movePriority[1] += 4
                }
            }

            handle(clear, randomStrafe, movePriority)
        }
    }

}
