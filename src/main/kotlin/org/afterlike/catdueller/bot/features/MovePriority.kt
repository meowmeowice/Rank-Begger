package org.afterlike.catdueller.bot.features

import org.afterlike.catdueller.bot.player.Combat
import org.afterlike.catdueller.bot.player.Movement
import org.afterlike.catdueller.utils.system.RandomUtil

/**
 * Interface for handling movement priority decisions during combat.
 *
 * Manages lateral strafing behavior with support for random strafing patterns
 * and wall avoidance. Uses a priority system to determine optimal movement direction.
 */
interface MovePriority {

    /**
     * Handles movement decisions based on priority values and strafing mode.
     *
     * Movement priority values indicate the desirability of moving in each direction,
     * with higher values indicating stronger preference. Values above 15 typically
     * indicate wall avoidance requirements.
     *
     * Behavior modes:
     * - Clear mode: Stops all strafing and clears lateral movement
     * - Random strafe mode: Uses randomized strafing unless wall avoidance is needed
     * - Priority mode: Moves in the direction with higher priority, or randomly if equal
     *
     * @param clear If true, stops all strafing and clears lateral movement
     * @param randomStrafe If true, enables random strafing pattern (when not clearing)
     * @param movePriority List containing movement priorities where index 0 is left priority
     *                     and index 1 is right priority. Higher values indicate stronger preference.
     */
    fun handle(clear: Boolean, randomStrafe: Boolean, movePriority: ArrayList<Int>) {
        if (clear) {
            Combat.stopRandomStrafe()
            Movement.clearLeftRight()
        } else {
            if (randomStrafe) {
                if (movePriority[0] > 15 || movePriority[1] > 15) {
                    Combat.stopRandomStrafe()
                    if (movePriority[0] > movePriority[1]) {
                        Movement.stopRight()
                        Movement.startLeft()
                    } else if (movePriority[1] > movePriority[0]) {
                        Movement.stopLeft()
                        Movement.startRight()
                    }
                } else {
                    Combat.startRandomStrafe(300, 1000)
                }
            } else {
                Combat.stopRandomStrafe()
                if (movePriority[0] > movePriority[1]) {
                    Movement.stopRight()
                    Movement.startLeft()
                } else if (movePriority[1] > movePriority[0]) {
                    Movement.stopLeft()
                    Movement.startRight()
                } else {
                    if (RandomUtil.randomBool()) {
                        Movement.startLeft()
                    } else {
                        Movement.startRight()
                    }
                }
            }
        }
    }

}
