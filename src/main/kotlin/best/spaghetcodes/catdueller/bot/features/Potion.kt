package best.spaghetcodes.catdueller.bot.features

import best.spaghetcodes.catdueller.bot.player.Combat
import best.spaghetcodes.catdueller.bot.player.Inventory
import best.spaghetcodes.catdueller.bot.player.Mouse
import best.spaghetcodes.catdueller.utils.client.ChatUtil
import best.spaghetcodes.catdueller.utils.client.TimerUtil
import best.spaghetcodes.catdueller.utils.system.RandomUtil

/**
 * Interface providing potion usage functionality for combat healing.
 *
 * Supports both splash potions (instant use) and drinkable potions (extended use time),
 * with optional retreat behavior for safer consumption during combat.
 */
interface Potion {

    var lastPotion: Long

    fun useSplashPotion(damage: Int, run: Boolean, facingAway: Boolean) {
        lastPotion = System.currentTimeMillis()
        fun pot(dmg: Int) {
            Mouse.stopLeftAC()
            // Stop strafe when using potion
            Combat.stopRandomStrafe()
            
            if (Inventory.setInvItemByDamage(dmg)) {
                ChatUtil.info("About to splash $dmg")
                TimerUtil.setTimeout(fun() {
                    Mouse.setUsingPotion(true)

                    TimerUtil.setTimeout(fun () {
                        val r = RandomUtil.randomIntInRange(80, 120)
                        Mouse.rClick(r)

                        TimerUtil.setTimeout(fun () {
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

    fun usePotion(damage: Int, run: Boolean, facingAway: Boolean) {
        fun pot(dmg: Int) {
            Mouse.stopLeftAC()
            // Stop strafe when using potion
            Combat.stopRandomStrafe()
            
            if (Inventory.setInvItemByDamage(dmg)) {
                ChatUtil.info("About to use $dmg")
                TimerUtil.setTimeout(fun () {
                    val r = RandomUtil.randomIntInRange(1900, 2050)
                    Mouse.rClick(r)
                    TimerUtil.setTimeout(fun () {
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
