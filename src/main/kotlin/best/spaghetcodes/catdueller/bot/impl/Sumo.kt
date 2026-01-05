package best.spaghetcodes.catdueller.bot.impl


import best.spaghetcodes.catdueller.CatDueller
import best.spaghetcodes.catdueller.bot.BotBase
import best.spaghetcodes.catdueller.bot.StateManager
import best.spaghetcodes.catdueller.bot.player.Combat
import best.spaghetcodes.catdueller.bot.player.LobbyMovement
import best.spaghetcodes.catdueller.bot.player.Mouse
import best.spaghetcodes.catdueller.bot.player.Movement
import best.spaghetcodes.catdueller.utils.*
import net.minecraft.client.Minecraft
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.BlockPos
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.*
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Bot implementation for Sumo Duels game mode.
 *
 * Sumo Duels is a knockback-based mode where players must push each other
 * off the platform. This bot features extensive edge awareness and includes:
 * - Edge detection and avoidance
 * - Blink mechanics for repositioning
 * - Strategic strafing based on distance and edge proximity
 * - Hurt strafe for momentum control after taking hits
 * - S-tap for additional knockback
 * - Opponent freeze detection
 * - Map and opponent dodging features
 * - Jump velocity mechanics
 */
class Sumo : BotBase("/play duels_sumo_duel") {

    init {
        setStatKeys(
            mapOf(
                "wins" to "player.stats.Duels.sumo_duel_wins",
                "losses" to "player.stats.Duels.sumo_duel_losses",
                "ws" to "player.stats.Duels.current_sumo_winstreak",
            )
        )
    }

    /**
     * Returns the display name of this bot.
     * @return The string "Sumo"
     */
    override fun getName(): String {
        return "Sumo"
    }

    /**
     * Determines whether Blink Tap feature should be disabled.
     * Disabled when bot is walking to middle during blink sequence.
     *
     * @return True if blink tap should be disabled
     */
    override fun shouldDisableBlinkTap(): Boolean {
        return walkingToMiddle
    }

    /** Flag indicating W-tap is currently active. */
    private var tapping = false

    /** Flag indicating opponent has fallen off the platform edge. */
    private var opponentOffEdge = false

    /** Flag indicating bot is in close-range strafe mode. */
    private var closeRangeStrafe = false

    /** Last strafe direction: 0=none, 1=left, 2=right. */
    private var lastStrafeDirection = 0

    /** Flag for pending delayed movement clear when opponent is off edge. */
    private var pendingMovementClear = false

    /** Timestamp when movement clear should execute. */
    private var movementClearTime = 0L

    /** Strafe direction when opponent is at edge: 0=none, 1=left, 2=right. */
    private var opponentEdgeStrafeDirection = 0

    /** Timestamp when opponent-at-edge strafe direction last changed. */
    private var opponentEdgeStrafeLastChange = 0L

    /** Flag indicating strafe direction for 3-6 block range has been decided. */
    private var midRangeStrafeDecided = false

    /** Timestamp when mid-range strafe started. */
    private var midRangeStrafeStartTime = 0L

    /** Flag indicating bot is walking to middle during blink sequence. */
    private var walkingToMiddle = false

    /** Flag indicating next blink is possible. */
    private var nextBlinkPossible = true

    /** Flag indicating blink key cooldown is active. */
    private var qCooldownActive = false

    /** Flag indicating second blink key press has occurred. */
    private var secondQPressed = false

    /** Flag indicating first blink key was pressed. */
    private var qPressed = false

    /** Timestamp of first blink key press. */
    private var firstQTime = 0L

    /** Timestamp when walk to middle sequence started. */
    private var walkToMiddleStartTime = 0L

    /** Flag indicating a blink was just executed. */
    private var justBlinked = false

    /** Flag indicating particle dodge delay is active. */
    private var particleDodgeDelayActive = false

    /** Timestamp when particle dodge check should execute. */
    private var particleDodgeCheckTime = 0L

    /** Robot instance for simulating key presses. */
    private val robot by lazy {
        try {
            java.awt.Robot()
        } catch (e: Exception) {
            ChatUtils.info("Failed to initialize Robot: ${e.message}")
            null
        }
    }

    /** Flag indicating mid-range strafe was triggered by a jump. */
    private var jumpTriggeredStrafe = false

    /** Flag indicating bot is waiting for opponent to attack first. */
    private var waitingForOpponentAttack = false

    /** Timestamp when waiting for opponent attack started. */
    private var waitingForOpponentAttackStartTime = 0L

    /** Timestamp when bot first entered attack range. */
    private var enteredAttackRangeTime = 0L

    /** Flag indicating bot was in attack range last tick. */
    private var wasInAttackRange = false

    /** Previous tick's player hurtTime value. */
    private var lastPlayerHurtTime = 0

    /** Duration bot has been on ground. */
    private var groundTime = 0L

    /** Flag indicating bot was on ground last tick. */
    private var lastOnGround = false

    /** Flag indicating distance was greater than 7 when waiting started. */
    private var waitingDistanceWasGreaterThan7 = false

    /** Flag indicating bot jumped at distance 7 while waiting. */
    private var jumpedAtDistance7 = false

    /** Hurt strafe direction: 0=none, 1=left, 2=right. */
    private var hurtStrafeDirection = 0

    /** Flag indicating jump key should be held for velocity. */
    private var setJump = false

    /** Previous tick's hurtTime value for jump velocity detection. */
    private var lastHurtTime = 0

    /** Flag to prevent movement override during edge stutter. */
    private var edgeStutterActive = false

    /** Flag indicating stop-at-edge feature is currently active. */
    private var stopAtEdgeActive = false

    /** Timestamp when stop-at-edge started. */
    private var stopAtEdgeStartTime = 0L

    /** Player's hurtTime when stop-at-edge started. */
    private var stopAtEdgeInitialHurtTime = 0

    /** Timestamp of last opponent rotation log. */
    private var lastOpponentRotationLogTime = 0L

    /** Last logged opponent yaw rotation. */
    private var lastLoggedOpponentYaw = 0f

    /** Last logged opponent pitch rotation. */
    private var lastLoggedOpponentPitch = 0f

    /** Timer for continuous opponent rotation logging. */
    private var rotationLoggingTimer: Timer? = null

    /** Last recorded opponent position for freeze detection. */
    private var lastOpponentPosition: Triple<Double, Double, Double>? = null

    /** Timestamp when opponent last moved. */
    private var opponentLastMoveTime = 0L

    /** Flag indicating freeze check is active. */
    private var opponentFreezeCheckActive = false

    /** Flag to prevent spam of leave command. */
    private var alreadySentLeaveCommand = false

    /** Timer for opponent freeze checking. */
    private var freezeCheckTimer: Timer? = null

    /** Flag indicating game start waiting period is active. */
    private var gameStartWaitActive = false

    /** Timestamp when game started. */
    private var gameStartTime = 0L

    /** Duration of game start waiting period in milliseconds. */
    private val gameStartWaitDuration = 1000L

    /** Last horizontal distance to opponent for S-tap trigger. */
    private var lastDistanceToOpponent = 999f

    /** Timestamp of last edge back-then-forward trigger. */
    private var lastBackForwardAtEdgeTime = 0L

    /** Flag indicating taunt message has been sent this game. */
    private var tauntMessageSent = false

    /** Flag indicating blatant mode was toggled due to edge proximity. */
    private var edgeBlatantToggled = false

    /** Flag tracking if player was off edge last tick. */
    private var playerOffEdgeLastTick = false

    /** Flag tracking if off-edge bind was triggered this game. */
    private var offEdgeBindTriggeredThisGame = false


    /**
     * Called when the bot joins a game lobby.
     * Handles lobby movement and rotation setup.
     */
    override fun onJoinGame() {
        super.onJoinGame()

        if (CatDueller.config?.lobbyMovement == true) {
            LobbyMovement.sumo()
        }

        if (CatDueller.config?.setMyRotation == true) {
            TimeUtils.setTimeout({
                val baseYaw = CatDueller.config?.myTargetYaw ?: 0.0f
                val basePitch = CatDueller.config?.myTargetPitch ?: 0.0f
                val tolerance = CatDueller.config?.myAngleTolerance ?: 0.01f

                if (CatDueller.config?.showRotationDebug == true) {
                    ChatUtils.info("Setting my rotation:")
                    ChatUtils.info("  yaw=$baseYaw, pitch=$basePitch, tolerance=$tolerance")
                }

                LobbyMovement.adjustRotation(baseYaw, basePitch, tolerance) {
                    if (CatDueller.config?.showRotationDebug == true) {
                        ChatUtils.info("My rotation adjustment completed!")
                    }
                }
            }, 1000)
        }
    }

    /**
     * Called before the game starts.
     * Stops lobby movement and handles long jump setup if enabled.
     */
    override fun beforeStart() {
        LobbyMovement.stop()

        if (CatDueller.config?.sumoLongJump == true) {
            val player = mc.thePlayer ?: return

            // Start long jump mode (sets internal state)
            Movement.startLongJump()

            try {
                // Check if any GUI is currently open
                val currentScreen = mc.currentScreen
                if (currentScreen != null) {
                    // Check if it's chat GUI - chat can't be used for long jump technic
                    if (currentScreen is net.minecraft.client.gui.GuiChat) {
                        // Close chat first
                        mc.displayGuiScreen(null)
                        TimeUtils.setTimeout({
                            try {
                                // Check if ESC menu was automatically opened (happens when window not focused)
                                when (val screenAfterClose = mc.currentScreen) {
                                    is net.minecraft.client.gui.GuiIngameMenu -> {
                                        // ESC menu automatically opened - use it
                                        ChatUtils.info("Sumo Long Jump activated - closed chat, using auto-opened ESC menu")
                                    }

                                    null -> {
                                        // No GUI auto-opened, manually open ESC menu
                                        mc.displayGuiScreen(net.minecraft.client.gui.GuiIngameMenu())
                                        ChatUtils.info("Sumo Long Jump activated - closed chat and opened ESC menu")
                                    }

                                    else -> {
                                        // Some other GUI opened, use it
                                        ChatUtils.info("Sumo Long Jump activated - closed chat, using ${screenAfterClose.javaClass.simpleName}")
                                    }
                                }
                            } catch (e: Exception) {
                                ChatUtils.info("Failed to handle GUI after closing chat: ${e.message}")
                                Movement.stopLongJump()
                            }
                        }, 50)
                    } else {
                        // Other GUI (ESC menu, etc.) works same as inventory for keybind handling
                        ChatUtils.info("Sumo Long Jump activated - using existing GUI (${currentScreen.javaClass.simpleName})")
                    }
                } else {
                    // No GUI open, open inventory
                    mc.displayGuiScreen(net.minecraft.client.gui.inventory.GuiInventory(player))
                    ChatUtils.info("Sumo Long Jump activated - opened inventory")
                }
            } catch (e: Exception) {
                ChatUtils.info("Error during long jump setup: ${e.message}")
                Movement.stopLongJump() // Cancel long jump on any error
            }
        }
    }

    /**
     * Called just before the game starts.
     *
     * Performs pre-game checks including:
     * - Huaxi server detection and dodging
     * - Map validation (air block detection)
     * - Standing still player detection
     * - Particle detection for bot dodging
     * - Opponent rotation verification
     */
    override fun onGameAlmostStart() {
        val player = mc.thePlayer ?: return

        if (CatDueller.config?.dodgeHuaxi == true) {
            if (readScoreboardLines8And9()) {
                // Huaxi detected, stop execution of onGameAlmostStart
                return
            }
        }

        val spawnY = player.posY.toInt()


        // Coordinates to check relative to spawn
        val coordsToCheck = listOf(
            Pair(2, 2),
            Pair(2, -2),
            Pair(-2, 2),
            Pair(-2, -2)
        )

        for ((xOffset, zOffset) in coordsToCheck) {
            val x = xOffset
            val z = zOffset
            val y1 = spawnY - 1
            val y2 = spawnY - 2

            val block1 = mc.theWorld.getBlockState(BlockPos(x, y1, z)).block
            val block2 = mc.theWorld.getBlockState(BlockPos(x, y2, z)).block

            // Only trigger if both blocks are air
            if (block1 == net.minecraft.init.Blocks.air && block2 == net.minecraft.init.Blocks.air) {
                ChatUtils.info("Air detected at $x, $y1, $z and $x, $y2, $z. Leaving Game...")
                TimeUtils.setTimeout(fun() {
                    player.sendChatMessage("/play duels_sumo_duel")
                }, RandomUtils.randomIntInRange(100, 300))
                return
            }
        }

        // If we reach here, no air blocks were detected at any coordinate
        ChatUtils.info("No air blocks detected - map is safe")


        // Check for standing still players if dodge feature is enabled
        if (CatDueller.config?.dodgeStandingStill == true) {
            checkAndDodgeStandingStillPlayer()
        }

        // Check for particles if dodge feature is enabled - with random delay
        val dodgeParticleType = CatDueller.config?.dodgeParticleType ?: 0
        if (dodgeParticleType > 0) {  // 0=None, 1=Slime, 2=Portal
            // Set up delayed particle checking (0-2 seconds)
            val delayMs = RandomUtils.randomIntInRange(0, 2000)
            particleDodgeDelayActive = true
            particleDodgeCheckTime = System.currentTimeMillis() + delayMs

            if (CatDueller.config?.combatLogs == true) {
                ChatUtils.info("Particle dodge check scheduled in ${delayMs}ms")
            }
        }

        // Check opponent rotation and dodge if it doesn't match expected values
        if (CatDueller.config?.dodgeWrongRotation == true) {
            val baseExpectedYaw = CatDueller.config?.expectedOpponentYaw ?: 0.0f
            val baseExpectedPitch = CatDueller.config?.expectedOpponentPitch ?: 0.0f
            val tolerance = CatDueller.config?.opponentAngleTolerance ?: 1.0f

            if (CatDueller.config?.showRotationDebug == true) {
                ChatUtils.info("Checking opponent rotation:")
                ChatUtils.info("  Expected: yaw=$baseExpectedYaw, pitch=$baseExpectedPitch, tolerance=$tolerance")
            }

            if (LobbyMovement.checkOpponentRotationAndDodge(baseExpectedYaw, baseExpectedPitch, tolerance)) {
                // If we're dodging due to wrong rotation, don't continue with other checks
                return
            }
        }

        // Check opponent rotation and dodge if it DOES match expected values (bot detection)
        if (CatDueller.config?.dodgeEachOtherBot == true) {
            val baseExpectedYaw = CatDueller.config?.expectedOpponentYaw ?: 0.0f
            val baseExpectedPitch = CatDueller.config?.expectedOpponentPitch ?: 0.0f
            val tolerance = CatDueller.config?.opponentAngleTolerance ?: 1.0f

            if (CatDueller.config?.showRotationDebug == true) {
                ChatUtils.info("Checking opponent rotation for bot detection:")
                ChatUtils.info("  Expected: yaw=$baseExpectedYaw, pitch=$baseExpectedPitch, tolerance=$tolerance")
            }

            if (LobbyMovement.checkOpponentRotationMatchAndDodge(baseExpectedYaw, baseExpectedPitch, tolerance)) {
                // If we're dodging due to bot detection, don't continue with other checks
                return
            }
        }

        super.onGameAlmostStart()  // Call BotBase method
    }

    /**
     * Called when the game starts.
     * Resets all state variables, handles long jump completion, and initiates movement.
     */
    override fun onGameStart() {
        super.onGameStart()
        LobbyMovement.stop()

        if (Movement.isLongJumpActive()) {
            TimeUtils.setTimeout({
                Movement.stopLongJump()
            }, 300)
        }

        if (!Movement.isLongJumpActive()) {
            Movement.startSprinting()
            Movement.startForward()
        }

        closeRangeStrafe = false
        lastStrafeDirection = 0
        midRangeStrafeDecided = false
        midRangeStrafeStartTime = 0L
        jumpTriggeredStrafe = false
        particleDodgeDelayActive = false
        particleDodgeCheckTime = 0L
        waitingForOpponentAttack = false
        waitingForOpponentAttackStartTime = 0L
        enteredAttackRangeTime = 0L
        wasInAttackRange = false
        lastPlayerHurtTime = 0
        groundTime = 0L
        lastOnGround = false
        waitingDistanceWasGreaterThan7 = false
        jumpedAtDistance7 = false
        hurtStrafeDirection = 0
        setJump = false
        lastHurtTime = 0
        stopAtEdgeActive = false
        stopAtEdgeStartTime = 0L
        stopAtEdgeInitialHurtTime = 0
        lastDistanceToOpponent = 999f
        lastBackForwardAtEdgeTime = 0L
        tauntMessageSent = false
        edgeBlatantToggled = false
        playerOffEdgeLastTick = false
        offEdgeBindTriggeredThisGame = false
        walkingToMiddle = false
        nextBlinkPossible = true
        qCooldownActive = false
        secondQPressed = false
        qPressed = false
        firstQTime = 0L
        walkToMiddleStartTime = 0L
        justBlinked = false

        gameStartWaitActive = true
        gameStartTime = System.currentTimeMillis()
        ChatUtils.combatInfo("Game started - waiting ${gameStartWaitDuration}ms before enabling edge features")

        resetOpponentFreezeDetection()
    }

    /**
     * Called when the game ends.
     *
     * Handles cleanup including turning off blatant mode, pressing freeze binds,
     * stopping rotation logging, and resetting freeze detection.
     */
    override fun onGameEnd() {
        if (edgeBlatantToggled && CatDueller.config?.toggleBlatantAtEdge == true) {
            val keyName = CatDueller.config?.blatantToggleKey ?: "F1"
            ChatUtils.combatInfo("Game ended - turning off edge blatant mode")
            simulateKeyPress(keyName)
        }

        if (CatDueller.config?.freezeWhenOffEdge == true && offEdgeBindTriggeredThisGame) {
            val freezeBind = CatDueller.config?.freezeBind ?: "F1"
            ChatUtils.combatInfo("Game ended - pressing freeze bind again (was triggered during game): $freezeBind")
            simulateKeyPress(freezeBind)
        }

        edgeBlatantToggled = false
        gameStartWaitActive = false

        stopOpponentRotationLogging()
        resetOpponentFreezeDetection()

        super.onGameEnd()

        checkTauntMessage()
        Mouse.stopHoldLeftClick()

        if (CatDueller.bot?.toggled() == true) {
            Movement.clearAll()
            Mouse.stopLeftAC()
            Combat.stopRandomStrafe()
        }
    }

    /**
     * Called when the bot successfully attacks the opponent.
     *
     * Resets close-range strafe, cancels stop-at-edge, and performs
     * W-tap or sprint reset based on configuration.
     */
    override fun onAttack() {
        super.onAttack()

        closeRangeStrafe = false
        midRangeStrafeStartTime = 0L

        if (stopAtEdgeActive) {
            stopAtEdgeActive = false
            if (CatDueller.config?.combatLogs == true) {
                ChatUtils.combatInfo("Stop When Opponent At Edge cancelled - attacked opponent")
            }
        }

        if (!tapping && CatDueller.config?.enableWTap == true) {
            tapping = true
            val delay = CatDueller.config?.wTapDelay ?: 100
            TimeUtils.setTimeout(fun() {
                val dur = 50
                if (CatDueller.config?.sprintReset == true) {
                    Combat.sprintReset(dur)
                } else {
                    Combat.wTap(dur)
                }
                TimeUtils.setTimeout(fun() {
                    tapping = false
                }, dur)
            }, delay)
        }
    }

    /**
     * Gets a list of all player names currently in the tab list.
     *
     * @return List of player names from the tab list
     */
    fun getPlayersInTab(): List<String> {
        val mc = Minecraft.getMinecraft()
        val player = mc.thePlayer ?: return emptyList()
        val players: List<String> = player.sendQueue.playerInfoMap.map { it.gameProfile.name }
        return players
    }

    /**
     * Checks if the opponent is a valid player in the tab list.
     *
     * @param opponentName The display name of the opponent
     * @return True if opponent is in tab list, false otherwise
     */
    fun isOpponentValid(opponentName: String): Boolean {
        val playersInTab = getPlayersInTab()
        return playersInTab.contains(opponentName)
    }

    /**
     * Called when opponent is found.
     *
     * Validates opponent is in tab list, starts mouse tracking,
     * and initiates rotation logging and freeze detection.
     */
    override fun onFoundOpponent() {
        val opponentPlayer = opponent() ?: return
        if (!isOpponentValid(opponentPlayer.displayNameString)) {
            ChatUtils.info("Opponent ${opponentPlayer.displayNameString} is not in tablist, ignoring")
            return
        }

        super.onFoundOpponent()

        if (CatDueller.config?.disableAiming != true) {
            Mouse.startTracking()
        }

        if (CatDueller.config?.showRotationDebug == true) {
            startOpponentRotationLogging()
        }

        startOpponentFreezeDetection()
    }

    /**
     * Checks if there is air (edge) to the left of the player.
     *
     * @param distance Distance to check
     * @return True if edge is on the left
     */
    fun leftEdge(distance: Float): Boolean {
        return (WorldUtils.airOnLeft(mc.thePlayer, distance))
    }

    /**
     * Checks if there is air (edge) to the right of the player.
     *
     * @param distance Distance to check
     * @return True if edge is on the right
     */
    fun rightEdge(distance: Float): Boolean {
        return (WorldUtils.airOnRight(mc.thePlayer, distance))
    }

    /**
     * Checks if the player is near any edge (left, right, or back).
     *
     * @param distance Distance to check
     * @return True if near any edge (excluding front)
     */
    fun nearEdge(distance: Float): Boolean {
        return (rightEdge(distance) || leftEdge(distance) || WorldUtils.airInBack(mc.thePlayer, distance))
    }

    /**
     * Checks if the player is near the left or right edge only.
     *
     * @param distance Distance to check
     * @return True if near left or right edge
     */
    fun nearLeftOrRightEdge(distance: Float): Boolean {
        return (rightEdge(distance) || leftEdge(distance))
    }

    /**
     * Checks if the opponent is near any edge.
     *
     * @param distance Distance to check
     * @return True if opponent is near an edge
     */
    fun opponentNearEdge(distance: Float): Boolean {
        val opponent = opponent() ?: return false
        return (WorldUtils.airInBack(opponent, distance) || WorldUtils.airOnLeft(
            opponent,
            distance
        ) || WorldUtils.airOnRight(opponent, distance))
    }

    /**
     * Checks if the game start waiting period is still active.
     *
     * @return True if in waiting period, false if edge features can be used
     */
    private fun isInGameStartWaitPeriod(): Boolean {
        if (!gameStartWaitActive) return false

        val currentTime = System.currentTimeMillis()
        if (currentTime - gameStartTime >= gameStartWaitDuration) {
            gameStartWaitActive = false
            ChatUtils.combatInfo("Game start wait period ended - edge features now enabled")
            return false
        }
        return true
    }

    /**
     * Decides strafe direction based on distance to left and right edges.
     *
     * Chooses to strafe towards the direction with more space (larger distance to edge).
     * Only considers left and right edges, not back edge.
     *
     * @return 1 for left, 2 for right
     */
    private fun decideStrafeDirectionByEdgeDistance(): Int {
        val player = mc.thePlayer ?: return if (RandomUtils.randomBool()) 1 else 2

        // Only check if near left or right edge (not back)
        if (!nearLeftOrRightEdge(6f)) {
            // Not near left or right edge, return random direction
            return if (RandomUtils.randomBool()) 1 else 2
        }

        val leftDistance = WorldUtils.distanceToLeftEdge(player)
        val rightDistance = WorldUtils.distanceToRightEdge(player)

        // Strafe towards the direction with more space (larger distance to edge)
        return if (leftDistance > rightDistance) 1 else 2
    }


    /**
     * Determines whether the bot should start attacking.
     *
     * Adds Sumo-specific logic that prevents attacking while
     * waiting for opponent to attack first.
     *
     * @param distance Current horizontal distance to opponent
     * @return True if attacking should begin, false otherwise
     */
    override fun shouldStartAttacking(distance: Float): Boolean {
        // First check base conditions
        if (!super.shouldStartAttacking(distance)) {
            return false
        }

        // Cannot attack if waiting for opponent attack
        if (waitingForOpponentAttack) {
            return false
        }



        return true
    }

    /**
     * Checks if opponent is standing still (on block center) and dodges if necessary.
     * Players standing exactly at x.5, z.5 coordinates are likely bots or afk.
     */
    private fun checkAndDodgeStandingStillPlayer() {
        val world = mc.theWorld ?: return
        val player = mc.thePlayer ?: return

        try {
            // Find all players in the world except ourselves
            val otherPlayers = world.playerEntities.filter {
                it != player && it is EntityPlayer
            }

            // Check if there's exactly one other player (our opponent)
            if (otherPlayers.size == 1) {
                val opponent = otherPlayers[0] as EntityPlayer
                val opponentX = opponent.posX
                val opponentZ = opponent.posZ

                // Check if both X and Z coordinates end with .5 (standing still on block center)
                val xDecimal = kotlin.math.abs(opponentX - kotlin.math.floor(opponentX))
                val zDecimal = kotlin.math.abs(opponentZ - kotlin.math.floor(opponentZ))

                // Check if decimal parts are exactly 0.5 (with small tolerance for floating point precision)
                val isStandingStill = kotlin.math.abs(xDecimal - 0.5) < 0.01 && kotlin.math.abs(zDecimal - 0.5) < 0.01

                if (isStandingStill) {
                    ChatUtils.info("Detected standing still player at ($opponentX, $opponentZ) - dodging!")

                    // Send queue command to dodge
                    TimeUtils.setTimeout(fun() {
                        ChatUtils.sendAsPlayer(queueCommand)
                    }, RandomUtils.randomIntInRange(100, 300))
                }
            }
        } catch (e: Exception) {
            ChatUtils.info("Error checking for standing still player: ${e.message}")
        }
    }

    /**
     * Checks for specific particle types near the player and dodges if detected.
     *
     * Uses S2APacketParticles for accurate particle detection in Forge 1.8.9.
     * Particles may indicate bot presence or special abilities.
     *
     * @param particleTypeIndex Particle type: 1=Slime, 2=Portal, 3=Redstone, 4=Heart, 5=Angry Villager
     */
    private fun checkAndDodgeParticles(particleTypeIndex: Int) {
        val player = mc.thePlayer ?: return

        try {
            val particleType = when (particleTypeIndex) {
                1 -> net.minecraft.util.EnumParticleTypes.SLIME
                2 -> net.minecraft.util.EnumParticleTypes.PORTAL
                3 -> net.minecraft.util.EnumParticleTypes.REDSTONE
                4 -> net.minecraft.util.EnumParticleTypes.HEART
                5 -> net.minecraft.util.EnumParticleTypes.VILLAGER_ANGRY
                else -> return  // Invalid type, do nothing
            }

            val particleName = when (particleTypeIndex) {
                1 -> "Slime"
                2 -> "Portal"
                3 -> "Redstone"
                4 -> "Heart"
                5 -> "Angry Villager"
                else -> "Unknown"
            }

            // Enable debug mode to see what's happening
            val debug = CatDueller.config?.combatLogs == true

            if (debug) {
                ChatUtils.info("Checking for $particleName particles using S2APacketParticles...")
                ChatUtils.info(ParticleDetector.getDebugInfo())
            }

            if (ParticleDetector.hasParticleInRange(
                    player.posX, player.posY, player.posZ, particleType, 1.0, 20.0, debug
                )
            ) {
                ChatUtils.info("Detected $particleName particles within 1-20 blocks - dodging!")

                // Send queue command to dodge
                TimeUtils.setTimeout(fun() {
                    ChatUtils.sendAsPlayer(queueCommand)
                }, RandomUtils.randomIntInRange(100, 300))
            } else if (debug) {
                ChatUtils.info("No $particleName particles detected within 1-20 blocks")
            }
        } catch (e: Exception) {
            ChatUtils.info("Error checking for particles: ${e.message}")
            e.printStackTrace()
        }
    }


    /**
     * Disables opponent speed tracking for Sumo.
     *
     * Sumo is melee-only and doesn't need projectile prediction,
     * so speed tracking is disabled for performance optimization.
     *
     * @return Always false for Sumo
     */
    override fun shouldTrackOpponentSpeed(): Boolean {
        return false
    }

    /**
     * Main game loop called every tick.
     *
     * Handles all Sumo-specific combat logic including:
     * - Edge detection and avoidance
     * - Blink mechanics when near edge
     * - Attack and strafe logic
     * - Hurt strafe for momentum control
     * - S-tap at edge for extra knockback
     * - Jump velocity when attacked
     * - Opponent freeze detection
     */
    override fun onTick() {
        super.onTick()

        val player = mc.thePlayer ?: return
        val currentOpponent = opponent()

        // Check for delayed particle dodging
        if (particleDodgeDelayActive && System.currentTimeMillis() >= particleDodgeCheckTime) {
            particleDodgeDelayActive = false
            val dodgeParticleType = CatDueller.config?.dodgeParticleType ?: 0
            if (dodgeParticleType > 0) {
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtils.info("Executing delayed particle dodge check")
                }
                checkAndDodgeParticles(dodgeParticleType)
            }
        }

        // Initialize sprinting if sprint reset is enabled and we have an opponent
        if (CatDueller.config?.sprintReset == true && currentOpponent != null && !Movement.sprinting()) {
            Movement.startSprinting()
        }

        // Check if opponent is off edge
        if (opponent() != null && mc.thePlayer != null) {
            val currentlyOffEdge = WorldUtils.entityOffEdge(opponent()!!)
            val distance = EntityUtils.getDistanceNoY(mc.thePlayer, opponent()!!)

            // Opponent is off edge if:
            // 1. Actually off edge, OR
            // 2. Was previously off edge AND distance > 6 (still far away)
            opponentOffEdge = currentlyOffEdge || (opponentOffEdge && distance > 17)

            // Check waitingForOpponentAttack logic every tick
            if (waitingForOpponentAttack) {
                // Jump when distance reaches 7 (if we were waiting from distance > 7 and feature is enabled)
                if (CatDueller.config?.distance7Jump == true && waitingDistanceWasGreaterThan7 && distance <= 7.0f && !jumpedAtDistance7 && player.onGround) {
                    val timeSinceGameStart = System.currentTimeMillis() - gameStartTime
                    if (timeSinceGameStart >= 1000) { // Only jump after 1 second from game start
                        Movement.singleJump(RandomUtils.randomIntInRange(100, 150))
                        jumpedAtDistance7 = true
                        if (CatDueller.config?.combatLogs == true) {
                            ChatUtils.combatInfo("waitingForOpponentAttack: Jumped at distance 7 (was waiting from >7)")
                        }
                    }
                }

                // Only cancel waiting if distance < 0.5 (very close)
                if (distance < 1f) {
                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtils.combatInfo("Cancelling waitingForOpponentAttack - distance < 0.5 ($distance)")
                    }
                    waitingForOpponentAttack = false
                    waitingForOpponentAttackStartTime = 0L
                    waitingDistanceWasGreaterThan7 = false
                    jumpedAtDistance7 = false // Reset jump flag

                    // Restart Hold Left Click (if enabled)
                    if (CatDueller.config?.holdLeftClick == true) {
                        Mouse.startHoldLeftClick()
                    }

                    // Clear movement states and start attacking
                    Combat.stopRandomStrafe()
                    Movement.clearLeftRight()
                    if (CatDueller.config?.holdLeftClick != true) {
                        Mouse.startLeftAC()
                    }
                    // Hold left click is managed by distance-based logic
                }
                // Remove all other cancellation conditions (timeout, distance <= 7, etc.)

                // Hold left click is managed by distance-based logic

                // Clear movement states and start attacking
                Combat.stopRandomStrafe()
                Movement.clearLeftRight()
                if (CatDueller.config?.holdLeftClick != true) {
                    Mouse.startLeftAC()
                }

            }

            // Reset opponentOffEdge if opponent comes back close (distance <= 6) and not actually off edge
            if (!currentlyOffEdge && distance <= 6) {
                opponentOffEdge = false
                pendingMovementClear = false  // Cancel any pending movement clear
            }
        } else {
            opponentOffEdge = false
            pendingMovementClear = false  // Cancel any pending movement clear
        }


        // Define hasActiveHurtStrafe outside the if block so it can be used in both branches
        val hasActiveHurtStrafe = hurtStrafeDirection != 0 &&
                opponent() != null &&
                mc.thePlayer != null &&
                EntityUtils.getDistanceNoY(mc.thePlayer, opponent()!!) < 4f

        if (!opponentOffEdge && mc.thePlayer != null && opponent() != null) {
            // Blink At Edge logic

            if (CatDueller.config?.blinkAtEdge == true && currentOpponent != null) {
                handleBlinkAtEdge(player, currentOpponent)
                if (walkingToMiddle) return
            }

            // Check if player is off edge and press freeze bind if enabled (only during game)
            if (CatDueller.config?.freezeWhenOffEdge == true && mc.thePlayer != null) {
                val playerOffEdge = WorldUtils.entityOffEdge(mc.thePlayer)

                // Only press key when transitioning from not off edge to off edge
                if (playerOffEdge && !playerOffEdgeLastTick) {
                    val freezeBind = CatDueller.config?.freezeBind ?: "F1"
                    ChatUtils.combatInfo("Player went off edge during game - pressing freeze bind: $freezeBind")
                    simulateKeyPress(freezeBind)
                    offEdgeBindTriggeredThisGame = true  // Mark that we triggered off edge bind this game
                }

                playerOffEdgeLastTick = playerOffEdge
            } else {
                // Reset tracking when not in game
                playerOffEdgeLastTick = false
            }


            // Ensure sprinting
            if (!player.isSprinting) {
                Movement.startSprinting()
            }

            if (CatDueller.config?.disableAiming != true) {
                Mouse.startTracking()
            }

            val distance = EntityUtils.getDistanceNoY(mc.thePlayer, opponent())

            // Track when we enter attack range for timeout calculation
            val maxAttackDistance = CatDueller.config?.maxDistanceAttack ?: 5
            val isInAttackRange = distance <= maxAttackDistance

            if (isInAttackRange && !wasInAttackRange) {
                // Just entered attack range
                enteredAttackRangeTime = System.currentTimeMillis()
            } else if (!isInAttackRange && wasInAttackRange) {
                // Just left attack range
                enteredAttackRangeTime = 0L
            }
            wasInAttackRange = isInAttackRange


            // Attack logic: Hold Left Click mode vs Normal mode
            if (CatDueller.config?.holdLeftClick == true) {
                // Hold Left Click mode: Distance-based control
                // Start hold left click when within attack range, stop when out of range
                val maxAttackDistance = CatDueller.config?.maxDistanceAttack ?: 5

                if (waitingForOpponentAttack) {
                    // Pause hold left click when waiting for opponent attack
                    Mouse.stopHoldLeftClick()
                } else if (distance <= maxAttackDistance) {
                    // Within attack range - start hold left click
                    Mouse.startHoldLeftClick()
                } else {
                    // Out of attack range - stop hold left click
                    Mouse.stopHoldLeftClick()
                }
            } else {
                // Normal mode: Continuous attack with hit select cancellation
                // Start attacking continuously, hit select will cancel attacks when needed via onClientAttack event
                if (shouldStartAttacking(distance)) {
                    Mouse.startLeftAC()  // Start continuous attacking
                } else {
                    Mouse.stopLeftAC()
                }
            }

            // Check if opponent is at edge while player is not at edge
            val opponentAtEdge = opponentNearEdge(3f)

            // S Tap: When opponent at edge and player not at edge, do "backward-forward" when crossing configured distance
            if (CatDueller.config?.sTap == true && opponentAtEdge && !nearEdge(2.5f)) {
                val sTapDistance = CatDueller.config?.sTapDistance ?: 3.7f
                val now = System.currentTimeMillis()
                val crossed = lastDistanceToOpponent > sTapDistance && distance <= sTapDistance
                if (crossed && now - lastBackForwardAtEdgeTime >= 500) { // 0.5 second cooldown
                    lastBackForwardAtEdgeTime = now
                    edgeStutterActive = true  // Set flag to prevent movement override


                    // S-Tap: Only stop forward, don't go backward
                    Movement.stopForward()
                    Movement.startBackward()

                    TimeUtils.setTimeout({
                        if (!tapping) {
                            Movement.startForward()
                        }
                        edgeStutterActive = false  // Clear flag after completion
                    }, 100) // Resume forward after 100ms
                }
            }

            // Stop When Opponent At Edge: Same trigger as S-Tap but stops forward until timeout or hurtTime > 0
            if (CatDueller.config?.stopWhenOpponentAtEdge == true && opponentAtEdge && !nearEdge(2.5f)) {
                val sTapDistance = CatDueller.config?.sTapDistance ?: 3.7f
                val now = System.currentTimeMillis()
                val crossed = lastDistanceToOpponent > sTapDistance && distance <= sTapDistance

                // Start stop at edge if crossed distance and not already active
                if (crossed && !stopAtEdgeActive && now - lastBackForwardAtEdgeTime >= 500) {
                    stopAtEdgeActive = true
                    stopAtEdgeStartTime = now
                    stopAtEdgeInitialHurtTime = player.hurtTime
                    Movement.stopForward()

                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtils.combatInfo("Stop When Opponent At Edge activated - distance: $distance")
                    }
                }

                // Check if we should end stop at edge
                if (stopAtEdgeActive) {
                    val stopDuration = CatDueller.config?.stopAtEdgeDuration?.toLong() ?: 500L
                    val timeElapsed = now - stopAtEdgeStartTime
                    val hurtTimeIncreased = player.hurtTime > 0 && player.hurtTime > stopAtEdgeInitialHurtTime

                    if (timeElapsed >= stopDuration || hurtTimeIncreased) {
                        stopAtEdgeActive = false
                        // Only restart forward if not tapping and not in edge stutter
                        if (!tapping && !edgeStutterActive) {
                            Movement.startForward()
                        }

                        if (CatDueller.config?.combatLogs == true) {
                            val reason = if (hurtTimeIncreased) "hurtTime > 0" else "timeout"
                            ChatUtils.combatInfo("Stop When Opponent At Edge ended - reason: $reason, duration: ${timeElapsed}ms")
                        }
                    }
                }
            } else if (stopAtEdgeActive) {
                // Cancel stop at edge if conditions no longer met (opponent not at edge or player at edge)
                stopAtEdgeActive = false
                // Only restart forward if not tapping and not in edge stutter
                if (!tapping && !edgeStutterActive) {
                    Movement.startForward()
                }

                if (CatDueller.config?.combatLogs == true) {
                    ChatUtils.combatInfo("Stop When Opponent At Edge cancelled - conditions no longer met")
                }
            }

            lastDistanceToOpponent = distance

            val movePriority = arrayListOf(0, 0)
            var clear = false
            var randomStrafe = false

            // Edge Safety Check: Force change mid-range strafe direction if too close to edges
            // But ignore edge safety for first 1 second after game start
            val timeSinceGameStart = System.currentTimeMillis() - gameStartTime
            val ignoreEdgeForEarlyGame = timeSinceGameStart < 1000
            val leftDistance = WorldUtils.distanceToLeftEdge(player)
            val rightDistance = WorldUtils.distanceToRightEdge(player)
            val tooCloseToLeftEdge = if (ignoreEdgeForEarlyGame) {
                if (CatDueller.config?.combatLogs == true && leftDistance < 7.0f) {
                    ChatUtils.combatInfo("Ignoring left edge safety for early game (${timeSinceGameStart}ms since start, distance: $leftDistance)")
                }
                false
            } else {
                leftDistance < 7.0f
            }
            val tooCloseToRightEdge = if (ignoreEdgeForEarlyGame) {
                if (CatDueller.config?.combatLogs == true && rightDistance < 7.0f) {
                    ChatUtils.combatInfo("Ignoring right edge safety for early game (${timeSinceGameStart}ms since start, distance: $rightDistance)")
                }
                false
            } else {
                rightDistance < 7.0f
            }

            // Force change mid-range strafe direction if too close to edge
            if ((tooCloseToLeftEdge || tooCloseToRightEdge) && midRangeStrafeDecided) {
                val safeDirection = if (leftDistance > rightDistance) 1 else 2 // 1=left, 2=right

                // Only change if we're not already going in the safe direction
                val needsChange = (tooCloseToLeftEdge && lastStrafeDirection != 2) ||
                        (tooCloseToRightEdge && lastStrafeDirection != 1)

                if (needsChange) {
                    lastStrafeDirection = safeDirection

                    if (CatDueller.config?.combatLogs == true) {
                        val edgeType = if (tooCloseToLeftEdge) "left" else "right"
                        val direction = if (safeDirection == 1) "left" else "right"
                        ChatUtils.combatInfo("Edge Safety: Too close to $edgeType edge (${"%.1f".format(if (tooCloseToLeftEdge) leftDistance else rightDistance)}), forcing mid range strafe $direction")
                    }
                }
            }

            // hurt strafe logic is now handled with absolute priority in movement section
            // No need for movePriority system - direct Movement control takes precedence

            if (distance > 8 && !WorldUtils.airInFront(player, 5f) && player.hurtTime == 0) {
                // Use random strafe if enabled, otherwise clear movement
                if (CatDueller.config?.randomStrafe == true) {
                    randomStrafe = true
                } else {
                    clear = true
                    // Only reset if not jump triggered strafe
                    if (!jumpTriggeredStrafe) {
                        midRangeStrafeDecided = false
                    }
                }

                // Jump when actually moving forward (check real velocity, not just key state)
                val isActuallyMovingForward = Movement.forward() && player.motionX != 0.0 || player.motionZ != 0.0
                val isMovingTowardsOpponent = opponent()?.let { opp ->
                    val dx = opp.posX - player.posX
                    val dz = opp.posZ - player.posZ
                    val playerMotionX = player.motionX
                    val playerMotionZ = player.motionZ
                    // Check if player's motion is in the same direction as opponent
                    (dx * playerMotionX + dz * playerMotionZ) > 0
                } ?: false

                if (player.onGround && !WorldUtils.airInFront(player, 3f) && !waitingForOpponentAttack &&
                    Movement.forward() && (isActuallyMovingForward || isMovingTowardsOpponent)
                ) {

                    // Jump first
                    Movement.singleJump(RandomUtils.randomIntInRange(100, 150))

                    // Handle strafe based on random strafe setting
                    if (CatDueller.config?.randomStrafe == true) {
                        // Use random strafe - let it handle the movement
                        randomStrafe = true

                    } else {
                        // Use fixed direction strafe (original logic)
                        if (!midRangeStrafeDecided) {
                            lastStrafeDirection = decideStrafeDirectionByEdgeDistance()
                            midRangeStrafeDecided = true
                            jumpTriggeredStrafe = true  // Mark as jump triggered

                        }

                        // Apply strafe immediately when jumping - both to movePriority and direct Movement
                        if (lastStrafeDirection == 1) {
                            movePriority[0] += 10  // left
                            Movement.stopRight()
                            Movement.startLeft()
                        } else if (lastStrafeDirection == 2) {
                            movePriority[1] += 10  // right
                            Movement.stopLeft()
                            Movement.startRight()
                        }
                    }
                } else if (jumpTriggeredStrafe) {
                    // Continue existing jump triggered strafe even if not jumping this tick
                    if (lastStrafeDirection == 1) {
                        movePriority[0] += 10  // left
                    } else if (lastStrafeDirection == 2) {
                        movePriority[1] += 10  // right
                    }
                }
            } else if (distance > 6) {
                // 6+ blocks: use random strafe if enabled
                if (CatDueller.config?.randomStrafe == true) {
                    randomStrafe = true
                } else {
                    // Fallback to mid-range strafe logic if random strafe is disabled
                    if (!midRangeStrafeDecided) {
                        lastStrafeDirection = decideStrafeDirectionByEdgeDistance()
                        midRangeStrafeDecided = true
                        midRangeStrafeStartTime = System.currentTimeMillis()
                        CatDueller.config?.strafeSwitchDelay ?: 3000

                    }

                    if (lastStrafeDirection == 1) {
                        movePriority[0] += 10  // left
                    } else if (lastStrafeDirection == 2) {
                        movePriority[1] += 10  // right
                    }
                }
            } else if (distance > 3.5) {
                // 3-6 blocks: transition range - continue previous strafe or start new
                val currentTime = System.currentTimeMillis()

                // Check mid-range strafe timeout (only if strafe switching is enabled)
                val switchDelay = CatDueller.config?.strafeSwitchDelay ?: 3000
                val enableStrafeSwitch = CatDueller.config?.enableStrafeSwitch ?: true

                if (enableStrafeSwitch && midRangeStrafeDecided && midRangeStrafeStartTime > 0 &&
                    currentTime - midRangeStrafeStartTime > switchDelay
                ) {
                    ChatUtils.combatInfo("Mid-range strafe timeout (${currentTime - midRangeStrafeStartTime}ms) - forcing close range strafe")
                    closeRangeStrafe = true
                    midRangeStrafeDecided = false
                    jumpTriggeredStrafe = false
                    midRangeStrafeStartTime = 0L

                    // Execute close range strafe immediately when timeout occurs
                    when (lastStrafeDirection) {
                        1 -> {
                            movePriority[1] += 15  // Switch to right with higher priority
                            ChatUtils.combatInfo("Timeout: Switching from left to right strafe")
                        }

                        2 -> {
                            movePriority[0] += 15  // Switch to left with higher priority
                            ChatUtils.combatInfo("Timeout: Switching from right to left strafe")
                        }

                        else -> {
                            lastStrafeDirection = decideStrafeDirectionByEdgeDistance()
                            if (lastStrafeDirection == 1) {
                                movePriority[1] += 15  // Start with opposite direction
                            } else {
                                movePriority[0] += 15  // Start with opposite direction
                            }
                            ChatUtils.combatInfo("Timeout: Starting close range strafe")
                        }
                    }
                }

                if (!midRangeStrafeDecided && !closeRangeStrafe) {
                    lastStrafeDirection = decideStrafeDirectionByEdgeDistance()
                    midRangeStrafeDecided = true
                    midRangeStrafeStartTime = currentTime
                    CatDueller.config?.strafeSwitchDelay ?: 3000

                }

                if (!closeRangeStrafe) {
                    if (lastStrafeDirection == 1) {
                        movePriority[0] += 10  // left
                    } else if (lastStrafeDirection == 2) {
                        movePriority[1] += 10  // right
                    }
                }
            } else {
                // enter 3 blocks: strafe switching (if enabled)
                val enableStrafeSwitch = CatDueller.config?.enableStrafeSwitch ?: true

                if (!closeRangeStrafe && enableStrafeSwitch) {
                    closeRangeStrafe = true
                    midRangeStrafeDecided = false
                    jumpTriggeredStrafe = false  // Reset jump triggered flag when entering close range
                    midRangeStrafeStartTime = 0L  // Reset timeout when entering close range
                    ChatUtils.combatInfo("Strafe switching - entered close range")
                }

                // strafe switch until attack (if strafe switching is enabled)
                if (closeRangeStrafe && enableStrafeSwitch) {
                    // Check if near LEFT OR RIGHT edge - if so, strafe towards larger space instead of reversing
                    if (nearLeftOrRightEdge(6f)) {
                        // Near left or right edge - strafe towards the direction with more space
                        val safeDirection = decideStrafeDirectionByEdgeDistance()
                        lastStrafeDirection = safeDirection

                        if (safeDirection == 1) {
                            movePriority[0] += 10  // left
                        } else {
                            movePriority[1] += 10  // right
                        }

                        if (CatDueller.config?.combatLogs == true) {
                            val leftDistance = WorldUtils.distanceToLeftEdge(player)
                            val rightDistance = WorldUtils.distanceToRightEdge(player)
                            val direction = if (safeDirection == 1) "left" else "right"
                            ChatUtils.combatInfo(
                                "Close Range Strafe (Near Left/Right Edge): Moving $direction (leftDist: ${
                                    "%.1f".format(
                                        leftDistance
                                    )
                                }, rightDist: ${"%.1f".format(rightDistance)})"
                            )
                        }
                    } else {
                        // Not near left/right edge - use normal reverse strafe logic
                        when (lastStrafeDirection) {
                            1 -> {
                                movePriority[1] += 10  // reverse to right
                            }

                            2 -> {
                                movePriority[0] += 10  // reverse to left
                            }

                            else -> {
                                lastStrafeDirection = decideStrafeDirectionByEdgeDistance()
                                if (lastStrafeDirection == 1) {
                                    movePriority[0] += 10  // left
                                } else {
                                    movePriority[1] += 10  // right
                                }
                            }
                        }
                    }
                } else if (!enableStrafeSwitch) {
                    // Strafe switching disabled - continue using mid-range strafe logic
                    if (!midRangeStrafeDecided) {
                        lastStrafeDirection = decideStrafeDirectionByEdgeDistance()
                        midRangeStrafeDecided = true
                        midRangeStrafeStartTime = System.currentTimeMillis()
                        if (CatDueller.config?.combatLogs == true) {
                            val direction = if (lastStrafeDirection == 1) "left" else "right"
                            ChatUtils.combatInfo("Strafe switching disabled - continuing mid-range strafe $direction at close range")
                        }
                    }

                    // Apply mid-range strafe direction
                    if (lastStrafeDirection == 1) {
                        movePriority[0] += 10  // left
                    } else if (lastStrafeDirection == 2) {
                        movePriority[1] += 10  // right
                    }
                }
            }

            // Ignore edge detection for first 1 second after game start (reuse variables from above)
            // val timeSinceGameStart and ignoreEdgeForEarlyGame already declared above
            val playerNearEdge = if (ignoreEdgeForEarlyGame) {
                if (CatDueller.config?.combatLogs == true && nearLeftOrRightEdge(5f)) {
                    ChatUtils.combatInfo("Ignoring edge detection for early game (${timeSinceGameStart}ms since start)")
                }
                false
            } else {
                nearLeftOrRightEdge(5f)
            }
            val bothAtEdge = opponentAtEdge && playerNearEdge

            // Only clear movement if combo >= 3 AND not both at edge (need to strafe away from edge)
            if (shouldClearMovementForCombo(bothAtEdge)) {
                clear = true
            }

            // For combo jump strafe, also ignore edge detection in early game
            if (!ignoreEdgeForEarlyGame) nearEdge(5f)
            // Strafe away from edge when opponent is at edge OR when both are at edge
            val playerNearLeftOrRightEdge = nearLeftOrRightEdge(6f)
            // Modified: Also strafe towards safer direction when only opponent is at edge (even if player is not near edge)
            val shouldStrafeAwayFromEdge = (bothAtEdge || (opponentAtEdge && distance <= 10f))

            // decide strafe direction when opponent at edge (regardless of player position)
            if (shouldStrafeAwayFromEdge) {
                val currentTime = System.currentTimeMillis()

                if (opponentEdgeStrafeDirection == 0 || currentTime - opponentEdgeStrafeLastChange >= 500) {
                    val leftDistance = WorldUtils.distanceToLeftEdge(player)
                    val rightDistance = WorldUtils.distanceToRightEdge(player)

                    // Always strafe towards the direction with more space (safer direction)
                    opponentEdgeStrafeDirection = if (playerNearLeftOrRightEdge) {
                        // Player is also near edge - use decideStrafeDirectionByEdgeDistance for player safety
                        decideStrafeDirectionByEdgeDistance()
                    } else {
                        // Only opponent is at edge - still strafe towards larger space to maintain positioning advantage
                        if (leftDistance > rightDistance) 1 else 2
                    }

                    val situation =
                        if (bothAtEdge) "BothAtEdge" else if (playerNearLeftOrRightEdge) "OpponentAtEdge+PlayerNearEdge" else "OpponentAtEdge"
                    val direction = if (opponentEdgeStrafeDirection == 1) "left" else "right"
                    ChatUtils.combatInfo(
                        "$situation strafe: $direction (leftDist: ${"%.1f".format(leftDistance)}, rightDist: ${
                            "%.1f".format(
                                rightDistance
                            )
                        }, strafing towards larger space)"
                    )
                    opponentEdgeStrafeLastChange = currentTime
                }

                // Use higher priority than normal strafe but lower than hurt strafe
                val edgePriority =
                    if (bothAtEdge) 18 else if (playerNearLeftOrRightEdge) 15 else 12  // Lower priority when only opponent at edge
                when (opponentEdgeStrafeDirection) {
                    1 -> movePriority[0] += edgePriority  // strafe left
                    2 -> movePriority[1] += edgePriority  // strafe right
                }
            } else {
                opponentEdgeStrafeDirection = 0
            }

            val waitingAndWillJump = waitingForOpponentAttack && waitingDistanceWasGreaterThan7 && distance > 7.0f

            // HURT STRAFE HAS ABSOLUTE HIGHEST PRIORITY - EXECUTE IMMEDIATELY
            if (hasActiveHurtStrafe) {
                // Force execute hurt strafe regardless of ANY other conditions
                Combat.stopRandomStrafe()
                Movement.clearLeftRight()

                // Check if current hurt strafe direction is safe (not towards edge)
                val leftDistance = WorldUtils.distanceToLeftEdge(player)
                val rightDistance = WorldUtils.distanceToRightEdge(player)
                val tooCloseToLeftEdge = leftDistance < 4.0f
                val tooCloseToRightEdge = rightDistance < 4.0f

                // Override hurt strafe direction if it would strafe towards edge
                var actualStrafeDirection = hurtStrafeDirection
                if (tooCloseToLeftEdge && hurtStrafeDirection == 1) {
                    // Too close to left edge but trying to strafe left - switch to right
                    actualStrafeDirection = 2
                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtils.combatInfo("Hurt Strafe: Overriding left strafe due to left edge proximity ($leftDistance)")
                    }
                } else if (tooCloseToRightEdge && hurtStrafeDirection == 2) {
                    // Too close to right edge but trying to strafe right - switch to left
                    actualStrafeDirection = 1
                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtils.combatInfo("Hurt Strafe: Overriding right strafe due to right edge proximity ($rightDistance)")
                    }
                }

                when (actualStrafeDirection) {
                    1 -> {
                        Movement.stopRight()
                        Movement.startLeft()
                    }

                    2 -> {
                        Movement.stopLeft()
                        Movement.startRight()
                    }
                }
                // Skip ALL other movement logic when hurt strafe is active
            } else if ((clear || player.hurtTime > 0 || waitingAndWillJump)) {
                // When hurt, check if near LEFT OR RIGHT edge and strafe away from edge
                if (player.hurtTime > 0 && nearLeftOrRightEdge(6f)) {
                    // Near left or right edge when hurt - strafe away from edge
                    Combat.stopRandomStrafe()

                    val leftDistance = WorldUtils.distanceToLeftEdge(player)
                    val rightDistance = WorldUtils.distanceToRightEdge(player)

                    if (leftDistance < rightDistance) {
                        // Closer to left edge - strafe right
                        Movement.stopLeft()
                        Movement.startRight()
                    } else {
                        // Closer to right edge - strafe left
                        Movement.stopRight()
                        Movement.startLeft()
                    }
                } else {
                    // Not near edge or not hurt - stop all strafing
                    Combat.stopRandomStrafe()
                    Movement.clearLeftRight()
                }
            } else if (!tapping) {
                if (randomStrafe) {
                    Movement.clearLeftRight()
                    Combat.startRandomStrafe(900, 1400)
                } else {
                    Combat.stopRandomStrafe()

                    // Final edge safety check before applying movement
                    val leftDistance = WorldUtils.distanceToLeftEdge(player)
                    val rightDistance = WorldUtils.distanceToRightEdge(player)
                    val tooCloseToLeftEdge = leftDistance < 4.0f
                    val tooCloseToRightEdge = rightDistance < 4.0f

                    // Determine intended strafe direction from movePriority
                    var intendedDirection: Int   // 0=none, 1=left, 2=right
                    intendedDirection = if (movePriority[0] > movePriority[1]) {
                        1  // Want to go left
                    } else if (movePriority[1] > movePriority[0]) {
                        2  // Want to go right
                    } else {
                        if (RandomUtils.randomBool()) 1 else 2
                    }

                    // Override if intended direction is towards edge
                    if (tooCloseToLeftEdge && intendedDirection == 1) {
                        // Too close to left edge but trying to strafe left - force right
                        intendedDirection = 2
                        if (CatDueller.config?.combatLogs == true) {
                            ChatUtils.combatInfo("Final Edge Safety: Overriding left strafe due to left edge proximity ($leftDistance)")
                        }
                    } else if (tooCloseToRightEdge && intendedDirection == 2) {
                        // Too close to right edge but trying to strafe right - force left
                        intendedDirection = 1
                        if (CatDueller.config?.combatLogs == true) {
                            ChatUtils.combatInfo("Final Edge Safety: Overriding right strafe due to right edge proximity ($rightDistance)")
                        }
                    }

                    // Apply final movement
                    when (intendedDirection) {
                        1 -> {
                            Movement.stopRight()
                            Movement.startLeft()
                        }

                        2 -> {
                            Movement.stopLeft()
                            Movement.startRight()
                        }
                    }
                }
            }

            // Check for combo >= 3 and close distance to stop forward movement
            if (shouldStopForwardForCombo(distance, edgeStutterActive, stopAtEdgeActive)) {
                Movement.stopForward()
                Movement.startBackward()
            } else if (distance < 1.8 && WorldUtils.airInFront(mc.thePlayer, 5f)) {
                if (!edgeStutterActive && !stopAtEdgeActive) {  // Don't override during edge stutter or stop at edge
                    Movement.stopForward()
                    Movement.startBackward()
                }
            } else {
                if (!edgeStutterActive && !stopAtEdgeActive) {  // Don't override during edge stutter or stop at edge
                    Movement.stopBackward()
                    if (!tapping) {
                        Movement.startForward()
                    }
                }
            }

            if (!waitingForOpponentAttack && mc.thePlayer != null) {
                opponent()?.let { opp ->
                    val distance = EntityUtils.getDistanceNoY(mc.thePlayer, opp)

                    // Only trigger when hit select at edge is enabled, player is near edge, opponent is at medium distance, AND distance > 7
                    if (CatDueller.config?.hitSelectAtEdge == true && playerNearEdge && distance > 7.0f && distance <= 12.0f) {
                        if (CatDueller.config?.combatLogs == true) {
                            ChatUtils.combatInfo("Player near edge - late hit selecting (distance: $distance, >7: true)")
                        }

                        waitingForOpponentAttack = true
                        waitingForOpponentAttackStartTime = System.currentTimeMillis()

                        // Distance is guaranteed to be > 7 at this point
                        waitingDistanceWasGreaterThan7 = true

                        if (CatDueller.config?.combatLogs == true) {
                            val hitSelectDelay = CatDueller.config?.hitSelectDelay ?: 400
                            ChatUtils.combatInfo("waitingForOpponentAttack set to TRUE - ${hitSelectDelay}ms timeout")
                        }

                        if (CatDueller.config?.holdLeftClick == true) {
                            Mouse.stopHoldLeftClick()
                        }
                    } else if (CatDueller.config?.hitSelectAtEdge == true && playerNearEdge && distance >= 3.5f && distance <= 7.0f) {
                        // When near edge but distance <= 7, don't trigger waiting - just log for debugging
                        if (CatDueller.config?.combatLogs == true) {
                            ChatUtils.combatInfo("Player near edge but distance <= 7 ($distance) - not triggering waitingForOpponentAttack")
                        }
                    }
                }
            }

            // Track time player has been on ground
            if (mc.thePlayer != null) {
                val currentOnGround = player.onGround
                val currentTime = System.currentTimeMillis()

                if (currentOnGround && !lastOnGround) {
                    // Just landed, reset ground time
                    groundTime = currentTime
                } else if (!currentOnGround) {
                    // Not on ground, reset ground time
                    groundTime = 0L
                }

                lastOnGround = currentOnGround
            }

            // Detect if attacked by opponent
            if (mc.thePlayer != null) {
                val currentHurtTime = player.hurtTime

                // Cancel game start wait period when attacked
                if (gameStartWaitActive && currentHurtTime > 0 && lastPlayerHurtTime == 0) {
                    gameStartWaitActive = false
                    ChatUtils.combatInfo("Game start wait period cancelled - player was attacked, edge features now enabled")
                }


                // Check once at hurtTime = 4 and set hurt strafe direction (400ms after hit)
                if (currentHurtTime == 4 && CatDueller.config?.hurtStrafe == true) {
                    // If there was previous hurt strafe, stop it first

                    // Always activate hurt strafe - decide direction based on edge distance
                    hurtStrafeDirection = decideStrafeDirectionByEdgeDistance()

                    val leftDistance = WorldUtils.distanceToLeftEdge(player)
                    val rightDistance = WorldUtils.distanceToRightEdge(player)

                    if (CatDueller.config?.combatLogs == true) {
                        val direction = if (hurtStrafeDirection == 1) "left" else "right"
                        ChatUtils.combatInfo(
                            "Hurt Strafe: activated $direction (leftDist: ${"%.1f".format(leftDistance)}, rightDist: ${
                                "%.1f".format(
                                    rightDistance
                                )
                            })"
                        )
                    }

                    // Auto stop after 400ms
                    TimeUtils.setTimeout({
                        hurtStrafeDirection = 0
                        if (CatDueller.config?.combatLogs == true) {
                            ChatUtils.combatInfo("Hurt Strafe: deactivated")
                        }
                    }, 400)
                }

                // Jump Velocity logic - press jump key when hurtTime increases (attacked)
                val jumpChance = CatDueller.config?.jumpVelocity ?: 0
                if (jumpChance > 0 && currentHurtTime > lastHurtTime && !tapping && !edgeStutterActive) {
                    // hurtTime increased - player was just attacked
                    val randomChance = (1..100).random()
                    if (randomChance <= jumpChance) {
                        // Set jump key to pressed state
                        setJump = true
                        net.minecraft.client.settings.KeyBinding.setKeyBindState(
                            mc.gameSettings.keyBindJump.keyCode,
                            true
                        )
                        net.minecraft.client.settings.KeyBinding.onTick(mc.gameSettings.keyBindJump.keyCode)

                        if (CatDueller.config?.combatLogs == true) {
                            ChatUtils.combatInfo("Jump Velocity: pressed jump key with ${jumpChance}% chance (rolled ${randomChance})")
                        }
                    }
                }


                // Update lastHurtTime for next tick
                lastHurtTime = currentHurtTime

                // If waiting for opponent attack and detected being attacked (hurtTime == 10 means just got hit)
                if (waitingForOpponentAttack && currentHurtTime > 0 && lastPlayerHurtTime == 0) {
                    ChatUtils.combatInfo("Cancelling waitingForOpponentAttack - player was attacked (hurtTime: $currentHurtTime)")
                    waitingForOpponentAttack = false
                    waitingForOpponentAttackStartTime = 0L  // Reset timeout
                    enteredAttackRangeTime = 0L  // Reset attack range time
                    waitingDistanceWasGreaterThan7 = false  // Reset distance state
                    jumpedAtDistance7 = false  // Reset jump flag

                    // Hold left click is managed by distance-based logic

                    // Clear all movement states when attacked to avoid server lag
                    Combat.stopRandomStrafe()
                    Movement.clearLeftRight()

                    // Immediately start attacking (non-Hold Left Click mode)
                    if (CatDueller.config?.holdLeftClick != true) {
                        Mouse.startLeftAC()
                    }
                }






                lastPlayerHurtTime = currentHurtTime
            }

            // Toggle blatant at edge logic
            // Only trigger if not already in blatant mode due to blacklisted player
            if (CatDueller.config?.toggleBlatantAtEdge == true && !edgeBlatantToggled && !blatantToggled && mc.thePlayer != null && !isInGameStartWaitPeriod()) {

                // Calculate distance from center (same method as blinkAtEdge)
                val centerX = 0.5
                val centerZ = 0.5
                val centerDx = kotlin.math.abs(player.posX) - centerX
                val centerDz = kotlin.math.abs(player.posZ) - centerZ
                val distanceFromCenter = sqrt(centerDx * centerDx + centerDz * centerDz)
                val triggerDistance = CatDueller.config?.toggleBlatantDistance?.toDouble() ?: 6.0

                // Trigger blatant mode if either:
                // 1. Player is far from center (configurable distance), OR
                // 2. Opponent combo >= 2 (being comboed by opponent)
                val shouldToggleBlatant = (distanceFromCenter > triggerDistance || opponentCombo >= 2) && combo < 2

                if (shouldToggleBlatant) {
                    val keyName = CatDueller.config?.blatantToggleKey ?: "F1"
                    val reason = if (distanceFromCenter > triggerDistance)
                        "Distance from center: $distanceFromCenter > $triggerDistance"
                    else
                        "Opponent combo >= 2 ($opponentCombo)"
                    ChatUtils.combatInfo("$reason - toggling blatant mode with key: $keyName")

                    simulateKeyPress(keyName)
                    edgeBlatantToggled = true  // Mark as toggled to prevent spam
                }
            }

            // don't walk off an edge - but don't interfere with hurt strafe
            if (!hasActiveHurtStrafe) {
                if (WorldUtils.airInFront(player, 2f) && player.onGround) {
                    Movement.startSneaking()
                } else {
                    Movement.stopSneaking()
                }
            }

            if (WorldUtils.airInBack(player, 0.3f) && player.onGround) {
                Movement.startSneaking()
            } else {
                Movement.stopSneaking()
            }


        } else {
            if (opponentOffEdge && StateManager.state == StateManager.States.PLAYING) {
                val currentOpponent = opponent()
                if (currentOpponent != null && mc.thePlayer != null) {
                    val distance = mc.thePlayer.getDistanceToEntity(currentOpponent)

                    // Check if player is also near LEFT OR RIGHT edge
                    val playerNearEdge = nearEdge(5f) || WorldUtils.airInFront(mc.thePlayer, 5f)

                    if (playerNearEdge) {
                        clearMovementAndCombat()
                        pendingMovementClear = false
                    } else {
                        // Player not near edge - use original clear logic
                        if (distance > 5f) {
                            // If distance > 5, delay movement clear by 1 second
                            if (!pendingMovementClear) {
                                pendingMovementClear = true
                                movementClearTime = System.currentTimeMillis() + 500L // 1 second delay
                                ChatUtils.combatInfo(
                                    "Opponent off edge, distance > 5 (${
                                        String.format(
                                            "%.1f",
                                            distance
                                        )
                                    }), delaying movement clear by 1s"
                                )
                            }
                        } else {
                            // If distance <= 7, clear immediately
                            clearMovementAndCombat()
                            pendingMovementClear = false
                        }
                    }
                } else {
                    // No opponent or player, clear immediately
                    clearMovementAndCombat()
                    pendingMovementClear = false
                }
            } else {
                // Opponent not off edge, cancel any pending clear
                pendingMovementClear = false
            }
        }

        // Check for pending movement clear timeout
        if (pendingMovementClear && System.currentTimeMillis() >= movementClearTime) {
            // Check if player is near LEFT OR RIGHT edge before clearing
            val playerNearEdge = nearLeftOrRightEdge(3.5f)
            if (playerNearEdge) {
                // Player is near left/right edge - don't clear, instead strafe to safety
                val saferDirection = decideStrafeDirectionByEdgeDistance()
                opponentEdgeStrafeDirection = saferDirection

                // Apply strafe to move away from edge
                when (saferDirection) {
                    1 -> {
                        Movement.stopRight()
                        Movement.startLeft()
                    }

                    2 -> {
                        Movement.stopLeft()
                        Movement.startRight()
                    }
                }

                ChatUtils.combatInfo("Delayed clear cancelled - player near edge, strafing ${if (saferDirection == 1) "left" else "right"} for safety")
                pendingMovementClear = false
            } else {
                // Player not near edge - safe to clear
                clearMovementAndCombat()
                pendingMovementClear = false
                ChatUtils.combatInfo("Delayed movement clear executed")
            }
        }


    }

    /**
     * Checks if game duration exceeds the configured threshold and sends a taunt message.
     *
     * Randomly selects from a list of taunt messages and sends it via /ac command
     * if the game lasted longer than the configured threshold.
     */
    private fun checkTauntMessage() {
        // Only check if taunt messages are enabled and we haven't sent one yet
        if (!tauntMessageSent && CatDueller.config?.enableTauntMessages == true) {
            // Check if we have valid game duration data
            if (StateManager.gameStartedAt > 0 && StateManager.lastGameDuration > 0) {
                val gameDurationSeconds = StateManager.lastGameDuration / 1000.0
                val thresholdSeconds = CatDueller.config?.tauntThresholdSeconds ?: 20

                if (gameDurationSeconds >= thresholdSeconds) {
                    // Random taunt messages
                    val tauntMessages =
                        listOf("lol", "lmao", "xd", "zzz", "wtf", "wow", "??", "ok", "nice one", "bai bye")
                    val randomTaunt = tauntMessages[RandomUtils.randomIntInRange(0, tauntMessages.size - 1)]

                    // Send the taunt message with delay
                    TimeUtils.setTimeout(fun() {
                        if (CatDueller.bot?.toggled() == true) {
                            ChatUtils.sendAsPlayer("/ac $randomTaunt")
                            ChatUtils.info("Sent taunt message after $gameDurationSeconds seconds: $randomTaunt")
                        }
                    }, RandomUtils.randomIntInRange(200, 500))  // Delay after game end

                    tauntMessageSent = true  // Mark as sent to avoid spam
                }
            }
        }
    }

    /**
     * Handles the blink-at-edge mechanic.
     *
     * When the player is near an edge, triggers a blink to walk to the middle
     * of the platform, then blinks again to attack the opponent.
     *
     * @param player The player entity
     * @param currentOpponent The opponent entity
     */
    private fun handleBlinkAtEdge(player: EntityPlayer, currentOpponent: EntityPlayer) {
        val now = System.currentTimeMillis()
        EntityUtils.getDistanceNoY(player, currentOpponent)
        val centerX = 0.5
        val centerZ = 0.5
        val centerDx = kotlin.math.abs(player.posX) - centerX
        val centerDz = kotlin.math.abs(player.posZ) - centerZ
        val distanceFromCenter = sqrt(centerDx * centerDx + centerDz * centerDz)

        if (!walkingToMiddle &&
                (WorldUtils.airInBack(player, 5f) ||
                        WorldUtils.airOnRight(player, 3.5f) ||
                        WorldUtils.airOnLeft(player, 3.5f) ||
                        distanceFromCenter > 6.2f) &&
                distanceFromCenter > 3.6f && nextBlinkPossible && !opponentOffEdge
        ) {

            walkingToMiddle = true
            nextBlinkPossible = false
            walkToMiddleStartTime = now
            qCooldownActive = true
            secondQPressed = false // reset for this cycle
            ChatUtils.combatInfo("Triggering walk to middle")

            // Stop tracking and left click when starting blink
            Mouse.stopTracking()
            Mouse.stopHoldLeftClick()
            Movement.clearAll()
            Combat.stopRandomStrafe()

            // First Q press
            if (!qPressed) {
                Mouse.stopHoldLeftClick()
                ChatUtils.combatInfo("Released mouse button on ${CatDueller.config?.blinkKey ?: "Q"} press (start walk)")
                val blinkKeyCode = getKeyCodeFromName(CatDueller.config?.blinkKey ?: "Q")
                robot?.let {
                    it.keyPress(blinkKeyCode)
                    it.keyRelease(blinkKeyCode)
                }
                firstQTime = now // Track the first Q press time
                qPressed = true
            }
        }

        if (walkingToMiddle) {
            val targetX = 0.5
            val targetZ = 0.5
            val toMiddleX = targetX - kotlin.math.abs(player.posX)
            val toMiddleZ = targetZ - kotlin.math.abs(player.posZ)
            val distanceToMiddle = sqrt(toMiddleX * toMiddleX + toMiddleZ * toMiddleZ)
            val toMiddleX2 = targetX - player.posX
            val toMiddleZ2 = targetZ - player.posZ
            val angleToMiddle = atan2(-toMiddleX2, toMiddleZ2)
            val yawToMiddle = Math.toDegrees(angleToMiddle)
            player.rotationYaw = yawToMiddle.toFloat()

            if (distanceToMiddle > 1.2) {
                Movement.startForward()
            } else {
                ChatUtils.combatInfo("Middle Reached")
                // Face opponent
                currentOpponent.let {
                    val opponentXDiff = it.posX - player.posX
                    val opponentZDiff = it.posZ - player.posZ
                    val angleToOpponent = atan2(-opponentXDiff, opponentZDiff)
                    val yawToOpponent = Math.toDegrees(angleToOpponent)
                    player.rotationYaw = yawToOpponent.toFloat()
                }

                Mouse.startHoldLeftClick()
                ChatUtils.combatInfo("Started Mouse Press")
                Movement.startForward()

                if (!secondQPressed) {
                    val blinkKeyCode = getKeyCodeFromName(CatDueller.config?.blinkKey ?: "Q")
                    robot?.let {
                        it.keyPress(blinkKeyCode)
                        it.keyRelease(blinkKeyCode)
                    }
                    ChatUtils.combatInfo("Pressed ${CatDueller.config?.blinkKey ?: "Q"} Key (Reached Middle)")
                    qPressed = false
                    secondQPressed = true
                    qCooldownActive = false
                    walkingToMiddle = false
                    justBlinked = true

                    // Resume tracking and left click after blink
                    if (CatDueller.config?.disableAiming != true) {
                        Mouse.startTracking()
                    }
                    if (CatDueller.config?.holdLeftClick == true) {
                        Mouse.startHoldLeftClick()
                    }
                }
                nextBlinkPossible = true
            }
        }

        // Automatically press blink key if 6 seconds have passed without second press
        if (walkingToMiddle && qPressed && !secondQPressed && now - firstQTime > 6000) {
            ChatUtils.combatInfo("Automatically pressing ${CatDueller.config?.blinkKey ?: "Q"} after 6s timeout")
            secondQPressed = true
            qCooldownActive = false
            walkingToMiddle = false
            qPressed = false
            justBlinked = true
            nextBlinkPossible = true  // Reset to allow next blink

            // Resume tracking and left click after timeout
            if (CatDueller.config?.disableAiming != true) {
                Mouse.startTracking()
            }
            if (CatDueller.config?.holdLeftClick == true) {
                Mouse.startHoldLeftClick()
            }
        }
    }

    /**
     * Reads scoreboard lines 8 and 9 to detect Huaxi server.
     *
     * If Huaxi server is detected, sends a queue command to dodge the game.
     *
     * @return True if Huaxi was detected and dodge initiated, false otherwise
     */
    private fun readScoreboardLines8And9(): Boolean {
        val world = mc.theWorld ?: return false
        val scoreboard = world.scoreboard
        val sidebarObjective = scoreboard.getObjectiveInDisplaySlot(1)
        val player = mc.thePlayer ?: return false

        if (sidebarObjective != null) {
            val scores = scoreboard.getSortedScores(sidebarObjective)
                .filter { it.playerName != null && it.playerName.isNotBlank() }
                .sortedByDescending { it.scorePoints }

            val lines = scores.map { score ->
                val team = scoreboard.getPlayersTeam(score.playerName)
                val line = if (team != null)
                    team.colorPrefix + score.playerName + team.colorSuffix
                else
                    score.playerName
                net.minecraft.util.StringUtils.stripControlCodes(line)
            }.reversed()

            if (lines.size >= 9) {
                if ("Huaxi" in lines[8] && StateManager.state != StateManager.States.PLAYING) {
                    ChatUtils.info("Huaxi server detected - dodging!")
                    player.sendChatMessage("/play duels_sumo_duel")
                    return true  // Return true to indicate Huaxi was detected
                }
            } else {
                ChatUtils.info("Only ${lines.size} scoreboard lines available.")
            }
        }
        return false  // Return false if Huaxi was not detected
    }


    /**
     * Starts continuous logging of opponent's rotation angles.
     *
     * Used for debugging network sync precision by logging yaw and pitch changes.
     */
    private fun startOpponentRotationLogging() {
        stopOpponentRotationLogging() // Stop any existing timer

        ChatUtils.info("Starting opponent rotation logging...")
        rotationLoggingTimer = Timer()
        rotationLoggingTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                try {
                    logOpponentRotation()
                } catch (_: Exception) {
                    // Ignore errors to prevent timer from stopping
                }
            }
        }, 0, 100) // Log every 100ms
    }

    /**
     * Stop opponent rotation logging
     */
    private fun stopOpponentRotationLogging() {
        rotationLoggingTimer?.cancel()
        rotationLoggingTimer = null
    }

    /**
     * Check if opponent has been frozen (not moving) for 3 seconds
     */
    private fun checkOpponentFreeze() {
        if (CatDueller.config?.leaveWhenOpponentFreeze != true) return
        if (StateManager.state != StateManager.States.PLAYING) return
        if (alreadySentLeaveCommand) return

        val opponent = opponent() ?: return
        val currentTime = System.currentTimeMillis()
        val currentPosition = Triple(opponent.posX, opponent.posY, opponent.posZ)

        // Initialize tracking on first check
        if (lastOpponentPosition == null) {
            lastOpponentPosition = currentPosition
            opponentLastMoveTime = currentTime
            opponentFreezeCheckActive = true
            return
        }

        // Check if opponent has moved (with 0.1 block tolerance)
        val lastPos = lastOpponentPosition!!
        val moved = kotlin.math.abs(currentPosition.first - lastPos.first) > 0.1 ||
                kotlin.math.abs(currentPosition.second - lastPos.second) > 0.1 ||
                kotlin.math.abs(currentPosition.third - lastPos.third) > 0.1

        if (moved) {
            // Opponent moved, reset timer
            lastOpponentPosition = currentPosition
            opponentLastMoveTime = currentTime
            if (CatDueller.config?.showRotationDebug == true) {
                ChatUtils.info("Opponent moved, resetting freeze timer")
            }
        } else {
            // Opponent hasn't moved, check if 3 seconds have passed
            val timeSinceLastMove = currentTime - opponentLastMoveTime

            if (timeSinceLastMove >= 3000) { // 3 seconds
                ChatUtils.info("Opponent has been frozen for ${timeSinceLastMove}ms - sending /l command")

                // Send /l command
                try {
                    ChatUtils.sendAsPlayer("/l")
                    alreadySentLeaveCommand = true
                } catch (e: Exception) {
                    ChatUtils.info("Failed to send /l command: ${e.message}")
                }
            } else if (CatDueller.config?.showRotationDebug == true && timeSinceLastMove % 1000 < 100) {
                // Debug info every second (with 100ms tolerance to avoid spam)
                ChatUtils.info("Opponent frozen for ${timeSinceLastMove}ms / 3000ms")
            }
        }
    }

    /**
     * Start opponent freeze detection
     */
    private fun startOpponentFreezeDetection() {
        if (CatDueller.config?.leaveWhenOpponentFreeze != true) return

        stopOpponentFreezeDetection() // Stop any existing timer

        ChatUtils.info("Starting opponent freeze detection...")
        freezeCheckTimer = Timer()
        freezeCheckTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                try {
                    checkOpponentFreeze()
                } catch (_: Exception) {
                    // Ignore errors to prevent timer from stopping
                }
            }
        }, 0, 100) // Check every 100ms
    }

    /**
     * Stop opponent freeze detection
     */
    private fun stopOpponentFreezeDetection() {
        freezeCheckTimer?.cancel()
        freezeCheckTimer = null
    }

    /**
     * Reset opponent freeze detection
     */
    private fun resetOpponentFreezeDetection() {
        stopOpponentFreezeDetection()
        lastOpponentPosition = null
        opponentLastMoveTime = 0L
        opponentFreezeCheckActive = false
        alreadySentLeaveCommand = false
    }

    /**
     * Log opponent's rotation angles for debugging network sync precision
     */
    private fun logOpponentRotation() {
        val opponent = opponent() ?: return
        val currentYaw = opponent.rotationYaw
        val currentPitch = opponent.rotationPitch

        // Check if rotation has changed since last log
        val yawChanged = kotlin.math.abs(currentYaw - lastLoggedOpponentYaw) > 0.001f
        val pitchChanged = kotlin.math.abs(currentPitch - lastLoggedOpponentPitch) > 0.001f

        if (yawChanged || pitchChanged || lastOpponentRotationLogTime == 0L) {
            val yawDiff = if (lastOpponentRotationLogTime > 0L) currentYaw - lastLoggedOpponentYaw else 0f
            val pitchDiff = if (lastOpponentRotationLogTime > 0L) currentPitch - lastLoggedOpponentPitch else 0f

            ChatUtils.info(
                "Opponent Rotation: yaw=${String.format("%.6f", currentYaw)}, pitch=${
                    String.format(
                        "%.6f",
                        currentPitch
                    )
                }"
            )
            if (lastOpponentRotationLogTime > 0L) {
                ChatUtils.info(
                    "  Change: yaw=${String.format("%+.6f", yawDiff)}, pitch=${
                        String.format(
                            "%+.6f",
                            pitchDiff
                        )
                    }"
                )
                val yawStep = kotlin.math.abs(yawDiff)
                val pitchStep = kotlin.math.abs(pitchDiff)
                if (yawStep > 0) {
                    ChatUtils.info("  Yaw precision step: ${String.format("%.6f", yawStep)} degrees")
                }
                if (pitchStep > 0) {
                    ChatUtils.info("  Pitch precision step: ${String.format("%.6f", pitchStep)} degrees")
                }
            }

            lastLoggedOpponentYaw = currentYaw
            lastLoggedOpponentPitch = currentPitch
            lastOpponentRotationLogTime = System.currentTimeMillis()
        }
    }


    /**
     * Clear all movement and combat actions when opponent is off edge
     */
    private fun clearMovementAndCombat() {
        Movement.clearAll()
        Mouse.stopLeftAC()
        Mouse.stopHoldLeftClick()
        Combat.stopRandomStrafe()
        Mouse.stopTracking()
    }

    /**
     * PostMotion equivalent - handle jump key release
     * This mimics the Java PostMotionEvent logic
     */
    @SubscribeEvent
    fun onSumoClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        if (!toggled()) return

        val player = mc.thePlayer ?: return

        // PostMotion logic: Release jump key when player lands or starts falling
        // Equivalent to: if (setJump && !Utils.jumpDown())
        // Release when: player is on ground OR player is falling (motionY > 0)
        if (setJump) {
            val shouldRelease = player.onGround || player.motionY > 0.0

            if (shouldRelease) {
                setJump = false
                net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.keyCode, false)

                if (CatDueller.config?.combatLogs == true) {
                    val reason = if (player.onGround) "landed" else "falling"
                    ChatUtils.combatInfo("Jump Velocity (PostMotion): released jump key ($reason)")
                }
            }
        }
    }

}
