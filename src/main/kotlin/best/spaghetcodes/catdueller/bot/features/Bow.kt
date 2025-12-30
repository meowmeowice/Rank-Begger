package best.spaghetcodes.catdueller.bot.features

import best.spaghetcodes.catdueller.CatDueller
import best.spaghetcodes.catdueller.bot.StateManager
import best.spaghetcodes.catdueller.bot.player.Inventory
import best.spaghetcodes.catdueller.bot.player.Mouse
import best.spaghetcodes.catdueller.utils.RandomUtils
import best.spaghetcodes.catdueller.utils.TimeUtils

interface Bow {

    fun useBow(distance: Float, cb: () -> Unit) {
        // Prevent repeated bow usage if already using projectile or blocking arrow
        if (Mouse.isUsingProjectile() || Mouse.isBlockingArrow()) {
            return
        }
        
        // Stop attacking based on config
        if (CatDueller.config?.holdLeftClick == true) {
            Mouse.stopHoldLeftClick()
        } else {
            Mouse.stopLeftAC()
        }
        
        // Use bow directly without evasive maneuver (dodge is only for enemy bow)
        Mouse.setUsingProjectile(true)
        TimeUtils.setTimeout(fun () {
            Inventory.setInvItem("bow")
            TimeUtils.setTimeout(fun () {
                val r = when (distance) {
                    in 0f..7f -> RandomUtils.randomIntInRange(700, 900)
                    in 7f..15f -> RandomUtils.randomIntInRange(700, 900)
                    else -> RandomUtils.randomIntInRange(900, 1100)
                }
                Mouse.rClick(r)
                TimeUtils.setTimeout(fun () {
                    Mouse.setUsingProjectile(false)
                    Inventory.setInvItem("sword")
                    TimeUtils.setTimeout(fun () {
                        if (StateManager.state == StateManager.States.PLAYING) {
                            // Don't start attacking here - let the distance control logic in onTick handle it
                            cb()
                        }
                    }, RandomUtils.randomIntInRange(100, 200))
                }, r + RandomUtils.randomIntInRange(100, 150))
            }, RandomUtils.randomIntInRange(100, 200))
        }, RandomUtils.randomIntInRange(50, 100))
    }

}
