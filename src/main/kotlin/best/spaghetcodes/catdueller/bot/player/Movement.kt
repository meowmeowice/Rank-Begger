package best.spaghetcodes.catdueller.bot.player

import best.spaghetcodes.catdueller.CatDueller
import best.spaghetcodes.catdueller.utils.RandomUtils
import best.spaghetcodes.catdueller.utils.TimeUtils
import net.minecraft.client.settings.KeyBinding
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent

object Movement {
    private var forward = false
    private var backward = false
    private var left = false
    private var right = false
    private var jumping = false
    private var sprinting = false
    private var sneaking = false
    
    // Long jump state tracking
    private var longJumpActive = false
    private var lastGuiState = false
    private var longJumpWaitingForGround = false  // Waiting for onGround after GUI closes
    private var wasOnGroundLastTick = false       // Track previous onGround state


    fun startForward() {
        if (CatDueller.bot?.toggled() == true) { // need to do this because the type is Boolean? so it could be null
            forward = true
            KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindForward.keyCode, true)
        }
    }

    fun stopForward() {
        if (CatDueller.bot?.toggled() == true) {
            forward = false
            KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindForward.keyCode, false)
        }
    }

    fun startBackward() {
        if (CatDueller.bot?.toggled() == true) {
            backward = true
            KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindBack.keyCode, true)
        }
    }

    fun stopBackward() {
        if (CatDueller.bot?.toggled() == true) {
            backward = false
            KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindBack.keyCode, false)
        }
    }

    fun startLeft() {
        if (CatDueller.bot?.toggled() == true) {
            left = true
            KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindLeft.keyCode, true)
        }
    }

    fun stopLeft() {
        if (CatDueller.bot?.toggled() == true) {
            left = false
            KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindLeft.keyCode, false)
        }
    }

    fun startRight() {
        if (CatDueller.bot?.toggled() == true) {
            right = true
            KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindRight.keyCode, true)
        }
    }

    fun stopRight() {
        if (CatDueller.bot?.toggled() == true) {
            right = false
            KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindRight.keyCode, false)
        }
    }

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

    fun startSprinting() {
        if (CatDueller.bot?.toggled() == true) {
            sprinting = true
            KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindSprint.keyCode, true)
        }
    }

    fun stopSprinting() {
        if (CatDueller.bot?.toggled() == true) {
            sprinting = false
            KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindSprint.keyCode, false)
        }
    }

    fun startSneaking() {
        if (CatDueller.bot?.toggled() == true) {
            sneaking = true
            KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindSneak.keyCode, true)
        }
    }

    fun stopSneaking() {
        if (CatDueller.bot?.toggled() == true) {
            sneaking = false
            KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindSneak.keyCode, false)
        }
    }

    fun singleJump(holdDuration: Int) {
        if (CatDueller.config?.combatLogs == true) {
            println("Movement.singleJump() called with duration: $holdDuration, bot toggled: ${CatDueller.bot?.toggled()}")
        }
        startJumping()
        TimeUtils.setTimeout(this::stopJumping, holdDuration)
    }

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

    fun clearLeftRight() {
        stopLeft()
        stopRight()
    }

    fun swapLeftRight() {
        if (left) {
            stopLeft()
            startRight()
        } else if (right) {
            stopRight()
            startLeft()
        }
    }

    fun forward(): Boolean {
        return forward
    }

    fun backward(): Boolean {
        return backward
    }

    fun left(): Boolean {
        return left
    }

    fun right(): Boolean {
        return right
    }

    fun jumping(): Boolean {
        return jumping
    }

    fun sprinting(): Boolean {
        return sprinting
    }

    fun sneaking(): Boolean {
        return sneaking
    }

    // Force movement methods for MovementRecorder (bypass bot toggle check)
    fun forceStartForward() {
        forward = true
        KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindForward.keyCode, true)
    }

    fun forceStopForward() {
        forward = false
        KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindForward.keyCode, false)
    }

    fun forceStartBackward() {
        backward = true
        KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindBack.keyCode, true)
    }

    fun forceStopBackward() {
        backward = false
        KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindBack.keyCode, false)
    }

    fun forceStartLeft() {
        left = true
        KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindLeft.keyCode, true)
    }

    fun forceStopLeft() {
        left = false
        KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindLeft.keyCode, false)
    }

    fun forceStartRight() {
        right = true
        KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindRight.keyCode, true)
    }

    fun forceStopRight() {
        right = false
        KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindRight.keyCode, false)
    }

    fun forceStartJumping() {
        jumping = true
        KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindJump.keyCode, true)
    }

    fun forceStopJumping() {
        jumping = false
        KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindJump.keyCode, false)
    }

    fun forceStartSneaking() {
        sneaking = true
        KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindSneak.keyCode, true)
    }

    fun forceStopSneaking() {
        sneaking = false
        KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindSneak.keyCode, false)
    }

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
     * Stop long jump mode
     */
    fun stopLongJump() {
        longJumpActive = false
        longJumpWaitingForGround = false
        wasOnGroundLastTick = false
        // Don't stop any actions - let jump finish naturally and keep forward/sprint
    }
    
    /**
     * Check if long jump is active
     */
    fun isLongJumpActive(): Boolean {
        return longJumpActive
    }
    
    /**
     * GUI state monitoring for long jump functionality and jump queue processing
     */
    @SubscribeEvent
    fun onTick(ev: TickEvent.ClientTickEvent) {
        if (CatDueller.mc.thePlayer == null) return
        val player = CatDueller.mc.thePlayer
        
        val currentGuiState = CatDueller.mc.currentScreen != null
        
        // Detect GUI closing (was open, now closed)
        if (lastGuiState && !currentGuiState && longJumpActive) {
            // GUI just closed and long jump is active - start forward and sprint, wait for ground to jump
            if (CatDueller.bot?.toggled() == true) {
                // Start forward and sprint immediately
                forward = true
                sprinting = true
                KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindForward.keyCode, true)
                KeyBinding.setKeyBindState(CatDueller.mc.gameSettings.keyBindSprint.keyCode, true)
                
                // Set flag to wait for onGround to jump
                longJumpWaitingForGround = true
                println("Long jump started: GUI closed, forward + sprint active, waiting for onGround to jump")
            }
        }
        
        // Check for onGround transition to execute jump
        
        if (player != null && longJumpWaitingForGround) {
            val currentOnGround = player.onGround
            // Detect landing (not on ground -> on ground)
            if (!wasOnGroundLastTick && currentOnGround) {
                // Player just landed - execute single jump
                if (CatDueller.bot?.toggled() == true) {
                    singleJump(100)  // Single jump with 100ms duration
                    println("Long jump executed: Player landed, executing single jump")
                    
                    // Stop waiting for ground
                    longJumpWaitingForGround = false
                }
            }
            
            wasOnGroundLastTick = currentOnGround
        }
        
        lastGuiState = currentGuiState
    }


}
