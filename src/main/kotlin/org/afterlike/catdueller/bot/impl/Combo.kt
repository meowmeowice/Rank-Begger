package org.afterlike.catdueller.bot.impl

import org.afterlike.catdueller.CatDueller
import org.afterlike.catdueller.bot.BotBase
import org.afterlike.catdueller.bot.features.Gap
import org.afterlike.catdueller.bot.features.MovePriority
import org.afterlike.catdueller.bot.features.Potion
import org.afterlike.catdueller.bot.player.Combat
import org.afterlike.catdueller.bot.player.Inventory
import org.afterlike.catdueller.bot.player.Mouse
import org.afterlike.catdueller.bot.player.Movement
import org.afterlike.catdueller.utils.client.TimerUtil
import org.afterlike.catdueller.utils.game.EntityUtil
import org.afterlike.catdueller.utils.game.WorldUtil
import org.afterlike.catdueller.utils.system.RandomUtil
import net.minecraft.init.Blocks
import net.minecraft.util.Vec3

/**
 * Bot implementation for Combo Duels game mode.
 *
 * This bot handles combat mechanics for Combo Duels, which includes:
 * - Strength potion usage for damage boost
 * - Golden apple consumption for healing and absorption
 * - Ender pearl teleportation to catch fleeing opponents
 * - Automatic armor re-equipping when broken
 * - Strategic strafing based on opponent position and visibility
 */
class Combo : BotBase("/play duels_combo_duel"), MovePriority, Gap, Potion {

    /**
     * Returns the display name of this bot.
     * @return The string "Combo"
     */
    override fun getName(): String {
        return "Combo"
    }

    init {
        setStatKeys(
            mapOf(
                "wins" to "player.stats.Duels.combo_duel_wins",
                "losses" to "player.stats.Duels.combo_duel_losses",
                "ws" to "player.stats.Duels.current_combo_winstreak",
            )
        )
    }

    /** Flag indicating W-tap is currently active. */
    private var tapping = false

    /** Number of strength potions remaining. */
    private var strengthPots = 2

    /** Timestamp of last potion usage. */
    override var lastPotion = 0L

    /** Number of golden apples remaining. */
    private var gaps = 32

    /** Timestamp of last golden apple usage. */
    override var lastGap = 0L

    /** Number of ender pearls remaining. */
    private var pearls = 5

    /** Timestamp of last ender pearl usage. */
    private var lastPearl = 0L

    /** Flag to prevent starting left click autoclicker during item usage. */
    private var dontStartLeftAC = false

    /**
     * Enumeration of armor slot types.
     * Maps to inventory slot indices: BOOTS=0, LEGGINGS=1, CHESTPLATE=2, HELMET=3.
     */
    enum class ArmorEnum {
        BOOTS, LEGGINGS, CHESTPLATE, HELMET
    }

    /**
     * Tracks remaining backup armor pieces per slot.
     * Key is armor slot index, value is count of backup pieces available.
     */
    private var armor = hashMapOf(
        0 to 1,
        1 to 1,
        2 to 1,
        3 to 1
    )

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
     * Called when the game ends.
     * Resets all state variables and stops combat actions.
     */
    override fun onGameEnd() {
        TimerUtil.setTimeout(fun() {
            Movement.clearAll()
            Mouse.stopLeftAC()
            Combat.stopRandomStrafe()
            tapping = false
            strengthPots = 2
            lastPotion = 0L
            gaps = 32
            lastGap = 0L
            pearls = 5
            lastPearl = 0L
            armor = hashMapOf(
                0 to 1,
                1 to 1,
                2 to 1,
                3 to 1
            )
        }, RandomUtil.randomIntInRange(100, 300))
    }

    /**
     * Main game loop called every tick.
     *
     * Handles combat, consumable usage, armor re-equipping,
     * ender pearl throwing, and strategic movement.
     */
    override fun onTick() {
        if (opponent() != null && mc.theWorld != null && mc.thePlayer != null) {
            val distance = EntityUtil.getDistanceNoY(mc.thePlayer, opponent())

            if (!mc.thePlayer.isSprinting) {
                Movement.startSprinting()
            }

            if (distance < (CatDueller.config?.maxDistanceAttack ?: 10)) {
                if (shouldStartAttacking(distance)) {
                    if (mc.thePlayer.heldItem != null && mc.thePlayer.heldItem.unlocalizedName.lowercase()
                            .contains("sword")
                    ) {
                        if (!dontStartLeftAC) {
                            Mouse.startLeftAC()  // Start continuous attacking, hit select will handle cancellation
                        }
                    }
                } else {
                    Mouse.stopLeftAC()
                }
            } else {
                Mouse.stopLeftAC()
            }

            if (distance < (CatDueller.config?.maxDistanceLook ?: 150)) {
                Mouse.startTracking()
            } else {
                Mouse.stopTracking()
            }

            if (distance < 8) {
                Movement.stopJumping()
            }

            if (combo >= 3 && distance >= 3.2 && mc.thePlayer.onGround) {
                Movement.singleJump(RandomUtil.randomIntInRange(100, 150))
            }

            if (distance < 1.5 || (distance < 2.4 && combo >= 1)) {
                Movement.stopForward()
            } else {
                if (!tapping) {
                    Movement.startForward()
                }
            }

            if (WorldUtil.blockInFront(mc.thePlayer, 3f, 1.5f) != Blocks.air) {
                Mouse.setRunningAway(false)
            }

            if (!mc.thePlayer.isPotionActive(net.minecraft.potion.Potion.damageBoost) && System.currentTimeMillis() - lastPotion > 5000) {
                lastPotion = System.currentTimeMillis()
                if (strengthPots > 0) {
                    strengthPots--
                    Movement.stopJumping()  // Stop jumping when using strength potion
                    usePotion(8, distance < 3, EntityUtil.entityFacingAway(opponent()!!, mc.thePlayer))
                }
            }

            for (i in 0..3) {
                if (mc.thePlayer.inventory.armorItemInSlot(i) == null) {
                    Mouse.stopLeftAC()
                    dontStartLeftAC = true
                    if (armor[i]!! > 0) {
                        TimerUtil.setTimeout(fun() {
                            val a = Inventory.setInvItem(ArmorEnum.values()[i].name.lowercase())
                            if (a) {
                                armor[i] = armor[i]!! - 1
                                TimerUtil.setTimeout(fun() {
                                    val r = RandomUtil.randomIntInRange(100, 150)
                                    Mouse.rClick(r)
                                    TimerUtil.setTimeout(fun() {
                                        Inventory.setInvItem("sword")
                                        TimerUtil.setTimeout(fun() {
                                            dontStartLeftAC = false
                                        }, RandomUtil.randomIntInRange(200, 300))
                                    }, r + RandomUtil.randomIntInRange(100, 150))
                                }, RandomUtil.randomIntInRange(200, 400))
                            } else {
                                dontStartLeftAC = false
                            }
                        }, RandomUtil.randomIntInRange(250, 500))
                    }
                }
            }

            if ((mc.thePlayer.health < 10 || !mc.thePlayer.isPotionActive(net.minecraft.potion.Potion.absorption)) && gaps > 0) {
                if (System.currentTimeMillis() - lastGap > 3500 && System.currentTimeMillis() - lastPotion > 3500) {
                    useGap(distance, distance < 2, EntityUtil.entityFacingAway(mc.thePlayer, opponent()!!))
                    gaps--
                }
            }

            val movePriority = arrayListOf(0, 0)
            var clear = false
            var randomStrafe = false

            if (distance > 18 && EntityUtil.entityFacingAway(
                    opponent()!!,
                    mc.thePlayer
                ) && !Mouse.isRunningAway() && System.currentTimeMillis() - lastPearl > 5000 && pearls > 0
            ) {
                lastPearl = System.currentTimeMillis()
                Mouse.stopLeftAC()
                dontStartLeftAC = true
                TimerUtil.setTimeout(fun() {
                    if (Inventory.setInvItem("pearl")) {
                        pearls--
                        Mouse.setUsingProjectile(true)
                        TimerUtil.setTimeout(fun() {
                            Mouse.rClick(RandomUtil.randomIntInRange(100, 150))
                            TimerUtil.setTimeout(fun() {
                                Mouse.setUsingProjectile(false)
                                Inventory.setInvItem("sword")
                                TimerUtil.setTimeout(fun() {
                                    dontStartLeftAC = false
                                }, RandomUtil.randomIntInRange(200, 300))
                            }, RandomUtil.randomIntInRange(250, 300))
                        }, RandomUtil.randomIntInRange(300, 600))
                    } else {
                        dontStartLeftAC = false
                    }
                }, RandomUtil.randomIntInRange(250, 500))
            } else {
                if (distance < 8) {
                    if (opponent()!!.isInvisibleToPlayer(mc.thePlayer)) {
                        clear = false
                        if (WorldUtil.leftOrRightToPoint(mc.thePlayer, Vec3(0.0, 0.0, 0.0))) {
                            movePriority[0] += 4
                        } else {
                            movePriority[1] += 4
                        }
                    } else {
                        if (distance < 4 && combo > 2) {
                            randomStrafe = false
                            val rotations = EntityUtil.getRotations(opponent()!!, mc.thePlayer, false)
                            if (rotations != null) {
                                if (rotations[0] < 0) {
                                    movePriority[1] += 5
                                } else {
                                    movePriority[0] += 5
                                }
                            }
                        } else {
                            randomStrafe = true
                        }
                    }
                }

                handle(clear, randomStrafe, movePriority)
            }
        }
    }

}
