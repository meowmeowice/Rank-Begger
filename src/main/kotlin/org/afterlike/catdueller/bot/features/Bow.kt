package org.afterlike.catdueller.bot.features

import org.afterlike.catdueller.CatDueller
import org.afterlike.catdueller.bot.player.Inventory
import org.afterlike.catdueller.bot.player.Mouse
import org.afterlike.catdueller.bot.state.StateManager
import org.afterlike.catdueller.utils.client.TimerUtil
import org.afterlike.catdueller.utils.system.RandomUtil

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
        if (Mouse.isUsingProjectile() || Mouse.isBlockingArrow() || Mouse.lClickDown) {
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
                // Verify we're still holding a bow before right-clicking
                val mc = net.minecraft.client.Minecraft.getMinecraft()
                if (mc.thePlayer?.heldItem?.unlocalizedName?.lowercase()?.contains("bow") != true) {
                    Mouse.setUsingProjectile(false)
                    return
                }
                val r = when (distance) {
                    in 0f..7f -> RandomUtil.randomIntInRange(700, 900)
                    in 7f..15f -> RandomUtil.randomIntInRange(900, 1100)
                    else -> RandomUtil.randomIntInRange(900, 1100)
                }
                Mouse.rClick(r)
                TimerUtil.setTimeout(fun() {
                    Mouse.setUsingProjectile(false)
                    Inventory.setInvItem("sword")
                    if (StateManager.state == StateManager.States.PLAYING) {
                        cb()
                    }
                }, r + RandomUtil.randomIntInRange(100, 150))
            }, RandomUtil.randomIntInRange(100, 200))
        }, RandomUtil.randomIntInRange(50, 100))
    }

    /**
     * Draws the bow and holds it at full charge, calling [onReady] with a release function.
     *
     * The caller decides when to release by invoking the provided release callback.
     * After release, the bow fires, switches back to [returnItem], and calls [cb].
     *
     * @param distance Distance to target, used to calculate minimum charge time
     * @param returnItem Item to switch to after firing (default "sword")
     * @param onReady Called once bow is fully charged, receives a release function
     * @param cb Called after the shot completes and weapon is switched back
     */
    fun useBowHold(distance: Float, returnItem: String = "sword", onReady: (() -> Unit) -> Unit, cb: () -> Unit) {
        if (Mouse.isUsingProjectile() || Mouse.isBlockingArrow() || Mouse.lClickDown) {
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
                // Verify we're still holding a bow before right-clicking
                val mc = net.minecraft.client.Minecraft.getMinecraft()
                if (mc.thePlayer?.heldItem?.unlocalizedName?.lowercase()?.contains("bow") != true) {
                    Mouse.setUsingProjectile(false)
                    return
                }
                val chargeTime = when (distance) {
                    in 0f..7f -> RandomUtil.randomIntInRange(700, 900)
                    in 7f..15f -> RandomUtil.randomIntInRange(700, 900)
                    else -> RandomUtil.randomIntInRange(900, 1100)
                }
                // Start holding right click
                Mouse.startRightClick()

                // After charge time, call onReady with a release function
                TimerUtil.setTimeout(fun() {
                    if (!Mouse.isUsingProjectile()) return // was interrupted

                    val release = fun() {
                        Mouse.rClickUp()
                        TimerUtil.setTimeout(fun() {
                            Mouse.setUsingProjectile(false)
                            Inventory.setInvItem(returnItem)
                            if (StateManager.state == StateManager.States.PLAYING) {
                                cb()
                            }
                        }, RandomUtil.randomIntInRange(100, 150))
                    }
                    onReady(release)
                }, chargeTime)
            }, RandomUtil.randomIntInRange(100, 200))
        }, RandomUtil.randomIntInRange(50, 100))
    }

}
