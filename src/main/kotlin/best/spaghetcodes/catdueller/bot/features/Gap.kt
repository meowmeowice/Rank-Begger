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
                TimerUtil.setTimeout(fun () {
                    Mouse.setRunningAway(false)
                }, RandomUtil.randomIntInRange(200, 400))
                val r = RandomUtil.randomIntInRange(1800, 1900)
                Mouse.rClick(r)
                Movement.startJumping()
                Movement.stopForward()
                Movement.startBackward()

                TimerUtil.setTimeout(fun () {
                    Inventory.setInvItem("sword")
                    Movement.stopBackward()
                    Movement.startForward()
                    Mouse.setUsingGap(false)
                }, r + RandomUtil.randomIntInRange(200, 300))
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

            TimerUtil.setTimeout(fun () { gap() }, time)
        } else {
            gap()
        }
    }

}
