package best.spaghetcodes.catdueller.bot.player

import best.spaghetcodes.catdueller.CatDueller
import best.spaghetcodes.catdueller.utils.TimeUtils
import net.minecraft.client.settings.KeyBinding
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent

/**
 * Handles player movement input simulation including walking, jumping, sprinting, and sneaking.
 * Provides both standard methods (respecting bot toggle state) and force methods (bypassing toggle checks).
 */
object Movement {

    /** Whether the forward key is currently pressed. */
    private var forward = false

    /** Whether the backward key is currently pressed. */
    private var backward = false

    /** Whether the left strafe key is currently pressed. */
    private var left = false

    /** Whether the right strafe key is currently pressed. */
    private var right = false

    /** Whether the jump key is currently pressed. */
    private var jumping = false

    /** Whether the sprint key is currently pressed. */
    private var sprinting = false

    /** Whether the sneak key is currently pressed. */
    private var sneaking = false

    /** Whether long jump mode is currently active. */
    private var longJumpActive = false

    /** Previous GUI open state for detecting GUI close events. */
    private var lastGuiState = false

    /** Whether waiting for player to land to execute long jump. */
    private var longJumpWaitingForGround = false

    /** Previous onGround state for detecting landing events. */
    private var wasOnGroundLastTick = false

    /**
     * Starts moving forward. Requires bot to be toggled on.
     */
    fun startForward() {
        if (CatDueller.bot?.toggled() == true) {
            forward = true
            KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindForward.keyCode, true)
        }
    }

    /**
     * Stops moving forward. Requires bot to be toggled on.
     */
    fun stopForward() {
        if (CatDueller.bot?.toggled() == true) {
            forward = false
            KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindForward.keyCode, false)
        }
    }

    /**
     * Starts moving backward. Requires bot to be toggled on.
     */
    fun startBackward() {
        if (CatDueller.bot?.toggled() == true) {
            backward = true
            KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindBack.keyCode, true)
        }
    }

    /**
     * Stops moving backward. Requires bot to be toggled on.
     */
    fun stopBackward() {
        if (CatDueller.bot?.toggled() == true) {
            backward = false
            KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindBack.keyCode, false)
        }
    }

    /**
     * Starts strafing left. Requires bot to be toggled on.
     */
    fun startLeft() {
        if (CatDueller.bot?.toggled() == true) {
            left = true
            KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindLeft.keyCode, true)
        }
    }

    /**
     * Stops strafing left. Requires bot to be toggled on.
     */
    fun stopLeft() {
        if (CatDueller.bot?.toggled() == true) {
            left = false
            KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindLeft.keyCode, false)
        }
    }

    /**
     * Starts strafing right. Requires bot to be toggled on.
     */
    fun startRight() {
        if (CatDueller.bot?.toggled() == true) {
            right = true
            KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindRight.keyCode, true)
        }
    }

    /**
     * Stops strafing right. Requires bot to be toggled on.
     */
    fun stopRight() {
        if (CatDueller.bot?.toggled() == true) {
            right = false
            KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindRight.keyCode, false)
        }
    }

    /**
     * Starts jumping (holds jump key). Requires bot to be toggled on.
     */
    fun startJumping() {
        if (CatDueller.bot?.toggled() == true) {
            jumping = true
            KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindJump.keyCode, true)
            if (CatDueller.config?.combatLogs == true) {
                println("Movement.startJumping() executed - jumping state set to true")
            }
        } else if (CatDueller.config?.combatLogs == true) {
            println("Movement.startJumping() BLOCKED - bot not toggled")
        }
    }

    /**
     * Stops jumping (releases jump key). Requires bot to be toggled on.
     */
    fun stopJumping() {
        if (CatDueller.bot?.toggled() == true) {
            jumping = false
            KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindJump.keyCode, false)
            if (CatDueller.config?.combatLogs == true) {
                println("Movement.stopJumping() executed - jumping state set to false")
            }
        } else if (CatDueller.config?.combatLogs == true) {
            println("Movement.stopJumping() BLOCKED - bot not toggled")
        }
    }

    /**
     * Starts sprinting. Requires bot to be toggled on.
     */
    fun startSprinting() {
        if (CatDueller.bot?.toggled() == true) {
            sprinting = true
            KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindSprint.keyCode, true)
        }
    }

    /**
     * Stops sprinting. Requires bot to be toggled on.
     */
    fun stopSprinting() {
        if (CatDueller.bot?.toggled() == true) {
            sprinting = false
            KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindSprint.keyCode, false)
        }
    }

    /**
     * Starts sneaking. Requires bot to be toggled on.
     */
    fun startSneaking() {
        if (CatDueller.bot?.toggled() == true) {
            sneaking = true
            KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindSneak.keyCode, true)
        }
    }

    /**
     * Stops sneaking. Requires bot to be toggled on.
     */
    fun stopSneaking() {
        if (CatDueller.bot?.toggled() == true) {
            sneaking = false
            KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindSneak.keyCode, false)
        }
    }

    /**
     * Performs a single jump by pressing and releasing the jump key.
     *
     * @param holdDuration Time in milliseconds to hold the jump key.
     */
    fun singleJump(holdDuration: Int) {
        if (CatDueller.config?.combatLogs == true) {
            println("Movement.singleJump() called with duration: $holdDuration, bot toggled: ${CatDueller.bot?.toggled()}")
        }
        startJumping()
        TimeUtils.setTimeout(this::stopJumping, holdDuration)
    }

    /**
     * Stops all movement inputs and random strafing.
     */
    fun clearAll() {
        stopForward()
        stopBackward()
        stopLeft()
        stopRight()
        stopJumping()
        stopSprinting()
        stopSneaking()
        Combat.stopRandomStrafe()
    }

    /**
     * Stops both left and right strafe inputs.
     */
    fun clearLeftRight() {
        stopLeft()
        stopRight()
    }

    /**
     * Swaps the current strafe direction (left becomes right and vice versa).
     */
    fun swapLeftRight() {
        if (left) {
            stopLeft()
            startRight()
        } else if (right) {
            stopRight()
            startLeft()
        }
    }

    /** Returns whether forward movement is active. */
    fun forward(): Boolean {
        return forward
    }

    /** Returns whether backward movement is active. */
    fun backward(): Boolean {
        return backward
    }

    /** Returns whether left strafe is active. */
    fun left(): Boolean {
        return left
    }

    /** Returns whether right strafe is active. */
    fun right(): Boolean {
        return right
    }

    /** Returns whether jumping is active. */
    fun jumping(): Boolean {
        return jumping
    }

    /** Returns whether sprinting is active. */
    fun sprinting(): Boolean {
        return sprinting
    }

    /** Returns whether sneaking is active. */
    fun sneaking(): Boolean {
        return sneaking
    }

    /**
     * Forces forward movement, bypassing bot toggle check.
     * Used by MovementRecorder for playback.
     */
    fun forceStartForward() {
        forward = true
        KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindForward.keyCode, true)
    }

    /**
     * Forces stop forward movement, bypassing bot toggle check.
     */
    fun forceStopForward() {
        forward = false
        KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindForward.keyCode, false)
    }

    /**
     * Forces backward movement, bypassing bot toggle check.
     */
    fun forceStartBackward() {
        backward = true
        KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindBack.keyCode, true)
    }

    /**
     * Forces stop backward movement, bypassing bot toggle check.
     */
    fun forceStopBackward() {
        backward = false
        KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindBack.keyCode, false)
    }

    /**
     * Forces left strafe, bypassing bot toggle check.
     */
    fun forceStartLeft() {
        left = true
        KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindLeft.keyCode, true)
    }

    /**
     * Forces stop left strafe, bypassing bot toggle check.
     */
    fun forceStopLeft() {
        left = false
        KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindLeft.keyCode, false)
    }

    /**
     * Forces right strafe, bypassing bot toggle check.
     */
    fun forceStartRight() {
        right = true
        KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindRight.keyCode, true)
    }

    /**
     * Forces stop right strafe, bypassing bot toggle check.
     */
    fun forceStopRight() {
        right = false
        KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindRight.keyCode, false)
    }

    /**
     * Forces jumping, bypassing bot toggle check.
     */
    fun forceStartJumping() {
        jumping = true
        KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindJump.keyCode, true)
    }

    /**
     * Forces stop jumping, bypassing bot toggle check.
     */
    fun forceStopJumping() {
        jumping = false
        KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindJump.keyCode, false)
    }

    /**
     * Forces sneaking, bypassing bot toggle check.
     */
    fun forceStartSneaking() {
        sneaking = true
        KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindSneak.keyCode, true)
    }

    /**
     * Forces stop sneaking, bypassing bot toggle check.
     */
    fun forceStopSneaking() {
        sneaking = false
        KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindSneak.keyCode, false)
    }

    /**
     * Forces clearing all movement states, bypassing bot toggle check.
     */
    fun forceClearAll() {
        forceStopForward()
        forceStopBackward()
        forceStopLeft()
        forceStopRight()
        forceStopJumping()
        forceStopSneaking()
        Combat.stopRandomStrafe()
    }

    /**
     * Start long jump mode - sets internal state but waits for GUI close to execute
     */
    fun startLongJump() {
        if (CatDueller.bot?.toggled() == true) {
            longJumpActive = true
            // Set internal states but don't execute keybinds yet
            forward = true
            jumping = true
            sprinting = true
        }
    }

    /**
     * Stops long jump mode and resets related tracking variables.
     */
    fun stopLongJump() {
        longJumpActive = false
        longJumpWaitingForGround = false
        wasOnGroundLastTick = false
    }

    /**
     * Returns whether long jump mode is currently active.
     */
    fun isLongJumpActive(): Boolean {
        return longJumpActive
    }

    /**
     * Tick event handler for long jump functionality.
     * Monitors GUI state and ground contact to execute long jumps at the appropriate time.
     *
     * @param ev The client tick event.
     */
    @SubscribeEvent
    fun onTick(ev: TickEvent.ClientTickEvent) {
        if (CatDueller.mc.thePlayer == null) return
        val player = CatDueller.mc.thePlayer

        val currentGuiState = CatDueller.mc.currentScreen != null

        if (lastGuiState && !currentGuiState && longJumpActive) {
            if (CatDueller.bot?.toggled() == true) {
                forward = true
                sprinting = true
                KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindForward.keyCode, true)
                KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindSprint.keyCode, true)

                longJumpWaitingForGround = true
            }
        }

        if (player != null && longJumpWaitingForGround) {
            val currentOnGround = player.onGround
            if (!wasOnGroundLastTick && currentOnGround) {
                if (CatDueller.bot?.toggled() == true) {
                    singleJump(100)
                    longJumpWaitingForGround = false
                }
            }
            wasOnGroundLastTick = currentOnGround
        }

        lastGuiState = currentGuiState
    }
}
