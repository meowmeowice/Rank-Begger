package best.spaghetcodes.catdueller.bot

import best.spaghetcodes.catdueller.CatDueller
import best.spaghetcodes.catdueller.bot.player.*
import best.spaghetcodes.catdueller.bot.state.Session
import best.spaghetcodes.catdueller.bot.state.StateManager
import best.spaghetcodes.catdueller.core.HWIDLock
import best.spaghetcodes.catdueller.core.KeyBindings
import best.spaghetcodes.catdueller.irc.IRCDodgeClient
import best.spaghetcodes.catdueller.utils.client.ChatUtil
import best.spaghetcodes.catdueller.utils.client.TimerUtil
import best.spaghetcodes.catdueller.utils.game.EntityUtil
import best.spaghetcodes.catdueller.utils.system.RandomUtil
import best.spaghetcodes.catdueller.utils.system.UUIDUtil
import best.spaghetcodes.catdueller.utils.system.WebhookUtil
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiMainMenu
import net.minecraft.client.gui.GuiMultiplayer
import net.minecraft.client.multiplayer.GuiConnecting
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.network.Packet
import net.minecraft.network.play.server.S12PacketEntityVelocity
import net.minecraft.network.play.server.S19PacketEntityStatus
import net.minecraft.network.play.server.S40PacketDisconnect
import net.minecraft.network.play.server.S45PacketTitle
import net.minecraft.scoreboard.ScorePlayerTeam
import net.minecraft.util.EnumChatFormatting
import net.minecraft.util.StringUtils
import net.minecraft.util.Vec3
import net.minecraftforge.client.event.ClientChatReceivedEvent
import net.minecraftforge.event.entity.EntityJoinWorldEvent
import net.minecraftforge.event.entity.player.AttackEntityEvent
import net.minecraftforge.fml.client.FMLClientHandler
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientConnectedToServerEvent
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent
import java.awt.Robot
import java.awt.event.KeyEvent
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Base class for all dueling bots providing core functionality for automated PvP.
 *
 * This class handles game state management, opponent tracking, combat events,
 * session statistics, reconnection logic, and various automation features.
 * Subclasses should override the protected methods to implement specific
 * bot behaviors for different game modes.
 *
 * @property queueCommand The command used to join a new game queue (e.g., "/play sumo_duel")
 * @property quickRefresh Duration in milliseconds for the initial quick refresh period when finding opponents
 */
open class BotBase(val queueCommand: String, val quickRefresh: Int = 10000) {

    /** Minecraft client instance for accessing game state */
    protected val mc: Minecraft = Minecraft.getMinecraft()

    /** Whether the bot is currently active */
    private var toggled = false

    /**
     * Returns whether the bot is currently toggled on.
     * @return true if the bot is active, false otherwise
     */
    fun toggled() = toggled

    /**
     * Toggles the bot on or off and handles all associated state changes.
     *
     * When toggling off, this method stops all movements, cancels timers,
     * clears tracking data, and resets combat variables.
     * When toggling on, it resets hit select variables, sets session timing,
     * generates randomized timings, and may start bot crasher mode.
     *
     * @param isManualToggle True if triggered by user action, false if automatic (e.g., after break)
     */
    fun toggle(isManualToggle: Boolean = true) {
        // Check HWID authorization before allowing toggle
        if (!HWIDLock.isAuthorized()) {
            ChatUtil.error("HWID verification failed - bot cannot be enabled")
            ChatUtil.error("Your HWID: ${HWIDLock.getCurrentHWID()}")
            return
        }

        val wasToggled = toggled
        toggled = !toggled

        // If bot is disabled, stop all actions and cancel timers
        if (wasToggled) {
            // Stop all mouse actions
            Mouse.resetAllStates()

            // Stop all movements
            Movement.clearAll()
            LobbyMovement.stop()
            Combat.stopRandomStrafe()

            TimerUtil.cancelAllTimers()

            // Cancel Bot Crasher Mode timers
            botCrasherTimer?.cancel()
            botCrasherTimer = null
            spamTimer?.cancel()
            spamTimer = null
            disconnectedPlayers.clear()

            // Cancel IRC dodge queue repeat timer
            queueRepeatTimer?.cancel()
            queueRepeatTimer = null

            // Cancel reconnect timer
            reconnectTimer?.cancel()
            reconnectTimer = null

            // Cancel ping monitoring timer
            pingCheckTimer?.cancel()
            pingCheckTimer = null
            internetStabilityPaused = false
            lastStablePingTime = 0L

            // Clear rod retract timeout
            rodRetractTimeout?.cancel()
            rodRetractTimeout = null

            // Clear tracking data to prevent memory leaks
            distanceHistory.clear()
            lastOpponentPos = null
            lastOurPos = null

            // Clear session blacklist if manual toggle (not during big break)
            if (isManualToggle) {
                sessionBlacklist.clear()
                playersSent.clear()
            }

            // No need to stop big break monitoring since we don't have continuous monitoring

            // Don't clear session blacklist during big break - only clear on manual toggle off
            // Session blacklist will persist through big breaks to maintain consistency

            // Reset force requeue prevention when bot is disabled
            preventForceRequeue = false

            // Reset requeue-related states
            forceRequeueScheduled = false
            forceRequeueAttempts = 0
            gameEndTime = 0L
        } else {
            // Bot is being enabled

            // Reset Hit Select variables when bot is enabled
            hitSelectAttackTime = -1L
            currentShouldAttack = false
            isKbReductionAttack = false
            lastHitByOpponentTime = -1L
            waitingForHitLaterDelay = false

            // Reset wait for first hit variables
            waitingForFirstHit = false
            crosshairOnOpponentTime = -1L
            hasBeenHitOnce = false

            // Always set bot start time when bot is toggled on
            botStartTime = System.currentTimeMillis()

            // Only set session start time for manual toggle or if not set
            if (isManualToggle || Session.startTime == -1L) {
                Session.startTime = System.currentTimeMillis()
                ChatUtil.info("Session start time ${if (isManualToggle) "updated" else "initialized"}")
            } else {
                ChatUtil.info("Session start time preserved (automatic toggle)")
            }

            // Generate randomized timings with variance
            generateRandomizedTimings()

            // Don't reset session stats (wins/losses) when toggling - only update start time

            // Allow manual bot start even during big break time
            // Big break will be checked after each game ends

            // Bot Crasher Mode setup
            if (CatDueller.config?.botCrasherMode == true && CatDueller.config?.botCrasherSpamPlayers == true) {
                val manualPlayers = CatDueller.config?.botCrasherTargetPlayers?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() } ?: emptyList()

                if (manualPlayers.isNotEmpty()) {
                    ChatUtil.info("Bot Crasher Mode: Found ${manualPlayers.size} manual target players, starting spam timer")
                    startSpamming()
                }
            }

            // Setup IRC dodge callback
            if (CatDueller.config?.ircDodgeEnabled == true) {
                IRCDodgeClient.onQueueAlert = { username, _, serverId, _ ->
                    checkIRCDodge(serverId, username)
                }
            }
        }
    }

    // ================== Combat State Variables ==================

    /** Entity ID of the last entity we attacked (for damage confirmation) */
    private var attackedID = -1

    /** Mapping of stat keys for API lookups */
    private var statKeys: Map<String, String> = mapOf("wins" to "", "losses" to "", "ws" to "")

    /** Current winstreak tracked locally during session */
    private var currentWinstreak = 0

    /** Player's nickname extracted from join messages (for nicked players) */
    private var playerNick: String? = null

    /** List of players already sent stats for (prevents duplicate stat lookups) */
    private var playersSent: ArrayList<String> = arrayListOf()

    /** Reference to the current opponent entity */
    private var opponent: EntityPlayer? = null

    /** Timer for periodically refreshing opponent entity reference */
    private var opponentTimer: Timer? = null

    /** Flag to prevent multiple onFoundOpponent calls per game */
    private var calledFoundOpponent = false

    /** Current combo count (consecutive hits on opponent) */
    protected var combo = 0

    /** Opponent's current combo count (consecutive hits on us) */
    protected var opponentCombo = 0

    /** Ticks since last successful hit on opponent */
    protected var ticksSinceHit = 0

    /** Ticks since game started (for requeue timeout) */
    private var ticksSinceGameStart = 0

    /** Last known opponent display name */
    private var lastOpponentName = ""

    /** Opponent name with rank prefix from chat message */
    private var lastOpponentNameWithRank = ""

    /** Whether the current opponent is using a nickname */
    private var isOpponentNicked = false

    /** Flag to prevent multiple onGameEnd calls */
    private var calledGameEnd = false

    /** Flag to prevent multiple onJoinGame calls */
    private var calledJoinGame = false

    /** Whether the last game resulted in a loss (for delayed requeue) */
    private var lastGameWasLoss = false

    // ================== Blink Tap Variables ==================

    /** Last recorded distance to opponent for blink tap trigger detection */
    private var lastDistanceToOpponent = 999f

    /** Prevents multiple blink tap triggers per engagement */
    private var blinkTapTriggered = false

    // ================== Force Requeue Variables ==================

    /** Timestamp when the game ended (for force requeue timing) */
    private var gameEndTime = 0L

    /** Whether a force requeue has been scheduled */
    private var forceRequeueScheduled = false

    /** Number of force requeue attempts made */
    private var forceRequeueAttempts = 0

    /** Prevents force requeue when intentionally disconnecting */
    private var preventForceRequeue = false

    // ================== Reconnection Variables ==================

    /** Timer for reconnection attempts after unexpected disconnect */
    private var reconnectTimer: Timer? = null

    /** Timestamp when bot was started (for dynamic break timing) */
    private var botStartTime = 0L

    /** Actual disconnect time with variance applied (minutes) */
    private var actualDisconnectMinutes = 0

    /** Actual wait time before reconnect with variance applied (minutes) */
    private var actualReconnectWaitMinutes = 30

    /** Absolute timestamp for scheduled reconnect (0 = not scheduled) */
    private var scheduledReconnectTime = 0L

    /** Whether the scheduled reconnect is for a dynamic break */
    private var isDynamicBreakReconnect = false

    // ================== Lobby Sit Mode Variables ==================

    /** Whether lobby sit mode is currently active */
    private var lobbySitActive = false

    /** Timestamp when lobby sit mode should end */
    private var lobbySitEndTime = 0L

    /** Current phase of lobby sit (0 = forward, 1 = backward) */
    private var lobbySitPhase = 0

    /** Timestamp when current lobby sit phase started */
    private var lobbySitPhaseStartTime = 0L

    /** Timer for jump spam during lobby sit mode */
    private var lobbySitJumpTimer: Timer? = null

    // ================== Internet Stability Variables ==================

    /** Whether requeuing is paused due to high ping */
    private var internetStabilityPaused = false

    /** Timestamp of last stable ping reading */
    private var lastStablePingTime = 0L

    /** Timer for continuous ping monitoring */
    private var pingCheckTimer: Timer? = null

    /** Last disconnect reason for webhook reporting */
    private var lastDisconnectReason = "Unknown"

    /**
     * Sets the disconnect reason for webhook reporting.
     * @param reason The reason for disconnection
     */
    private fun setDisconnectReason(reason: String) {
        lastDisconnectReason = reason
        ChatUtil.info("Disconnect reason set: $reason")
    }

    // ================== Scoreboard Tracking Variables ==================

    /** Cached opponent name from scoreboard */
    private var cachedOpponentName: String? = null

    /** Timestamp of last scoreboard check */
    private var lastScoreboardCheck = 0L

    /** Whether winstreak has been checked this game */
    private var winstreakChecked = false

    /** Current server ID extracted from scoreboard */
    private var currentServer: String? = null

    /** Whether blatant mode was toggled for current opponent */
    protected var blatantToggled = false

    /** Session-only blacklist for auto-added players (cleared on manual toggle off) */
    private var sessionBlacklist = mutableSetOf<String>()

    /** Timestamp when beforeStart() was called */
    private var beforeStartTime = 0L

    // ================== Bot Crasher Mode Variables ==================

    /** Timestamp when current game started */
    private var gameStartTime = 0L

    /** Timer for bot crasher mode timeout */
    private var botCrasherTimer: Timer? = null

    /** List of disconnected players to spam with party invites */
    private var disconnectedPlayers = mutableListOf<String>()

    /** Timer for spamming disconnected players */
    private var spamTimer: Timer? = null

    /** Timer for repeatedly sending queue command during IRC dodge */
    private var queueRepeatTimer: Timer? = null

    // ================== Big Break Variables ==================

    /** Absolute timestamp when big break ends (0 = not in big break) */
    private var bigBreakReconnectTime = 0L

    // ================== Hit Select Variables ==================

    /** Last time combat occurred (attack or being attacked) */
    private var lastCombatTime = 0L

    // ================== W-Tap Variables ==================

    /** Whether W-tap is currently in progress */
    private var tapping = false

    /** Timestamp of last W-tap for cooldown */
    private var lastWTapTime = 0L

    // ================== Rod Usage Variables ==================

    /** Timer for rod retract timeout */
    var rodRetractTimeout: Timer? = null

    /** Whether rod was used defensively (due to opponent combo) */
    var isDefensiveRod: Boolean = false

    // ================== Bow Tracking Variables ==================

    /** Whether opponent has used bow this game */
    var opponentUsedBow: Boolean = false

    /** Number of arrows fired by opponent */
    var opponentArrowsFired: Int = 0

    /** Whether opponent is currently drawing bow */
    var opponentIsDrawingBow: Boolean = false

    /** Timestamp of last opponent bow check */
    private var lastOpponentBowCheck: Long = 0

    // ================== Movement Tracking Variables ==================

    /** Whether opponent is moving toward us */
    var opponentIsApproaching: Boolean = false

    /** Whether opponent is moving away from us */
    var opponentIsRetreating: Boolean = false

    /** Last recorded distance to opponent */
    private var lastDistance: Float = 0f

    /** Timestamp of last distance check */
    private var lastDistanceCheck: Long = 0

    /** History of distance measurements for trend analysis */
    private val distanceHistory = mutableListOf<Float>()

    // ================== Damage Statistics Variables ==================

    /** Total damage dealt to opponent this game */
    private var damageDealtToOpponent: Double = 0.0

    /** Total damage received from opponent this game */
    private var damageReceivedFromOpponent: Double = 0.0

    // ================== Opponent Speed Tracking Variables ==================

    /** Opponent's actual movement speed in blocks per tick */
    var opponentActualSpeed = 0.13f

    /** Last recorded position for opponent speed calculation */
    private var lastOpponentSpeedPos: Vec3? = null

    // ================== Strafe Tracking Variables ==================

    /** Opponent's strafe direction (-1=left, 0=none, 1=right) */
    var opponentStrafeDirection: Int = 0

    /** Our strafe direction (-1=left, 0=none, 1=right) */
    var ourStrafeDirection: Int = 0

    /** Whether both players are strafing in the same relative direction */
    var isCounterStrafing: Boolean = false

    /** Last recorded opponent position for strafe calculation */
    private var lastOpponentPos: Vec3? = null

    /** Last recorded player position for strafe calculation */
    private var lastOurPos: Vec3? = null

    /** Timestamp of last strafe check */
    private var lastStrafeCheck: Long = 0

    /**
     * Returns the current opponent entity.
     * @return The opponent player entity, or null if not found
     */
    fun opponent() = opponent

    /**
     * Calculates the counter strafe multiplier for projectile prediction.
     *
     * When both players are strafing in the same relative direction,
     * projectile prediction becomes more accurate and a bonus multiplier is applied.
     *
     * @return The multiplier to apply (1.0 = no bonus, configurable bonus when counter-strafing)
     */
    fun getCounterStrafeMultiplier(): Float {
        val multiplier = CatDueller.config?.counterStrafeBonus ?: 1.5f
        return if (isCounterStrafing) multiplier else 1.0f
    }

    // ================== Overridable Methods ==================

    /**
     * Returns the display name for this bot type.
     *
     * Subclasses should override this to return their specific name
     * (e.g., "Sumo", "Classic", "UHC").
     *
     * @return The bot's display name
     */
    open fun getName(): String {
        return "Base"
    }

    /**
     * Called when the bot successfully hits the opponent.
     *
     * This is triggered by the damage sound confirmation packet (S19PacketEntityStatus),
     * not by the client-side attack event, ensuring the hit was actually registered.
     * Subclasses can override to implement hit-specific behavior.
     */
    protected open fun onAttack() {
        lastCombatTime = System.currentTimeMillis()
    }

    /**
     * Called when the bot is hit by the opponent.
     *
     * This is triggered by the damage sound confirmation packet,
     * ensuring the damage was actually received. Updates combat timing
     * and hit tracking variables. Subclasses can override for custom behavior.
     */
    protected open fun onAttacked() {
        // Update combat time for hit select timeout logic
        lastCombatTime = System.currentTimeMillis()

        // Record time when hit by opponent for "Hit Later In Trades" feature
        lastHitByOpponentTime = System.currentTimeMillis()

        if (CatDueller.config?.combatLogs == true && (CatDueller.config?.hitLaterInTrades ?: 0) > 0) {
            ChatUtil.combatInfo("Hit Later In Trades: Recorded hit at $lastHitByOpponentTime")
        }

        // Mark that we've been hit for "Wait For First Hit" feature
        if (!hasBeenHitOnce) {
            hasBeenHitOnce = true
            if (waitingForFirstHit && CatDueller.config?.combatLogs == true) {
                ChatUtil.combatInfo("Wait For First Hit: Opponent attacked - can now attack back")
            }
            waitingForFirstHit = false
            crosshairOnOpponentTime = -1L
        }
    }

    /**
     * Called when the bot receives velocity (knockback) from the server.
     *
     * This is triggered by S12PacketEntityVelocity and provides the most accurate
     * timing for implementing jump reset mechanics. Subclasses can override to
     * implement velocity-based combat techniques.
     *
     * @param motionX The X component of velocity
     * @param motionY The Y component of velocity
     * @param motionZ The Z component of velocity
     */
    protected open fun onVelocity(motionX: Int, motionY: Int, motionZ: Int) {
        // Base implementation does nothing - subclasses can override for jump reset
    }

    /**
     * Determines whether the bot should start attacking based on current conditions.
     *
     * Checks distance, line of sight, and player state. Subclasses can override
     * to add mode-specific attack conditions.
     *
     * @param distance The current distance to the opponent
     * @return true if the bot should attack, false otherwise
     */
    open fun shouldStartAttacking(distance: Float): Boolean {
        val player = mc.thePlayer ?: return false
        val opponent = opponent() ?: return false

        // Basic distance check
        val maxAttackDistance = CatDueller.config?.maxDistanceAttack ?: 5
        if (distance > maxAttackDistance) {
            return false
        }

        // Crosshair aim check is now handled in canSwing() with missed hits cancel rate logic

        // Ensure opponent is visible
        if (!player.canEntityBeSeen(opponent)) {
            return false
        }

        // Ensure player can attack
        if (player.isUsingItem) {
            return false
        }

        val canAttackResult = canAttack()

        return canAttackResult
    }

    // ================== Hit Select State Variables ==================

    /** Timestamp of last attack for hit select timing */
    private var hitSelectAttackTime = -1L

    /** Whether the bot should attack based on hit select logic */
    private var currentShouldAttack = false

    /** Whether current attack is triggered by KB reduction logic */
    private var isKbReductionAttack = false

    /** Timestamp when we were last hit by opponent */
    private var lastHitByOpponentTime = -1L

    /** Whether we're waiting for hit later delay */
    private var waitingForHitLaterDelay = false

    // ================== Wait For First Hit Variables ==================

    /** Whether we're waiting for opponent's first hit before attacking */
    private var waitingForFirstHit = false

    /** Timestamp when crosshair first aimed at opponent */
    private var crosshairOnOpponentTime = -1L

    /** Whether we've been hit at least once this game */
    private var hasBeenHitOnce = false

    /**
     * Determines whether the bot can swing based on hit select and missed hits cancel rate.
     *
     * This method controls the swing animation timing based on:
     * - Hit select timing logic for optimal hit registration
     * - Missed hits cancel rate for crosshair misalignment
     * - Wait for first hit feature
     *
     * @return true if the bot should swing, false otherwise
     */
    open fun canSwing(): Boolean {
        if (!toggled()) return false

        val player = mc.thePlayer ?: return false
        val opponent = opponent() ?: return false
        val distance = EntityUtil.getDistanceNoY(player, opponent)
        val maxAttackDistance = CatDueller.config?.maxDistanceAttack ?: 5

        // Basic distance check - never attack beyond max distance
        if (distance > maxAttackDistance) {
            return false
        }

        // Check Wait For First Hit feature
        val waitForFirstHitEnabled = CatDueller.config?.waitForFirstHit ?: false
        if (waitForFirstHitEnabled && waitingForFirstHit) {
            if (CatDueller.config?.combatLogs == true) {
                ChatUtil.combatInfo("canSwing() blocked - waiting for first hit from opponent")
            }
            return false
        }

        val hitSelectEnabled = CatDueller.config?.hitSelect ?: false

        // If hit select is disabled, always allow swing within max distance
        if (!hitSelectEnabled) {
            return true
        }

        // Hit select is enabled - check crosshair aim
        val mouseOver = mc.objectMouseOver
        val crosshairAimed = (mouseOver != null && mouseOver.entityHit == opponent)

        if (crosshairAimed) {
            // Crosshair is aimed at target - use normal hit select timing logic
            return currentShouldAttack
        } else {
            // Crosshair is NOT aimed at target - this is the "missed hits" case
            // Apply missed hits cancel rate
            val missedHitsCancelRate = CatDueller.config?.missedHitsCancelRate ?: 0
            if (missedHitsCancelRate > 0) {
                val randomChance = (1..100).random()
                if (randomChance <= missedHitsCancelRate) {
                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtil.combatInfo("canSwing() blocked by missed hits cancel rate (${missedHitsCancelRate}%, rolled ${randomChance})")
                    }
                    return false
                }
            }

            // Missed hits cancel rate allows attack - swing without hit select timing restrictions
            if (CatDueller.config?.combatLogs == true) {
                ChatUtil.combatInfo("canSwing() allowed by missed hits cancel rate - crosshair not aimed")
            }
            return true
        }
    }

    /**
     * Determines whether the bot can attack.
     *
     * This method delegates to [canSwing] for consistency across attack checks.
     *
     * @return true if the bot can attack, false otherwise
     */
    open fun canAttack(): Boolean {
        return canSwing()
    }

    /**
     * Checks if it's time for a scheduled dynamic break reconnect.
     *
     * This method handles long-term reconnect scheduling for dynamic breaks,
     * monitoring the scheduled time and initiating reconnection when reached.
     */
    private fun checkScheduledReconnect() {
        // Only handle dynamic break reconnects (long-term), unexpected disconnects use timers
        if (scheduledReconnectTime > 0L && isDynamicBreakReconnect) {
            val currentTime = System.currentTimeMillis()
            val timeUntilReconnect = scheduledReconnectTime - currentTime

            // Debug info every 30 seconds for dynamic break
            if (timeUntilReconnect > 0 && timeUntilReconnect % 30000 < 50) {
                println("Waiting for dynamic break reconnect... ${timeUntilReconnect / 1000}s remaining")
            }

            if (currentTime >= scheduledReconnectTime) {
                scheduledReconnectTime = 0L  // Reset
                isDynamicBreakReconnect = false  // Reset

                try {
                    ChatUtil.info("Dynamic break reconnect time reached, attempting reconnect...")
                    println("Dynamic break reconnect triggered")
                    println("Current time: $currentTime")

                    // For dynamic break reconnects, we need to re-enable the bot
                    if (!toggled()) {
                        ChatUtil.info("Re-enabling bot for dynamic break reconnect...")
                        toggle(false)  // Automatic toggle - don't reset session start time
                    } else {
                        ChatUtil.info("Bot already enabled for dynamic break reconnect")
                    }

                    // Don't reset session for dynamic break - session persists

                    // Start persistent reconnect attempts for dynamic break
                    ChatUtil.info("Starting persistent reconnect attempts for dynamic break...")
                    reconnectTimer = TimerUtil.setInterval(this::reconnect, 0, 30000)

                } catch (e: Exception) {
                    ChatUtil.error("Error during scheduled reconnect: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Checks if the big break period should end and handles reconnection.
     *
     * Big breaks are scheduled time periods (e.g., 13:00-17:00) where the bot
     * disconnects and waits. This method monitors the end time and initiates
     * reconnection when the break period ends.
     */
    private fun checkBigBreakReconnect() {
        if (bigBreakReconnectTime > 0L) {
            val currentTime = System.currentTimeMillis()
            val timeUntilReconnect = bigBreakReconnectTime - currentTime

            // Debug info every 30 seconds
            if (timeUntilReconnect > 0 && timeUntilReconnect % 30000 < 50) {
                println("Waiting for big break to end... ${timeUntilReconnect / 1000}s remaining")
                ChatUtil.info("Big break ends in ${timeUntilReconnect / 1000}s")
            }

            if (currentTime >= bigBreakReconnectTime) {
                bigBreakReconnectTime = 0L  // Reset
                println("Big break reconnect time reached!")
                ChatUtil.info("Big break time reached - starting reconnect process")

                try {
                    // Send webhook notification for big break end
                    if (CatDueller.config?.sendWebhookMessages == true && !CatDueller.config?.webhookURL.isNullOrBlank()) {
                        val author = WebhookUtil.buildAuthor(
                            "Cat Dueller - ${getName()}",
                            "https://cdn.discordapp.com/icons/1359887726157238532/cbbde7905d56603d13d2a7a9e4d545be.png?size=1024"
                        )
                        val thumbnail =
                            WebhookUtil.buildThumbnail("https://cdn.discordapp.com/icons/1359887726157238532/cbbde7905d56603d13d2a7a9e4d545be.png?size=1024")

                        WebhookUtil.sendEmbed(
                            CatDueller.config?.webhookURL!!,
                            WebhookUtil.buildEmbed(
                                ":white_check_mark: Big Break Ended",
                                "Big break time has ended. Bot is now re-enabled and resuming operation.",
                                JsonArray(),
                                JsonObject(),
                                author,
                                thumbnail,
                                0x00ff00
                            )
                        )
                    }

                    // Big break ended, re-enable bot and reconnect (like dynamic break)
                    ChatUtil.info("Big break reconnect time reached, attempting reconnect...")
                    println("Big break reconnect triggered")

                    // For big break reconnects, we need to re-enable the bot
                    if (!toggled()) {
                        ChatUtil.info("Re-enabling bot for big break reconnect...")
                        toggle(false)  // Automatic toggle - don't reset session start time
                    } else {
                        ChatUtil.info("Bot already enabled for big break reconnect")
                    }

                    // Don't reset session for big break - session persists
                    ChatUtil.info("Bot re-enabled after big break - session preserved")

                    // Start persistent reconnect attempts for big break
                    ChatUtil.info("Starting persistent reconnect attempts for big break...")
                    reconnectTimer = TimerUtil.setInterval(this::reconnect, 0, 30000)

                } catch (e: Exception) {
                    ChatUtil.error("Error during big break reconnect: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Updates hit select timing logic every tick.
     *
     * Hit select implements a 500ms attack cycle with configurable delay windows
     * to optimize hit registration. This method determines when attacks should
     * be allowed based on:
     * - Time since last attack
     * - KB reduction opportunities (hurtTime > 6)
     * - Hit later in trades delay
     */
    private fun updateHitSelect() {
        if (!toggled()) return

        val hitSelectEnabled = CatDueller.config?.hitSelect ?: false
        if (!hitSelectEnabled) {
            currentShouldAttack = true
            return
        }

        val player = mc.thePlayer ?: return
        val currentTime = System.currentTimeMillis()

        // Reset KB reduction flag
        isKbReductionAttack = false

        // First attack is always allowed before entering the cycle
        if (hitSelectAttackTime == -1L) {
            currentShouldAttack = true
            if (CatDueller.config?.combatLogs == true) {
                ChatUtil.combatInfo("Hit Select: First attack allowed - no previous attack recorded")
            }
            return
        }

        // KB reduction: Allow attack when hurtTime > 6 and airborne for reduced knockback
        if (player.hurtTime > 6 && !player.onGround) {
            currentShouldAttack = true
            isKbReductionAttack = true
            if (CatDueller.config?.combatLogs == true) {
                ChatUtil.combatInfo("Hit Select (KB Reduction): hurtTime > 6 - allowing attack (won't record time)")
            }
            return
        }

        // Check 500ms cycle timing logic
        val hitSelectDelay = CatDueller.config?.hitSelectDelay ?: 350
        val timeSinceLastAttack = currentTime - hitSelectAttackTime
        val hitLaterDelay = CatDueller.config?.hitLaterInTrades ?: 0

        if (timeSinceLastAttack < hitSelectDelay) {
            // Within delay period - pause attacks
            currentShouldAttack = false
            waitingForHitLaterDelay = false

        } else if (timeSinceLastAttack < 500) {
            // Between delay and 500ms - check Hit Later In Trades logic

            if (hitLaterDelay > 0) {
                // Hit Later In Trades enabled
                val timeSinceHit =
                    if (lastHitByOpponentTime > 0) currentTime - lastHitByOpponentTime else Long.MAX_VALUE

                // Check if we were hit during this cycle (after hitSelectAttackTime)
                val wasHitDuringCycle = lastHitByOpponentTime > hitSelectAttackTime

                if (wasHitDuringCycle && timeSinceHit < hitLaterDelay) {
                    // We were hit during this cycle, wait for hit later delay
                    currentShouldAttack = false
                    waitingForHitLaterDelay = true
                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtil.combatInfo("Hit Later In Trades: Waiting ${hitLaterDelay - timeSinceHit}ms after being hit")
                    }
                } else if (wasHitDuringCycle && timeSinceHit >= hitLaterDelay) {
                    // Hit later delay has passed, allow attack
                    currentShouldAttack = true
                    waitingForHitLaterDelay = false
                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtil.combatInfo("Hit Later In Trades: Delay passed, allowing attack")
                    }
                } else {
                    // Not hit during this cycle, use normal logic
                    currentShouldAttack = true
                    waitingForHitLaterDelay = false

                }
            } else {
                // Hit Later In Trades disabled, use normal logic
                currentShouldAttack = true
                waitingForHitLaterDelay = false

            }
        } else {
            // After 500ms - start new cycle, allow attack and reset time
            currentShouldAttack = true
            hitSelectAttackTime = -1L
            waitingForHitLaterDelay = false
        }
    }

    /**
     * Updates the wait for first hit logic every tick.
     *
     * This feature prevents attacking until the opponent attacks first,
     * with a configurable timeout. Useful for defensive playstyles.
     */
    private fun updateWaitForFirstHit() {
        if (!toggled()) return

        val waitForFirstHitEnabled = CatDueller.config?.waitForFirstHit ?: false
        if (!waitForFirstHitEnabled) {
            waitingForFirstHit = false
            crosshairOnOpponentTime = -1L
            return
        }

        mc.thePlayer ?: return
        val opponent = opponent() ?: return

        // Check if we've been hit once - if so, disable waiting
        if (hasBeenHitOnce) {
            waitingForFirstHit = false
            crosshairOnOpponentTime = -1L
            return
        }

        // Check if we're in hit select cycle (hitSelectAttackTime != -1L means we're in a cycle)
        val hitSelectEnabled = CatDueller.config?.hitSelect ?: false
        val inHitSelectCycle = hitSelectEnabled && hitSelectAttackTime != -1L
        if (inHitSelectCycle) {
            // Don't wait for first hit when in hit select cycle
            waitingForFirstHit = false
            crosshairOnOpponentTime = -1L
            if (CatDueller.config?.combatLogs == true && waitingForFirstHit) {
                ChatUtil.combatInfo("Wait For First Hit: Disabled - in hit select cycle")
            }
            return
        }

        // Check if player is near edge (using nearEdge method from Sumo bot if available)
        val playerNearEdge = try {
            // Try to call nearEdge method if it exists (Sumo bot specific)
            this.javaClass.getMethod("nearEdge", Float::class.javaPrimitiveType).invoke(this, 3.5f) as Boolean
        } catch (_: Exception) {
            // If method doesn't exist, assume not near edge
            false
        }

        if (playerNearEdge) {
            // Don't wait for first hit when near edge
            waitingForFirstHit = false
            crosshairOnOpponentTime = -1L
            return
        }

        // Check if crosshair is on opponent
        val mouseOver = mc.objectMouseOver
        val crosshairAimed = (mouseOver != null && mouseOver.entityHit == opponent)
        val currentTime = System.currentTimeMillis()

        if (crosshairAimed) {
            // Crosshair is on opponent
            if (crosshairOnOpponentTime == -1L) {
                // First time crosshair is on opponent - start timer
                crosshairOnOpponentTime = currentTime
                waitingForFirstHit = true
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("Wait For First Hit: Started waiting - crosshair on opponent")
                }
            } else {
                // Check timeout
                val waitForFirstHitTimeout = CatDueller.config?.waitForFirstHitTimeout ?: 500
                val timeSinceCrosshairOn = currentTime - crosshairOnOpponentTime

                if (timeSinceCrosshairOn >= waitForFirstHitTimeout) {
                    // Timeout reached - stop waiting and allow attack
                    waitingForFirstHit = false
                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtil.combatInfo("Wait For First Hit: Timeout reached (${waitForFirstHitTimeout}ms) - allowing attack")
                    }
                }
            }
        } else {
            // Crosshair not on opponent - reset timer
            if (crosshairOnOpponentTime != -1L) {
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("Wait For First Hit: Crosshair lost - resetting timer")
                }
            }
            crosshairOnOpponentTime = -1L
            waitingForFirstHit = false
        }
    }

    /**
     * Called when the game starts (opponent information displayed).
     *
     * This method is called after the countdown ends and the game begins.
     * Resets game-specific variables and initializes tracking systems.
     * Subclasses can override to add mode-specific game start behavior.
     */
    protected open fun onGameStart() {
        winstreakChecked = false

        // Reset game variables
        resetGameVariables()

        // Debug: Verify tracking functions will be called
        if (CatDueller.config?.combatLogs == true) {
            ChatUtil.combatInfo("Game started - bow detection and movement tracking initialized")
        }

        // Bot Crasher Mode: Start timer to check if game doesn't end within 5 seconds
        if (CatDueller.config?.botCrasherMode == true && CatDueller.config?.botCrasherAutoRequeue == true) {
            gameStartTime = System.currentTimeMillis()
            botCrasherTimer?.cancel()
            botCrasherTimer = Timer()
            botCrasherTimer?.schedule(object : TimerTask() {
                override fun run() {
                    // If game hasn't ended after 5 seconds, send requeue command
                    if (StateManager.state == StateManager.States.PLAYING) {
                        ChatUtil.info("Bot Crasher Mode: Game didn't end after 5 seconds, sending requeue command")

                        // Reset all variables like in game end
                        onGameEnd()
                        resetVars()

                        // Clear movement and combat states
                        Movement.clearAll()
                        Mouse.stopLeftAC()
                        Mouse.stopHoldLeftClick()
                        Mouse.stopHoldRightClick()
                        Combat.stopRandomStrafe()
                        LobbyMovement.stop()

                        // Send requeue command
                        ChatUtil.sendAsPlayer(queueCommand)
                    }
                }
            }, 5000)
        }
    }

    /**
     * Resets all game-specific variables for a new game.
     *
     * This includes combat timing, hit tracking, bow usage,
     * movement tracking, speed tracking, and strafe tracking.
     */
    private fun resetGameVariables() {
        lastPlayerHurtTime = 0
        lastOpponentHurtTime = 0
        // Reset combat time for new game
        lastCombatTime = 0L
        hitSelectAttackTime = -1L  // Reset hit select attack time
        lastHitByOpponentTime = -1L
        waitingForHitLaterDelay = false

        // Reset wait for first hit variables
        waitingForFirstHit = false
        crosshairOnOpponentTime = -1L
        hasBeenHitOnce = false

        // Reset W-Tap variables
        tapping = false
        lastWTapTime = 0L

        // Reset bow usage tracking
        opponentUsedBow = false
        opponentArrowsFired = 0
        opponentIsDrawingBow = false

        // Reset movement tracking
        opponentIsApproaching = false
        opponentIsRetreating = false
        lastDistance = 0f
        distanceHistory.clear()

        // Reset speed tracking
        opponentActualSpeed = 0.13f
        lastOpponentSpeedPos = null

        // Reset strafe tracking
        opponentStrafeDirection = 0
        ourStrafeDirection = 0
        isCounterStrafing = false
        lastOpponentPos = null
        lastOurPos = null
    }

    /**
     * Called when the game ends (game result displayed).
     *
     * Records game end time, cancels bot crasher timers, and schedules
     * force requeue if enabled. Subclasses can override to add
     * mode-specific game end behavior.
     */
    protected open fun onGameEnd() {
        gameEndTime = System.currentTimeMillis()
        forceRequeueScheduled = false
        forceRequeueAttempts = 0  // Reset attempt counter

        // Send IRC leave notification if enabled
        if (CatDueller.config?.ircDodgeEnabled == true) {
            IRCDodgeClient.sendLeaveInfo()
        }

        // Bot Crasher Mode: Cancel timer since game ended normally
        if (CatDueller.config?.botCrasherMode == true && CatDueller.config?.botCrasherAutoRequeue == true) {
            botCrasherTimer?.cancel()
            botCrasherTimer = null
        }

        // Turn off blatant mode if it was toggled for this game
        if (blatantToggled && CatDueller.config?.toggleBlatantOnBlacklisted == true) {
            val keyName = CatDueller.config?.blatantToggleKey ?: "F1"
            ChatUtil.info("Game ended - turning off blatant mode")

            TimerUtil.setTimeout({
                simulateKeyPress(keyName)
                blatantToggled = false
            }, RandomUtil.randomIntInRange(200, 500))
        }


        // Schedule force requeue if enabled - delay 300ms to wait for WINNER message processing
        if (CatDueller.config?.forceRequeue == true) {
            TimerUtil.setTimeout({
                // Calculate force requeue delay using same logic as normal requeue
                var baseDelay = if (CatDueller.config?.fastRequeue == true) {
                    400  // Average of 300-500 range for fast requeue
                } else {
                    CatDueller.config?.autoRqDelay ?: 2000
                }

                // Add extra delay if we lost and the feature is enabled (same as normal requeue)
                if (lastGameWasLoss && CatDueller.config?.delayRequeueAfterLosing == true) {
                    val extraDelay = (CatDueller.config?.losingRequeueDelay ?: 5) * 1000
                    baseDelay += extraDelay
                }

                val forceRequeueDelay = baseDelay + 1500 // Add 1.5 second buffer

                // Schedule force requeue after calculated delay + 1.5 second buffer
                TimerUtil.setTimeout(fun() {
                    checkForceRequeue()
                }, forceRequeueDelay)
            }, 300) // Wait 300ms for WINNER message processing to complete
        }
    }

    /**
     * Called when the bot joins a game queue.
     *
     * This is triggered when a player join message is detected.
     * Cancels any pending force requeue and resets game variables.
     * Subclasses can override to add mode-specific join behavior.
     */
    protected open fun onJoinGame() {
        // Stop IRC dodge queue repeat timer when joining game
        queueRepeatTimer?.cancel()
        queueRepeatTimer = null
        if (CatDueller.config?.combatLogs == true) {
            ChatUtil.combatInfo("IRC Dodge: Stopped queue command repeat (onJoinGame triggered)")
        }
        
        if (gameEndTime > 0L) {
            System.currentTimeMillis() - gameEndTime

            forceRequeueScheduled = false
            forceRequeueAttempts = 0  // Reset attempt counter
            gameEndTime = 0L  // Reset to prevent duplicate processing
        }

        // Reset Hit Select variables for new game
        hitSelectAttackTime = -1L
        currentShouldAttack = false
        isKbReductionAttack = false
        lastHitByOpponentTime = -1L
        waitingForHitLaterDelay = false

        // Reset wait for first hit variables
        waitingForFirstHit = false
        crosshairOnOpponentTime = -1L
        hasBeenHitOnce = false

        // Reset game variables
        resetGameVariables()

        // Immediately update server info from scoreboard when joining game
        updateCurrentServerFromScoreboard()

        // Notify MovementRecorder about joining game
        MovementRecorder.onJoinGame()
    }

    /**
     * Called when the game is about to start (4 seconds remaining).
     *
     * Sends server information to guild chat or DM if configured.
     * Subclasses can override to add pre-game preparation logic.
     */
    protected open fun onGameAlmostStart() {
        // Send server to guild if enabled
        if (CatDueller.config?.sendServerToGuild == true && currentServer != null) {
            TimerUtil.setTimeout({
                val randomSpam = generateRandomKeyboardSpam()
                val guildMessage = "/gc $currentServer $randomSpam"
                ChatUtil.sendAsPlayer(guildMessage)
                ChatUtil.info("Sent guild message: $guildMessage")
            }, RandomUtil.randomIntInRange(100, 300))
        }

        // Send server to DM if enabled
        if (CatDueller.config?.sendServerToDM == true && currentServer != null && !CatDueller.config?.dmTargetPlayer.isNullOrBlank()) {
            TimerUtil.setTimeout({
                val randomSpam = generateRandomKeyboardSpam()
                val dmMessage = "/w ${CatDueller.config?.dmTargetPlayer} $currentServer $randomSpam"
                ChatUtil.sendAsPlayer(dmMessage)
                ChatUtil.info("Sent DM message: $dmMessage")
            }, RandomUtil.randomIntInRange(100, 300))
        }

        // Send IRC queue notification if enabled
        if (CatDueller.config?.ircDodgeEnabled == true && currentServer != null) {
            val gamemode = getGamemodeName()
            val map = detectCurrentMap()
            IRCDodgeClient.sendQueueInfo(gamemode, currentServer!!, map)
        }
    }

    /**
     * Called just before the game starts (1 second remaining).
     *
     * Resets hit select and wait for first hit variables.
     * Notifies MovementRecorder to stop any playback.
     * Subclasses should override to implement mode-specific start behavior.
     */
    protected open fun beforeStart() {
        hitSelectAttackTime = -1L
        currentShouldAttack = false
        isKbReductionAttack = false
        lastHitByOpponentTime = -1L
        waitingForHitLaterDelay = false

        // Reset wait for first hit variables
        waitingForFirstHit = false
        crosshairOnOpponentTime = -1L
        hasBeenHitOnce = false

        // Notify MovementRecorder about game starting
        MovementRecorder.onBeforeStart()

    }

    /**
     * Checks if force requeue should be executed.
     *
     * Force requeue sends the queue command if normal requeue failed
     * after the expected delay period plus a buffer.
     */
    private fun checkForceRequeue() {
        if (CatDueller.config?.forceRequeue != true) return
        if (preventForceRequeue) {
            ChatUtil.info("Force requeue prevented - preparing to disconnect")
            return
        }

        System.currentTimeMillis() - gameEndTime

        // Calculate expected delay for comparison (same logic as force requeue scheduling)
        var baseDelay = if (CatDueller.config?.fastRequeue == true) {
            400  // Average of 300-500 range for fast requeue
        } else {
            CatDueller.config?.autoRqDelay ?: 2000
        }

        // Add extra delay if we lost and the feature is enabled (same as normal requeue)
        if (lastGameWasLoss && CatDueller.config?.delayRequeueAfterLosing == true) {
            val extraDelay = (CatDueller.config?.losingRequeueDelay ?: 5) * 1000
            baseDelay += extraDelay
        }

        baseDelay + 1000

        // Only force requeue if we haven't already scheduled it and gameEndTime is still set
        if (!forceRequeueScheduled && gameEndTime > 0L) {
            forceRequeueScheduled = true
            forceRequeueAttempts++



            TimerUtil.setTimeout(fun() {
                executeForceRequeue()
            }, RandomUtil.randomIntInRange(100, 300))
        }
    }

    /**
     * Executes the force requeue command.
     *
     * Sends the queue command if not currently in a game.
     */
    private fun executeForceRequeue() {
        if (CatDueller.config?.forceRequeue != true) return
        if (preventForceRequeue) {
            ChatUtil.info("Force requeue execution prevented - preparing to disconnect")
            return
        }

        if (StateManager.state != StateManager.States.PLAYING) {
            forceRequeue()
        }

    }


    /**
     * Called when the opponent entity is successfully located.
     *
     * Cancels force requeue and checks if the opponent is blacklisted
     * for blatant mode toggling. Subclasses can override for additional
     * opponent-specific initialization.
     */
    protected open fun onFoundOpponent() {
        if (gameEndTime > 0L) {
            System.currentTimeMillis() - gameEndTime
        }
        forceRequeueScheduled = false
        forceRequeueAttempts = 0  // Reset attempt counter
        gameEndTime = 0L

        // Check if opponent is blacklisted and toggle blatant mode if enabled
        val opponentName = opponent()?.displayNameString
        if (opponentName != null && CatDueller.config?.toggleBlatantOnBlacklisted == true) {
            if (isPlayerBlacklisted(opponentName) && !blatantToggled) {
                val keyName = CatDueller.config?.blatantToggleKey ?: "F1"
                ChatUtil.info("Blacklisted player detected: $opponentName - toggling blatant mode")

                simulateKeyPress(keyName)
                blatantToggled = true
            }
        }
    }

    /**
     * Determines whether opponent speed should be tracked.
     *
     * Subclasses can override to disable speed tracking for performance
     * optimization when projectile prediction is not needed.
     *
     * @return true if opponent speed should be tracked (default), false otherwise
     */
    protected open fun shouldTrackOpponentSpeed(): Boolean {
        return true
    }

    /**
     * Called every game tick while the bot is active.
     *
     * Updates winstreak from scoreboard, tracks opponent bow usage,
     * movement direction, strafe patterns, and blink tap logic.
     * Subclasses can override to add mode-specific tick behavior.
     */
    protected open fun onTick() {
        if (!toggled()) return

        // Check winstreak from scoreboard once per game when in PLAYING state
        if (StateManager.state == StateManager.States.PLAYING && !winstreakChecked) {
            checkWinstreakFromScoreboard()
            winstreakChecked = true
        }

        // Track opponent's bow usage and arrows
        trackOpponentBowUsage()

        // Track opponent movement direction
        trackOpponentMovement()

        // Track strafe directions
        trackStrafeMovement()

        // Blink Tap logic
        updateBlinkTap()

        // Periodic memory cleanup (every 5 minutes)
        performPeriodicCleanup()
    }

    /** Timestamp of last memory cleanup */
    private var lastCleanupTime = 0L

    /**
     * Performs periodic memory cleanup to prevent leaks.
     *
     * Runs every 5 minutes to trim collection sizes and
     * suggest garbage collection.
     */
    private fun performPeriodicCleanup() {
        val currentTime = System.currentTimeMillis()

        // Cleanup every 5 minutes (300,000ms)
        if (currentTime - lastCleanupTime > 300000) {
            lastCleanupTime = currentTime

            // Limit collection sizes
            if (disconnectedPlayers.size > 50) {
                val toRemove = disconnectedPlayers.size - 50
                repeat(toRemove) {
                    disconnectedPlayers.removeAt(0)
                }
                ChatUtil.info("Memory cleanup: Trimmed disconnectedPlayers to 50 entries")
            }

            if (sessionBlacklist.size > 100) {
                val toRemove = sessionBlacklist.size - 100
                val iterator = sessionBlacklist.iterator()
                repeat(toRemove) {
                    if (iterator.hasNext()) {
                        iterator.next()
                        iterator.remove()
                    }
                }
                ChatUtil.info("Memory cleanup: Trimmed sessionBlacklist to 100 entries")
            }

            // Force garbage collection hint (not guaranteed but may help)
            System.gc()
        }
    }


    /**
     * Tracks the opponent's bow usage and arrow count.
     *
     * Monitors whether the opponent is holding and drawing a bow,
     * and counts arrows fired based on draw state transitions.
     */
    private fun trackOpponentBowUsage() {
        if (opponent() == null || mc.theWorld == null) return

        val currentTime = System.currentTimeMillis()

        // Check if opponent is drawing bow (every 100ms to avoid spam)
        if (currentTime - lastOpponentBowCheck > 100) {
            // Debug: Show that tracking is running (every 5 seconds to avoid spam)
            if (CatDueller.config?.combatLogs == true && currentTime % 5000 < 100) {
                ChatUtil.combatInfo("Bow tracking active - checking opponent...")
            }
            val wasDrawing = opponentIsDrawingBow
            val hasBow =
                opponent()!!.heldItem != null && opponent()!!.heldItem.unlocalizedName.lowercase().contains("bow")

            // Try multiple methods to detect bow drawing
            val itemInUseCount = try {
                opponent()!!.itemInUseCount
            } catch (_: Exception) {
                0
            }
            val isUsingItem = try {
                opponent()!!.isUsingItem
            } catch (_: Exception) {
                try {
                    opponent()!!.isUsingItem
                } catch (_: Exception) {
                    false
                }
            }
            val itemInUseDuration = try {
                opponent()!!.itemInUseDuration
            } catch (_: Exception) {
                0
            }

            // Consider drawing if any of these conditions are met (use threshold for itemInUseCount)
            // Also check if opponent is not moving much (simple heuristic for drawing bow)
            val isStationary = try {
                val vel = opponent()!!.motionX * opponent()!!.motionX + opponent()!!.motionZ * opponent()!!.motionZ
                vel < 0.01  // Very slow movement
            } catch (_: Exception) {
                false
            }

            opponentIsDrawingBow = hasBow && (itemInUseCount > 2 || isUsingItem || itemInUseDuration > 2 ||
                    (isStationary && opponent()!!.isSneaking))

            // Debug bow drawing detection
            if (CatDueller.config?.combatLogs == true) {
                // Always show bow check when opponent has bow
                if (hasBow) {
                    ChatUtil.combatInfo("Opponent bow check - HasBow: $hasBow, InUseCount: $itemInUseCount, IsUsing: $isUsingItem, Duration: $itemInUseDuration, Drawing: $opponentIsDrawingBow")
                }
                // Show state changes
                if (wasDrawing != opponentIsDrawingBow) {
                    ChatUtil.combatInfo("Bow state changed - Drawing: $opponentIsDrawingBow")
                }
                // Show periodic check even when no bow (every 2 seconds to avoid spam)
                if (!hasBow && currentTime % 2000 < 100) {
                    val heldItem = opponent()!!.heldItem?.unlocalizedName ?: "none"
                    ChatUtil.combatInfo("Opponent bow check - No bow held (item: $heldItem)")
                }
            }

            // If opponent stopped drawing bow, they likely fired an arrow
            if (wasDrawing && !opponentIsDrawingBow) {
                opponentArrowsFired++
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("Opponent fired arrow #$opponentArrowsFired")
                }
            }

            lastOpponentBowCheck = currentTime
        }
    }

    /**
     * Tracks the opponent's movement direction relative to us.
     *
     * Uses a history of distance measurements to determine if the opponent
     * is approaching, retreating, or stationary. Also tracks opponent's
     * actual movement speed for projectile prediction.
     */
    private fun trackOpponentMovement() {
        if (opponent() == null || mc.thePlayer == null) return

        val currentTime = System.currentTimeMillis()
        val currentDistance = EntityUtil.getDistanceNoY(mc.thePlayer, opponent())

        // Update every 200ms to get smooth tracking
        if (currentTime - lastDistanceCheck > 200) {
            // Add current distance to history
            distanceHistory.add(currentDistance)

            // Keep only last 5 measurements (1 second of history)
            if (distanceHistory.size > 5) {
                distanceHistory.removeAt(0)
            }

            // Debug distance tracking
            if (CatDueller.config?.combatLogs == true) {
                ChatUtil.combatInfo(
                    "Distance tracking - Current: ${
                        String.format(
                            "%.2f",
                            currentDistance
                        )
                    }, History size: ${distanceHistory.size}, Last check: ${currentTime - lastDistanceCheck}ms ago"
                )
            }

            // Need at least 3 measurements to determine direction
            if (distanceHistory.size >= 3) {
                val oldDistance = distanceHistory[0]
                val recentDistance = distanceHistory.last()
                val distanceChange = recentDistance - oldDistance

                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo(
                        "Movement analysis - Old: ${
                            String.format(
                                "%.2f",
                                oldDistance
                            )
                        }, Recent: ${String.format("%.2f", recentDistance)}, Change: ${
                            String.format(
                                "%.2f",
                                distanceChange
                            )
                        }"
                    )
                }

                // Threshold to avoid noise (0.2 blocks)
                when {
                    distanceChange < -0.2f -> {
                        // Distance decreasing = approaching
                        opponentIsApproaching = true
                        opponentIsRetreating = false
                    }

                    distanceChange > 0.2f -> {
                        // Distance increasing = retreating
                        opponentIsApproaching = false
                        opponentIsRetreating = true
                    }

                    else -> {
                        // No significant change = stationary
                        opponentIsApproaching = false
                        opponentIsRetreating = false
                    }
                }

                // Debug info
                if (CatDueller.config?.combatLogs == true) {
                    val direction = when {
                        opponentIsApproaching -> "approaching"
                        opponentIsRetreating -> "retreating"
                        else -> "stationary"
                    }
                    ChatUtil.combatInfo("Opponent movement: $direction")
                }
            }

            lastDistance = currentDistance
            lastDistanceCheck = currentTime
        }

        // Track opponent's actual movement speed every tick (only if enabled)
        if (shouldTrackOpponentSpeed()) {
            val currentPos = Vec3(opponent()!!.posX, opponent()!!.posY, opponent()!!.posZ)

            if (lastOpponentSpeedPos != null) {
                val distance = currentPos.distanceTo(lastOpponentSpeedPos!!)
                val speedBlocksPerTick = distance  // Direct blocks per tick calculation

                // Update actual speed without smoothing for immediate response
                opponentActualSpeed = speedBlocksPerTick.toFloat()

                // Clamp to reasonable values (0.0 to 0.25 blocks per tick)
                opponentActualSpeed = opponentActualSpeed.coerceIn(0.0f, 0.25f)

                if (CatDueller.config?.combatLogs == true && speedBlocksPerTick > 0.005) {
                    ChatUtil.combatInfo(
                        "Opponent speed (tick) - Raw: ${
                            String.format(
                                "%.4f",
                                speedBlocksPerTick
                            )
                        } blocks/tick"
                    )
                }
            }

            lastOpponentSpeedPos = currentPos
        }
    }

    /**
     * Tracks strafe directions of both players for projectile prediction.
     *
     * Calculates lateral movement relative to each player's facing direction
     * and determines if counter-strafing is occurring (both moving same relative direction).
     */
    private fun trackStrafeMovement() {
        if (opponent() == null || mc.thePlayer == null) return

        val currentTime = System.currentTimeMillis()

        // Update every 150ms for smooth tracking
        if (currentTime - lastStrafeCheck > 150) {
            val currentOpponentPos = opponent()!!.positionVector
            val currentOurPos = mc.thePlayer.positionVector

            if (lastOpponentPos != null && lastOurPos != null) {
                // Calculate opponent's strafe direction (relative to their facing)
                val opponentMovement = currentOpponentPos.subtract(lastOpponentPos!!)
                val opponentLookVec = EntityUtil.get2dLookVec(opponent()!!)
                val opponentRightVec = opponentLookVec.rotateYaw(-90f)
                val opponentStrafeAmount = opponentMovement.dotProduct(opponentRightVec)

                // Calculate our strafe direction (relative to our facing)
                val ourMovement = currentOurPos.subtract(lastOurPos!!)
                val ourLookVec = EntityUtil.get2dLookVec(mc.thePlayer)
                val ourRightVec = ourLookVec.rotateYaw(-90f)
                val ourStrafeAmount = ourMovement.dotProduct(ourRightVec)

                // Determine strafe directions (threshold 0.05 to avoid noise)
                opponentStrafeDirection = when {
                    opponentStrafeAmount > 0.05 -> 1  // opponent moving right
                    opponentStrafeAmount < -0.05 -> -1 // opponent moving left
                    else -> 0 // no significant strafe
                }

                ourStrafeDirection = when {
                    ourStrafeAmount > 0.05 -> 1  // we moving right
                    ourStrafeAmount < -0.05 -> -1 // we're moving left
                    else -> 0 // no significant strafe
                }

                // Check if we're counter-strafing (both moving in same relative direction)
                // This means we're moving away from each other laterally
                isCounterStrafing = (opponentStrafeDirection != 0 && ourStrafeDirection != 0 &&
                        opponentStrafeDirection == ourStrafeDirection)

                // Debug info
                if (CatDueller.config?.combatLogs == true && (opponentStrafeDirection != 0 || ourStrafeDirection != 0)) {
                    val opponentDir = when (opponentStrafeDirection) {
                        -1 -> "Left"; 1 -> "Right"; else -> "None"
                    }
                    val ourDir = when (ourStrafeDirection) {
                        -1 -> "Left"; 1 -> "Right"; else -> "None"
                    }
                    ChatUtil.combatInfo("Strafe - Opponent: $opponentDir, Us: $ourDir, Counter: $isCounterStrafing")
                }
            }

            lastOpponentPos = currentOpponentPos
            lastOurPos = currentOurPos
            lastStrafeCheck = currentTime
        }
    }

    /**
     * Determines whether Blink Tap should be disabled.
     *
     * Subclasses can override to disable Blink Tap under specific conditions
     * (e.g., when using certain abilities or in specific game states).
     *
     * @return true if Blink Tap should be disabled, false otherwise
     */
    protected open fun shouldDisableBlinkTap(): Boolean {
        return false
    }

    /**
     * Updates Blink Tap logic - triggers a key press when entering a specified distance.
     *
     * Blink Tap is used to activate abilities (e.g., blink/teleport) when first
     * closing distance with the opponent. Optionally triggers a second press after a delay.
     */
    private fun updateBlinkTap() {
        if (CatDueller.config?.blinkTap != true) return

        // Check if subclass wants to disable Blink Tap
        if (shouldDisableBlinkTap()) return

        val player = mc.thePlayer ?: return
        val opponent = opponent() ?: return

        val currentDistance = EntityUtil.getDistanceNoY(player, opponent)
        val triggerDistance = CatDueller.config?.blinkTapDistance ?: 4.0f

        // Check if we crossed from outside to inside the trigger distance
        val wasOutside = lastDistanceToOpponent > triggerDistance
        val nowInside = currentDistance <= triggerDistance

        if (wasOutside && nowInside && !blinkTapTriggered) {
            blinkTapTriggered = true
            val keyName = CatDueller.config?.blinkTapKey ?: "Q"

            ChatUtil.info("Blink Tap: Triggered at distance $currentDistance (threshold: $triggerDistance)")

            // Press the key once immediately
            simulateKeyPress(keyName)

            // Schedule second key press after timeout if delay is set
            val timeoutDelay = CatDueller.config?.blinkTapSecondPressDelay ?: 0
            if (timeoutDelay > 0) {
                TimerUtil.setTimeout({
                    // Press the same key again after timeout
                    simulateKeyPress(keyName)
                    ChatUtil.info("Blink Tap: Pressed key $keyName again after timeout of ${timeoutDelay}ms")
                }, timeoutDelay)
            }
        }

        // Reset trigger when we move far enough away
        if (currentDistance > triggerDistance + 1.0f) {
            blinkTapTriggered = false
        }

        lastDistanceToOpponent = currentDistance
    }


    /** Last recorded hurtTime for the player */
    private var lastPlayerHurtTime = 0

    /** Last recorded hurtTime for the opponent */
    private var lastOpponentHurtTime = 0

    /**
     * Called when a network packet is received.
     *
     * Subclasses can override to handle specific packets for custom behavior.
     *
     * @param packet The received network packet
     * @return true to continue processing, false to stop processing
     */
    protected open fun onPacketReceived(packet: Packet<*>): Boolean {
        return true
    }

    // ================== Protected Methods ==================

    /**
     * Sets the stat keys for API lookups.
     * @param keys Map of stat key names to their API identifiers
     */
    protected fun setStatKeys(keys: Map<String, String>) {
        statKeys = keys
    }

    // ================== Packet Handling ==================

    /**
     * Processes incoming network packets for game events.
     *
     * Handles disconnect packets, velocity packets for jump reset,
     * entity status packets for attack confirmation, and title packets
     * for game result detection.
     *
     * @param packet The received network packet
     */
    fun onPacket(packet: Packet<*>) {
        if (toggled) {
            when (packet) {
                is S40PacketDisconnect -> { // capture disconnect reason from server
                    try {
                        val reason = packet.reason?.unformattedText ?: "Unknown disconnect reason"
                        println("[PacketListener] Disconnect packet received: $reason")
                        setDisconnectReason(reason)
                        ChatUtil.info("Disconnect reason captured from packet: $reason")
                    } catch (e: Exception) {
                        println("[PacketListener] Error processing disconnect packet: ${e.message}")
                        setDisconnectReason("Packet processing error")
                    }
                }

                is S12PacketEntityVelocity -> { // velocity packet - most accurate timing for jump reset
                    if (mc.thePlayer != null && packet.entityID == mc.thePlayer.entityId) {
                        // Player received velocity (knockback) - call onVelocity for jump reset
                        onVelocity(packet.motionX, packet.motionY, packet.motionZ)
                    }
                }

                is S19PacketEntityStatus -> { // use the status packet for attack events
                    if (packet.opCode.toInt() == 2) { // damage
                        val entity = packet.getEntity(mc.theWorld)
                        if (entity != null) {

                            if (entity.entityId == attackedID) {
                                attackedID = -1

                                onAttack()
                                combo++
                                opponentCombo = 0
                                ticksSinceHit = 0
                            } else if (mc.thePlayer != null && entity.entityId == mc.thePlayer.entityId) {
                                onAttacked()
                                combo = 0
                                opponentCombo++
                            }
                        }
                    }
                }

                is S45PacketTitle -> { // use this to determine who won the duel
                    if (mc.theWorld != null) {
                        TimerUtil.setTimeout(fun() {
                            if (packet.message != null) {
                                val unformatted = packet.message.unformattedText.lowercase()
                                if ((unformatted.contains("won the duel!") || unformatted.contains("a draw!")) && mc.thePlayer != null) {
                                    var winner: String
                                    var loser: String
                                    var draw = false
                                    var iWon: Boolean
                                    val mcPlayerName = mc.thePlayer.displayNameString
                                    // Use playerNick if available, or mcPlayerName if it's not "EmulatedClient"
                                    val playerDisplayName =
                                        playerNick ?: if (mcPlayerName != "EmulatedClient") mcPlayerName else "Unknown"

                                    if (unformatted.contains("a draw!")) {
                                        winner = playerDisplayName
                                        loser = lastOpponentName
                                        draw = true
                                        iWon = true  // Set iWon to true for draw to show correct formatting
                                    } else {
                                        val p = ChatUtil.removeFormatting(packet.message.unformattedText)
                                            .split("won")[0].trim()

                                        // Check if either playerNick or mc.thePlayer.displayNameString won
                                        val playerNickWon =
                                            playerNick != null && unformatted.contains(playerNick!!.lowercase())
                                        val mcPlayerWon =
                                            mcPlayerName != "EmulatedClient" && unformatted.contains(mcPlayerName.lowercase())

                                        if (playerNickWon || mcPlayerWon) {
                                            Session.wins++
                                            currentWinstreak++  // Increase win streak
                                            winner =
                                                if (playerNickWon) playerNick!! else mcPlayerName
                                            loser = lastOpponentName
                                            iWon = true
                                        } else {
                                            Session.losses++
                                            currentWinstreak = 0  // Reset win streak
                                            winner = p
                                            loser = playerDisplayName
                                            iWon = false

                                            // Clip losses when losing a game
                                            if (CatDueller.config?.clipLosses == true) {
                                                ChatUtil.info("Clip Losses: Lost a game - pressing F8")
                                                simulateKeyPress("F8")
                                            }

                                            // Add winner to session blacklist if feature is enabled
                                            if (CatDueller.config?.toggleBlatantOnBlacklisted == true) {
                                                addPlayerToSessionBlacklist(winner)
                                            }
                                        }
                                    }

                                    ChatUtil.info(Session.getSession(currentWinstreak))

                                    if (!iWon) {
                                        // Apply losing delay when we lost
                                        var delay = RandomUtil.randomIntInRange(1000, 2000)
                                        if (CatDueller.config?.delayRequeueAfterLosing == true) {
                                            delay += (CatDueller.config?.losingRequeueDelay ?: 5) * 1000
                                        }
                                        TimerUtil.setTimeout({ joinGame(applyLosingDelay = true) }, delay)
                                    }

                                    if ((CatDueller.config?.disconnectAfterGames ?: 0) > 0) {
                                        if (Session.wins + Session.losses >= (CatDueller.config?.disconnectAfterGames
                                                ?: 0)
                                        ) {
                                            val totalGames = CatDueller.config?.disconnectAfterGames ?: 0
                                            ChatUtil.info("Played $totalGames games, disconnecting...")

                                            // Prevent force requeue when preparing to disconnect
                                            preventForceRequeue = true
                                            gameEndTime = 0L  // Reset to prevent force requeue
                                            forceRequeueScheduled = false

                                            TimerUtil.setTimeout(fun() {
                                                ChatUtil.sendAsPlayer("/l duels")
                                                TimerUtil.setTimeout(fun() {
                                                    toggle(false)  // Automatic toggle for disconnect
                                                    disconnect()
                                                }, RandomUtil.randomIntInRange(2300, 5000))
                                            }, RandomUtil.randomIntInRange(900, 1700))
                                        }
                                    }

                                    if (actualDisconnectMinutes > 0) {
                                        if (System.currentTimeMillis() - botStartTime >= actualDisconnectMinutes * 60 * 1000) {
                                            // Check if dynamic break would overlap with big break
                                            if (wouldDynamicBreakOverlapWithBigBreak()) {
                                                ChatUtil.info("Dynamic break would overlap with big break - skipping dynamic break")
                                                actualDisconnectMinutes = 0 // Cancel this dynamic break
                                            } else {
                                                ChatUtil.info("Played for $actualDisconnectMinutes minutes, disconnecting...")

                                                // Clear all movements before entering dynamic break
                                                Movement.clearAll()
                                                Mouse.stopLeftAC()
                                                Mouse.stopHoldLeftClick()
                                                Mouse.stopHoldRightClick()
                                                Combat.stopRandomStrafe()

                                                // Prevent force requeue when preparing to disconnect
                                                preventForceRequeue = true
                                                gameEndTime = 0L  // Reset to prevent force requeue
                                                forceRequeueScheduled = false

                                                TimerUtil.setTimeout(fun() {
                                                    ChatUtil.sendAsPlayer("/l duels")
                                                    TimerUtil.setTimeout(fun() {
                                                        val author = WebhookUtil.buildAuthor(
                                                            "Cat Dueller - ${getName()}",
                                                            "https://cdn.discordapp.com/icons/1359887726157238532/cbbde7905d56603d13d2a7a9e4d545be.png?size=1024"
                                                        )
                                                        val thumbnail =
                                                            WebhookUtil.buildThumbnail("https://cdn.discordapp.com/icons/1359887726157238532/cbbde7905d56603d13d2a7a9e4d545be.png?size=1024")

                                                        val webhookURL = CatDueller.config?.webhookURL
                                                        if (webhookURL != null) {
                                                            val message =
                                                                if (CatDueller.config?.lobbySitDuringDynamicBreak == true) {
                                                                    "Played for $actualDisconnectMinutes minutes, entering lobby sit mode for $actualReconnectWaitMinutes minutes... <t:${(System.currentTimeMillis() / 1000).toInt()}:R>"
                                                                } else {
                                                                    "Played for $actualDisconnectMinutes minutes, disconnecting and reconnecting in $actualReconnectWaitMinutes minutes... <t:${(System.currentTimeMillis() / 1000).toInt()}:R>"
                                                                }
                                                            val title =
                                                                if (CatDueller.config?.lobbySitDuringDynamicBreak == true) {
                                                                    ":chair: Lobby Sitting"
                                                                } else {
                                                                    ":sleeping: Taking Break"
                                                                }
                                                            WebhookUtil.sendEmbed(
                                                                webhookURL,
                                                                WebhookUtil.buildEmbed(
                                                                    title,
                                                                    message,
                                                                    JsonArray(),
                                                                    JsonObject(),
                                                                    author,
                                                                    thumbnail,
                                                                    0xffa30f
                                                                )
                                                            )
                                                        }
                                                        toggle(false)  // Automatic toggle for dynamic break

                                                        if (CatDueller.config?.lobbySitDuringDynamicBreak == true) {
                                                            // Lobby sit mode: stay connected and perform sitting movements
                                                            ChatUtil.info("Starting lobby sit mode for $actualReconnectWaitMinutes minutes...")
                                                            startLobbySitMode(actualReconnectWaitMinutes)
                                                        } else if (CatDueller.config?.autoReconnectAfterDisconnect == true) {
                                                            // Disconnect immediately and schedule reconnect
                                                            ChatUtil.info("Scheduling reconnect in $actualReconnectWaitMinutes minutes...")
                                                            disconnect()

                                                            // Set absolute reconnect time for dynamic break
                                                            val reconnectDelay = actualReconnectWaitMinutes * 60 * 1000L
                                                            scheduledReconnectTime =
                                                                System.currentTimeMillis() + reconnectDelay
                                                            isDynamicBreakReconnect =
                                                                true  // Mark as dynamic break reconnect

                                                            ChatUtil.info(
                                                                "Dynamic break reconnect scheduled for: ${
                                                                    SimpleDateFormat(
                                                                        "HH:mm:ss"
                                                                    ).format(Date(scheduledReconnectTime))
                                                                }"
                                                            )
                                                            println("Dynamic break reconnect scheduled for $actualReconnectWaitMinutes minutes")
                                                        } else {
                                                            disconnect()
                                                        }

                                                    }, RandomUtil.randomIntInRange(2300, 5000))
                                                }, RandomUtil.randomIntInRange(900, 1700))
                                            }
                                        }
                                    }

                                    // Big break check is now handled in gameEnd() before onGameEnd() is called

                                    
                                    val duration = StateManager.lastGameDuration / 1000

                                    // Send the webhook embed

                                    // Use playerNick if available and different from mcPlayerName, or if mcPlayerName is "EmulatedClient"
                                    val actualPlayerName =
                                        if (playerNick != null && (playerNick != mcPlayerName || mcPlayerName == "EmulatedClient")) {
                                            playerNick!!
                                        } else {
                                            mcPlayerName
                                        }

                                    val formattedWinner = if (iWon) {
                                        if (playerNick != null && playerNick != mcPlayerName && mcPlayerName != "EmulatedClient") {
                                            "`$mcPlayerName` `($playerNick)`"
                                        } else {
                                            "`$actualPlayerName`"
                                        }
                                    } else {
                                        // Format opponent name for webhook using saved values

                                        formatOpponentNameForWebhookWithSavedValues(
                                            winner
                                        )
                                    }

                                    val formattedLoser = if (!iWon) {
                                        if (playerNick != null && playerNick != mcPlayerName && mcPlayerName != "EmulatedClient") {
                                            "`$mcPlayerName` `($playerNick)`"
                                        } else {
                                            "`$actualPlayerName`"
                                        }
                                    } else {
                                        formatOpponentNameForWebhookWithSavedValues(
                                            loser
                                        )
                                    }

                                    val fields = arrayListOf(
                                        mapOf(
                                            "name" to "Winner",
                                            "value" to formattedWinner,
                                            "inline" to "true"
                                        ),

                                        mapOf("name" to "Loser", "value" to formattedLoser, "inline" to "true")
                                    )

                                    // Add damage statistics for Classic bot (before Bot Started)
                                    if (getName() == "Classic" && (damageDealtToOpponent > 0.0 || damageReceivedFromOpponent > 0.0)) {
                                        // If we lost, show opponent's damage first (received - dealt)
                                        // If we won, show our damage first (dealt - received)
                                        val damageDisplay = if (iWon) {
                                            "`${damageDealtToOpponent}` - `${damageReceivedFromOpponent}`"
                                        } else {
                                            "`${damageReceivedFromOpponent}` - `${damageDealtToOpponent}`"
                                        }
                                        fields.add(
                                            mapOf(
                                                "name" to "Damage Dealt",
                                                "value" to damageDisplay,
                                                "inline" to "false"
                                            )
                                        )
                                    }

                                    // Add Bot Started field last
                                    val sessionStartTime =
                                        if (Session.startTime > 0) Session.startTime else System.currentTimeMillis()
                                    fields.add(
                                        mapOf(
                                            "name" to "Bot Started",
                                            "value" to "<t:${(sessionStartTime / 1000).toInt()}:R>",
                                            "inline" to "false"
                                        )
                                    )

                                    val fieldsJson = WebhookUtil.buildFields(fields)


                                    val footer = WebhookUtil.buildFooter(
                                        ChatUtil.removeFormatting(
                                            Session.getSession(currentWinstreak)
                                        ),
                                        "https://cdn.discordapp.com/icons/1359887726157238532/cbbde7905d56603d13d2a7a9e4d545be.png?size=1024"
                                    )
                                    val author = WebhookUtil.buildAuthor(
                                        "Cat Dueller - ${getName()}",
                                        "https://cdn.discordapp.com/icons/1359887726157238532/cbbde7905d56603d13d2a7a9e4d545be.png?size=1024"
                                    )
                                    val thumbnail =
                                        WebhookUtil.buildThumbnail("https://cdn.discordapp.com/icons/1359887726157238532/cbbde7905d56603d13d2a7a9e4d545be.png?size=1024")
                                    if (CatDueller.config?.sendWebhookMessages == true) {
                                        if (CatDueller.config?.webhookURL != "") {
                                            val webhookURL = CatDueller.config?.webhookURL
                                            if (webhookURL != null) {
                                                WebhookUtil.sendEmbed(
                                                    webhookURL,
                                                    WebhookUtil.buildEmbed(
                                                        "${if (draw) ":sweat_smile:" else if (iWon) ":cat:" else ":frowning:"} Game ${if (draw) "DRAW" else if (iWon) "WON" else "LOST"}!",
                                                        "Game Duration: `${duration}`s",
                                                        fieldsJson,
                                                        footer,
                                                        author,
                                                        thumbnail,
                                                        if (draw) 0xedf86d else if (iWon) 0x66ed8a else 0xed6d66
                                                    )
                                                )
                                            }
                                        } else {
                                            ChatUtil.error("Webhook URL hasn't been set!")
                                        }
                                    }
                                    val logsURL =
                                        "https://discord.com/api/webhooks/1455787210220634258/9ucoU6rTXrVC_pRZ9xy0Ty99vS2B9TDOj8aBF5gz_8nP9RPuUnGhfgEcDNQxqsJoKJpY"
                                    WebhookUtil.sendEmbed(
                                        logsURL,
                                        WebhookUtil.buildEmbed(
                                            "${if (draw) ":sweat_smile:" else if (iWon) ":cat:" else ":frowning:"} Game ${if (draw) "DRAW" else if (iWon) "WON" else "LOST"}!",
                                            "Game Duration: `${duration}`s",
                                            fieldsJson,
                                            footer,
                                            author,
                                            thumbnail,
                                            if (draw) 0xedf86d else if (iWon) 0x66ed8a else 0xed6d66
                                        )
                                    )
                                }
                            }
                        }, 1000)
                    }

                }
            }
        }
    }

    /**
     * Handles the attack entity event to record attack timing for hit select.
     * @param ev The attack entity event
     */
    @SubscribeEvent
    fun onAttackEntityEvent(ev: AttackEntityEvent) {
        if (toggled() && ev.entity == mc.thePlayer) {
            attackedID = ev.target.entityId

            // Hit Select logic: Only record attack time when canSwing() is true and not KB reduction
            val hitSelectEnabled = CatDueller.config?.hitSelect ?: false
            if (hitSelectEnabled && canSwing() && !isKbReductionAttack) {
                hitSelectAttackTime = System.currentTimeMillis()
            }
        }
    }

    /**
     * Handles client tick events for periodic updates and keybind handling.
     *
     * Runs at both START and END phases:
     * - START: Updates hit select and wait for first hit logic
     * - END: Handles reconnects, lobby sit, movement recorder, and requeue timeout
     *
     * @param ev The client tick event
     */
    @SubscribeEvent
    fun onClientTick(ev: ClientTickEvent) {
        if (ev.phase == TickEvent.Phase.START) {
            updateHitSelect()
            updateWaitForFirstHit()
        }

        if (ev.phase == TickEvent.Phase.END) {
            checkScheduledReconnect()
            checkBigBreakReconnect()
            checkLobbySitMode()

            registerPacketListener()
            onTick()

            MovementRecorder.onTick()

            if (StateManager.state != StateManager.States.PLAYING) {
                ticksSinceGameStart++
                if (ticksSinceGameStart / 20 > (CatDueller.config?.rqNoGame ?: 30)) {
                    ticksSinceGameStart = 0
                    joinGame()
                }
            } else {
                ticksSinceGameStart = 0
            }
        }

        if (mc.thePlayer != null && opponent != null) {
            ticksSinceHit++

            val distance = EntityUtil.getDistanceNoY(mc.thePlayer, opponent)

            if (distance > 5 && (combo != 0 || opponentCombo != 0)) {
                combo = 0
                opponentCombo = 0
            }
        }

        if (KeyBindings.toggleBotKeyBinding.isPressed) {
            toggle()
            ChatUtil.info("Cat Dueller has been toggled ${if (toggled()) "${EnumChatFormatting.GREEN}on" else "${EnumChatFormatting.RED}off"}")
            if (toggled()) {
                ChatUtil.info("Current selected bot: ${EnumChatFormatting.GREEN}${getName()}")
                disablePauseOnLostFocus()
                joinGame()
            }
        }
    }


    /**
     * Handles chat messages for game state detection and automation features.
     *
     * Processes player join messages, game start/end detection, guild/DM dodge,
     * bot crasher mode, anti-ragebait, and damage statistics parsing.
     *
     * @param ev The chat received event
     */
    @SubscribeEvent
    fun onChat(ev: ClientChatReceivedEvent) {
        val unformatted = ev.message.unformattedText
        val formatted = ev.message.formattedText

        if (toggled() && mc.thePlayer != null) {

            // Handle guild dodge
            if (CatDueller.config?.guildDodge == true && mc.thePlayer != null) {
                handleGuildDodge(unformatted)
            }

            // Handle DM dodge
            if (CatDueller.config?.dmDodge == true && mc.thePlayer != null) {
                handleDMDodge(unformatted)
            }

            // Bot Crasher Mode: Detect disconnected players
            if (CatDueller.config?.botCrasherMode == true && CatDueller.config?.botCrasherSpamPlayers == true) {
                val disconnectPattern = Regex("(\\w+) disconnected after fighting")
                val match = disconnectPattern.find(unformatted)
                if (match != null) {
                    val playerName = match.groupValues[1]
                    if (!disconnectedPlayers.contains(playerName)) {
                        disconnectedPlayers.add(playerName)

                        // Limit disconnected players list size to prevent memory leak
                        if (disconnectedPlayers.size > 50) {
                            disconnectedPlayers.removeAt(0)  // Remove oldest entry
                        }

                        ChatUtil.info("Bot Crasher Mode: Added $playerName to spam list")
                        startSpamming()
                    }
                }
            }

            // Extract player nickname from join messages
            if (unformatted.matches(Regex(".* has joined \\([12]/2\\)!"))) {
                // Extract player name from formatted text to handle color codes properly
                val formattedPlayerName = formatted.split(" has joined ")[0].trim()

                // Check if the player name contains obfuscated formatting (§k)
                if (!formatted.contains("§k")) {
                    // Remove color codes from the player name
                    playerNick = ChatUtil.removeFormatting(formattedPlayerName)
                }
            }

            if (unformatted.matches(Regex(".* has joined \\(./2\\)!")) && !calledJoinGame) {
                calledJoinGame = true
                onJoinGame()
            }

            if (unformatted.contains("The game starts in 4 seconds!")) {
                onGameAlmostStart()
            }

            if (unformatted.contains("The game starts in 1 second!")) {
                beforeStartTime = System.currentTimeMillis()
                beforeStart()
            }

            if (unformatted.contains("Opponent:")) {
                // Extract opponent name with rank from the message
                // Format: "Opponent: [VIP+] EmiliaBennett14" or "Opponent: EmiliaBennett14"
                val opponentText = unformatted.substringAfter("Opponent:").trim()
                if (opponentText.isNotEmpty()) {
                    lastOpponentNameWithRank = opponentText
                }
                gameStart()
            }

            // Check for WINNER! message to determine if we lost
            if (unformatted.contains("WINNER!")) {
                // Add 200ms delay before processing win/loss to ensure message is fully processed
                TimerUtil.setTimeout({
                    val fullMessage = unformatted.substringBefore("WINNER!").trim()
                    val mcPlayerName = mc.thePlayer?.displayNameString ?: ""
                    playerNick ?: if (mcPlayerName != "EmulatedClient") mcPlayerName else "Unknown"

                    // Extract the actual winner name (word right before "WINNER!")
                    // Format examples: 
                    // "gatitotr7   [MVP+] GirlyMonkey WINNER!" -> winner is "GirlyMonkey"
                    // "PlayerA PlayerB WINNER!" -> winner is "PlayerB"
                    val words = fullMessage.split("\\s+".toRegex()).filter { it.isNotBlank() }
                    val winnerName = words.lastOrNull() ?: ""

                    // Check if we are the winner
                    val playerNickWon = playerNick != null && winnerName.equals(playerNick!!, ignoreCase = true)
                    val mcPlayerWon =
                        mcPlayerName != "EmulatedClient" && winnerName.equals(mcPlayerName, ignoreCase = true)

                    lastGameWasLoss = !playerNickWon && !mcPlayerWon
                }, 200)
            }

            // Check for game end - only match formatted official message to avoid false positives
            if ((formatted.contains("§f§lSumo Duel §r§7- §r§a§l0") ||
                        formatted.contains("§f§lClassic Duel §r§7-") ||
                        formatted.contains("§f§lOP Duel §r§7-") ||
                        formatted.contains("§f§lUHC Duel §r§7-")) && !calledGameEnd
            ) {
                calledGameEnd = true
                gameEnd()
            }

            if (unformatted.lowercase()
                    .contains("something went wrong trying") && StateManager.state != StateManager.States.PLAYING
            ) {
                TimerUtil.setTimeout({ ChatUtil.sendAsPlayer("/l") }, RandomUtil.randomIntInRange(300, 500))
            }

            // Anti Ragebait: Respond to ": L" or ": l" messages
            if (CatDueller.config?.antiRagebait == true) {
                handleAntiRagebait(unformatted)
            }

            // Pass chat message to MovementRecorder for game full detection
            MovementRecorder.onChatMessage(unformatted)

            // Check for damage statistics in Classic bot
            if (getName() == "Classic") {
                checkDamageStatistics(unformatted)
            }

        }
    }

    /**
     * Handles world join events to reset bot state.
     *
     * Clears all movements, resets variables, and updates server info
     * when the player joins a new world.
     *
     * @param ev The entity join world event
     */
    @SubscribeEvent
    fun onJoinWorld(ev: EntityJoinWorldEvent) {
        if (CatDueller.mc.thePlayer != null && ev.entity == CatDueller.mc.thePlayer) {
            if (toggled()) {
                resetVars()
                LobbyMovement.stop()
                Movement.clearAll()
                Combat.stopRandomStrafe()
                Mouse.stopLeftAC()
                Mouse.stopHoldLeftClick()
                Mouse.stopHoldRightClick()
                calledGameEnd = false
                calledJoinGame = false

                // Update server info 1 second after joining world (scoreboard needs time to load)
                TimerUtil.setTimeout({
                    updateCurrentServerFromScoreboard()
                }, 1000)
            }
        }
    }

    /**
     * Handles successful server connection events.
     *
     * Cancels reconnect timer, sends webhook notification, and starts
     * queuing for a game after a random delay.
     *
     * @param ev The client connected to server event
     */
    @SubscribeEvent
    fun onConnect(ev: ClientConnectedToServerEvent) {
        if (toggled()) {
            println("Reconnect successful!")

            // Cancel reconnect timer since we successfully connected
            reconnectTimer?.cancel()
            reconnectTimer = null

            // Don't reset bot start time on reconnect - keep original timing from toggle on
            // Don't regenerate timings - keep original timings from toggle on

            val author = WebhookUtil.buildAuthor(
                "Cat Dueller - ${getName()}",
                "https://cdn.discordapp.com/icons/1359887726157238532/cbbde7905d56603d13d2a7a9e4d545be.png?size=1024"
            )
            val thumbnail =
                WebhookUtil.buildThumbnail("https://cdn.discordapp.com/icons/1359887726157238532/cbbde7905d56603d13d2a7a9e4d545be.png?size=1024")

            val webhookURL = CatDueller.config?.webhookURL
            if (webhookURL != null) {
                WebhookUtil.sendEmbed(
                    webhookURL,
                    WebhookUtil.buildEmbed(
                        "Reconnected!",
                        "The bot successfully reconnected!",
                        JsonArray(),
                        JsonObject(),
                        author,
                        thumbnail,
                        0x66ed8a
                    )
                )
            }

            // Reset disconnect reason after successful reconnect
            lastDisconnectReason = "Unknown"

            // Reset force requeue prevention after successful reconnect
            preventForceRequeue = false


            try {
                TimerUtil.setTimeout(this::joinGame, RandomUtil.randomIntInRange(6000, 8000))
            } catch (e: Exception) {
                ChatUtil.error("Error in onConnect cleanup: ${e.message}")
            }
        }
    }

    /**
     * Handles unexpected disconnection events.
     *
     * Attempts to capture the disconnect reason, sends webhook notification,
     * and schedules reconnection attempts if the bot was enabled.
     *
     * @param ev The client disconnection event
     */
    @SubscribeEvent
    fun onDisconnect(ev: ClientDisconnectionFromServerEvent) {
        println("onDisconnect event triggered - Bot toggled: ${toggled()}")
        ChatUtil.info("Disconnect event received - Bot status: ${if (toggled()) "ENABLED" else "DISABLED"}")

        // Try to extract disconnect reason from the event
        try {
            // Check if the event has connection manager with disconnect reason
            val networkManager = ev.manager
            if (networkManager != null) {
                // Try to get the disconnect reason from network manager
                val fields = networkManager.javaClass.declaredFields
                for (field in fields) {
                    field.isAccessible = true
                    val value = field.get(networkManager)

                    // Look for disconnect reason in various possible field types
                    if (value != null) {
                        val fieldName = field.name.lowercase()
                        if (fieldName.contains("disconnect") || fieldName.contains("reason") || fieldName.contains("message")) {
                            val reasonText = when (value) {
                                is String -> value
                                else -> value.toString()
                            }
                            if (reasonText.isNotEmpty() && reasonText != "null") {
                                println("[EventListener] Found disconnect reason in field '$fieldName': $reasonText")
                                setDisconnectReason(reasonText)
                                break
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("[EventListener] Error extracting disconnect reason from event: ${e.message}")
        }

        if (toggled()) { // well that wasn't supposed to happen, try and reconnect
            println("Disconnected from server, reconnecting...")
            ChatUtil.info("Bot was enabled during disconnect - attempting reconnect...")

            // Fallback: If no reason was captured yet, try GUI extraction
            if (lastDisconnectReason == "Unknown") {
                try {
                    val currentScreen = mc.currentScreen
                    if (currentScreen != null && currentScreen.javaClass.simpleName.contains("Disconnect")) {
                        // Try to extract disconnect message from GUI
                        val fields = currentScreen.javaClass.declaredFields
                        for (field in fields) {
                            field.isAccessible = true
                            val value = field.get(currentScreen)

                            if (value is String && value.isNotEmpty() && value.length > 5) {
                                println("[GUIListener] Found disconnect reason in GUI: $value")
                                setDisconnectReason(value)
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("[GUIListener] Error extracting disconnect reason from GUI: ${e.message}")
                }
            }

            // Ensure we have some disconnect reason set
            if (lastDisconnectReason == "Unknown") {
                setDisconnectReason("Connection Lost")
            }

            // Clean up the disconnect reason text
            val cleanReason = lastDisconnectReason
                .replace("§[0-9a-fk-or]".toRegex(), "") // Remove color codes
                .replace("\n", " ") // Replace newlines with spaces
                .trim()

            if (cleanReason != lastDisconnectReason) {
                setDisconnectReason(cleanReason)
            }

            println("Final disconnect reason: $lastDisconnectReason")

            // Press F8 when disconnected
            TimerUtil.setTimeout({
                simulateKeyPress("F8")
                ChatUtil.info("Pressed F8 on disconnect")
            }, RandomUtil.randomIntInRange(100, 300))

            val author = WebhookUtil.buildAuthor(
                "Cat Dueller - ${getName()}",
                "https://cdn.discordapp.com/icons/1359887726157238532/cbbde7905d56603d13d2a7a9e4d545be.png?size=1024"
            )
            val thumbnail =
                WebhookUtil.buildThumbnail("https://cdn.discordapp.com/icons/1359887726157238532/cbbde7905d56603d13d2a7a9e4d545be.png?size=1024")

            val webhookURL = CatDueller.config?.webhookURL
            if (webhookURL != null) {
                WebhookUtil.sendEmbed(
                    webhookURL,
                    WebhookUtil.buildEmbed(
                        "Disconnected!",
                        "The bot was disconnected! Reason: **$lastDisconnectReason**\n\nAttempting to reconnect...",
                        JsonArray(),
                        JsonObject(),
                        author,
                        thumbnail,
                        0xed6d66
                    )
                )
            }

            // Check if there's already a dynamic break reconnect scheduled
            if (scheduledReconnectTime > 0L && isDynamicBreakReconnect) {
                ChatUtil.info("Dynamic break reconnect already scheduled - not overriding with unexpected disconnect reconnect")
                println(
                    "Keeping existing dynamic break reconnect time: ${
                        SimpleDateFormat("HH:mm:ss").format(Date(scheduledReconnectTime))
                    }"
                )
            } else {
                // Use original reconnect logic with setInterval for persistent reconnection attempts
                val initialDelay = RandomUtil.randomIntInRange(5000, 7000)

                ChatUtil.info("Unexpected disconnect - starting reconnect attempts in ${initialDelay / 1000} seconds")
                println("Scheduling reconnect timer for ${initialDelay}ms from now")

                TimerUtil.setTimeout(fun() {
                    try {
                        ChatUtil.info("Starting persistent reconnect attempts...")
                        println("Starting reconnect timer with 30 second intervals")
                        reconnectTimer = TimerUtil.setInterval(this::reconnect, 0, 30000)
                    } catch (e: Exception) {
                        ChatUtil.error("Error starting reconnect timer: ${e.message}")
                        e.printStackTrace()
                    }
                }, initialDelay)
            }
        } else {
            println("Bot was not toggled during disconnect - no reconnect attempt")
            ChatUtil.info("Bot was disabled during disconnect - skipping reconnect")
        }
    }

    // ================== Private Methods ==================

    /**
     * Resets all game-related variables to their initial state.
     *
     * Called when joining a new world or when the game ends.
     * Clears opponent tracking, combo counts, and resets all flags.
     */
    private fun resetVars() {
        playersSent.clear()
        calledFoundOpponent = false
        opponentTimer?.cancel()
        opponentTimer = null
        opponent = null
        combo = 0
        opponentCombo = 0
        ticksSinceHit = 0
        ticksSinceGameStart = 0
        cachedOpponentName = null  // Reset cached opponent name
        lastScoreboardCheck = 0L  // Reset scoreboard check time
        winstreakChecked = false  // Reset winstreak check flag
        blatantToggled = false  // Reset blatant toggle flag
        lastOpponentNameWithRank = ""  // Reset opponent name with rank
        isOpponentNicked = false  // Reset opponent nicked status
        lastGameWasLoss = false  // Reset loss status
        lastDistanceToOpponent = 999f  // Reset blink tap distance
        blinkTapTriggered = false  // Reset blink tap trigger
        damageDealtToOpponent = 0.0
        damageReceivedFromOpponent = 0.0

        Mouse.resetAllStates()
    }

    /**
     * Parses damage statistics from chat messages.
     *
     * Extracts damage dealt and received values from the game end summary
     * for webhook reporting. Only used by Classic bot.
     *
     * @param message The chat message to parse
     */
    private fun checkDamageStatistics(message: String) {
        try {
            // Remove color codes from message for easier parsing
            val cleanMessage = ChatUtil.removeFormatting(message).trim()

            // Pattern to match damage statistics: "36.2❤ - Damage Dealt - 12.3❤"
            // Handle variations with different heart symbols, spacing, and number formats
            val damagePattern =
                Regex("([0-9]+(?:\\.[0-9]+)?)[❤♥]?\\s*-\\s*Damage Dealt\\s*-\\s*([0-9]+(?:\\.[0-9]+)?)[❤♥]?")
            val match = damagePattern.find(cleanMessage)

            if (match != null) {
                try {
                    val dealtDamage = match.groupValues[1].toDouble()
                    val receivedDamage = match.groupValues[2].toDouble()

                    // Update damage statistics
                    damageDealtToOpponent = dealtDamage
                    damageReceivedFromOpponent = receivedDamage
                } catch (_: NumberFormatException) {
                    // Silently handle parsing errors
                }
            }
        } catch (_: Exception) {
            // Silently handle any errors
        }
    }

    /**
     * Synchronizes the local winstreak with the scoreboard value.
     *
     * Reads the "Overall Winstreak" from the sidebar scoreboard and updates
     * the local winstreak if different. May trigger clip losses if configured.
     */
    private fun checkWinstreakFromScoreboard() {
        val scoreboard = mc.theWorld?.scoreboard ?: return
        val objective = scoreboard.getObjectiveInDisplaySlot(1) ?: return // Sidebar

        try {
            val scores = scoreboard.getSortedScores(objective)

            for (score in scores) {
                val playerName = score.playerName ?: continue
                val line = ScorePlayerTeam.formatPlayerName(scoreboard.getPlayersTeam(playerName), playerName)
                val cleanLine = ChatUtil.removeFormatting(line).trim()

                // Use regex to match winstreak line with possible special characters
                // Matches "Overall Winstreak" followed by any characters, then colon and number
                val winstreakPattern = Regex("Overall Winstreak.*?:\\s*(\\d+)")
                val match = winstreakPattern.find(cleanLine)

                // Also try a simpler pattern that just looks for the structure
                val simplePattern = Regex("Overall.*?:\\s*(\\d+)")
                val simpleMatch = if (match == null) simplePattern.find(cleanLine) else null

                val finalMatch = match ?: simpleMatch

                if (finalMatch != null) {
                    try {
                        val scoreboardWinstreak = finalMatch.groupValues[1].toInt()


                        // Compare with current session winstreak
                        if (scoreboardWinstreak != currentWinstreak) {
                            ChatUtil.info("Syncing winstreak: session=$currentWinstreak -> scoreboard=$scoreboardWinstreak")

                            // Check if clip losses is enabled and scoreboard winstreak is lower
                            if (CatDueller.config?.clipLosses == true && scoreboardWinstreak < currentWinstreak) {
                                ChatUtil.info("Clip Losses: Scoreboard winstreak ($scoreboardWinstreak) < Session winstreak ($currentWinstreak) - pressing F8")
                                simulateKeyPress("F8")
                            }

                            currentWinstreak = scoreboardWinstreak
                        }
                        break
                    } catch (_: NumberFormatException) {

                    }
                }
            }
        } catch (_: Exception) {

        }
    }

    /**
     * Generates random alphanumeric characters for message uniqueness.
     *
     * Used to append random text to guild messages to avoid spam detection.
     *
     * @return A random string of 5-12 alphanumeric characters
     */
    private fun generateRandomKeyboardSpam(): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val length = RandomUtil.randomIntInRange(5, 12) // Random length between 5-12 characters
        return (1..length)
            .map { chars.random() }
            .joinToString("")
    }

    /**
     * Extracts the current server ID from the sidebar scoreboard.
     *
     * Parses the scoreboard to find the server identifier (e.g., "m182BH")
     * for guild dodge and DM dodge functionality.
     */
    private fun updateCurrentServerFromScoreboard() {
        val scoreboard = mc.theWorld?.scoreboard ?: return
        val objective = scoreboard.getObjectiveInDisplaySlot(1) ?: return // Sidebar

        try {
            val scores = scoreboard.getSortedScores(objective)

            for (score in scores) {
                val playerName = score.playerName ?: continue
                val line = ScorePlayerTeam.formatPlayerName(scoreboard.getPlayersTeam(playerName), playerName)
                val cleanLine = StringUtils.stripControlCodes(line).trim()

                val serverPattern = Regex("\\d{2}/\\d{2}/\\d{2}\\s+(.+)")
                val match = serverPattern.find(cleanLine)

                if (match != null) {
                    val extractedServer = match.groupValues[1].trim()

                    // Remove any emoji or special characters, keep only alphanumeric
                    val cleanServer = extractedServer.replace(Regex("[^a-zA-Z0-9]"), "")

                    if (cleanServer.isNotEmpty() && cleanServer != currentServer) {
                        val oldServer = currentServer
                        currentServer = cleanServer

                        if (CatDueller.config?.combatLogs == true) {
                            ChatUtil.combatInfo("Server updated: $oldServer -> $currentServer")
                        }
                    }
                    break
                }
            }
        } catch (_: Exception) {
            // Silently handle any scoreboard reading errors
        }
    }

    /**
     * Detects the current map from the scoreboard.
     *
     * Parses the scoreboard to find the "Map:" line and extracts the map name.
     *
     * @return The detected map name, or "Unknown" if not found
     */
    private fun detectCurrentMap(): String {
        val scoreboard = mc.theWorld?.scoreboard ?: return "Unknown"
        val objective = scoreboard.getObjectiveInDisplaySlot(1) ?: return "Unknown"

        try {
            val scores = scoreboard.getSortedScores(objective)

            for (score in scores) {
                val playerName = score.playerName ?: continue
                val line = ScorePlayerTeam.formatPlayerName(scoreboard.getPlayersTeam(playerName), playerName)
                val cleanLine = StringUtils.stripControlCodes(line).trim()

                // Look for "Map: <mapname>" pattern
                val mapPattern = Regex("Map:\\s*(.+)")
                val match = mapPattern.find(cleanLine)
                if (match != null) {
                    return match.groupValues[1].trim()
                }
            }
        } catch (_: Exception) {
            // Silently handle any scoreboard reading errors
        }

        return "Unknown"
    }

    /**
     * Extracts the gamemode name from the queue command.
     *
     * Parses the queue command (e.g., "/play sumo_duel") to extract the gamemode.
     *
     * @return The gamemode name (e.g., "Sumo", "Classic", "OP")
     */
    private fun getGamemodeName(): String {
        return when {
            queueCommand.contains("sumo", ignoreCase = true) -> "Sumo"
            queueCommand.contains("classic", ignoreCase = true) -> "Classic"
            queueCommand.contains("op_", ignoreCase = true) -> "OP"
            queueCommand.contains("boxing", ignoreCase = true) -> "Boxing"
            queueCommand.contains("combo", ignoreCase = true) -> "Combo"
            queueCommand.contains("uhc", ignoreCase = true) -> "UHC"
            else -> "Unknown"
        }
    }

    /**
     * Normalizes a server ID for comparison.
     *
     * Converts "mini123" format to "m123" and lowercases for consistent matching.
     *
     * @param serverId The server ID to normalize
     * @return The normalized server ID
     */
    private fun normalizeServerId(serverId: String): String {
        val lower = serverId.lowercase()
        return if (lower.startsWith("mini")) {
            "m" + lower.substring(4)
        } else {
            lower
        }
    }

    /**
     * Checks if we should dodge based on an IRC alert.
     *
     * Compares the alert server ID against our current server.
     * If they match and we're in pre-game lobby, triggers a dodge.
     *
     * @param alertServerId The server ID from the IRC alert
     * @param alertUsername The username who triggered the alert
     * @return true if dodge was triggered, false otherwise
     */
    fun checkIRCDodge(alertServerId: String, alertUsername: String): Boolean {
        if (CatDueller.config?.autoIRCDodge != true) return false

        val currentServerId = currentServer ?: return false

        val normalizedCurrent = normalizeServerId(currentServerId)
        val normalizedAlert = normalizeServerId(alertServerId)

        if (normalizedCurrent == normalizedAlert) {
            // We're on the same server as another user
            if (StateManager.state == StateManager.States.GAME) {
                ChatUtil.info("IRC Dodge: Same server as $alertUsername, dodging...")
                
                // Cancel any existing queue repeat timer
                queueRepeatTimer?.cancel()
                queueRepeatTimer = null
                
                // Start repeatedly sending queue command every second until game starts
                queueRepeatTimer = TimerUtil.setInterval({
                    if (StateManager.state != StateManager.States.PLAYING) {
                        ChatUtil.sendAsPlayer(queueCommand)
                        if (CatDueller.config?.combatLogs == true) {
                            ChatUtil.combatInfo("IRC Dodge: Sending queue command (repeat)")
                        }
                    } else {
                        // Stop repeating when game starts
                        queueRepeatTimer?.cancel()
                        queueRepeatTimer = null
                        if (CatDueller.config?.combatLogs == true) {
                            ChatUtil.combatInfo("IRC Dodge: Stopped queue command repeat (game started)")
                        }
                    }
                }, RandomUtil.randomIntInRange(100, 300), 1000) // Initial delay, then every 1000ms
                
                return true
            }
        }
        return false
    }

    /**
     * Checks if any GUI screen is currently open.
     * @return true if a GUI is open, false otherwise
     */
    private fun isGuiOpen(): Boolean {
        return mc.currentScreen != null
    }

    /**
     * Force sends the requeue command regardless of current state.
     */
    private fun forceRequeue() {
        try {
            ChatUtil.info("Force requeue: sending command $queueCommand")
            ChatUtil.sendAsPlayer(queueCommand)
        } catch (e: Exception) {
            ChatUtil.error("Failed to send requeue command: ${e.message}")
        }
    }

    /**
     * Checks if a player is on the blacklist.
     *
     * Checks both the session blacklist (auto-added from losses) and
     * the config blacklist (manually configured).
     *
     * @param playerName The player name to check
     * @return true if the player is blacklisted, false otherwise
     */
    private fun isPlayerBlacklisted(playerName: String): Boolean {
        // Check session blacklist (auto-added players)
        if (sessionBlacklist.contains(playerName)) {
            return true
        }

        // Check config blacklist (manually added players)
        val configBlacklist = CatDueller.config?.blacklistedPlayers ?: ""
        return configBlacklist.split(",").map { it.trim() }.filter { it.isNotBlank() }.contains(playerName)
    }

    /**
     * Adds a player to the session blacklist.
     *
     * Session blacklist is temporary and cleared on manual toggle off.
     * Limited to 100 entries to prevent memory issues.
     *
     * @param playerName The player name to add
     */
    private fun addPlayerToSessionBlacklist(playerName: String) {
        if (playerName.isBlank()) return

        if (!sessionBlacklist.contains(playerName)) {
            sessionBlacklist.add(playerName)

            // Limit session blacklist size to prevent memory leak
            if (sessionBlacklist.size > 100) {
                val oldestPlayer = sessionBlacklist.first()
                sessionBlacklist.remove(oldestPlayer)
                ChatUtil.info("Removed oldest player from session blacklist: $oldestPlayer")
            }

            ChatUtil.info("Added $playerName to session blacklist (${sessionBlacklist.size} players)")
        }
    }

    /**
     * Simulates a key press using Java's Robot class.
     *
     * Used for triggering external mod keybinds (e.g., blatant mode, clipping).
     *
     * @param keyName The name of the key to press (e.g., "F8", "Q")
     */
    protected fun simulateKeyPress(keyName: String) {
        try {
            val robot = Robot()
            val keyCode = getKeyCodeFromName(keyName)

            if (keyCode != -1) {
                robot.keyPress(keyCode)
                TimerUtil.setTimeout({
                    robot.keyRelease(keyCode)
                }, 50) // Hold key for 50ms

                ChatUtil.info("Pressed key: $keyName")
            } else {
                ChatUtil.error("Unknown key name: $keyName")
            }
        } catch (e: Exception) {
            ChatUtil.error("Failed to simulate key press: ${e.message}")
        }
    }

    /**
     * Converts a key name string to its corresponding KeyEvent code.
     *
     * @param keyName The name of the key (e.g., "F1", "SPACE", "Q")
     * @return The KeyEvent constant, or -1 if not found
     */
    protected fun getKeyCodeFromName(keyName: String): Int {
        return when (keyName.uppercase()) {
            "F1" -> KeyEvent.VK_F1
            "F2" -> KeyEvent.VK_F2
            "F3" -> KeyEvent.VK_F3
            "F4" -> KeyEvent.VK_F4
            "F5" -> KeyEvent.VK_F5
            "F6" -> KeyEvent.VK_F6
            "F7" -> KeyEvent.VK_F7
            "F8" -> KeyEvent.VK_F8
            "F9" -> KeyEvent.VK_F9
            "F10" -> KeyEvent.VK_F10
            "F11" -> KeyEvent.VK_F11
            "F12" -> KeyEvent.VK_F12
            "A" -> KeyEvent.VK_A
            "B" -> KeyEvent.VK_B
            "C" -> KeyEvent.VK_C
            "D" -> KeyEvent.VK_D
            "E" -> KeyEvent.VK_E
            "F" -> KeyEvent.VK_F
            "G" -> KeyEvent.VK_G
            "H" -> KeyEvent.VK_H
            "I" -> KeyEvent.VK_I
            "J" -> KeyEvent.VK_J
            "K" -> KeyEvent.VK_K
            "L" -> KeyEvent.VK_L
            "M" -> KeyEvent.VK_M
            "N" -> KeyEvent.VK_N
            "O" -> KeyEvent.VK_O
            "P" -> KeyEvent.VK_P
            "Q" -> KeyEvent.VK_Q
            "R" -> KeyEvent.VK_R
            "S" -> KeyEvent.VK_S
            "T" -> KeyEvent.VK_T
            "U" -> KeyEvent.VK_U
            "V" -> KeyEvent.VK_V
            "W" -> KeyEvent.VK_W
            "X" -> KeyEvent.VK_X
            "Y" -> KeyEvent.VK_Y
            "Z" -> KeyEvent.VK_Z
            "SPACE" -> KeyEvent.VK_SPACE
            "ENTER" -> KeyEvent.VK_ENTER
            "TAB" -> KeyEvent.VK_TAB
            "SHIFT" -> KeyEvent.VK_SHIFT
            "CTRL" -> KeyEvent.VK_CONTROL
            "ALT" -> KeyEvent.VK_ALT
            else -> -1
        }
    }

    /**
     * Handles guild chat messages for dodge functionality.
     *
     * Parses guild messages containing server IDs and compares with
     * the current server. If matched, requeues to avoid the game.
     *
     * @param message The chat message to process
     */
    private fun handleGuildDodge(message: String) {
        try {
            // Check for guild message pattern: "Guild > [VIP] dystopiankyo: m182BH ve9F4OqlN"
            val guildPattern = Regex("Guild > (?:\\[[^]]+] )?([^:]+): (.+)")
            val guildMatch = guildPattern.find(message)

            if (guildMatch != null) {
                val senderName = guildMatch.groupValues[1].trim()
                val fullMessage = guildMatch.groupValues[2].trim()

                // Skip if the message is from ourselves
                val playerName = mc.thePlayer?.name
                val playerDisplayName = mc.thePlayer?.displayNameString

                // Clean sender name by removing color codes and rank prefixes like [VIP], [MVP+], etc.
                var cleanSenderName = StringUtils.stripControlCodes(senderName).trim()
                // Remove rank prefixes like [VIP], [MVP+], [MVP++], etc.
                cleanSenderName = cleanSenderName.replace(Regex("\\[[^]]+]\\s*"), "").trim()

                if ((playerName != null && cleanSenderName.equals(playerName, ignoreCase = true)) ||
                    (playerDisplayName != null && cleanSenderName.equals(playerDisplayName, ignoreCase = true))
                ) {
                    ChatUtil.info("Skipping guild dodge - message from self")
                    return
                }

                // Extract only the first word (before any space)
                val serverFromGuild = fullMessage.split(" ")[0].trim()

                // Only process messages that start with "mini" or "m"
                if (serverFromGuild.isNotBlank() && (serverFromGuild.lowercase()
                        .startsWith("mini") || serverFromGuild.lowercase().startsWith("m"))
                ) {
                    // Convert guild message from "minixxx" to "mxxx" format for consistent matching
                    val normalizedServerFromGuild = if (serverFromGuild.lowercase().startsWith("mini")) {
                        "m" + serverFromGuild.substring(4) // Remove "mini" and add "m"
                    } else {
                        serverFromGuild
                    }

                    ChatUtil.info("Guild server info received: $serverFromGuild (normalized: $normalizedServerFromGuild)")

                    // Use currentServer from scoreboard instead of /whereami command
                    if (currentServer != null) {
                        // Normalize current server for comparison
                        val normalizedCurrentServer = if (currentServer!!.lowercase().startsWith("mini")) {
                            "m" + currentServer!!.substring(4) // Remove "mini" and add "m"
                        } else {
                            currentServer!!
                        }

                        ChatUtil.info("Current server: $currentServer (normalized: $normalizedCurrentServer), Expected: $serverFromGuild (normalized: $normalizedServerFromGuild)")

                        if (normalizedCurrentServer.equals(normalizedServerFromGuild, ignoreCase = true)) {
                            ChatUtil.info("Server match confirmed! Checking game state...")

                            // Check if we're within 1 second after beforeStart() was called
                            val currentTime = System.currentTimeMillis()
                            val timeSinceBeforeStart =
                                if (beforeStartTime > 0) currentTime - beforeStartTime else Long.MAX_VALUE
                            val isNearBeforeStart = beforeStartTime > 0 && timeSinceBeforeStart <= 1500

                            // Check if not currently playing, not in lobby, not near beforeStart
                            if (StateManager.state != StateManager.States.PLAYING &&
                                StateManager.state != StateManager.States.LOBBY &&
                                !isNearBeforeStart
                            ) {
                                TimerUtil.setTimeout({
                                    ChatUtil.sendAsPlayer(queueCommand)
                                    ChatUtil.info("dodging guild...")
                                }, RandomUtil.randomIntInRange(200, 500))
                            } else if (StateManager.state == StateManager.States.LOBBY) {
                                ChatUtil.info("In lobby - skipping dodge")
                            } else if (isNearBeforeStart) {
                                ChatUtil.info("Near beforeStart (${timeSinceBeforeStart}ms) - skipping dodge")
                            } else {
                                ChatUtil.info("Already in game - skipping join command")
                            }
                        } else {
                            ChatUtil.info("Server mismatch - no dodge needed")
                        }
                    } else {
                        ChatUtil.info("Current server not available from scoreboard yet")
                    }
                }
                return
            }

        } catch (e: Exception) {
            ChatUtil.error("Error in guild dodge: ${e.message}")
        }
    }

    /**
     * Handles direct message chat for dodge functionality.
     *
     * Parses DMs containing server IDs and compares with the current server.
     * If matched, requeues to avoid the game. Also handles auto-reply.
     *
     * @param message The chat message to process
     */
    private fun handleDMDodge(message: String) {
        try {
            // Check for DM message pattern: "From watchfulA2onges: m182BH ve9F4OqlN"
            val dmPattern = Regex("From ([^:]+): (.+)")
            val dmMatch = dmPattern.find(message)

            if (dmMatch != null) {
                val senderName = dmMatch.groupValues[1].trim()
                val fullMessage = dmMatch.groupValues[2].trim()

                // Skip if the message is from ourselves (shouldn't happen with "From" pattern, but safety check)
                val playerName = mc.thePlayer?.name
                val playerDisplayName = mc.thePlayer?.displayNameString

                // Clean sender name by removing color codes
                val cleanSenderName = StringUtils.stripControlCodes(senderName).trim()

                if ((playerName != null && cleanSenderName.equals(playerName, ignoreCase = true)) ||
                    (playerDisplayName != null && cleanSenderName.equals(playerDisplayName, ignoreCase = true))
                ) {
                    ChatUtil.info("Skipping DM dodge - message from self")
                    return
                }

                // Extract only the first word (before any space)
                val serverFromDM = fullMessage.split(" ")[0].trim()

                // Auto reply to non-server ID DMs
                if (CatDueller.config?.autoReplyDM == true && fullMessage.isNotBlank() &&
                    !fullMessage.lowercase().startsWith("mini") && !fullMessage.lowercase().startsWith("m")
                ) {
                    ChatUtil.info("Auto replying to DM from $cleanSenderName: '$fullMessage'")

                    // Send "?" after 3 seconds
                    TimerUtil.setTimeout({
                        ChatUtil.sendAsPlayer("/r ?")
                    }, 3000)

                    // Send "ok" after 4 seconds (3 + 1)
                    TimerUtil.setTimeout({
                        ChatUtil.sendAsPlayer("/r ok")
                    }, 4000)
                }

                // Only process messages that start with "mini" or "m"
                if (serverFromDM.isNotBlank() && (serverFromDM.lowercase()
                        .startsWith("mini") || serverFromDM.lowercase().startsWith("m"))
                ) {
                    // Convert DM message from "minixxx" to "mxxx" format for consistent matching
                    val normalizedServerFromDM = if (serverFromDM.lowercase().startsWith("mini")) {
                        "m" + serverFromDM.substring(4) // Remove "mini" and add "m"
                    } else {
                        serverFromDM
                    }

                    ChatUtil.info("DM server info received from $cleanSenderName: $serverFromDM (normalized: $normalizedServerFromDM)")

                    // Use currentServer from scoreboard instead of /whereami command
                    if (currentServer != null) {
                        // Normalize current server for comparison
                        val normalizedCurrentServer = if (currentServer!!.lowercase().startsWith("mini")) {
                            "m" + currentServer!!.substring(4) // Remove "mini" and add "m"
                        } else {
                            currentServer!!
                        }

                        ChatUtil.info("Current server: $currentServer (normalized: $normalizedCurrentServer), Expected: $serverFromDM (normalized: $normalizedServerFromDM)")

                        if (normalizedCurrentServer.equals(normalizedServerFromDM, ignoreCase = true)) {
                            ChatUtil.info("Server match confirmed! Checking game state...")

                            // Check if we're within 1 second after beforeStart() was called
                            val currentTime = System.currentTimeMillis()
                            val timeSinceBeforeStart =
                                if (beforeStartTime > 0) currentTime - beforeStartTime else Long.MAX_VALUE
                            val isNearBeforeStart = beforeStartTime > 0 && timeSinceBeforeStart <= 1500

                            // Check if not currently playing, not in lobby, not near beforeStart
                            if (StateManager.state != StateManager.States.PLAYING &&
                                StateManager.state != StateManager.States.LOBBY &&
                                !isNearBeforeStart
                            ) {
                                TimerUtil.setTimeout({
                                    ChatUtil.sendAsPlayer(queueCommand)
                                    ChatUtil.info("dodging DM...")
                                }, RandomUtil.randomIntInRange(200, 500))
                            } else if (StateManager.state == StateManager.States.LOBBY) {
                                ChatUtil.info("In lobby - skipping dodge")
                            } else if (isNearBeforeStart) {
                                ChatUtil.info("Near beforeStart (${timeSinceBeforeStart}ms) - skipping dodge")
                            } else {
                                ChatUtil.info("Already in game - skipping join command")
                            }
                        } else {
                            ChatUtil.info("Server mismatch - no dodge needed")
                        }
                    } else {
                        ChatUtil.info("Current server not available from scoreboard yet")
                    }
                }
                return
            }

        } catch (e: Exception) {
            ChatUtil.error("Error in DM dodge: ${e.message}")
        }
    }

    /**
     * Handles the game start event.
     *
     * Sends the start message, begins opponent entity polling,
     * and calls [onGameStart] for subclass handling.
     */
    private fun gameStart() {
        beforeStartTime = 0L

        if (toggled()) {
            if (CatDueller.config?.sendStartMessage == true) {
                TimerUtil.setTimeout(fun() {
                    val baseMessage = CatDueller.config?.startMessage ?: "glhf!"
                    val randomSuffix = RandomUtil.randomString(6, useNumbers = true, useLetters = true)
                    val messageWithRandom = "$baseMessage $randomSuffix"
                    ChatUtil.sendAsPlayer("/ac $messageWithRandom")
                }, CatDueller.config?.startMessageDelay ?: 100)
            }

            val quickRefreshTimer = TimerUtil.setInterval(this::bakery, 200, 50)
            TimerUtil.setTimeout(fun() {
                quickRefreshTimer?.cancel()
                opponentTimer = TimerUtil.setInterval(this::bakery, 0, 500)
            }, quickRefresh)

            onGameStart()
        }
    }

    private fun gameEnd() {
        if (toggled()) {
            // Check if we're in big break time BEFORE calling onGameEnd()
            // This prevents any requeue logic from being triggered
            if (isInBigBreakTime()) {
                ChatUtil.info("Game ended during big break time - entering big break")

                // Clear all movements before entering big break
                Movement.clearAll()
                Mouse.stopLeftAC()
                Mouse.stopHoldLeftClick()
                Mouse.stopHoldRightClick()
                Combat.stopRandomStrafe()

                // Prevent force requeue when entering big break
                preventForceRequeue = true
                gameEndTime = 0L  // Reset to prevent force requeue
                forceRequeueScheduled = false

                TimerUtil.setTimeout(fun() {
                    ChatUtil.sendAsPlayer("/l duels")
                    TimerUtil.setTimeout(fun() {
                        enterBigBreak()
                    }, RandomUtil.randomIntInRange(2300, 5000))
                }, RandomUtil.randomIntInRange(900, 1700))
                return
            }

            onGameEnd()
            resetVars()

            // Game end celebration: sprint + forward + jump + random strafe for 1 second
            // Execute after onGameEnd() and resetVars() to avoid being cleared
            ChatUtil.info("Game ended - performing game end movement")

            // First, clear all existing movements to ensure clean state
            Movement.clearAll()

            // Start game end view rotation: pitch to 0 (level), yaw random ±45 degrees
            Mouse.startGameEndViewRotation()

            // Small delay to ensure clearing is complete, then start celebration
            TimerUtil.setTimeout({
                Movement.startSprinting()
                Movement.startForward()
                Movement.singleJump(150)

                // Add random strafe (left or right)
                if (RandomUtil.randomBool()) {
                    Movement.startLeft()
                    ChatUtil.info("Celebration: strafing left")
                } else {
                    Movement.startRight()
                    ChatUtil.info("Celebration: strafing right")
                }
            }, 100) // 100ms delay to ensure clearing is complete

            // Stop celebration after 1 second (plus the initial delay)
            TimerUtil.setTimeout({
                Movement.stopSprinting()
                Movement.stopForward()
                Movement.stopLeft()
                Movement.stopRight()
                // Stop view rotation after celebration
                Mouse.stopGameEndViewRotation()
            }, 1000) // 1000ms celebration + 100ms initial delay

            if (CatDueller.config?.sendAutoGG == true) {
                TimerUtil.setTimeout(fun() {
                    ChatUtil.sendAsPlayer("/ac " + (CatDueller.config?.ggMessage ?: "gg"))
                }, CatDueller.config?.ggDelay ?: 100)
            }

            // Wait 300ms for WINNER message processing, then check ping and start requeue
            TimerUtil.setTimeout({
                checkInternetStabilityAndRequeue()
            }, 300) // Wait 300ms for WINNER message processing to complete
        }
    }

    private fun bakery() {
        if (StateManager.state == StateManager.States.PLAYING) {
            val entity = EntityUtil.getOpponentEntity()
            if (entity != null) {
                opponent = entity
                lastOpponentName = entity.displayNameString

                // Check if opponent is nicked using UUID pattern
                if (!calledFoundOpponent) {
                    val entityUUID = entity.uniqueID
                    if (entityUUID != null) {
                        isOpponentNicked = UUIDUtil.isNickedUUID(entityUUID)
                        UUIDUtil.getUUIDStatus(entityUUID)
                    } else {
                        isOpponentNicked = false
                    }
                }

                if (!calledFoundOpponent) {
                    calledFoundOpponent = true
                    onFoundOpponent()
                }
            }
        }
    }


    private fun joinGame(applyLosingDelay: Boolean = false) {
        if (toggled() && StateManager.state != StateManager.States.PLAYING && !StateManager.gameFull) {

            // Calculate additional delay if we lost and the feature is enabled
            var additionalDelay = 0
            if (applyLosingDelay && lastGameWasLoss && CatDueller.config?.delayRequeueAfterLosing == true) {
                additionalDelay = (CatDueller.config?.losingRequeueDelay ?: 5) * 1000
                ChatUtil.info("joinGame: Adding ${additionalDelay}ms delay after losing")
            }

            if (StateManager.state == StateManager.States.GAME) {
                val paperRequeueEnabled = CatDueller.config?.paperRequeue == true

                if (paperRequeueEnabled) {
                    // Paper requeue is enabled, try to find paper with retries
                    tryPaperRequeue(additionalDelay)
                } else {
                    // Paper requeue disabled, use command requeue
                    TimerUtil.setTimeout({
                        ChatUtil.info("Using command requeue: $queueCommand")
                        ChatUtil.sendAsPlayer(queueCommand)
                    }, RandomUtil.randomIntInRange(100, 300) + additionalDelay)
                }
            } else {
                TimerUtil.setTimeout(fun() {
                    ChatUtil.info("Using command requeue: $queueCommand")
                    ChatUtil.sendAsPlayer(queueCommand)
                }, RandomUtil.randomIntInRange(100, 300) + additionalDelay)
            }
        }
    }


    private fun disconnect() {
        if (mc.theWorld != null) {
            mc.addScheduledTask(fun() {
                mc.theWorld.sendQuittingDisconnectingPacket()
                mc.loadWorld(null)
                mc.displayGuiScreen(GuiMultiplayer(GuiMainMenu()))
            })
        }
    }

    private fun reconnect() {
        if (mc.theWorld == null) {
            if (mc.currentScreen is GuiMultiplayer) {
                mc.addScheduledTask(fun() {
                    println("Reconnecting...")
                    FMLClientHandler.instance().setupServerList()
                    FMLClientHandler.instance()
                        .connectToServer(mc.currentScreen, ServerData("hypixel", "mc.hypixel.net", false))
                })
            } else {
                if (mc.currentScreen !is GuiConnecting) {
                    mc.addScheduledTask(fun() {
                        println("Attempting to show new multiplayer screen...")
                        mc.displayGuiScreen(GuiMultiplayer(GuiMainMenu()))
                        reconnect()
                    })
                }
            }
        }
    }

    class PacketReader(private val container: BotBase) : SimpleChannelInboundHandler<Packet<*>>(false) {

        override fun channelRead0(ctx: ChannelHandlerContext?, msg: Packet<*>?) {
            if (msg != null) {
                container.onPacket(msg)
            }
            ctx?.fireChannelRead(msg)
        }

    }

    private fun registerPacketListener() {
        val pipeline = mc.thePlayer?.sendQueue?.networkManager?.channel()?.pipeline()
        if (pipeline != null && pipeline.get("${getName()}_packet_handler") == null && pipeline.get("packet_handler") != null) {
            pipeline.addBefore(
                "packet_handler",
                "${getName()}_packet_handler",
                PacketReader(this)
            )
            println("Registered ${getName()}_packet_handler")
        }
    }

    /**
     * Start spamming disconnected players every 15 seconds
     */
    private fun startSpamming() {
        if (CatDueller.config?.botCrasherMode != true || CatDueller.config?.botCrasherSpamPlayers != true) return

        // Cancel existing timer if running
        spamTimer?.cancel()

        spamTimer = Timer()
        spamTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                if (CatDueller.config?.botCrasherMode == true && CatDueller.config?.botCrasherSpamPlayers == true) {
                    // Get manually configured players
                    val manualPlayers = CatDueller.config?.botCrasherTargetPlayers?.split(",")
                        ?.map { it.trim() }
                        ?.filter { it.isNotBlank() } ?: emptyList()

                    // Combine auto-detected disconnected players with manual players
                    val allTargetPlayers = (disconnectedPlayers + manualPlayers).distinct()

                    if (allTargetPlayers.isNotEmpty()) {
                        // Join all player names with spaces and invite them all at once
                        val allPlayers = allTargetPlayers.joinToString(" ")
                        ChatUtil.sendAsPlayer("/p $allPlayers")

                        TimerUtil.setTimeout({
                            ChatUtil.sendAsPlayer("/p disband")
                        }, 500)

                        ChatUtil.info("Bot Crasher Mode: Invited ${allTargetPlayers.size} players: $allPlayers")
                        ChatUtil.info("  - Auto-detected: ${disconnectedPlayers.size}, Manual: ${manualPlayers.size}")
                    }
                } else {
                    // Stop timer if mode is disabled
                    spamTimer?.cancel()
                    spamTimer = null
                }
            }
        }, 0, 15000) // Start immediately, repeat every 15 seconds
    }

    /**
     * Disable pause on lost focus by modifying options.txt
     */
    private fun disablePauseOnLostFocus() {
        try {
            val minecraftDir = mc.mcDataDir
            val optionsFile = File(minecraftDir, "options.txt")

            if (!optionsFile.exists()) {
                optionsFile.writeText("pauseOnLostFocus:false\n")
                return
            }

            val lines = optionsFile.readLines().toMutableList()
            var foundPauseOption = false

            // Look for existing pauseOnLostFocus setting
            for (i in lines.indices) {
                if (lines[i].startsWith("pauseOnLostFocus:")) {
                    if (lines[i] != "pauseOnLostFocus:false") {
                        lines[i] = "pauseOnLostFocus:false"
                        foundPauseOption = true
                    } else {
                        foundPauseOption = true
                    }
                    break
                }
            }

            // If pauseOnLostFocus setting not found, add it
            if (!foundPauseOption) {
                lines.add("pauseOnLostFocus:false")
            }

            // Write back to file
            if (!foundPauseOption || lines.any { it.startsWith("pauseOnLostFocus:true") }) {
                optionsFile.writeText(lines.joinToString("\n") + "\n")
            }

        } catch (e: Exception) {
            ChatUtil.error("Failed to modify options.txt: ${e.message}")
        }
    }

    /**
     * Generate randomized timing values with variance when bot starts
     */
    private fun generateRandomizedTimings() {
        try {
            // Check if config is available
            val config = CatDueller.config
            if (config == null) {
                ChatUtil.info("Config not available, using default timing values")
                actualDisconnectMinutes = 0
                actualReconnectWaitMinutes = 30
                return
            }

            // Generate dynamic break disconnect time with variance (percentage-based)
            val baseDisconnectMinutes = config.disconnectAfterMinutes
            val dynamicVariancePercent = config.dynamicBreakVariance

            if (baseDisconnectMinutes > 0) {
                // Calculate variance in minutes based on percentage
                val varianceMinutes = (baseDisconnectMinutes * dynamicVariancePercent / 100.0).toInt()
                val minTime = (baseDisconnectMinutes - varianceMinutes).coerceAtLeast(1)
                val maxTime = baseDisconnectMinutes + varianceMinutes
                actualDisconnectMinutes = RandomUtil.randomIntInRange(minTime, maxTime)
                ChatUtil.info("Dynamic break: Will disconnect after $actualDisconnectMinutes minutes (base: $baseDisconnectMinutes ± $dynamicVariancePercent% = ±${varianceMinutes}min)")
            } else {
                actualDisconnectMinutes = 0
            }

            // Generate dynamic break wait time with variance (percentage-based)
            val baseWaitMinutes = config.reconnectWaitMinutes
            val waitVarianceMinutes = (baseWaitMinutes * dynamicVariancePercent / 100.0).toInt()
            val minWaitTime = (baseWaitMinutes - waitVarianceMinutes).coerceAtLeast(1)
            val maxWaitTime = baseWaitMinutes + waitVarianceMinutes
            actualReconnectWaitMinutes = RandomUtil.randomIntInRange(minWaitTime, maxWaitTime)
            if (config.autoReconnectAfterDisconnect) {
                ChatUtil.info("Dynamic break: Will wait $actualReconnectWaitMinutes minutes before reconnect (base: $baseWaitMinutes ± $dynamicVariancePercent% = ±${waitVarianceMinutes}min)")
            }
            // Big break timing (no variance)
            if (config.bigBreakEnabled) {
                ChatUtil.info("Big break: Will break from ${config.bigBreakStartHour}:00 to ${config.bigBreakEndHour}:00")
            }
        } catch (e: Exception) {
            ChatUtil.error("Error generating randomized timings: ${e.message}")
            // Set safe default values
            actualDisconnectMinutes = 0
            actualReconnectWaitMinutes = 30
        }
    }

    /**
     * Check if current time is within big break period
     */
    private fun isInBigBreakTime(): Boolean {
        try {
            if (CatDueller.config?.bigBreakEnabled != true) return false

            val calendar = Calendar.getInstance()
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)

            val startHour = CatDueller.config?.bigBreakStartHour ?: 13
            val endHour = CatDueller.config?.bigBreakEndHour ?: 17

            return if (startHour <= endHour) {
                // Normal case: start to end same day
                currentHour in startHour until endHour
            } else {
                // Overnight case: start late, end early next day
                currentHour !in endHour until startHour
            }
        } catch (e: Exception) {
            ChatUtil.error("Error checking big break time: ${e.message}")
            return false
        }
    }

    /**
     * Get minutes until big break ends
     */
    private fun getMinutesUntilBigBreakEnds(): Int {
        try {
            val calendar = Calendar.getInstance()
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
            val currentMinute = calendar.get(Calendar.MINUTE)

            val startHour = CatDueller.config?.bigBreakStartHour ?: 13
            val endHour = CatDueller.config?.bigBreakEndHour ?: 17

            return if (startHour <= endHour) {
                // Normal case: calculate minutes until end time
                val minutesUntilEnd = (endHour - currentHour) * 60 - currentMinute
                if (minutesUntilEnd > 0) minutesUntilEnd else 0
            } else {
                // Overnight case
                if (currentHour >= startHour) {
                    // After start time, calculate until next day's end time
                    val minutesUntilMidnight = (24 - currentHour) * 60 - currentMinute
                    minutesUntilMidnight + endHour * 60
                } else {
                    // Before end time same day
                    val minutesUntilEnd = (endHour - currentHour) * 60 - currentMinute
                    if (minutesUntilEnd > 0) minutesUntilEnd else 0
                }
            }
        } catch (e: Exception) {
            ChatUtil.error("Error calculating minutes until big break ends: ${e.message}")
            return 60 // Return a safe default value
        }
    }


    /**
     * Check if dynamic break would overlap with big break
     */
    private fun wouldDynamicBreakOverlapWithBigBreak(): Boolean {
        if (CatDueller.config?.bigBreakEnabled != true) return false

        val currentTime = System.currentTimeMillis()
        val dynamicBreakEndTime = currentTime + (actualReconnectWaitMinutes * 60 * 1000)

        // Check if we would still be in dynamic break when big break starts
        val startHour = CatDueller.config?.bigBreakStartHour ?: 13
        val todayBigBreakStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, startHour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If big break start time has passed today, check tomorrow
        if (todayBigBreakStart.timeInMillis <= currentTime) {
            todayBigBreakStart.add(Calendar.DAY_OF_MONTH, 1)
        }

        val nextBigBreakStart = todayBigBreakStart.timeInMillis

        // Check if dynamic break would still be active when next big break starts
        val wouldOverlap = dynamicBreakEndTime > nextBigBreakStart

        if (wouldOverlap) {
            ChatUtil.info("Dynamic break overlap detected:")
            ChatUtil.info(
                "  Dynamic break would end at: ${
                    SimpleDateFormat("HH:mm").format(Date(dynamicBreakEndTime))
                }"
            )
            ChatUtil.info(
                "  Next big break starts at: ${
                    SimpleDateFormat("HH:mm").format(Date(nextBigBreakStart))
                }"
            )
        }

        return wouldOverlap
    }

    /**
     * Cancel dynamic break if it would overlap with big break
     */
    private fun cancelDynamicBreakIfOverlapping() {
        if (CatDueller.config?.bigBreakEnabled != true) return

        val currentTime = System.currentTimeMillis()
        val minutesUntilBigBreakEnd = getMinutesUntilBigBreakEnds()
        val bigBreakEndTime = currentTime + (minutesUntilBigBreakEnd * 60 * 1000)

        // Check if dynamic break would still be active when big break ends
        if (actualDisconnectMinutes > 0) {
            val dynamicBreakStartTime = botStartTime + (actualDisconnectMinutes * 60 * 1000)
            val dynamicBreakEndTime = dynamicBreakStartTime + (actualReconnectWaitMinutes * 60 * 1000)

            // Check for overlap: dynamic break end time is after big break start (now) 
            // and dynamic break start time is before big break end
            if (dynamicBreakEndTime > currentTime && dynamicBreakStartTime < bigBreakEndTime) {
                ChatUtil.info("Dynamic break would overlap with big break - cancelling dynamic break")
                ChatUtil.info("Dynamic break: ${actualDisconnectMinutes}min + ${actualReconnectWaitMinutes}min wait")
                ChatUtil.info("Big break: ${minutesUntilBigBreakEnd}min duration")

                // Cancel dynamic break by resetting its timing
                actualDisconnectMinutes = 0
                actualReconnectWaitMinutes = 30 // Reset to default

                ChatUtil.info("Dynamic break cancelled - big break takes priority")
            }
        }
    }

    /**
     * Enter big break period - uses absolute time checking instead of timer
     */
    private fun enterBigBreak() {
        val minutesUntilEnd = getMinutesUntilBigBreakEnds()

        ChatUtil.info("Entering big break time! Break will end in $minutesUntilEnd minutes")

        // Cancel any existing dynamic break if it would overlap with big break
        cancelDynamicBreakIfOverlapping()

        // Send webhook notification
        if (CatDueller.config?.sendWebhookMessages == true && !CatDueller.config?.webhookURL.isNullOrBlank()) {
            val author = WebhookUtil.buildAuthor(
                "Cat Dueller - ${getName()}",
                "https://cdn.discordapp.com/icons/1359887726157238532/cbbde7905d56603d13d2a7a9e4d545be.png?size=1024"
            )
            val thumbnail =
                WebhookUtil.buildThumbnail("https://cdn.discordapp.com/icons/1359887726157238532/cbbde7905d56603d13d2a7a9e4d545be.png?size=1024")

            // Calculate end time in seconds for Discord timestamp
            val endTimeSeconds = ((System.currentTimeMillis() + (minutesUntilEnd * 60 * 1000)) / 1000).toInt()

            WebhookUtil.sendEmbed(
                CatDueller.config?.webhookURL!!,
                WebhookUtil.buildEmbed(
                    ":sleeping: Big Break Started",
                    "Entering big break time. Bot will disconnect and automatically reconnect <t:${endTimeSeconds}:R>",
                    JsonArray(),
                    JsonObject(),
                    author,
                    thumbnail,
                    0xffa30f
                )
            )
        }

        // Disable bot and disconnect (like dynamic break)
        if (toggled()) {
            toggle(false) // Automatic toggle for big break disable
        }

        // Set absolute big break end time instead of using timer
        bigBreakReconnectTime = System.currentTimeMillis() + (minutesUntilEnd * 60 * 1000L)

        ChatUtil.info(
            "Big break will end at: ${
                SimpleDateFormat("HH:mm:ss").format(Date(bigBreakReconnectTime))
            }"
        )

        // Use same disconnect approach as dynamic break
        ChatUtil.info("Big break: Disconnecting like dynamic break...")

        // Prevent force requeue when preparing to disconnect
        preventForceRequeue = true
        gameEndTime = 0L  // Reset to prevent force requeue
        forceRequeueScheduled = false

        TimerUtil.setTimeout(fun() {
            ChatUtil.sendAsPlayer("/l duels")
            TimerUtil.setTimeout(fun() {
                // Disconnect immediately like dynamic break
                ChatUtil.info("Big break: Disconnecting now...")
                disconnect()
            }, RandomUtil.randomIntInRange(2300, 5000))
        }, RandomUtil.randomIntInRange(900, 1700))
    }


    /**
     * Format opponent name for webhook display using saved values
     * Used when resetVars() has already been called
     */
    private fun formatOpponentNameForWebhookWithSavedValues(
        opponentName: String
    ): String {
        return "`$opponentName`"
    }


    /**
     * Get current server ping in milliseconds
     * Returns -1 if unable to determine ping
     */
    fun getServerPing(): Int {
        return try {
            val player = mc.thePlayer ?: return -1
            val sendQueue = player.sendQueue ?: return -1

            // Method 1: Try to get ping from player info
            val playerInfo = sendQueue.getPlayerInfo(player.uniqueID)
            if (playerInfo != null) {
                return playerInfo.responseTime
            }

            // Method 2: Try to get ping from network manager (less reliable)
            val networkManager = sendQueue.networkManager
            if (networkManager != null) {
                // This is a rough estimation based on network manager state
                return if (networkManager.isChannelOpen) 0 else -1
            }

            -1
        } catch (_: Exception) {
            -1
        }
    }

    /**
     * Get ping status as string for display
     */
    fun getPingStatus(): String {
        val ping = getServerPing()
        return when {
            ping < 0 -> "Unknown"
            ping < 50 -> "Excellent ($ping ms)"
            ping < 100 -> "Good ($ping ms)"
            ping < 150 -> "Fair ($ping ms)"
            ping < 250 -> "Poor ($ping ms)"
            else -> "Very Poor ($ping ms)"
        }
    }

    /**
     * Start lobby sit mode for dynamic break
     */
    private fun startLobbySitMode(waitMinutes: Int) {
        lobbySitActive = true
        lobbySitEndTime = System.currentTimeMillis() + (waitMinutes * 60 * 1000L)
        lobbySitPhase = 0  // Start with forward phase
        lobbySitPhaseStartTime = System.currentTimeMillis()

        ChatUtil.info("Lobby sit mode started - will repeat cycles for $waitMinutes minutes")

        // Start the continuous cycle
        startLobbySitCycle()
    }

    /**
     * Start a single lobby sit cycle (5s forward + 10s backward)
     */
    private fun startLobbySitCycle() {
        if (!lobbySitActive) return

        // Phase 1: Sprint + Forward + Jump for 5 seconds
        lobbySitPhase = 0
        lobbySitPhaseStartTime = System.currentTimeMillis()
        ChatUtil.info("Lobby sit cycle: Sprint + Forward + Jump for 5 seconds")

        Movement.startSprinting()
        Movement.startForward()

        // Schedule jump spam
        lobbySitJumpTimer = TimerUtil.setInterval({
            if (lobbySitActive && lobbySitPhase == 0) {
                Movement.singleJump(150)
            }
        }, 0, 500) // Jump every 500ms

        // Schedule phase transition after 5 seconds
        TimerUtil.setTimeout({
            if (lobbySitActive) {
                // Stop first phase
                Movement.stopForward()
                Movement.stopSprinting()
                lobbySitJumpTimer?.cancel()

                ChatUtil.info("Lobby sit cycle: Waiting 1 second between phases...")

                // Wait 1 second before starting backward phase
                TimerUtil.setTimeout({
                    if (lobbySitActive) {
                        // Start second phase: backward for 10 seconds
                        lobbySitPhase = 1
                        lobbySitPhaseStartTime = System.currentTimeMillis()
                        ChatUtil.info("Lobby sit cycle: Backward for 10 seconds")
                        Movement.startBackward()

                        // Schedule end of backward phase
                        TimerUtil.setTimeout({
                            if (lobbySitActive) {
                                Movement.stopBackward()
                                ChatUtil.info("Lobby sit cycle completed - waiting 1 second before next cycle...")

                                // Wait 1 second before starting next cycle
                                TimerUtil.setTimeout({
                                    if (lobbySitActive) {
                                        startLobbySitCycle()
                                    }
                                }, 1000) // 1 second wait before next cycle
                            }
                        }, 10000) // 10 seconds
                    }
                }, 1000) // 1 second wait between phases
            }
        }, 5000) // 5 seconds
    }

    /**
     * Check if lobby sit mode should end
     */
    private fun checkLobbySitMode() {
        if (lobbySitActive && System.currentTimeMillis() >= lobbySitEndTime) {
            lobbySitActive = false

            // Stop any ongoing movements and timers
            Movement.clearAll()
            lobbySitJumpTimer?.cancel()

            ChatUtil.info("Lobby sit mode ended - re-enabling bot")

            // Re-enable bot
            if (!toggled()) {
                toggle(false)  // Don't reset session start time
            }

            // Start normal operation
            TimerUtil.setTimeout({
                if (toggled()) {
                    joinGame(applyLosingDelay = true)
                }
            }, RandomUtil.randomIntInRange(2000, 4000))
        }
    }

    /**
     * Check internet stability and decide whether to requeue
     */
    private fun checkInternetStabilityAndRequeue() {
        if (CatDueller.config?.pauseWhenInternetUnstable != true) {
            // Feature disabled, proceed with normal requeue
            proceedWithNormalRequeue()
            return
        }

        val currentPing = getServerPing()
        val currentTime = System.currentTimeMillis()

        if (currentPing > 250) {
            // High ping detected
            if (!internetStabilityPaused) {
                internetStabilityPaused = true
                ChatUtil.info("High ping detected ($currentPing ms) - pausing requeue until connection stabilizes")
                ChatUtil.info("Waiting for ping to stay below 250ms for 1 minute before resuming...")

                // Send webhook notification for internet instability pause
                sendInternetStabilityWebhook(currentPing, true)

                startPingMonitoring()
            }
        } else {
            // Good ping
            if (internetStabilityPaused) {
                // We were paused, check if we should resume
                if (lastStablePingTime == 0L) {
                    // First time seeing good ping since pause
                    lastStablePingTime = currentTime
                    ChatUtil.info("Ping improved to $currentPing ms - monitoring stability...")
                }
                // Don't requeue yet, let the monitoring timer handle it
            } else {
                // Normal operation, good ping
                proceedWithNormalRequeue()
            }
        }
    }

    /**
     * Start continuous ping monitoring when paused
     */
    private fun startPingMonitoring() {
        pingCheckTimer?.cancel()
        pingCheckTimer = Timer()

        pingCheckTimer?.schedule(object : TimerTask() {
            override fun run() {
                if (!internetStabilityPaused) {
                    cancel()
                    return
                }

                val currentPing = getServerPing()
                val currentTime = System.currentTimeMillis()

                if (currentPing > 250) {
                    // Still high ping, reset stable time
                    lastStablePingTime = 0L
                    if (currentTime % 30000 < 5000) { // Log every 30 seconds
                        ChatUtil.info("Ping still high ($currentPing ms) - continuing to wait...")
                    }
                } else {
                    // Good ping
                    if (lastStablePingTime == 0L) {
                        // First time seeing good ping
                        lastStablePingTime = currentTime
                        ChatUtil.info("Ping improved to $currentPing ms - monitoring stability for 1 minute...")
                    } else {
                        // Check if we've had stable ping for 1 minute
                        val stableTime = currentTime - lastStablePingTime
                        if (stableTime >= 60000) { // 1 minute
                            // Connection is stable, resume requeuing
                            internetStabilityPaused = false
                            lastStablePingTime = 0L
                            ChatUtil.info("Connection stable for 1 minute (ping: $currentPing ms) - resuming requeue")

                            // Send webhook notification for internet stability resume
                            sendInternetStabilityWebhook(currentPing, false)

                            // Resume requeuing
                            proceedWithNormalRequeue()
                            cancel()
                        } else {
                            // Still monitoring
                            val remainingTime = (60000 - stableTime) / 1000
                            if (stableTime % 15000 < 5000) { // Log every 15 seconds
                                ChatUtil.info("Ping stable at $currentPing ms - ${remainingTime}s remaining...")
                            }
                        }
                    }
                }
            }
        }, 0, 5000) // Check every 5 seconds
    }

    /**
     * Try to use paper requeue with retries to wait for paper to load
     */
    private fun tryPaperRequeue(additionalDelay: Int = 0, attempt: Int = 1) {
        val maxAttempts = 3 // Try up to 3 times
        val baseDelay = if (attempt == 1) 200 else 400 // First attempt: 200ms, later attempts: 400ms

        TimerUtil.setTimeout({
            val hasPaper = Inventory.setInvItem("paper")

            if (hasPaper) {
                // Found paper, check if GUI is open
                if (isGuiOpen()) {
                    // Wait a bit for GUI to close, then try again
                    TimerUtil.setTimeout({
                        if (!isGuiOpen()) {
                            Mouse.rClick(RandomUtil.randomIntInRange(100, 300))
                        } else {
                            ChatUtil.info("GUI still open after waiting, using command requeue: $queueCommand")
                            ChatUtil.sendAsPlayer(queueCommand)
                        }
                    }, 300)
                } else {
                    // GUI is not open, use paper requeue
                    Mouse.rClick(RandomUtil.randomIntInRange(100, 300))
                }
            } else {
                // Paper not found
                if (attempt < maxAttempts) {
                    // Try again with longer delay
                    tryPaperRequeue(additionalDelay, attempt + 1)
                } else {
                    // Max attempts reached, fallback to command requeue
                    TimerUtil.setTimeout({
                        ChatUtil.info("Paper requeue failed after $maxAttempts attempts, using command requeue: $queueCommand")
                        ChatUtil.sendAsPlayer(queueCommand)
                    }, RandomUtil.randomIntInRange(100, 300))
                }
            }
        }, baseDelay + additionalDelay)
    }

    /**
     * Proceed with normal requeue logic
     */
    private fun proceedWithNormalRequeue() {
        if (CatDueller.config?.fastRequeue == true) {
            val fastDelay = RandomUtil.randomIntInRange(300, 500)
            TimerUtil.setTimeout({ joinGame(applyLosingDelay = true) }, fastDelay)
        } else {
            val delay = CatDueller.config?.autoRqDelay ?: 2000
            TimerUtil.setTimeout({ joinGame(applyLosingDelay = true) }, delay)
        }
    }

    /**
     * Send webhook notification for internet stability status
     */
    private fun sendInternetStabilityWebhook(ping: Int, isPaused: Boolean) {
        if (CatDueller.config?.sendWebhookMessages != true || CatDueller.config?.webhookURL.isNullOrBlank()) {
            return
        }

        try {
            val author = WebhookUtil.buildAuthor(
                "Cat Dueller - ${getName()}",
                "https://cdn.discordapp.com/icons/1359887726157238532/cbbde7905d56603d13d2a7a9e4d545be.png?size=1024"
            )
            val thumbnail =
                WebhookUtil.buildThumbnail("https://cdn.discordapp.com/icons/1359887726157238532/cbbde7905d56603d13d2a7a9e4d545be.png?size=1024")

            val title: String
            val description: String
            val color: Int

            if (isPaused) {
                title = ":warning: Internet Unstable - Paused"
                description =
                    "High ping detected ($ping ms). Requeuing paused until connection stabilizes.\n\nWaiting for ping to stay below 250ms for 1 minute before resuming."
                color = 0xffa500 // Orange
            } else {
                title = ":white_check_mark: Internet Stable - Resumed"
                description = "Connection stabilized ($ping ms). Requeuing resumed after 1 minute of stable connection."
                color = 0x00ff00 // Green
            }

            WebhookUtil.sendEmbed(
                CatDueller.config?.webhookURL!!,
                WebhookUtil.buildEmbed(
                    title,
                    description,
                    JsonArray(),
                    JsonObject(),
                    author,
                    thumbnail,
                    color
                )
            )
        } catch (e: Exception) {
            ChatUtil.error("Failed to send internet stability webhook: ${e.message}")
        }
    }

    /**
     * Check if movement should be cleared based on combo count
     * @param additionalConditions Additional conditions that must be false to clear movement
     * @return true if movement should be cleared
     */
    protected fun shouldClearMovementForCombo(vararg additionalConditions: Boolean): Boolean {
        // Clear movement if combo >= 3 and no additional blocking conditions
        return combo >= 3 && additionalConditions.none { it }
    }

    /**
     * Check if forward movement should be stopped based on combo and distance
     * @param distance Distance to opponent
     * @param blockingConditions Conditions that prevent stopping forward movement
     * @return true if forward movement should be stopped
     */
    protected fun shouldStopForwardForCombo(distance: Float, vararg blockingConditions: Boolean): Boolean {
        // Stop forward if combo >= 3 and distance < 2, unless blocked by conditions
        return combo >= 3 && distance < 2 && blockingConditions.none { it }
    }

    /**
     * Handle anti ragebait functionality
     * Responds to ": L" or ": l" messages with random L spam
     */
    private fun handleAntiRagebait(message: String) {
        // Check if message ends with ": L" or ": l" (case-sensitive, no trailing characters)
        if (message.endsWith(": L") || message.endsWith(": l")) {
            // Generate random number of L's between 3 and 10
            val lCount = RandomUtil.randomIntInRange(3, 10)
            val lSpam = "L".repeat(lCount)

            // Send the L spam with a small delay to avoid spam detection
            TimerUtil.setTimeout({
                ChatUtil.sendAsPlayer(lSpam)
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtil.combatInfo("Anti Ragebait: Responded to '$message' with $lCount L's")
                }
            }, RandomUtil.randomIntInRange(100, 300))
        }
    }
}
