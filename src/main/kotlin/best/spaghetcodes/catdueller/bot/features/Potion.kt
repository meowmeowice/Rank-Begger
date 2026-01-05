package best.spaghetcodes.catdueller.bot.features

import best.spaghetcodes.catdueller.bot.player.Inventory
import best.spaghetcodes.catdueller.bot.player.Mouse
import best.spaghetcodes.catdueller.utils.ChatUtils
import best.spaghetcodes.catdueller.utils.RandomUtils
import best.spaghetcodes.catdueller.utils.TimeUtils

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
                ChatUtils.info("About to splash $dmg")
                TimeUtils.setTimeout(fun() {
                    Mouse.setUsingPotion(true)

                    TimeUtils.setTimeout(fun() {
                        val r = RandomUtils.randomIntInRange(80, 120)
                        Mouse.rClick(r)

                        TimeUtils.setTimeout(fun() {
                            Mouse.setUsingPotion(false)
                            TimeUtils.setTimeout(fun() {
                                Inventory.setInvItem("sword")

                                TimeUtils.setTimeout(fun() {
                                    Mouse.setRunningAway(false)
                                }, RandomUtils.randomIntInRange(500, 700))
                            }, RandomUtils.randomIntInRange(80, 120))
                        }, r + RandomUtils.randomIntInRange(80, 120))
                    }, RandomUtils.randomIntInRange(100, 200))
                }, RandomUtils.randomIntInRange(50, 100))
            }
        }

        if (run && !facingAway) {
            Mouse.setUsingProjectile(false)
            Mouse.setRunningAway(true)
            TimeUtils.setTimeout(fun() {
                pot(damage)
            }, RandomUtils.randomIntInRange(300, 500))
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
                ChatUtils.info("About to use $dmg")
                TimeUtils.setTimeout(fun() {
                    val r = RandomUtils.randomIntInRange(1900, 2050)
                    Mouse.rClick(r)
                    TimeUtils.setTimeout(fun() {
                        Inventory.setInvItem("sword")
                        TimeUtils.setTimeout(fun() {
                            Mouse.setRunningAway(false)
                        }, RandomUtils.randomIntInRange(500, 700))
                    }, r + RandomUtils.randomIntInRange(80, 120))
                }, RandomUtils.randomIntInRange(200, 400))
            } else {
                ChatUtils.error("No $dmg potion in inventory")
            }
        }

        if (run && !facingAway) {
            Mouse.setUsingProjectile(false)
            Mouse.setRunningAway(true)
            TimeUtils.setTimeout(fun() {
                pot(damage)
            }, RandomUtils.randomIntInRange(300, 500))
        } else {
            pot(damage)
        }
    }

}
