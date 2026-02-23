package org.afterlike.catdueller.bot.features

import net.minecraft.client.Minecraft
import org.afterlike.catdueller.CatDueller
import org.afterlike.catdueller.bot.player.Inventory
import org.afterlike.catdueller.bot.player.Mouse
import org.afterlike.catdueller.utils.client.TimerUtil
import org.afterlike.catdueller.utils.system.RandomUtil

/**
 * Interface providing fishing rod usage functionality for combat.
 *
 * Handles the complete rod cast and retract sequence, supporting both offensive
 * and defensive rod usage. Manages state tracking, automatic retraction,
 * and smooth aim restoration after rod actions.
 */
interface Rod {

    /**
     * Minecraft client instance accessor for player state checks.
     */
    private val mc_: Minecraft
        get() = Minecraft.getMinecraft()

    /**
     * Casts the fishing rod with proper state management and automatic retraction.
     *
     * The method performs the following steps:
     * 1. Retracts any existing rod if one is in use
     * 2. Stops ongoing attack actions
     * 3. Switches to sword then to rod (ensures clean rod state)
     * 4. Casts the rod and schedules automatic retraction
     *
     * @param isDefensive Whether this rod usage is defensive (e.g., to create distance)
     *                    versus offensive (e.g., combo setup). Defaults to false.
     */
    fun useRod(isDefensive: Boolean = false) {
        if (Mouse.lClickDown) return  // Don't interrupt block breaking
        if (Mouse.isUsingProjectile() || CatDueller.bot?.rodRetractTimeout != null) {
            immediateRetractRod()
        }

        if (CatDueller.config?.holdLeftClick == true) {
            Mouse.stopHoldLeftClick()
        } else {
            Mouse.stopLeftAC()
        }

        Mouse.setUsingProjectile(true)
        CatDueller.bot?.isDefensiveRod = isDefensive

        TimerUtil.setTimeout(fun() {
            Inventory.setInvItem("sword")
            TimerUtil.setTimeout(fun() {
                Inventory.setInvItem("rod")
                TimerUtil.setTimeout(fun() {
                    Inventory.setInvItem("rod") // Force rod in hand right before cast
                    val r = RandomUtil.randomIntInRange(100, 200)
                    Mouse.rClick(r)

                    CatDueller.bot?.rodRetractTimeout = TimerUtil.setTimeout(fun() {
                        retractRod()
                    }, r + RandomUtil.randomIntInRange(100, 200))
                }, RandomUtil.randomIntInRange(80, 120))
            }, RandomUtil.randomIntInRange(50, 80))
        }, RandomUtil.randomIntInRange(10, 30))
    }

    /**
     * Retracts the fishing rod and restores combat state.
     *
     * Handles rod retraction by switching to sword (which automatically retracts
     * any cast rod). Temporarily disables aim tracking to prevent jarring aim
     * snap-back, then smoothly restores tracking after a short delay.
     * Clears rod-related state flags upon completion.
     */
    fun retractRod() {
        Mouse.rClickUp()
        Mouse.setUsingProjectile(false)

        if (mc_.thePlayer.heldItem != null && !mc_.thePlayer.heldItem.unlocalizedName.lowercase().contains("bow") && !Mouse.lClickDown) {
            Inventory.setInvItem("sword")
        }

        val wasTracking = Mouse.isTracking()
        if (wasTracking) {
            Mouse.stopTracking()
            TimerUtil.setTimeout({
                Mouse.startTracking()
            }, RandomUtil.randomIntInRange(100, 200))
        }

        CatDueller.bot?.rodRetractTimeout = null
        CatDueller.bot?.isDefensiveRod = false
    }

    /**
     * Immediately retracts the fishing rod, canceling any pending retraction timer.
     *
     * Used when a new rod action needs to be performed before the current one
     * completes, or when immediate rod retraction is required for other actions.
     */
    fun immediateRetractRod() {
        CatDueller.bot?.rodRetractTimeout?.cancel()
        CatDueller.bot?.rodRetractTimeout = null
        Mouse.setUsingProjectile(false)
        retractRod()
    }

}
