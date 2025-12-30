package best.spaghetcodes.catdueller.bot.features

import best.spaghetcodes.catdueller.bot.player.Combat
import best.spaghetcodes.catdueller.bot.player.Movement
import best.spaghetcodes.catdueller.utils.RandomUtils

interface MovePriority {

    fun handle(clear: Boolean, randomStrafe: Boolean, movePriority: ArrayList<Int>) {
        if (clear) {
            Combat.stopRandomStrafe()
            Movement.clearLeftRight()
        } else {
            if (randomStrafe) {
                // Wall detection override: if random strafe would move into wall, force opposite direction
                if (movePriority[0] > 15 || movePriority[1] > 15) {
                    // High priority movement detected (likely wall avoidance)
                    // Stop random strafe and force wall avoidance direction
                    Combat.stopRandomStrafe()
                    if (movePriority[0] > movePriority[1]) {
                        Movement.stopRight()
                        Movement.startLeft()
                    } else if (movePriority[1] > movePriority[0]) {
                        Movement.stopLeft()
                        Movement.startRight()
                    }
                    // Don't restart random strafe immediately - let wall avoidance take control
                } else {
                    // No wall conflict, use normal random strafe
                    Combat.startRandomStrafe(500, 1000)
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
                    if (RandomUtils.randomBool()) {
                        Movement.startLeft()
                    } else {
                        Movement.startRight()
                    }
                }
            }
        }
    }

}
