package best.spaghetcodes.catdueller.bot.player

import best.spaghetcodes.catdueller.utils.RandomUtils
import best.spaghetcodes.catdueller.utils.TimeUtils

/**
 * Handles combat-related movement techniques such as W-tapping, sprint resetting,
 * and strafing patterns used during PvP encounters.
 */
object Combat {

    /** Whether random strafing is currently active. */
    private var randomStrafe = false

    /** Minimum interval in milliseconds between strafe direction changes. */
    private var randomStrafeMin = 0

    /** Maximum interval in milliseconds between strafe direction changes. */
    private var randomStrafeMax = 0

    /**
     * Performs a W-tap by briefly releasing and re-pressing the forward key.
     * This technique is used to reset sprint and gain a knockback advantage.
     *
     * @param duration Time in milliseconds to hold the forward key released.
     */
    fun wTap(duration: Int) {
        Movement.stopForward()
        TimeUtils.setTimeout(Movement::startForward, duration)
    }

    /**
     * Resets sprint by briefly stopping and restarting the sprint key.
     * Similar to W-tap but only affects the sprint state.
     *
     * @param duration Time in milliseconds to hold sprint released.
     */
    fun sprintReset(duration: Int) {
        Movement.stopSprinting()
        TimeUtils.setTimeout(Movement::startSprinting, duration)
    }

    /**
     * Starts random strafing behavior that alternates between left and right
     * at random intervals within the specified range.
     *
     * @param min Minimum interval in milliseconds between direction changes.
     * @param max Maximum interval in milliseconds between direction changes.
     */
    fun startRandomStrafe(min: Int, max: Int) {
        randomStrafeMin = min
        randomStrafeMax = max
        if (!randomStrafe) {
            randomStrafe = true
            randomStrafeFunc()
        }
    }

    /**
     * Stops the random strafing behavior and clears any active left/right movement.
     */
    fun stopRandomStrafe() {
        if (randomStrafe) {
            randomStrafe = false
            Movement.clearLeftRight()
        }
    }

    /**
     * Internal recursive function that handles the random strafe direction changes.
     * Alternates between left and right movement at random intervals.
     */
    private fun randomStrafeFunc() {
        if (randomStrafe) {
            if (!(Movement.left() || Movement.right())) {
                if (RandomUtils.randomBool()) {
                    Movement.startLeft()
                } else {
                    Movement.startRight()
                }
            } else {
                Movement.swapLeftRight()
            }
            TimeUtils.setTimeout(this::randomStrafeFunc, RandomUtils.randomIntInRange(randomStrafeMin, randomStrafeMax))
        }
    }

}
