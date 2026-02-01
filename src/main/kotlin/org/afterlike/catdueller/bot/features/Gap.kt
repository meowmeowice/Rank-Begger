package org.afterlike.catdueller.bot.features

import org.afterlike.catdueller.bot.player.Inventory
import org.afterlike.catdueller.bot.player.Mouse
import org.afterlike.catdueller.bot.player.Movement
import org.afterlike.catdueller.utils.client.ChatUtil
import org.afterlike.catdueller.utils.client.TimerUtil
import org.afterlike.catdueller.utils.system.RandomUtil

/**
 * Interface providing golden apple (gap) consumption functionality.
 *
 * Handles the complete gap eating sequence including retreat behavior,
 * inventory management, and state tracking to prevent spam usage.
 */
interface Gap {

    var lastGap: Long

    fun useGap(distance: Float, run: Boolean, facingAway: Boolean) {
        lastGap = System.currentTimeMillis()
        fun gap() {
            Mouse.stopLeftAC()
            if (Inventory.setInvItem("apple")) {
                ChatUtil.info("About to gap")
                Mouse.setUsingGap(true)
                
                // Ensure right click is released before starting
                Mouse.rClickUp()
                
                TimerUtil.setTimeout(fun() {
                    Mouse.setRunningAway(false)
                }, RandomUtil.randomIntInRange(200, 400))
                val r = RandomUtil.randomIntInRange(1700, 1800)
                Mouse.rClick(r)
                Movement.startJumping()
                
                Movement.stopForward()
                Movement.startBackward()
            
                TimerUtil.setTimeout(fun() {
                    Inventory.setInvItem("sword")
                    Movement.stopBackward()
                    Movement.startForward()
                    Mouse.setUsingGap(false)
                }, r + RandomUtil.randomIntInRange(100, 200))
            }
        }

        val time = when (distance) {
            in 0f..7f -> RandomUtil.randomIntInRange(2200, 2600)
            in 7f..15f -> RandomUtil.randomIntInRange(1700, 2200)
            else -> RandomUtil.randomIntInRange(1400, 1700)
        }
        if (run && !facingAway) {
            Mouse.setUsingProjectile(false)
            Mouse.setRunningAway(true)
            Movement.startJumping()

            TimerUtil.setTimeout(fun() { gap() }, time)
        } else {
            TimerUtil.setTimeout(fun() { gap() }, RandomUtil.randomIntInRange(100, 200))
        }
    }

}
