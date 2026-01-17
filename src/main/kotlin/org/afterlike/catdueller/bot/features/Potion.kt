package org.afterlike.catdueller.bot.features

import org.afterlike.catdueller.bot.player.Inventory
import org.afterlike.catdueller.bot.player.Mouse
import org.afterlike.catdueller.utils.client.ChatUtil
import org.afterlike.catdueller.utils.client.TimerUtil
import org.afterlike.catdueller.utils.system.RandomUtil

/**
 * Interface providing potion usage functionality for combat healing.
 *
 * Supports both splash potions (instant use) and drinkable potions (extended use time),
 * with optional retreat behavior for safer consumption during combat.
 */
interface Potion {

    /**
     * Timestamp of the last potion usage in milliseconds.
     * Used to enforce cooldowns and prevent rapid potion consumption.
     */
    var lastPotion: Long

    /**
     * Uses a splash potion with optional retreat behavior.
     *
     * Splash potions have instant effect upon throwing (80-120ms throw time).
     * The method handles inventory switching, throwing, and returning to combat.
     *
     * @param damage The damage value used to identify the specific potion in inventory
     * @param run Whether to retreat before using the potion
     * @param facingAway Whether the player is already facing away from the opponent
     */
    fun useSplashPotion(damage: Int, run: Boolean, facingAway: Boolean) {
        lastPotion = System.currentTimeMillis()
        fun pot(dmg: Int) {
            Mouse.stopLeftAC()
            if (Inventory.setInvItemByDamage(dmg)) {
                ChatUtil.info("About to splash $dmg")
                TimerUtil.setTimeout(fun() {
                    Mouse.setUsingPotion(true)

                    TimerUtil.setTimeout(fun() {
                        val r = RandomUtil.randomIntInRange(80, 120)
                        Mouse.rClick(r)

                        TimerUtil.setTimeout(fun() {
                            Mouse.setUsingPotion(false)
                            TimerUtil.setTimeout(fun() {
                                Inventory.setInvItem("sword")

                                TimerUtil.setTimeout(fun() {
                                    Mouse.setRunningAway(false)
                                }, RandomUtil.randomIntInRange(500, 700))
                            }, RandomUtil.randomIntInRange(80, 120))
                        }, r + RandomUtil.randomIntInRange(80, 120))
                    }, RandomUtil.randomIntInRange(100, 200))
                }, RandomUtil.randomIntInRange(50, 100))
            }
        }

        if (run && !facingAway) {
            Mouse.setUsingProjectile(false)
            Mouse.setRunningAway(true)
            TimerUtil.setTimeout(fun() {
                pot(damage)
            }, RandomUtil.randomIntInRange(300, 500))
        } else {
            pot(damage)
        }
    }

    /**
     * Uses a drinkable potion with optional retreat behavior.
     *
     * Drinkable potions require extended consumption time (1900-2050ms drink time).
     * The method handles inventory switching, drinking, and returning to combat.
     * Logs an error if the specified potion is not found in inventory.
     *
     * @param damage The damage value used to identify the specific potion in inventory
     * @param run Whether to retreat before using the potion
     * @param facingAway Whether the player is already facing away from the opponent
     */
    fun usePotion(damage: Int, run: Boolean, facingAway: Boolean) {
        fun pot(dmg: Int) {
            Mouse.stopLeftAC()
            if (Inventory.setInvItemByDamage(dmg)) {
                ChatUtil.info("About to use $dmg")
                TimerUtil.setTimeout(fun() {
                    val r = RandomUtil.randomIntInRange(1900, 2050)
                    Mouse.rClick(r)
                    TimerUtil.setTimeout(fun() {
                        Inventory.setInvItem("sword")
                        TimerUtil.setTimeout(fun() {
                            Mouse.setRunningAway(false)
                        }, RandomUtil.randomIntInRange(500, 700))
                    }, r + RandomUtil.randomIntInRange(80, 120))
                }, RandomUtil.randomIntInRange(200, 400))
            } else {
                ChatUtil.error("No $dmg potion in inventory")
            }
        }

        if (run && !facingAway) {
            Mouse.setUsingProjectile(false)
            Mouse.setRunningAway(true)
            TimerUtil.setTimeout(fun() {
                pot(damage)
            }, RandomUtil.randomIntInRange(300, 500))
        } else {
            pot(damage)
        }
    }

}
