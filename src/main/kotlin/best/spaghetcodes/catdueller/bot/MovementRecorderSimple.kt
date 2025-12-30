package best.spaghetcodes.catdueller.bot

import best.spaghetcodes.catdueller.CatDueller
import best.spaghetcodes.catdueller.bot.player.Movement
import best.spaghetcodes.catdueller.utils.ChatUtils
import net.minecraft.client.Minecraft

/**
 * Simplified version for testing basic movement recording
 */
object MovementRecorderSimple {
    
    private val mc = Minecraft.getMinecraft()
    
    // Simple movement frame - just WASD for testing
    data class SimpleFrame(
        val forward: Boolean,
        val backward: Boolean,
        val left: Boolean,
        val right: Boolean
    )
    
    private var isRecording = false
    private var recordedFrames = mutableListOf<SimpleFrame>()
    private var isPlaying = false
    private var playbackIndex = 0
    
    /**
     * Start recording (simplified)
     */
    fun startRecording() {
        if (isRecording) {
            ChatUtils.info("Already recording!")
            return
        }
        
        isRecording = true
        recordedFrames.clear()
        ChatUtils.info("Started simple recording - move around then use /movement stoptest")
    }
    
    /**
     * Stop recording
     */
    fun stopRecording() {
        if (!isRecording) {
            ChatUtils.info("Not recording!")
            return
        }
        
        isRecording = false
        ChatUtils.info("Stopped recording - captured ${recordedFrames.size} frames")
    }
    
    /**
     * Start playback
     */
    fun startPlayback() {
        if (recordedFrames.isEmpty()) {
            ChatUtils.info("No recorded frames!")
            return
        }
        
        if (isPlaying) {
            ChatUtils.info("Already playing!")
            return
        }
        
        isPlaying = true
        playbackIndex = 0
        ChatUtils.info("Started playback of ${recordedFrames.size} frames")
    }
    
    /**
     * Stop playback
     */
    fun stopPlayback() {
        if (!isPlaying) return
        
        isPlaying = false
        playbackIndex = 0
        Movement.clearAll()
        ChatUtils.info("Stopped playback")
    }
    
    /**
     * Update every tick
     */
    fun onTick() {
        if (isRecording) {
            recordFrame()
        } else if (isPlaying) {
            playFrame()
        }
    }
    
    private fun recordFrame() {
        val frame = SimpleFrame(
            forward = mc.gameSettings.keyBindForward.isKeyDown,
            backward = mc.gameSettings.keyBindBack.isKeyDown,
            left = mc.gameSettings.keyBindLeft.isKeyDown,
            right = mc.gameSettings.keyBindRight.isKeyDown
        )
        recordedFrames.add(frame)
    }
    
    private fun playFrame() {
        if (playbackIndex >= recordedFrames.size) {
            stopPlayback()
            return
        }
        
        val frame = recordedFrames[playbackIndex]
        
        // Apply movement
        if (frame.forward) Movement.startForward() else Movement.stopForward()
        if (frame.backward) Movement.startBackward() else Movement.stopBackward()
        if (frame.left) Movement.startLeft() else Movement.stopLeft()
        if (frame.right) Movement.startRight() else Movement.stopRight()
        
        playbackIndex++
        
        // Play every 2 ticks to slow it down a bit
        if (playbackIndex % 2 == 0) {
            playbackIndex++
        }
    }
    
    fun getStatus(): String {
        return when {
            isRecording -> "Recording (${recordedFrames.size} frames)"
            isPlaying -> "Playing (${playbackIndex}/${recordedFrames.size})"
            else -> "Idle (${recordedFrames.size} frames recorded)"
        }
    }
}