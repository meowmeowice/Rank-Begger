package best.spaghetcodes.catdueller.bot

import best.spaghetcodes.catdueller.bot.player.Movement
import best.spaghetcodes.catdueller.utils.ChatUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.minecraft.client.Minecraft
import net.minecraft.client.settings.KeyBinding
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import kotlin.random.Random

/**
 * Records and plays back player movement patterns for automated lobby navigation.
 *
 * This system allows users to record their movement inputs (WASD, jump, sneak, mouse clicks,
 * and rotation) in the pre-game lobby and replay them automatically in future games.
 * Supports multiple named patterns with automatic recording mode and relative rotation playback.
 *
 * Features:
 * - Manual recording via `/movement record` command
 * - Automatic recording mode that captures lobby movement each game
 * - Relative rotation playback (patterns adjust to player's starting orientation)
 * - Click recording for inventory interactions
 * - JSON persistence for patterns across sessions
 * - Rotation limiting to prevent jarring camera movements
 */
object MovementRecorder {

    private val mc = Minecraft.getMinecraft()
    private val gson = Gson()

    // Movement recording data
    data class MovementFrame(
        val tick: Int,
        val forward: Boolean,
        val backward: Boolean,
        val left: Boolean,
        val right: Boolean,
        val jump: Boolean,
        val sneak: Boolean,
        val yaw: Float,
        val pitch: Float,
        val leftClick: Boolean,
        val rightClick: Boolean
    )

    data class MovementPattern(
        val name: String,
        val duration: Int, // in ticks
        val frames: List<MovementFrame>
    )

    // Recording state
    private var isRecording = false
    private var recordingStartTick = 0
    private var currentRecording = mutableListOf<MovementFrame>()
    private var recordingName = ""

    // Click recording enabled - MovementRecorder handles movement, rotation, and clicks

    // Auto recording state
    private var autoRecordEnabled = false
    private var pendingAutoRecord = false
    private var autoRecordCounter = 0

    // Playback state
    private var isPlaying = false
    private var currentPattern: MovementPattern? = null
    private var playbackStartTick = 0
    private var currentFrameIndex = 0

    // Relative rotation for playback
    private var playbackStartYaw = 0f  // Player's yaw when playback starts
    private var playbackStartPitch = 0f  // Player's pitch when playback starts
    private var recordingStartYaw = 0f  // Recording's initial yaw
    private var recordingStartPitch = 0f  // Recording's initial pitch

    // Rotation limiting for playback
    private var lastPlaybackYaw = 0f  // Last applied yaw for rotation limiting
    private var lastPlaybackPitch = 0f  // Last applied pitch for rotation limiting
    private val maxRotationPerTick = 45f  // Maximum rotation change per tick in degrees

    // Auto movement on game full
    private var autoMovementOnGameFull = true  // Enable auto movement when game is full

    // Stored patterns
    private var patterns = mutableListOf<MovementPattern>()
    private val patternsFile = File("catdueller_movement_patterns.json")

    init {
        loadPatterns()
        initializeAutoRecordCounter()
    }

    /**
     * Start recording a new movement pattern
     */
    fun startRecording(patternName: String) {
        if (isRecording) {
            ChatUtils.info("Already recording! Stop current recording first.")
            return
        }

        if (isPlaying) {
            stopPlayback()
        }

        isRecording = true
        recordingName = patternName
        recordingStartTick = getCurrentTick()
        currentRecording.clear()

        // Click recording enabled

        ChatUtils.info("Started recording movement pattern: $patternName")
        ChatUtils.info("Move around in lobby, then use /catdueller stoprecord to finish")
    }

    /**
     * Stop recording and save the pattern
     */
    fun stopRecording() {
        if (!isRecording) {
            ChatUtils.info("Not currently recording!")
            return
        }

        isRecording = false
        val duration = getCurrentTick() - recordingStartTick

        if (currentRecording.isEmpty()) {
            ChatUtils.info("No movement data recorded!")
            return
        }

        val pattern = MovementPattern(recordingName, duration, currentRecording.toList())
        patterns.add(pattern)
        savePatterns()

        ChatUtils.info("Saved movement pattern '$recordingName' with ${currentRecording.size} frames (${duration} ticks)")
        currentRecording.clear()
    }

    /**
     * Start playing a random pattern
     */
    fun startRandomPlayback(): Boolean {
        if (patterns.isEmpty()) {
            ChatUtils.info("No movement patterns available! Record some first.")
            return false
        }

        if (isRecording) {
            ChatUtils.info("Cannot playback while recording!")
            return false
        }

        // Use explicit random selection to ensure it works properly
        val randomIndex = Random.nextInt(patterns.size)
        val randomPattern = patterns[randomIndex]
        ChatUtils.info("Randomly selected pattern: ${randomPattern.name} (${randomIndex + 1}/${patterns.size})")
        return startPlayback(randomPattern)
    }

    /**
     * Start playing a specific pattern
     */
    fun startPlayback(pattern: MovementPattern): Boolean {
        if (isRecording) {
            ChatUtils.info("Cannot playback while recording!")
            return false
        }

        stopPlayback() // Stop any current playback

        isPlaying = true
        currentPattern = pattern
        playbackStartTick = getCurrentTick()
        currentFrameIndex = 0

        // Record initial rotations for relative playback
        mc.thePlayer?.let { player ->
            playbackStartYaw = player.rotationYaw
            playbackStartPitch = player.rotationPitch
            // Initialize last playback rotations
            lastPlaybackYaw = player.rotationYaw
            lastPlaybackPitch = player.rotationPitch
        }

        // Get the first frame's rotation as the recording's initial rotation
        if (pattern.frames.isNotEmpty()) {
            recordingStartYaw = pattern.frames[0].yaw
            recordingStartPitch = pattern.frames[0].pitch
        }

        ChatUtils.info("Started playing movement pattern: ${pattern.name}")
        return true
    }

    /**
     * Stop current playback
     */
    fun stopPlayback() {
        if (!isPlaying) return

        isPlaying = false
        currentPattern = null
        currentFrameIndex = 0

        // Clear all movement (use force method to bypass bot toggle check)
        Movement.forceClearAll()

        // Clear click states
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.keyCode, false)
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.keyCode, false)

        ChatUtils.info("Stopped movement playback")
    }

    /**
     * Update recording/playback - call this every tick
     */
    fun onTick() {
        mc.thePlayer ?: return

        if (isRecording) {
            recordCurrentFrame()
        } else if (isPlaying) {
            playCurrentFrame()
        }
    }

    private fun recordCurrentFrame() {
        val player = mc.thePlayer ?: return
        val currentTick = getCurrentTick() - recordingStartTick

        // Record all input including clicks
        val frame = MovementFrame(
            tick = currentTick,
            forward = mc.gameSettings.keyBindForward.isKeyDown,
            backward = mc.gameSettings.keyBindBack.isKeyDown,
            left = mc.gameSettings.keyBindLeft.isKeyDown,
            right = mc.gameSettings.keyBindRight.isKeyDown,
            jump = mc.gameSettings.keyBindJump.isKeyDown,
            sneak = mc.gameSettings.keyBindSneak.isKeyDown,
            yaw = player.rotationYaw,
            pitch = player.rotationPitch,
            leftClick = mc.gameSettings.keyBindAttack.isKeyDown,      // Record left click
            rightClick = mc.gameSettings.keyBindUseItem.isKeyDown     // Record right click
        )

        currentRecording.add(frame)
    }

    private fun playCurrentFrame() {
        val pattern = currentPattern ?: return
        val currentTick = getCurrentTick() - playbackStartTick

        // Use more efficient frame lookup
        while (currentFrameIndex < pattern.frames.size && pattern.frames[currentFrameIndex].tick <= currentTick) {
            val targetFrame = pattern.frames[currentFrameIndex]

            // Apply movement (use force methods to bypass bot toggle check)
            if (targetFrame.forward) Movement.forceStartForward() else Movement.forceStopForward()
            if (targetFrame.backward) Movement.forceStartBackward() else Movement.forceStopBackward()
            if (targetFrame.left) Movement.forceStartLeft() else Movement.forceStopLeft()
            if (targetFrame.right) Movement.forceStartRight() else Movement.forceStopRight()
            if (targetFrame.jump) Movement.forceStartJumping() else Movement.forceStopJumping()
            if (targetFrame.sneak) Movement.forceStartSneaking() else Movement.forceStopSneaking()

            // Apply click events - directly set key binding states
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.keyCode, targetFrame.leftClick)
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.keyCode, targetFrame.rightClick)

            // Apply relative rotation based on player's starting direction with rotation limiting
            mc.thePlayer?.let { player ->
                // Calculate the relative rotation from the recording
                val recordingYawDelta = targetFrame.yaw - recordingStartYaw
                val recordingPitchDelta = targetFrame.pitch - recordingStartPitch

                // Calculate target rotations
                val targetYaw = playbackStartYaw + recordingYawDelta
                val targetPitch = playbackStartPitch + recordingPitchDelta

                // Calculate rotation changes from last applied rotation
                val yawChange = kotlin.math.abs(targetYaw - lastPlaybackYaw)
                val pitchChange = kotlin.math.abs(targetPitch - lastPlaybackPitch)

                // Apply rotation limiting - skip if change is too large
                if (yawChange <= maxRotationPerTick && pitchChange <= maxRotationPerTick) {
                    player.rotationYaw = targetYaw
                    player.rotationPitch = targetPitch
                    // Update last applied rotations
                    lastPlaybackYaw = targetYaw
                    lastPlaybackPitch = targetPitch
                } else {
                    // Log when rotation is skipped due to being too large
                    if (yawChange > maxRotationPerTick || pitchChange > maxRotationPerTick) {
                        ChatUtils.combatInfo("MovementRecorder: Skipped large rotation change (yaw: ${yawChange.toInt()}°, pitch: ${pitchChange.toInt()}°)")
                    }
                }
            }

            currentFrameIndex++
        }

        // Check if playback is finished
        if (currentTick >= pattern.duration || currentFrameIndex >= pattern.frames.size) {
            stopPlayback()
        }
    }

    private fun getCurrentTick(): Int {
        // Use system time instead of world time for more reliable timing
        return ((System.currentTimeMillis() / 50) % Int.MAX_VALUE).toInt() // 50ms = 1 tick
    }

    /**
     * Save patterns to file
     */
    private fun savePatterns() {
        try {
            FileWriter(patternsFile).use { writer ->
                gson.toJson(patterns, writer)
            }
        } catch (e: Exception) {
            ChatUtils.info("Failed to save movement patterns: ${e.message}")
        }
    }

    /**
     * Load patterns from file
     */
    private fun loadPatterns() {
        if (!patternsFile.exists()) return

        try {
            FileReader(patternsFile).use { reader ->
                val jsonText = reader.readText()

                // Try to load with new format first
                try {
                    val type = object : TypeToken<List<MovementPattern>>() {}.type
                    val loadedPatterns: List<MovementPattern> = gson.fromJson(jsonText, type) ?: emptyList()
                    patterns.clear()
                    patterns.addAll(loadedPatterns)
                } catch (e: Exception) {
                    // If new format fails, try to convert old format
                    ChatUtils.info("Converting old movement patterns format...")
                    convertOldPatterns(jsonText)
                }

                if (patterns.isNotEmpty()) {
                    ChatUtils.info("Loaded ${patterns.size} movement patterns")
                }
            }
        } catch (e: Exception) {
            ChatUtils.info("Failed to load movement patterns: ${e.message}")
        }
    }

    /**
     * Convert old format patterns to new format with click support
     */
    private fun convertOldPatterns(jsonText: String) {
        try {
            // Define old format data classes
            data class OldMovementFrame(
                val tick: Int,
                val forward: Boolean,
                val backward: Boolean,
                val left: Boolean,
                val right: Boolean,
                val jump: Boolean,
                val sneak: Boolean,
                val yaw: Float,
                val pitch: Float
            )

            data class OldMovementPattern(
                val name: String,
                val duration: Int,
                val frames: List<OldMovementFrame>
            )

            val oldType = object : TypeToken<List<OldMovementPattern>>() {}.type
            val oldPatterns: List<OldMovementPattern> = gson.fromJson(jsonText, oldType) ?: emptyList()

            // Convert to new format
            patterns.clear()
            oldPatterns.forEach { oldPattern ->
                val newFrames = oldPattern.frames.map { oldFrame ->
                    MovementFrame(
                        tick = oldFrame.tick,
                        forward = oldFrame.forward,
                        backward = oldFrame.backward,
                        left = oldFrame.left,
                        right = oldFrame.right,
                        jump = oldFrame.jump,
                        sneak = oldFrame.sneak,
                        yaw = oldFrame.yaw,
                        pitch = oldFrame.pitch,
                        leftClick = false,  // Default to no clicks for old patterns
                        rightClick = false
                    )
                }

                patterns.add(
                    MovementPattern(
                        name = oldPattern.name,
                        duration = oldPattern.duration,
                        frames = newFrames
                    )
                )
            }

            // Save in new format
            savePatterns()
            ChatUtils.info("Successfully converted ${patterns.size} old patterns to new format")

        } catch (e: Exception) {
            ChatUtils.info("Failed to convert old patterns: ${e.message}")
        }
    }

    /**
     * List all available patterns
     */
    fun listPatterns() {
        if (patterns.isEmpty()) {
            ChatUtils.info("No movement patterns available")
            return
        }

        ChatUtils.info("Available movement patterns:")
        patterns.forEachIndexed { index, pattern ->
            ChatUtils.info("${index + 1}. ${pattern.name} (${pattern.duration} ticks, ${pattern.frames.size} frames)")
        }
    }

    /**
     * Delete a pattern by name
     */
    fun deletePattern(name: String): Boolean {
        val removed = patterns.removeIf { it.name.equals(name, ignoreCase = true) }
        if (removed) {
            savePatterns()
            ChatUtils.info("Deleted movement pattern: $name")
        } else {
            ChatUtils.info("Pattern not found: $name")
        }
        return removed
    }

    /**
     * Enable auto recording mode
     */
    fun enableAutoRecord() {
        autoRecordEnabled = true
        pendingAutoRecord = true
        ChatUtils.info("Auto recording enabled - will start recording on next lobby join")
    }

    /**
     * Disable auto recording mode
     */
    fun disableAutoRecord() {
        autoRecordEnabled = false
        pendingAutoRecord = false
        if (isRecording) {
            stopRecording()
        }
        ChatUtils.info("Auto recording disabled")
    }

    /**
     * Called when joining a game lobby
     */
    fun onJoinGame() {
        // Only start recording if we're actually joining a new game (not returning from a finished game)
        if (autoRecordEnabled && pendingAutoRecord && !isRecording) {
            // Find next available auto_lobby number
            var nextNumber = autoRecordCounter + 1
            var autoName = "auto_lobby_$nextNumber"

            // Ensure the name doesn't already exist
            while (patterns.any { it.name == autoName }) {
                nextNumber++
                autoName = "auto_lobby_$nextNumber"
            }

            autoRecordCounter = nextNumber
            startRecording(autoName)
            pendingAutoRecord = false
            ChatUtils.info("Auto recording started: $autoName")
        }
    }

    /**
     * Called before game starts
     */
    fun onBeforeStart() {
        if (autoRecordEnabled && isRecording) {
            stopRecording()
            pendingAutoRecord = true // Ready for next lobby
            ChatUtils.info("Auto recording stopped - ready for next lobby")
        }
    }

    /**
     * Check if currently playing
     */
    fun isPlaying() = isPlaying

    /**
     * Handle chat messages to detect game full events
     */
    fun onChatMessage(message: String) {
        // Check for game full message pattern: "PlayerName has joined (2/2)! (gameFull)"
        if (message.contains("has joined") && message.contains("(2/2)!") && message.contains("(gameFull)")) {
            // Only trigger if not currently playing a movement pattern
            if (autoMovementOnGameFull && !isPlaying && patterns.isNotEmpty()) {
                ChatUtils.info("Game is full! Starting random movement pattern...")
                startRandomPlayback()
            }
        }
    }

    /**
     * Initialize auto record counter based on existing patterns
     */
    private fun initializeAutoRecordCounter() {
        // Find the highest auto_lobby number in existing patterns
        var maxAutoNumber = 0
        patterns.forEach { pattern ->
            if (pattern.name.startsWith("auto_lobby_")) {
                val numberPart = pattern.name.substring("auto_lobby_".length)
                try {
                    val number = numberPart.toInt()
                    if (number > maxAutoNumber) {
                        maxAutoNumber = number
                    }
                } catch (e: NumberFormatException) {
                    // Ignore patterns with invalid numbers
                }
            }
        }
        autoRecordCounter = maxAutoNumber
        if (maxAutoNumber > 0) {
            ChatUtils.info("Auto record counter initialized to $autoRecordCounter (found existing auto_lobby patterns)")
        }
    }
}