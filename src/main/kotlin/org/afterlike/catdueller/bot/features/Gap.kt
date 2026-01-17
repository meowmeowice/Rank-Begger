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

    /**
     * Timestamp of the last golden apple usage in milliseconds.
     * Used to enforce cooldowns and prevent rapid gap consumption.
     */
    var lastGap: Long

    /**
     * Consumes a golden apple with retreat behavior.
     *
     * The method performs the following steps:
     * 1. Sets runningaway to false first if currently running away
     * 2. Records the usage timestamp for cooldown tracking
     * 3. Optionally retreats from the opponent if conditions are met
     * 4. Switches to golden apple and consumes it (2100-2200ms eat time)
     * 5. During gap usage, stops forward movement and moves backward
     * 6. Switches back to sword after consumption
     *
     * Retreat delay before eating is adjusted based on distance:
     * - Close range (0-7 blocks): 2200-2600ms delay
     * - Medium range (7-15 blocks): 1700-2200ms delay
     * - Long range (15+ blocks): 1400-1700ms delay
     *
     * @param distance The distance to the opponent in blocks, affects retreat timing
     * @param run Whether to retreat before consuming the golden apple
     * @param facingAway Whether the player is already facing away from the opponent
     */
    fun useGap(distance: Float, run: Boolean, facingAway: Boolean) {
        lastGap = System.currentTimeMillis()
        
        // Set runningaway to false first if currently running away
        if (Mouse.isRunningAway()) {
            Mouse.setRunningAway(false)
        }
        
        fun gap() {
            Mouse.stopLeftAC()
            if (Inventory.setInvItem("gold")) {
                ChatUtil.info("About to gap")
                
                // Set gap usage state
                Mouse.setUsingGap(true)
                
                // Stop forward movement and start backward movement during gap
                Movement.stopForward()
                Movement.startBackward()
                Movement.startJumping()  // Keep jumping during gap for better mobility
                
                val r = RandomUtil.randomIntInRange(1800, 1900)
                Mouse.rClick(r)

                TimerUtil.setTimeout(fun() {
                    Inventory.setInvItem("sword")

                    TimerUtil.setTimeout(fun() {
                        // Stop backward movement after gap is consumed
                        Movement.stopBackward()
                        // Clear gap usage state
                        Mouse.setUsingGap(false)
                        // Don't automatically start forward - let normal movement logic handle it
                    }, RandomUtil.randomIntInRange(40, 70))
                }, r + RandomUtil.randomIntInRange(40, 70))
            } else {
                // If failed to switch to gap, clear the usage state
                Mouse.setUsingGap(false)
            }
        }

        val time = when (distance) {
            in 0f..7f -> RandomUtil.randomIntInRange(2200, 2600)
            in 7f..15f -> RandomUtil.randomIntInRange(1700, 2200)
            else -> RandomUtil.randomIntInRange(1400, 1700)
        }
        
        if (run && !facingAway) {
            Mouse.setUsingProjectile(false)
            Movement.startJumping()

            TimerUtil.setTimeout(fun() { gap() }, time)
        } else {
            gap()
        }
    }

}
