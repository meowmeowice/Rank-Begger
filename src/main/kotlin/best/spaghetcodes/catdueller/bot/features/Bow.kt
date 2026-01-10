package best.spaghetcodes.catdueller.bot.features

import best.spaghetcodes.catdueller.CatDueller
import best.spaghetcodes.catdueller.bot.player.Inventory
import best.spaghetcodes.catdueller.bot.player.Mouse
import best.spaghetcodes.catdueller.bot.state.StateManager
import best.spaghetcodes.catdueller.utils.client.TimerUtil
import best.spaghetcodes.catdueller.utils.system.RandomUtil

/**
 * Interface providing bow usage functionality for combat scenarios.
 *
 * Handles the complete bow shooting sequence including inventory switching,
 * charge timing based on distance, and proper state management.
 */
interface Bow {

    /**
     * Executes a bow shot sequence with distance-based charge timing.
     *
     * The method performs the following steps:
     * 1. Validates that no other projectile action is in progress
     * 2. Stops any ongoing attack actions
     * 3. Switches to bow and charges based on target distance
     * 4. Releases the shot and switches back to sword
     * 5. Invokes the callback when the sequence completes
     *
     * Charge times are adjusted based on distance:
     * - Close range (0-15 blocks): 700-900ms charge
     * - Long range (15+ blocks): 900-1100ms charge
     *
     * @param distance The distance to the target in blocks, used to calculate charge time
     * @param cb Callback function invoked after the bow sequence completes successfully
     */
    fun useBow(distance: Float, cb: () -> Unit) {
        if (Mouse.isUsingProjectile() || Mouse.isBlockingArrow()) {
            return
        }

        if (CatDueller.config?.holdLeftClick == true) {
            Mouse.stopHoldLeftClick()
        } else {
            Mouse.stopLeftAC()
        }

        Mouse.setUsingProjectile(true)
        TimerUtil.setTimeout(fun() {
            Inventory.setInvItem("bow")
            TimerUtil.setTimeout(fun() {
                val r = when (distance) {
                    in 0f..7f -> RandomUtil.randomIntInRange(700, 900)
                    in 7f..15f -> RandomUtil.randomIntInRange(900, 1100)
                    else -> RandomUtil.randomIntInRange(900, 1100)
                }
                Mouse.rClick(r)
                TimerUtil.setTimeout(fun() {
                    Mouse.setUsingProjectile(false)
                    Inventory.setInvItem("sword")
                    TimerUtil.setTimeout(fun() {
                        if (StateManager.state == StateManager.States.PLAYING) {
                            cb()
                        }
                    }, RandomUtil.randomIntInRange(100, 200))
                }, r + RandomUtil.randomIntInRange(100, 150))
            }, RandomUtil.randomIntInRange(100, 200))
        }, RandomUtil.randomIntInRange(50, 100))
    }

}
