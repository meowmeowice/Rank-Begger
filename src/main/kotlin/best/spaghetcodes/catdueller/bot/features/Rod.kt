package best.spaghetcodes.catdueller.bot.features

import best.spaghetcodes.catdueller.CatDueller
import best.spaghetcodes.catdueller.bot.player.Inventory
import best.spaghetcodes.catdueller.bot.player.Mouse
import best.spaghetcodes.catdueller.utils.RandomUtils
import best.spaghetcodes.catdueller.utils.TimeUtils
import net.minecraft.client.Minecraft

interface Rod {

    private val mc_: Minecraft
        get() = Minecraft.getMinecraft()


    fun useRod(isDefensive: Boolean = false) {
        // If rod is already in use, immediately retract it first
        if (Mouse.isUsingProjectile() || CatDueller.bot?.rodRetractTimeout != null) {
            immediateRetractRod()
        }

        // Stop attacking based on config
        if (CatDueller.config?.holdLeftClick == true) {
            Mouse.stopHoldLeftClick()
        } else {
            Mouse.stopLeftAC()
        }

        Mouse.setUsingProjectile(true)
        // Mark rod usage type
        CatDueller.bot?.isDefensiveRod = isDefensive

        TimeUtils.setTimeout(fun() {
            // Step 1: First switch to sword to ensure any existing rod is retracted
            Inventory.setInvItem("sword")
            TimeUtils.setTimeout(fun() {
                // Step 2: Now switch to rod
                Inventory.setInvItem("rod")
                TimeUtils.setTimeout(fun() {
                    // Step 3: Use rod (right click)
                    val r = RandomUtils.randomIntInRange(100, 200)
                    Mouse.rClick(r)
                    // Don't set setUsingProjectile(false) here - let retractRod() handle it

                    // Set up normal retract timeout (Step 4: Auto-retract by switching to sword)
                    CatDueller.bot?.rodRetractTimeout = TimeUtils.setTimeout(fun() {
                        retractRod()  // This will switch to sword to retract
                    }, r + RandomUtils.randomIntInRange(100, 200))
                }, RandomUtils.randomIntInRange(50, 90))
            }, RandomUtils.randomIntInRange(30, 60))  // Short delay to ensure sword switch completes
        }, RandomUtils.randomIntInRange(10, 30))
    }

    fun retractRod() {
        // First, stop any ongoing right click to ensure clean state
        Mouse.rClickUp()

        // Immediately allow next projectile usage (for rapid rod usage)
        Mouse.setUsingProjectile(false)

        // Retract rod by switching to sword (this automatically retracts the rod)
        if (mc_.thePlayer.heldItem != null && !mc_.thePlayer.heldItem.unlocalizedName.lowercase().contains("bow")) {
            Inventory.setInvItem("sword")
        }

        // Temporarily disable tracking to prevent immediate aim snap-back
        val wasTracking = Mouse.isTracking()
        if (wasTracking) {
            Mouse.stopTracking()
            // Re-enable tracking after rod retraction is complete (200-300ms total)
            TimeUtils.setTimeout({
                Mouse.startTracking()
            }, RandomUtils.randomIntInRange(100, 200))  // Additional delay for smooth aim restoration
        }

        // Don't start attacking here - let the distance control logic in onTick handle it 

        // Clear the timeout and defensive flag since we're retracting now
        CatDueller.bot?.rodRetractTimeout = null
        CatDueller.bot?.isDefensiveRod = false
    }

    fun immediateRetractRod() {
        // Cancel the normal retract timeout
        CatDueller.bot?.rodRetractTimeout?.cancel()
        CatDueller.bot?.rodRetractTimeout = null

        // Stop using projectile flag immediately
        Mouse.setUsingProjectile(false)

        // Immediately retract
        retractRod()
    }

}
