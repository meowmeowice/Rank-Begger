package best.spaghetcodes.catdueller.bot.features

import best.spaghetcodes.catdueller.bot.player.Inventory
import best.spaghetcodes.catdueller.bot.player.Mouse
import best.spaghetcodes.catdueller.bot.player.Movement
import best.spaghetcodes.catdueller.utils.client.ChatUtil
import best.spaghetcodes.catdueller.utils.client.TimerUtil
import best.spaghetcodes.catdueller.utils.system.RandomUtil

/**
 * Interface providing golden apple (gap) consumption functionality.
 *
 * Handles the complete gap eating sequence including optional retreat behavior,
 * inventory management, and state tracking to prevent spam usage.
 */
interface Gap {

    /**
     * Timestamp of the last golden apple usage in milliseconds.
     * Used to enforce cooldowns and prevent rapid gap consumption.
     */
    var lastGap: Long

    /**
     * Consumes a golden apple with optional retreat behavior based on combat distance.
     *
     * The method performs the following steps:
     * 1. Records the usage timestamp for cooldown tracking
     * 2. Optionally retreats from the opponent if conditions are met
     * 3. Switches to golden apple and consumes it (2100-2200ms eat time)
     * 4. Switches back to sword after consumption
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
        fun gap() {
            Mouse.stopLeftAC()
            if (Inventory.setInvItem("gold")) {
                ChatUtil.info("About to gap")
                val r = RandomUtil.randomIntInRange(2100, 2200)
                Mouse.rClick(r)

                TimerUtil.setTimeout(fun() {
                    Inventory.setInvItem("sword")

                    TimerUtil.setTimeout(fun() {
                        Mouse.setRunningAway(false)
                    }, RandomUtil.randomIntInRange(40, 70))
                }, r + RandomUtil.randomIntInRange(40, 70))
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
            gap()
        }
    }

}
