package best.spaghetcodes.catdueller.bot

import best.spaghetcodes.catdueller.CatDueller
import best.spaghetcodes.catdueller.bot.player.*
import best.spaghetcodes.catdueller.core.KeyBindings
import best.spaghetcodes.catdueller.core.HWIDLock
import best.spaghetcodes.catdueller.utils.*
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiMainMenu
import net.minecraft.client.gui.GuiMultiplayer
import net.minecraft.client.gui.GuiIngameMenu
import net.minecraft.client.multiplayer.GuiConnecting
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.projectile.EntityArrow
import net.minecraft.network.Packet
import java.util.Timer

import net.minecraft.network.play.server.S12PacketEntityVelocity
import net.minecraft.network.play.server.S19PacketEntityStatus
import net.minecraft.network.play.server.S3EPacketTeams
import net.minecraft.network.play.server.S40PacketDisconnect
import net.minecraft.network.play.server.S45PacketTitle
import net.minecraft.scoreboard.ScorePlayerTeam
import net.minecraft.util.EnumChatFormatting
import net.minecraft.util.MathHelper
import net.minecraft.util.Vec3
import net.minecraft.util.MovingObjectPosition
import net.minecraft.util.AxisAlignedBB
import net.minecraftforge.client.event.ClientChatReceivedEvent
import net.minecraftforge.event.entity.EntityJoinWorldEvent
import net.minecraftforge.event.entity.player.AttackEntityEvent
import kotlin.math.pow
import net.minecraftforge.fml.client.FMLClientHandler
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientConnectedToServerEvent
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent
import net.minecraft.network.play.client.C01PacketChatMessage
import net.minecraft.network.play.server.S38PacketPlayerListItem
import net.minecraft.network.play.server.S38PacketPlayerListItem.Action

import java.util.Calendar
import java.io.File
import java.awt.Robot
import java.awt.event.KeyEvent

/**
 * Base class for all bots
 * @param queueCommand Command to join a new game
 * @param quickRefresh MS for which to quickly refresh opponent entity
 */
open class BotBase(val queueCommand: String, val quickRefresh: Int = 10000) {

    protected val mc = Minecraft.getMinecraft()

    private var toggled = false
    fun toggled() = toggled
    fun toggle(isManualToggle: Boolean = true) {
        // Check HWID authorization before allowing toggle
        if (!HWIDLock.isAuthorized()) {
            ChatUtils.error("HWID verification failed - bot cannot be enabled")
            ChatUtils.error("Your HWID: ${HWIDLock.getCurrentHWID()}")
            return
        }
        
        val wasToggled = toggled
        toggled = !toggled
        
        // If bot is disabled, stop all actions and cancel timers
        if (wasToggled && !toggled) {
            // Stop all mouse actions
            Mouse.resetAllStates()
            
            // Stop all movements
            Movement.clearAll()
            LobbyMovement.stop()
            Combat.stopRandomStrafe()
            
            TimeUtils.cancelAllTimers()
            
            // Cancel Bot Crasher Mode timers
            botCrasherTimer?.cancel()
            botCrasherTimer = null
            spamTimer?.cancel()
            spamTimer = null
            disconnectedPlayers.clear()
            
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
        } else if (!wasToggled && toggled) {
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
                ChatUtils.info("Session start time ${if (isManualToggle) "updated" else "initialized"}")
            } else {
                ChatUtils.info("Session start time preserved (automatic toggle)")
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
                    ChatUtils.info("Bot Crasher Mode: Found ${manualPlayers.size} manual target players, starting spam timer")
                    startSpamming()
                }
            }
        }
    }

    private var attackedID = -1

    private var statKeys: Map<String, String> = mapOf("wins" to "", "losses" to "", "ws" to "")

    private var currentWinstreak = 0  // Local tracking of win streak
    private var playerNick: String? = null  // Player nickname extracted from join messages


    private var playersSent: ArrayList<String> = arrayListOf()

    private var opponent: EntityPlayer? = null
    private var opponentTimer: Timer? = null
    private var calledFoundOpponent = false

    protected var combo = 0
    protected var opponentCombo = 0
    protected var ticksSinceHit = 0




    private var ticksSinceGameStart = 0

    private var lastOpponentName = ""
    private var lastOpponentNameWithRank = ""  // Store opponent name with rank from chat
    private var isOpponentNicked = false  // Store whether opponent is nicked

    private var calledGameEnd = false
    private var calledJoinGame = false
    private var lastGameWasLoss = false  // Track if the last game was a loss
    
    // Blink Tap variables
    private var lastDistanceToOpponent = 999f  // Track distance for blink tap trigger
    private var blinkTapTriggered = false  // Prevent multiple triggers
    
    // Force requeue mechanism
    private var gameEndTime = 0L  // time when game ended
    private var forceRequeueScheduled = false  // whether force requeue is scheduled
    private var forceRequeueAttempts = 0  // number of force requeue attempts made
    private var preventForceRequeue = false  // prevent force requeue when preparing to disconnect
    
    // Reconnect timer for unexpected disconnections
    private var reconnectTimer: Timer? = null
    
    // Bot runtime tracking (separate from session stats)
    private var botStartTime = 0L  // time when bot was started/resumed
    
    // Dynamic break timing with variance
    private var actualDisconnectMinutes = 0  // actual disconnect time with variance applied
    private var actualReconnectWaitMinutes = 30  // actual wait time with variance applied
    private var scheduledReconnectTime = 0L  // absolute time when we should reconnect (0 = not scheduled)
    private var isDynamicBreakReconnect = false  // track if scheduled reconnect is for dynamic break
    
    // Lobby Sit mode variables
    private var lobbySitActive = false  // whether lobby sit mode is active
    private var lobbySitEndTime = 0L  // when lobby sit mode should end
    private var lobbySitPhase = 0  // 0 = forward phase, 1 = backward phase
    private var lobbySitPhaseStartTime = 0L  // when current phase started
    private var lobbySitJumpTimer: Timer? = null  // timer for jump spam
    
    // Internet stability monitoring variables
    private var internetStabilityPaused = false  // whether requeuing is paused due to high ping
    private var lastStablePingTime = 0L  // last time ping was below 250ms
    private var pingCheckTimer: Timer? = null  // timer for continuous ping monitoring
    
    // Disconnect reason tracking
    private var lastDisconnectReason = "Unknown"  // Track the reason for last disconnect
    
    /**
     * Set the disconnect reason for webhook reporting
     */
    private fun setDisconnectReason(reason: String) {
        lastDisconnectReason = reason
        ChatUtils.info("Disconnect reason set: $reason")
    }
    

    
    // Scoreboard opponent tracking
    private var cachedOpponentName: String? = null  // Cached opponent name
    private var lastScoreboardCheck = 0L  // Last time scoreboard was checked
    private var winstreakChecked = false  // Track if winstreak has been checked this game
    
    // Current server tracking from scoreboard
    private var currentServer: String? = null  // Current server ID extracted from scoreboard
    
    /**
     * Get current server ID extracted from scoreboard
     */
    fun getCurrentServer(): String? = currentServer
    protected var blatantToggled = false  // Track if blatant mode was toggled for current opponent
    private var sessionBlacklist = mutableSetOf<String>()  // Session-only blacklist for auto-added players
    private var beforeStartTime = 0L  // Track when beforeStart() was called
    
    // Bot Crasher Mode variables
    private var gameStartTime = 0L  // Time when game started
    private var botCrasherTimer: Timer? = null  // Timer for bot crasher mode
    private var disconnectedPlayers = mutableListOf<String>()  // List of disconnected players to spam
    private var spamTimer: Timer? = null  // Timer for spamming disconnected players
    
    // Hit Select variables for being attacked state
    // Note: Now only uses KB reduction mode
    
    // Big Break variables - simplified
    private var bigBreakReconnectTime = 0L  // absolute time when big break ends (0 = not in big break)
    
    // Hit Select variables
    private var lastCombatTime = 0L     // Last time we attacked or were attacked
    
    // W-Tap variables
    private var tapping = false         // Whether W-Tap is currently active
    private var lastWTapTime = 0L       // Track last W-Tap time for 500ms cooldown

    // Rod retract timeout for immediate retraction on hit
    var rodRetractTimeout: Timer? = null
    // Track if rod was used defensively (due to opponent combo)
    var isDefensiveRod: Boolean = false
    // Track if opponent has used bow (to allow our bow usage)
    var opponentUsedBow: Boolean = false
    
    // Track opponent's arrow usage
    var opponentArrowsFired: Int = 0
    var opponentIsDrawingBow: Boolean = false
    private var lastOpponentBowCheck: Long = 0
    
    // Track opponent movement direction
    var opponentIsApproaching: Boolean = false
    var opponentIsRetreating: Boolean = false
    private var lastDistance: Float = 0f
    private var lastDistanceCheck: Long = 0
    private val distanceHistory = mutableListOf<Float>()
    
    // Track opponent's actual movement speed
    var opponentActualSpeed = 0.13f  // Default to sprinting speed (blocks per tick)
    private var lastOpponentSpeedPos: Vec3? = null
    
    // Track lateral movement (strafe direction)
    var opponentStrafeDirection: Int = 0  // -1=left, 0=none, 1=right (relative to opponent)
    var ourStrafeDirection: Int = 0       // -1=left, 0=none, 1=right (relative to us)
    var isCounterStrafing: Boolean = false // true if both moving in same relative direction
    private var lastOpponentPos: Vec3? = null
    private var lastOurPos: Vec3? = null
    private var lastStrafeCheck: Long = 0

    fun opponent() = opponent
    
    /**
     * Get counter strafe multiplier for projectile prediction
     * @return Multiplier to apply for counter strafe prediction (1.0 = no bonus)
     */
    fun getCounterStrafeMultiplier(): Float {
        val multiplier = CatDueller.config?.counterStrafeBonus ?: 1.5f
        return if (isCounterStrafing) multiplier else 1.0f
    }
    
    /**
     * Get detailed opponent information including precise position and rotation
     * @return Formatted string with opponent details, or null if no opponent
     */
    fun getOpponentDetailedInfo(): String? {
        val opponent = opponent() ?: return null
        
        return "Opponent: ${opponent.displayNameString}\n" +
               "Position: X=${String.format("%.3f", opponent.posX)}, Z=${String.format("%.3f", opponent.posZ)}, Y=${String.format("%.3f", opponent.posY)}\n" +
               "Rotation: Yaw=${String.format("%.1f", opponent.rotationYaw)}, Pitch=${String.format("%.1f", opponent.rotationPitch)}\n" +
               "Status: ${if (opponent.onGround) "OnGround" else "InAir"}, Health=${String.format("%.1f", opponent.health)}\n" +
               "Movement: ${if (opponent.isSneaking) "Sneaking" else "Standing"}, ${if (opponent.isSprinting) "Sprinting" else "Walking"}"
    }
    
    /**
     * Get opponent's exact coordinates for precise positioning
     * @return Triple of (x, z, distance) or null if no opponent
     */
    fun getOpponentCoordinates(): Triple<Double, Double, Double>? {
        val opponent = opponent() ?: return null
        val player = mc.thePlayer ?: return null
        
        val distance = kotlin.math.sqrt(
            (opponent.posX - player.posX).pow(2.0) + 
            (opponent.posZ - player.posZ).pow(2.0)
        )
        
        return Triple(opponent.posX, opponent.posZ, distance)
    }

    /********
     * Methods to override
     ********/

    open fun getName(): String {
        return "Base"
    }

    /**
     * Called when the bot attacks the opponent
     * Triggered by the damage sound, not the clientside attack event
     */
    protected open fun onAttack() {
        // Update combat time for hit select timeout logic
        lastCombatTime = System.currentTimeMillis()
    }

    /**
     * Called when the bot is attacked
     * Triggered by the damage sound, not the clientside attack event
     */
    protected open fun onAttacked() {
        // Update combat time for hit select timeout logic
        lastCombatTime = System.currentTimeMillis()
        
        // Record time when hit by opponent for "Hit Later In Trades" feature
        lastHitByOpponentTime = System.currentTimeMillis()
        
        if (CatDueller.config?.combatLogs == true && CatDueller.config?.hitLaterInTrades ?: 0 > 0) {
            ChatUtils.combatInfo("Hit Later In Trades: Recorded hit at ${lastHitByOpponentTime}")
        }
        
        // Mark that we've been hit for "Wait For First Hit" feature
        if (!hasBeenHitOnce) {
            hasBeenHitOnce = true
            if (waitingForFirstHit && CatDueller.config?.combatLogs == true) {
                ChatUtils.combatInfo("Wait For First Hit: Opponent attacked - can now attack back")
            }
            waitingForFirstHit = false
            crosshairOnOpponentTime = -1L
        }
    }

    /**
     * Called when the bot receives velocity (knockback)
     * Triggered by S12PacketEntityVelocity - most accurate timing for jump reset
     * @param motionX X velocity
     * @param motionY Y velocity  
     * @param motionZ Z velocity
     */
    protected open fun onVelocity(motionX: Int, motionY: Int, motionZ: Int) {
        // Base implementation does nothing - subclasses can override for jump reset
    }



    /**
     * Determine if should start attacking
     * Can be overridden by subclasses for additional checks
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
        if (!canAttackResult && CatDueller.config?.combatLogs == true) {

        }
        return canAttackResult
    }
    
    // Hit Select variables (simplified implementation)
    private var hitSelectAttackTime = -1L
    private var currentShouldAttack = false
    private var isKbReductionAttack = false  // Track if current attack is due to KB reduction
    private var lastHitByOpponentTime = -1L  // Track when we were last hit by opponent
    private var waitingForHitLaterDelay = false  // Track if we're waiting for hit later delay
    
    // Wait For First Hit variables
    private var waitingForFirstHit = false  // Track if we're waiting for opponent's first hit
    private var crosshairOnOpponentTime = -1L  // Track when crosshair first aimed at opponent
    private var hasBeenHitOnce = false  // Track if we've been hit at least once this game
    


    /**
     * Check if bot can swing - controls swing animation based on hit select and missed hits cancel rate
     */
    open fun canSwing(): Boolean {
        if (!toggled()) return false
        
        val player = mc.thePlayer ?: return false
        val opponent = opponent() ?: return false
        val distance = EntityUtils.getDistanceNoY(player, opponent)
        val maxAttackDistance = CatDueller.config?.maxDistanceAttack ?: 5
        
        // Basic distance check - never attack beyond max distance
        if (distance > maxAttackDistance) {
            return false
        }
        
        // Check Wait For First Hit feature
        val waitForFirstHitEnabled = CatDueller.config?.waitForFirstHit ?: false
        if (waitForFirstHitEnabled && waitingForFirstHit) {
            if (CatDueller.config?.combatLogs == true) {
                ChatUtils.combatInfo("canSwing() blocked - waiting for first hit from opponent")
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
                        ChatUtils.combatInfo("canSwing() blocked by missed hits cancel rate (${missedHitsCancelRate}%, rolled ${randomChance})")
                    }
                    return false
                }
            }
            
            // Missed hits cancel rate allows attack - swing without hit select timing restrictions
            if (CatDueller.config?.combatLogs == true) {
                ChatUtils.combatInfo("canSwing() allowed by missed hits cancel rate - crosshair not aimed")
            }
            return true
        }
    }

    /**
     * Check if bot can attack - same as canSwing for consistency
     */
    open fun canAttack(): Boolean {
        return canSwing()
    }

    /**
     * Check if it's time for scheduled reconnect (dynamic break)
     */
    private fun checkScheduledReconnect() {
        // Only handle dynamic break reconnects (long-term), unexpected disconnects use timers
        if (scheduledReconnectTime > 0L && isDynamicBreakReconnect) {
            val currentTime = System.currentTimeMillis()
            val timeUntilReconnect = scheduledReconnectTime - currentTime
            
            // Debug info every 30 seconds for dynamic break
            if (timeUntilReconnect > 0 && timeUntilReconnect % 30000 < 50) {
                println("Waiting for dynamic break reconnect... ${timeUntilReconnect/1000}s remaining")
            }
            
            if (currentTime >= scheduledReconnectTime) {
                scheduledReconnectTime = 0L  // Reset
                isDynamicBreakReconnect = false  // Reset
                
                try {
                    ChatUtils.info("Dynamic break reconnect time reached, attempting reconnect...")
                    println("Dynamic break reconnect triggered")
                    println("Current time: $currentTime")
                
                    // For dynamic break reconnects, we need to re-enable the bot
                    if (!toggled()) {
                        ChatUtils.info("Re-enabling bot for dynamic break reconnect...")
                        toggle(false)  // Automatic toggle - don't reset session start time
                    } else {
                        ChatUtils.info("Bot already enabled for dynamic break reconnect")
                    }
                    
                    // Don't reset session for dynamic break - session persists
                    
                    // Start persistent reconnect attempts for dynamic break
                    ChatUtils.info("Starting persistent reconnect attempts for dynamic break...")
                    reconnectTimer = TimeUtils.setInterval(this::reconnect, 0, 30000)
                
                } catch (e: Exception) {
                    ChatUtils.error("Error during scheduled reconnect: ${e.message}")
                    e.printStackTrace()
                }
            }
        }   
    }
    
    /**
     * Check if big break should end
     */
    private fun checkBigBreakReconnect() {
        if (bigBreakReconnectTime > 0L) {
            val currentTime = System.currentTimeMillis()
            val timeUntilReconnect = bigBreakReconnectTime - currentTime
            
            // Debug info every 30 seconds
            if (timeUntilReconnect > 0 && timeUntilReconnect % 30000 < 50) {
                println("Waiting for big break to end... ${timeUntilReconnect/1000}s remaining")
                ChatUtils.info("Big break ends in ${timeUntilReconnect/1000}s")
            }
            
            if (currentTime >= bigBreakReconnectTime) {
                bigBreakReconnectTime = 0L  // Reset
                println("Big break reconnect time reached!")
                ChatUtils.info("Big break time reached - starting reconnect process")
            
                try {
                    // Send webhook notification for big break end
                    if (CatDueller.config?.sendWebhookMessages == true && !CatDueller.config?.webhookURL.isNullOrBlank()) {
                        val author = WebHook.buildAuthor("Cat Dueller - ${getName()}", "https://cdn.discordapp.com/icons/1359887726157238532/cbbde7905d56603d13d2a7a9e4d545be.png?size=1024")
                        val thumbnail = WebHook.buildThumbnail("https://cdn.discordapp.com/icons/1359887726157238532/cbbde7905d56603d13d2a7a9e4d545be.png?size=1024")
                        
                        WebHook.sendEmbed(
                            CatDueller.config?.webhookURL!!,
                            WebHook.buildEmbed(
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
                    ChatUtils.info("Big break reconnect time reached, attempting reconnect...")
                    println("Big break reconnect triggered")
                    
                    // For big break reconnects, we need to re-enable the bot
                    if (!toggled()) {
                        ChatUtils.info("Re-enabling bot for big break reconnect...")
                        toggle(false)  // Automatic toggle - don't reset session start time
                    } else {
                        ChatUtils.info("Bot already enabled for big break reconnect")
                    }
                    
                    // Don't reset session for big break - session persists
                    ChatUtils.info("Bot re-enabled after big break - session preserved")
                    
                    // Start persistent reconnect attempts for big break
                    ChatUtils.info("Starting persistent reconnect attempts for big break...")
                    reconnectTimer = TimeUtils.setInterval(this::reconnect, 0, 30000)
                    
                } catch (e: Exception) {
                    ChatUtils.error("Error during big break reconnect: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Update hit select logic - called every tick (equivalent to onPreUpdate)
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
        
        // 1. 首先在還沒進入循環前允許攻擊
        if (hitSelectAttackTime == -1L) {
            currentShouldAttack = true
            if (CatDueller.config?.combatLogs == true) {
                ChatUtils.combatInfo("Hit Select: First attack allowed - no previous attack recorded")
            }
            return
        }
        
        // 2. KB reduction: hurtTime > 6 時允許攻擊
        if (player.hurtTime > 6 && !player.onGround) {
            currentShouldAttack = true
            isKbReductionAttack = true  // Mark this as KB reduction attack
            if (CatDueller.config?.combatLogs == true) {
                ChatUtils.combatInfo("Hit Select (KB Reduction): hurtTime > 6 - allowing attack (won't record time)")
            }
            return
        }
        
        // 3. 檢查 500ms 循環邏輯
        val hitSelectDelay = CatDueller.config?.hitSelectDelay ?: 350
        val timeSinceLastAttack = currentTime - hitSelectAttackTime
        val hitLaterDelay = CatDueller.config?.hitLaterInTrades ?: 0
        
        if (timeSinceLastAttack < hitSelectDelay) {
            // 在 delay 期間內 - 暫停攻擊
            currentShouldAttack = false
            waitingForHitLaterDelay = false  // Reset waiting flag
            if (CatDueller.config?.combatLogs == true) {

            }
        } else if (timeSinceLastAttack < 500) {
            // delay 後到 500ms 前 - 檢查 Hit Later In Trades 邏輯
            
            if (hitLaterDelay > 0) {
                // Hit Later In Trades enabled
                val timeSinceHit = if (lastHitByOpponentTime > 0) currentTime - lastHitByOpponentTime else Long.MAX_VALUE
                
                // Check if we were hit during this cycle (after hitSelectAttackTime)
                val wasHitDuringCycle = lastHitByOpponentTime > hitSelectAttackTime
                
                if (wasHitDuringCycle && timeSinceHit < hitLaterDelay) {
                    // We were hit during this cycle, wait for hit later delay
                    currentShouldAttack = false
                    waitingForHitLaterDelay = true
                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtils.combatInfo("Hit Later In Trades: Waiting ${hitLaterDelay - timeSinceHit}ms after being hit")
                    }
                } else if (wasHitDuringCycle && timeSinceHit >= hitLaterDelay) {
                    // Hit later delay has passed, allow attack
                    currentShouldAttack = true
                    waitingForHitLaterDelay = false
                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtils.combatInfo("Hit Later In Trades: Delay passed, allowing attack")
                    }
                } else {
                    // Not hit during this cycle, use normal logic
                    currentShouldAttack = true
                    waitingForHitLaterDelay = false
                    if (CatDueller.config?.combatLogs == true) {

                    }
                }
            } else {
                // Hit Later In Trades disabled, use normal logic
                currentShouldAttack = true
                waitingForHitLaterDelay = false
                if (CatDueller.config?.combatLogs == true) {

                }
            }
        } else {
            // 500ms 後 - 開始新循環，允許攻擊並重置時間
            currentShouldAttack = true
            hitSelectAttackTime = -1L  // 重置，讓下次攻擊時重新記錄時間
            waitingForHitLaterDelay = false
            if (CatDueller.config?.combatLogs == true) {

            }
        }
    }

    /**
     * Update wait for first hit logic - called every tick
     */
    private fun updateWaitForFirstHit() {
        if (!toggled()) return
        
        val waitForFirstHitEnabled = CatDueller.config?.waitForFirstHit ?: false
        if (!waitForFirstHitEnabled) {
            waitingForFirstHit = false
            crosshairOnOpponentTime = -1L
            return
        }
        
        val player = mc.thePlayer ?: return
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
                ChatUtils.combatInfo("Wait For First Hit: Disabled - in hit select cycle")
            }
            return
        }
        
        // Check if player is near edge (using nearEdge method from Sumo bot if available)
        val playerNearEdge = try {
            // Try to call nearEdge method if it exists (Sumo bot specific)
            this.javaClass.getMethod("nearEdge", Float::class.javaPrimitiveType).invoke(this, 3.5f) as Boolean
        } catch (e: Exception) {
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
                    ChatUtils.combatInfo("Wait For First Hit: Started waiting - crosshair on opponent")
                }
            } else {
                // Check timeout
                val waitForFirstHitTimeout = CatDueller.config?.waitForFirstHitTimeout ?: 500
                val timeSinceCrosshairOn = currentTime - crosshairOnOpponentTime
                
                if (timeSinceCrosshairOn >= waitForFirstHitTimeout) {
                    // Timeout reached - stop waiting and allow attack
                    waitingForFirstHit = false
                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtils.combatInfo("Wait For First Hit: Timeout reached (${waitForFirstHitTimeout}ms) - allowing attack")
                    }
                }
            }
        } else {
            // Crosshair not on opponent - reset timer
            if (crosshairOnOpponentTime != -1L) {
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtils.combatInfo("Wait For First Hit: Crosshair lost - resetting timer")
                }
            }
            crosshairOnOpponentTime = -1L
            waitingForFirstHit = false
        }
    }

    /**
     * Check if player is moving (equivalent to MoveUtil.isMoving())
     */
    private fun isPlayerMoving(): Boolean {
        val player = mc.thePlayer ?: return false
        return player.motionX != 0.0 || player.motionZ != 0.0
    }
    

    


    /**
     * Called when the game starts
     */
    protected open fun onGameStart() {
        
        // Reset winstreak check flag for new game
        winstreakChecked = false
        
        // Reset game variables
        resetGameVariables()
        
        // Debug: Verify tracking functions will be called
        if (CatDueller.config?.combatLogs == true) {
            ChatUtils.combatInfo("Game started - bow detection and movement tracking initialized")
        }
        
        // Bot Crasher Mode: Start timer to check if game doesn't end within 5 seconds
        if (CatDueller.config?.botCrasherMode == true && CatDueller.config?.botCrasherAutoRequeue == true) {
            gameStartTime = System.currentTimeMillis()
            botCrasherTimer?.cancel()
            botCrasherTimer = Timer()
            botCrasherTimer?.schedule(object : java.util.TimerTask() {
                override fun run() {
                    // If game hasn't ended after 5 seconds, send requeue command
                    if (StateManager.state == StateManager.States.PLAYING) {
                        ChatUtils.info("Bot Crasher Mode: Game didn't end after 5 seconds, sending requeue command")
                        
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
                        ChatUtils.sendAsPlayer(queueCommand)
                    }
                }
            }, 5000)
        }
    }
    
    /**
     * Reset variables for new game
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
     * Called when the game ends
     */
    protected open fun onGameEnd() {
        // Record game end time for force requeue mechanism
        gameEndTime = System.currentTimeMillis()
        forceRequeueScheduled = false
        forceRequeueAttempts = 0  // Reset attempt counter
        
        // Bot Crasher Mode: Cancel timer since game ended normally
        if (CatDueller.config?.botCrasherMode == true && CatDueller.config?.botCrasherAutoRequeue == true) {
            botCrasherTimer?.cancel()
            botCrasherTimer = null
        }
        
        // Turn off blatant mode if it was toggled for this game
        if (blatantToggled && CatDueller.config?.toggleBlatantOnBlacklisted == true) {
            val keyName = CatDueller.config?.blatantToggleKey ?: "F1"
            ChatUtils.info("Game ended - turning off blatant mode")
            
            TimeUtils.setTimeout({
                simulateKeyPress(keyName)
                blatantToggled = false
            }, RandomUtils.randomIntInRange(200, 500))
        }
        

        
        // Schedule force requeue if enabled - delay 300ms to wait for WINNER message processing
        if (CatDueller.config?.forceRequeue == true) {
            TimeUtils.setTimeout({
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
                TimeUtils.setTimeout(fun () {
                    checkForceRequeue()
                }, forceRequeueDelay)
            }, 300) // Wait 300ms for WINNER message processing to complete
        }
    }

    /**
     * Called when the bot joins a game
     */
    protected open fun onJoinGame() {
        // Cancel force requeue since we successfully joined a game
        if (gameEndTime > 0L) {
            val timeSinceGameEnd = System.currentTimeMillis() - gameEndTime

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
        best.spaghetcodes.catdueller.bot.MovementRecorder.onJoinGame()
    }

    /**
     * Called when the game almost starts (4s)
     */
    protected open fun onGameAlmostStart() {
        // Send server to guild if enabled
        if (CatDueller.config?.sendServerToGuild == true && currentServer != null) {
            TimeUtils.setTimeout({
                val randomSpam = generateRandomKeyboardSpam()
                val guildMessage = "/gc $currentServer $randomSpam"
                ChatUtils.sendAsPlayer(guildMessage)
                ChatUtils.info("Sent guild message: $guildMessage")
            }, RandomUtils.randomIntInRange(100, 300)) 
        }
        
        // Send server to DM if enabled
        if (CatDueller.config?.sendServerToDM == true && currentServer != null && !CatDueller.config?.dmTargetPlayer.isNullOrBlank()) {
            TimeUtils.setTimeout({
                val randomSpam = generateRandomKeyboardSpam()
                val dmMessage = "/w ${CatDueller.config?.dmTargetPlayer} $currentServer $randomSpam"
                ChatUtils.sendAsPlayer(dmMessage)
                ChatUtils.info("Sent DM message: $dmMessage")
            }, RandomUtils.randomIntInRange(100, 300)) 
        }
    }

    /**
     * Called before the game starts (1s)
     */
    protected open fun beforeStart() {
        // Reset Hit Select variables before game starts
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
        best.spaghetcodes.catdueller.bot.MovementRecorder.onBeforeStart()
        
    }

    /**
     * Check if we need to force requeue after requeue delay + 1 second
     */
    private fun checkForceRequeue() {
        // Only proceed if force requeue is enabled and not prevented
        if (CatDueller.config?.forceRequeue != true) return
        if (preventForceRequeue) {
            ChatUtils.info("Force requeue prevented - preparing to disconnect")
            return
        }
        
        val timeSinceGameEnd = System.currentTimeMillis() - gameEndTime
        
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
        
        val expectedDelay = baseDelay + 1000
        
        // Only force requeue if we haven't already scheduled it and gameEndTime is still set
        if (!forceRequeueScheduled && gameEndTime > 0L) {
            forceRequeueScheduled = true
            forceRequeueAttempts++
            

            
            TimeUtils.setTimeout(fun () {
                executeForceRequeue()
            }, RandomUtils.randomIntInRange(100, 300))
        }
    }
    
    /**
     * Execute force requeue and schedule next attempt if needed
     */
    private fun executeForceRequeue() {
        // Only proceed if force requeue is enabled and not prevented
        if (CatDueller.config?.forceRequeue != true) return
        if (preventForceRequeue) {
            ChatUtils.info("Force requeue execution prevented - preparing to disconnect")
            return
        }
        
        if (StateManager.state != StateManager.States.PLAYING){
            forceRequeue()
        }
        
    }



    /**
     * Called when the opponent entity is found
     */
    protected open fun onFoundOpponent() {
        // Cancel force requeue since we found an opponent (successful requeue)
        if (gameEndTime > 0L) {
            val timeSinceGameEnd = System.currentTimeMillis() - gameEndTime
        }
        forceRequeueScheduled = false
        forceRequeueAttempts = 0  // Reset attempt counter
        gameEndTime = 0L
        
        // Check if opponent is blacklisted and toggle blatant mode if enabled
        val opponentName = opponent()?.displayNameString
        if (opponentName != null && CatDueller.config?.toggleBlatantOnBlacklisted == true) {
            if (isPlayerBlacklisted(opponentName) && !blatantToggled) {
                val keyName = CatDueller.config?.blatantToggleKey ?: "F1"
                ChatUtils.info("Blacklisted player detected: $opponentName - toggling blatant mode")
                
                simulateKeyPress(keyName)
                blatantToggled = true
            }
        }
    }

    /**
     * Override this method to disable opponent speed tracking for performance
     * Default: true (enabled for projectile-based bots)
     */
    protected open fun shouldTrackOpponentSpeed(): Boolean {
        return true
    }

    /**
     * Called every tick
     */
    protected open fun onTick() {
        // Only run when bot is toggled on to prevent performance issues
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
    
    private var lastCleanupTime = 0L
    
    /**
     * Perform periodic cleanup to prevent memory leaks
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
                ChatUtils.info("Memory cleanup: Trimmed disconnectedPlayers to 50 entries")
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
                ChatUtils.info("Memory cleanup: Trimmed sessionBlacklist to 100 entries")
            }
            
            // Force garbage collection hint (not guaranteed but may help)
            System.gc()
        }
    }
    

    
    /**
     * Track opponent's bow usage and arrow count
     */
    private fun trackOpponentBowUsage() {
        if (opponent() == null || mc.theWorld == null) return
        
        val currentTime = System.currentTimeMillis()
        
        // Check if opponent is drawing bow (every 100ms to avoid spam)
        if (currentTime - lastOpponentBowCheck > 100) {
            // Debug: Show that tracking is running (every 5 seconds to avoid spam)
            if (CatDueller.config?.combatLogs == true && currentTime % 5000 < 100) {
                ChatUtils.combatInfo("Bow tracking active - checking opponent...")
            }
            val wasDrawing = opponentIsDrawingBow
            val hasBow = opponent()!!.heldItem != null && opponent()!!.heldItem.unlocalizedName.lowercase().contains("bow")
            
            // Try multiple methods to detect bow drawing
            val itemInUseCount = try { opponent()!!.itemInUseCount } catch (e: Exception) { 0 }
            val isUsingItem = try { opponent()!!.isUsingItem() } catch (e: Exception) { 
                try { opponent()!!.isUsingItem } catch (e2: Exception) { false }
            }
            val itemInUseDuration = try { opponent()!!.itemInUseDuration } catch (e: Exception) { 0 }
            
            // Consider drawing if any of these conditions are met (use threshold for itemInUseCount)
            // Also check if opponent is not moving much (simple heuristic for drawing bow)
            val isStationary = try {
                val vel = opponent()!!.motionX * opponent()!!.motionX + opponent()!!.motionZ * opponent()!!.motionZ
                vel < 0.01  // Very slow movement
            } catch (e: Exception) { false }
            
            opponentIsDrawingBow = hasBow && (itemInUseCount > 2 || isUsingItem || itemInUseDuration > 2 || 
                                            (isStationary && opponent()!!.isSneaking))
            
            // Debug bow drawing detection
            if (CatDueller.config?.combatLogs == true) {
                // Always show bow check when opponent has bow
                if (hasBow) {
                    ChatUtils.combatInfo("Opponent bow check - HasBow: $hasBow, InUseCount: $itemInUseCount, IsUsing: $isUsingItem, Duration: $itemInUseDuration, Drawing: $opponentIsDrawingBow")
                }
                // Show state changes
                if (wasDrawing != opponentIsDrawingBow) {
                    ChatUtils.combatInfo("Bow state changed - Drawing: $opponentIsDrawingBow")
                }
                // Show periodic check even when no bow (every 2 seconds to avoid spam)
                if (!hasBow && currentTime % 2000 < 100) {
                    val heldItem = opponent()!!.heldItem?.unlocalizedName ?: "none"
                    ChatUtils.combatInfo("Opponent bow check - No bow held (item: $heldItem)")
                }
            }
            
            // If opponent stopped drawing bow, they likely fired an arrow
            if (wasDrawing && !opponentIsDrawingBow) {
                opponentArrowsFired++
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtils.combatInfo("Opponent fired arrow #$opponentArrowsFired")
                }
            }
            
            lastOpponentBowCheck = currentTime
        }
        
        // Count arrows in world (alternative method) - DISABLED for performance
        // This was causing game freeze due to expensive entity iteration every 100ms
        // val arrowCount = mc.theWorld.loadedEntityList
        //     .filterIsInstance<EntityArrow>()
        //     .count { arrow -> 
        //         // Check if arrow belongs to opponent (rough estimation based on distance)
        //         opponent()!!.getDistanceToEntity(arrow) < 5.0f
        //     }
    }

    /**
     * Track opponent's movement direction (approaching or retreating)
     */
    private fun trackOpponentMovement() {
        if (opponent() == null || mc.thePlayer == null) return
        
        val currentTime = System.currentTimeMillis()
        val currentDistance = EntityUtils.getDistanceNoY(mc.thePlayer, opponent())
        
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
                ChatUtils.combatInfo("Distance tracking - Current: ${String.format("%.2f", currentDistance)}, History size: ${distanceHistory.size}, Last check: ${currentTime - lastDistanceCheck}ms ago")
            }
            
            // Need at least 3 measurements to determine direction
            if (distanceHistory.size >= 3) {
                val oldDistance = distanceHistory[0]
                val recentDistance = distanceHistory.last()
                val distanceChange = recentDistance - oldDistance
                
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtils.combatInfo("Movement analysis - Old: ${String.format("%.2f", oldDistance)}, Recent: ${String.format("%.2f", recentDistance)}, Change: ${String.format("%.2f", distanceChange)}")
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
                    ChatUtils.combatInfo("Opponent movement: $direction")
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
                    ChatUtils.combatInfo("Opponent speed (tick) - Raw: ${String.format("%.4f", speedBlocksPerTick)} blocks/tick")
                }
            }
            
            lastOpponentSpeedPos = currentPos
        }
    }

    /**
     * Track strafe directions of both players for better projectile prediction
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
                val opponentLookVec = EntityUtils.get2dLookVec(opponent()!!)
                val opponentRightVec = opponentLookVec.rotateYaw(-90f)
                val opponentStrafeAmount = opponentMovement.dotProduct(opponentRightVec)
                
                // Calculate our strafe direction (relative to our facing)
                val ourMovement = currentOurPos.subtract(lastOurPos!!)
                val ourLookVec = EntityUtils.get2dLookVec(mc.thePlayer)
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
                    ourStrafeAmount < -0.05 -> -1 // we moving left
                    else -> 0 // no significant strafe
                }
                
                // Check if we're counter-strafing (both moving in same relative direction)
                // This means we're moving away from each other laterally
                isCounterStrafing = (opponentStrafeDirection != 0 && ourStrafeDirection != 0 && 
                                   opponentStrafeDirection == ourStrafeDirection)
                
                // Debug info
                if (CatDueller.config?.combatLogs == true && (opponentStrafeDirection != 0 || ourStrafeDirection != 0)) {
                    val opponentDir = when(opponentStrafeDirection) { -1 -> "Left"; 1 -> "Right"; else -> "None" }
                    val ourDir = when(ourStrafeDirection) { -1 -> "Left"; 1 -> "Right"; else -> "None" }
                    ChatUtils.combatInfo("Strafe - Opponent: $opponentDir, Us: $ourDir, Counter: $isCounterStrafing")
                }
            }
            
            lastOpponentPos = currentOpponentPos
            lastOurPos = currentOurPos
            lastStrafeCheck = currentTime
        }
    }

    /**
     * Update blink tap logic - trigger key press when entering specified distance
     */
    /**
     * Override this method in subclasses to disable Blink Tap under certain conditions
     * @return true if Blink Tap should be disabled, false otherwise
     */
    protected open fun shouldDisableBlinkTap(): Boolean {
        return false
    }
    
    private fun updateBlinkTap() {
        if (CatDueller.config?.blinkTap != true) return
        
        // Check if subclass wants to disable Blink Tap
        if (shouldDisableBlinkTap()) return
        
        val player = mc.thePlayer ?: return
        val opponent = opponent() ?: return
        
        val currentDistance = EntityUtils.getDistanceNoY(player, opponent)
        val triggerDistance = CatDueller.config?.blinkTapDistance ?: 4.0f
        
        // Check if we crossed from outside to inside the trigger distance
        val wasOutside = lastDistanceToOpponent > triggerDistance
        val nowInside = currentDistance <= triggerDistance
        
        if (wasOutside && nowInside && !blinkTapTriggered) {
            blinkTapTriggered = true
            val keyName = CatDueller.config?.blinkTapKey ?: "Q"
            
            ChatUtils.info("Blink Tap: Triggered at distance $currentDistance (threshold: $triggerDistance)")
            
            // Press the key once immediately
            simulateKeyPress(keyName)
            
            // Schedule second key press after timeout if delay is set
            val timeoutDelay = CatDueller.config?.blinkTapSecondPressDelay ?: 0
            if (timeoutDelay > 0) {
                TimeUtils.setTimeout({
                    // Press the same key again after timeout
                    simulateKeyPress(keyName)
                    ChatUtils.info("Blink Tap: Pressed key $keyName again after timeout of ${timeoutDelay}ms")
                }, timeoutDelay)
            }
        }
        
        // Reset trigger when we move far enough away
        if (currentDistance > triggerDistance + 1.0f) {
            blinkTapTriggered = false
        }
        
        lastDistanceToOpponent = currentDistance
    }
    

    
    private var lastPlayerHurtTime = 0
    private var lastOpponentHurtTime = 0



    /**
     * Called when a packet is received
     * @param packet The received packet
     * @return true to continue processing, false to stop processing
     */
    protected open fun onPacketReceived(packet: Packet<*>): Boolean {
        return true  // Default allows continued processing
    }
    

    /********
     * Protected Methods
     ********/

    protected fun setStatKeys(keys: Map<String, String>) {
        statKeys = keys
    }

    /********
     * Base Methods
     ********/
    
    fun onPacket(packet: Packet<*>) {
        if (toggled) {
            when (packet) {
                is S40PacketDisconnect -> { // capture disconnect reason from server
                    try {
                        val reason = packet.reason?.unformattedText ?: "Unknown disconnect reason"
                        println("[PacketListener] Disconnect packet received: $reason")
                        setDisconnectReason(reason)
                        ChatUtils.info("Disconnect reason captured from packet: $reason")
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
                            if (CatDueller.config?.combatLogs == true) {
    
                            }
                            if (entity.entityId == attackedID) {
                                attackedID = -1
                                if (CatDueller.config?.combatLogs == true) {
    
                                }
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
                        TimeUtils.setTimeout(fun () {
                            if (packet.message != null) {
                                val unformatted = packet.message.unformattedText.lowercase()
                                if ((unformatted.contains("won the duel!") || unformatted.contains("a draw!")) && mc.thePlayer != null) {
                                    var winner = ""
                                    var loser = ""
                                    var draw = false
                                    var iWon = false
                                    val mcPlayerName = mc.thePlayer.displayNameString
                                    // Use playerNick if available, or mcPlayerName if it's not "EmulatedClient"
                                    val playerDisplayName = playerNick ?: if (mcPlayerName != "EmulatedClient") mcPlayerName else "Unknown"
                                    
                                    if(unformatted.contains("a draw!")){
                                        winner = playerDisplayName
                                        loser = lastOpponentName
                                        draw = true
                                        iWon = true  // Set iWon to true for draw to show correct formatting
                                    }else{
                                        val p = ChatUtils.removeFormatting(packet.message.unformattedText).split("won")[0].trim()
                                        
                                        // Check if either playerNick or mc.thePlayer.displayNameString won
                                        val playerNickWon = playerNick != null && unformatted.contains(playerNick!!.lowercase())
                                        val mcPlayerWon = mcPlayerName != "EmulatedClient" && unformatted.contains(mcPlayerName.lowercase())
                                        
                                        if (playerNickWon || mcPlayerWon) {
                                            Session.wins++
                                            currentWinstreak++  // Increase win streak
                                            winner = if (playerNickWon) playerNick!! else if (mcPlayerWon) mcPlayerName else playerDisplayName
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
                                                ChatUtils.info("Clip Losses: Lost a game - pressing F8")
                                                simulateKeyPress("F8")
                                            }
                                            
                                            // Add winner to session blacklist if feature is enabled
                                            if (CatDueller.config?.toggleBlatantOnBlacklisted == true) {
                                                addPlayerToSessionBlacklist(winner)
                                            }
                                        }
                                    }

                                    ChatUtils.info(Session.getSession(currentWinstreak))

                                    if (!iWon) {
                                        // Apply losing delay when we lost
                                        var delay = RandomUtils.randomIntInRange(1000, 2000)
                                        if (CatDueller.config?.delayRequeueAfterLosing == true) {
                                            delay += (CatDueller.config?.losingRequeueDelay ?: 5) * 1000
                                        }
                                        TimeUtils.setTimeout({ joinGame(false, true) }, delay)
                                    }

                                    if ((CatDueller.config?.disconnectAfterGames ?: 0) > 0) {
                                        if (Session.wins + Session.losses >= (CatDueller.config?.disconnectAfterGames ?: 0)) {
                                            val totalGames = CatDueller.config?.disconnectAfterGames ?: 0
                                            ChatUtils.info("Played $totalGames games, disconnecting...")
                                            
                                            // Prevent force requeue when preparing to disconnect
                                            preventForceRequeue = true
                                            gameEndTime = 0L  // Reset to prevent force requeue
                                            forceRequeueScheduled = false

                                            TimeUtils.setTimeout(fun () {
                                                ChatUtils.sendAsPlayer("/l duels")
                                                TimeUtils.setTimeout(fun () {
                                                    toggle(false)  // Automatic toggle for disconnect
                                                    disconnect()
                                                }, RandomUtils.randomIntInRange(2300, 5000))
                                            }, RandomUtils.randomIntInRange(900, 1700))
                                        }
                                    }

                                    if (actualDisconnectMinutes > 0) {
                                        if (System.currentTimeMillis() - botStartTime >= actualDisconnectMinutes * 60 * 1000) {
                                            // Check if dynamic break would overlap with big break
                                            if (wouldDynamicBreakOverlapWithBigBreak()) {
                                                ChatUtils.info("Dynamic break would overlap with big break - skipping dynamic break")
                                                actualDisconnectMinutes = 0 // Cancel this dynamic break
                                            } else {
                                                ChatUtils.info("Played for $actualDisconnectMinutes minutes, disconnecting...")
                                                
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

                                                TimeUtils.setTimeout(fun () {
                                                    ChatUtils.sendAsPlayer("/l duels")
                                                    TimeUtils.setTimeout(fun () {
                                                        val author = WebHook.buildAuthor("Cat Dueller - ${getName()}", "https://cdn.discordapp.com/icons/1359887726157238532/cbbde7905d56603d13d2a7a9e4d545be.png?size=1024")
                                                        val thumbnail = WebHook.buildThumbnail("https://cdn.discordapp.com/icons/1359887726157238532/cbbde7905d56603d13d2a7a9e4d545be.png?size=1024")

                                                        val webhookURL = CatDueller.config?.webhookURL
                                                        if (webhookURL != null) {
                                                            val message = if (CatDueller.config?.lobbySitDuringDynamicBreak == true) {
                                                                "Played for $actualDisconnectMinutes minutes, entering lobby sit mode for $actualReconnectWaitMinutes minutes... <t:${(System.currentTimeMillis() / 1000).toInt()}:R>"
                                                            } else {
                                                                "Played for $actualDisconnectMinutes minutes, disconnecting and reconnecting in $actualReconnectWaitMinutes minutes... <t:${(System.currentTimeMillis() / 1000).toInt()}:R>"
                                                            }
                                                            val title = if (CatDueller.config?.lobbySitDuringDynamicBreak == true) {
                                                                ":chair: Lobby Sitting"
                                                            } else {
                                                                ":sleeping: Taking Break"
                                                            }
                                                            WebHook.sendEmbed(
                                                                webhookURL,
                                                                WebHook.buildEmbed(title, message, JsonArray(), JsonObject(), author, thumbnail, 0xffa30f))
                                                        }
                                                        toggle(false)  // Automatic toggle for dynamic break
                                                        
                                                        if (CatDueller.config?.lobbySitDuringDynamicBreak == true) {
                                                            // Lobby sit mode: stay connected and perform sitting movements
                                                            ChatUtils.info("Starting lobby sit mode for $actualReconnectWaitMinutes minutes...")
                                                            startLobbySitMode(actualReconnectWaitMinutes)
                                                        } else if (CatDueller.config?.autoReconnectAfterDisconnect == true) {
                                                            // Disconnect immediately and schedule reconnect
                                                            ChatUtils.info("Scheduling reconnect in $actualReconnectWaitMinutes minutes...")
                                                            disconnect()
                                                            
                                                            // Set absolute reconnect time for dynamic break
                                                            val reconnectDelay = actualReconnectWaitMinutes * 60 * 1000L
                                                            scheduledReconnectTime = System.currentTimeMillis() + reconnectDelay
                                                            isDynamicBreakReconnect = true  // Mark as dynamic break reconnect
                                                            
                                                            ChatUtils.info("Dynamic break reconnect scheduled for: ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date(scheduledReconnectTime))}")
                                                            println("Dynamic break reconnect scheduled for ${actualReconnectWaitMinutes} minutes")
                                                        } else {
                                                            disconnect()
                                                        }
                                                        
                                                    }, RandomUtils.randomIntInRange(2300, 5000))
                                                }, RandomUtils.randomIntInRange(900, 1700))
                                            }
                                        }
                                    }

                                    // Big break check is now handled in gameEnd() before onGameEnd() is called

                                    if (CatDueller.config?.sendWebhookMessages == true) {
                                        if (CatDueller.config?.webhookURL != "") {
                                            val duration = StateManager.lastGameDuration / 1000

                                            // Capture opponent info before resetVars() clears it
                                            val savedOpponentNameWithRank = lastOpponentNameWithRank
                                            val savedOpponentName = lastOpponentName
                                            val savedIsOpponentNicked = isOpponentNicked

                                            // Send the webhook embed
                                            // Format player names for webhook display
                                            val mcPlayerName = mc.thePlayer.displayNameString
                                            
                                            // Use playerNick if available and different from mcPlayerName, or if mcPlayerName is "EmulatedClient"
                                            val actualPlayerName = if (playerNick != null && (playerNick != mcPlayerName || mcPlayerName == "EmulatedClient")) {
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
                                               
                                                formatOpponentNameForWebhookWithSavedValues(winner, savedOpponentNameWithRank, savedOpponentName, savedIsOpponentNicked)
                                            }
                                            
                                            val formattedLoser = if (!iWon) {
                                                if (playerNick != null && playerNick != mcPlayerName && mcPlayerName != "EmulatedClient") {
                                                    "`$mcPlayerName` `($playerNick)`"
                                                } else {
                                                    "`$actualPlayerName`"
                                                }
                                            } else {
                                                formatOpponentNameForWebhookWithSavedValues(loser, savedOpponentNameWithRank, savedOpponentName, savedIsOpponentNicked)
                                            }
                                            
                                            val fields = WebHook.buildFields(arrayListOf(
                                                mapOf("name" to "Winner", "value" to formattedWinner, "inline" to "true"), 
                                                mapOf("name" to "Loser", "value" to formattedLoser, "inline" to "true"), 
                                                mapOf("name" to "Bot Started", "value" to "<t:${(Session.startTime / 1000).toInt()}:R>", "inline" to "false")
                                            ))
                                            

                                            val footer = WebHook.buildFooter(ChatUtils.removeFormatting(Session.getSession(currentWinstreak)), "https://cdn.discordapp.com/icons/1359887726157238532/cbbde7905d56603d13d2a7a9e4d545be.png?size=1024")
                                            val author = WebHook.buildAuthor("Cat Dueller - ${getName()}", "https://cdn.discordapp.com/icons/1359887726157238532/cbbde7905d56603d13d2a7a9e4d545be.png?size=1024")
                                            val thumbnail = WebHook.buildThumbnail("https://cdn.discordapp.com/icons/1359887726157238532/cbbde7905d56603d13d2a7a9e4d545be.png?size=1024")

                                            val webhookURL = CatDueller.config?.webhookURL
                                            if (webhookURL != null) {
                                                WebHook.sendEmbed(
                                                    webhookURL,
                                                    WebHook.buildEmbed(
                                                        "${if (draw) ":sweat_smile:" else if (iWon) ":cat:" else ":frowning:"} Game ${if (draw) "DRAW" else if (iWon) "WON" else "LOST"}!", 
                                                        "Game Duration: `${duration}`s", 
                                                        fields, 
                                                        footer, 
                                                        author, 
                                                        thumbnail, 
                                                        if (draw) 0xedf86d else if (iWon) 0x66ed8a else 0xed6d66
                                                    )
                                                )
                                            }
                                        } else {
                                            ChatUtils.error("Webhook URL hasn't been set!")
                                        }
                                    }
                                }
                            }
                        }, 1000)
                    }

                }
            }
        }
    }

    @SubscribeEvent
    fun onAttackEntityEvent(ev: AttackEntityEvent) {
        if (toggled() && ev.entity == mc.thePlayer) {
            attackedID = ev.target.entityId
            if (CatDueller.config?.combatLogs == true) {

            }
            
            // Hit Select logic: Only record attack time when canSwing() is true and not KB reduction
            val hitSelectEnabled = CatDueller.config?.hitSelect ?: false
            if (hitSelectEnabled && canSwing() && !isKbReductionAttack) {
                hitSelectAttackTime = System.currentTimeMillis()
                if (CatDueller.config?.combatLogs == true) {
    
                }
            } else if (hitSelectEnabled && CatDueller.config?.combatLogs == true) {
                if (isKbReductionAttack) {

                } else {

                }
            }
        }
    }

    @SubscribeEvent
    fun onClientTick(ev: ClientTickEvent) {
        // Execute hit select updates at tick START to ensure Mouse.leftAC can see updated values immediately
        if (ev.phase == TickEvent.Phase.START) {
            updateHitSelect() // Update hit select logic at START phase
            updateWaitForFirstHit() // Update wait for first hit logic at START phase
        }
        
        // Execute other logic at default phase (END)
        if (ev.phase == TickEvent.Phase.END) {
            // Check scheduled reconnect time first - critical for reconnection
            checkScheduledReconnect()
            
            // Check big break reconnect time
            checkBigBreakReconnect()
            
            // Check lobby sit mode
            checkLobbySitMode()
            
            registerPacketListener()
            onTick()
            
            // Update MovementRecorder every tick
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

            val distance = EntityUtils.getDistanceNoY(mc.thePlayer, opponent)

            if (distance > 5 && (combo != 0 || opponentCombo != 0)) {
                combo = 0
                opponentCombo = 0

            }
        }

        if (KeyBindings.toggleBotKeyBinding.isPressed) {
            toggle()
            ChatUtils.info("Cat Dueller has been toggled ${if (toggled()) "${EnumChatFormatting.GREEN}on" else "${EnumChatFormatting.RED}off"}")
            if (toggled()) {
                ChatUtils.info("Current selected bot: ${EnumChatFormatting.GREEN}${getName()}")
                
                // Disable pause on lost focus to prevent ESC menu from opening
                disablePauseOnLostFocus()
                
                joinGame()
            }
        }
    }
    

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
                        
                        ChatUtils.info("Bot Crasher Mode: Added $playerName to spam list")
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
                    playerNick = ChatUtils.removeFormatting(formattedPlayerName)
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
                TimeUtils.setTimeout({
                    val fullMessage = unformatted.substringBefore("WINNER!").trim()
                    val mcPlayerName = mc.thePlayer?.displayNameString ?: ""
                    val actualPlayerName = playerNick ?: if (mcPlayerName != "EmulatedClient") mcPlayerName else "Unknown"
                    
                    // Extract the actual winner name (word right before "WINNER!")
                    // Format examples: 
                    // "gatitotr7   [MVP+] GirlyMonkey WINNER!" -> winner is "GirlyMonkey"
                    // "PlayerA PlayerB WINNER!" -> winner is "PlayerB"
                    val words = fullMessage.split("\\s+".toRegex()).filter { it.isNotBlank() }
                    val winnerName = words.lastOrNull() ?: ""
                    
                    // Check if we are the winner
                    val playerNickWon = playerNick != null && winnerName.equals(playerNick!!, ignoreCase = true)
                    val mcPlayerWon = mcPlayerName != "EmulatedClient" && winnerName.equals(mcPlayerName, ignoreCase = true)
                    
                    if (!playerNickWon && !mcPlayerWon) {
                        lastGameWasLoss = true
                    } else {
                        lastGameWasLoss = false
                    }
                }, 200)
            }

            // Check for game end - only match formatted official message to avoid false positives
            if ((formatted.contains("§f§lSumo Duel §r§7- §r§a§l0") || 
                 formatted.contains("§f§lClassic Duel §r§7-") ||
                 formatted.contains("§f§lOP Duel §r§7-") ||
                 formatted.contains("§f§lUHC Duel §r§7-")) && !calledGameEnd) {
                calledGameEnd = true
                gameEnd()
            }

            if (unformatted.lowercase().contains("something went wrong trying") && StateManager.state != StateManager.States.PLAYING) {
                TimeUtils.setTimeout({ ChatUtils.sendAsPlayer("/l") }, RandomUtils.randomIntInRange(300, 500))
            }
            
            // Anti Ragebait: Respond to ": L" or ": l" messages
            if (CatDueller.config?.antiRagebait == true) {
                handleAntiRagebait(unformatted)
            }
            
            // Pass chat message to MovementRecorder for game full detection
            MovementRecorder.onChatMessage(unformatted)

        }
    }

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
                TimeUtils.setTimeout({
                    updateCurrentServerFromScoreboard()
                }, 1000)
            }
        }
    }

    @SubscribeEvent
    fun onConnect(ev: ClientConnectedToServerEvent) {
        if (toggled()) {
            println("Reconnect successful!")
            
            // Cancel reconnect timer since we successfully connected
            reconnectTimer?.cancel()
            reconnectTimer = null
            
            // Don't reset bot start time on reconnect - keep original timing from toggle on
            // Don't regenerate timings - keep original timings from toggle on

            val author = WebHook.buildAuthor("Cat Dueller - ${getName()}", "https://cdn.discordapp.com/icons/1359887726157238532/cbbde7905d56603d13d2a7a9e4d545be.png?size=1024")
            val thumbnail = WebHook.buildThumbnail("https://cdn.discordapp.com/icons/1359887726157238532/cbbde7905d56603d13d2a7a9e4d545be.png?size=1024")

            val webhookURL = CatDueller.config?.webhookURL
            if (webhookURL != null) {
                WebHook.sendEmbed(
                    webhookURL,
                    WebHook.buildEmbed("Reconnected!", "The bot successfully reconnected!", JsonArray(), JsonObject(), author, thumbnail, 0x66ed8a))
            }
            
            // Reset disconnect reason after successful reconnect
            lastDisconnectReason = "Unknown"
            
            // Reset force requeue prevention after successful reconnect
            preventForceRequeue = false


            try {
                TimeUtils.setTimeout(this::joinGame, RandomUtils.randomIntInRange(6000, 8000))
            } catch (e: Exception) {
                ChatUtils.error("Error in onConnect cleanup: ${e.message}")
            }
        }
    }

    @SubscribeEvent
    fun onDisconnect(ev: ClientDisconnectionFromServerEvent) {
        println("onDisconnect event triggered - Bot toggled: ${toggled()}")
        ChatUtils.info("Disconnect event received - Bot status: ${if (toggled()) "ENABLED" else "DISABLED"}")
        
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
            ChatUtils.info("Bot was enabled during disconnect - attempting reconnect...")
            
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
            TimeUtils.setTimeout({
                simulateKeyPress("F8")
                ChatUtils.info("Pressed F8 on disconnect")
            }, RandomUtils.randomIntInRange(100, 300))

            val author = WebHook.buildAuthor("Cat Dueller - ${getName()}", "https://cdn.discordapp.com/icons/1359887726157238532/cbbde7905d56603d13d2a7a9e4d545be.png?size=1024")
            val thumbnail = WebHook.buildThumbnail("https://cdn.discordapp.com/icons/1359887726157238532/cbbde7905d56603d13d2a7a9e4d545be.png?size=1024")

            val webhookURL = CatDueller.config?.webhookURL
            if (webhookURL != null) {
                WebHook.sendEmbed(
                    webhookURL,
                    WebHook.buildEmbed("Disconnected!", "The bot was disconnected! Reason: **$lastDisconnectReason**\n\nAttempting to reconnect...", JsonArray(), JsonObject(), author, thumbnail, 0xed6d66))
            }

            // Check if there's already a dynamic break reconnect scheduled
            if (scheduledReconnectTime > 0L && isDynamicBreakReconnect) {
                ChatUtils.info("Dynamic break reconnect already scheduled - not overriding with unexpected disconnect reconnect")
                println("Keeping existing dynamic break reconnect time: ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date(scheduledReconnectTime))}")
            } else {
                // Use original reconnect logic with setInterval for persistent reconnection attempts
                val initialDelay = RandomUtils.randomIntInRange(5000, 7000)
                
                ChatUtils.info("Unexpected disconnect - starting reconnect attempts in ${initialDelay/1000} seconds")
                println("Scheduling reconnect timer for ${initialDelay}ms from now")
                
                TimeUtils.setTimeout(fun () {
                    try {
                        ChatUtils.info("Starting persistent reconnect attempts...")
                        println("Starting reconnect timer with 30 second intervals")
                        reconnectTimer = TimeUtils.setInterval(this::reconnect, 0, 30000)
                    } catch (e: Exception) {
                        ChatUtils.error("Error starting reconnect timer: ${e.message}")
                        e.printStackTrace()
                    }
                }, initialDelay)
            }
        } else {
            println("Bot was not toggled during disconnect - no reconnect attempt")
            ChatUtils.info("Bot was disabled during disconnect - skipping reconnect")
        }
    }

    /********
     * Private Methods
     ********/

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
        // Note: sessionBlacklist is NOT cleared here - it persists during the session
        

    }

    /**
     * Check and sync winstreak from scoreboard
     */
    private fun checkWinstreakFromScoreboard() {
        
        val scoreboard = mc.theWorld?.scoreboard ?: return
        val objective = scoreboard.getObjectiveInDisplaySlot(1) ?: return // Sidebar
        
        try {
            val scores = scoreboard.getSortedScores(objective)
            
            for (score in scores) {
                val playerName = score.playerName ?: continue
                val line = ScorePlayerTeam.formatPlayerName(scoreboard.getPlayersTeam(playerName), playerName)
                val cleanLine = ChatUtils.removeFormatting(line).trim()
                
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
                            ChatUtils.info("Syncing winstreak: session=$currentWinstreak -> scoreboard=$scoreboardWinstreak")
                            
                            // Check if clip losses is enabled and scoreboard winstreak is lower
                            if (CatDueller.config?.clipLosses == true && scoreboardWinstreak < currentWinstreak) {
                                ChatUtils.info("Clip Losses: Scoreboard winstreak ($scoreboardWinstreak) < Session winstreak ($currentWinstreak) - pressing F8")
                                simulateKeyPress("F8")
                            }
                            
                            currentWinstreak = scoreboardWinstreak
                        }
                        break
                    } catch (e: NumberFormatException) {

                    }
                }
            }
        } catch (e: Exception) {

        }
    }
    
    /**
     * Generate random keyboard spam
     */
    private fun generateRandomKeyboardSpam(): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val length = RandomUtils.randomIntInRange(5, 12) // Random length between 5-12 characters
        return (1..length)
            .map { chars.random() }
            .joinToString("")
    }
    
    /**
     * Update current server from scoreboard - now called only once after joining world
     */
    private fun updateCurrentServerFromScoreboard() {
        val scoreboard = mc.theWorld?.scoreboard ?: return
        val objective = scoreboard.getObjectiveInDisplaySlot(1) ?: return // Sidebar
        
        try {
            val scores = scoreboard.getSortedScores(objective)
            
            for (score in scores) {
                val playerName = score.playerName ?: continue
                val line = ScorePlayerTeam.formatPlayerName(scoreboard.getPlayersTeam(playerName), playerName)
                val cleanLine = net.minecraft.util.StringUtils.stripControlCodes(line).trim()
                
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
                            ChatUtils.combatInfo("Server updated: $oldServer -> $currentServer")
                        }
                    }
                    break
                }
            }
        } catch (e: Exception) {
            // Silently handle any scoreboard reading errors
        }
    }
    
    /**
     * Check if GUI is open (chat, ESC menu, etc.)
     */
    private fun isGuiOpen(): Boolean {
        return mc.currentScreen != null
    }
    
    /**
     * Force send requeue command, works even when GUI is open
     */
    private fun forceRequeue() {
        try {
            ChatUtils.info("Force requeue: sending command $queueCommand")
            ChatUtils.sendAsPlayer(queueCommand)
        } catch (e: Exception) {
            ChatUtils.error("Failed to send requeue command: ${e.message}")
        }
    }

    /**
     * Check if player is in blacklist (either config or session)
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
     * Add player to session blacklist (temporary, not saved to config)
     */
    private fun addPlayerToSessionBlacklist(playerName: String) {
        if (playerName.isBlank()) return
        
        if (!sessionBlacklist.contains(playerName)) {
            sessionBlacklist.add(playerName)
            
            // Limit session blacklist size to prevent memory leak
            if (sessionBlacklist.size > 100) {
                val oldestPlayer = sessionBlacklist.first()
                sessionBlacklist.remove(oldestPlayer)
                ChatUtils.info("Removed oldest player from session blacklist: $oldestPlayer")
            }
            
            ChatUtils.info("Added $playerName to session blacklist (${sessionBlacklist.size} players)")
        }
    }

    /**
     * Simulate key press using Robot
     */
    protected fun simulateKeyPress(keyName: String) {
        try {
            val robot = Robot()
            val keyCode = getKeyCodeFromName(keyName)
            
            if (keyCode != -1) {
                robot.keyPress(keyCode)
                TimeUtils.setTimeout({
                    robot.keyRelease(keyCode)
                }, 50) // Hold key for 50ms
                
                ChatUtils.info("Pressed key: $keyName")
            } else {
                ChatUtils.error("Unknown key name: $keyName")
            }
        } catch (e: Exception) {
            ChatUtils.error("Failed to simulate key press: ${e.message}")
        }
    }

    /**
     * Simulate key combination press using Robot (e.g., Alt + F10)
     */
    private fun simulateKeyCombo(modifierKey: Int, mainKey: Int) {
        try {
            val robot = Robot()
            
            // Press modifier key first
            robot.keyPress(modifierKey)
            TimeUtils.setTimeout({
                // Press main key while holding modifier
                robot.keyPress(mainKey)
                TimeUtils.setTimeout({
                    // Release main key first
                    robot.keyRelease(mainKey)
                    // Then release modifier key
                    robot.keyRelease(modifierKey)
                }, 50)
            }, 10)
            
            ChatUtils.info("Pressed key combination")
        } catch (e: Exception) {
            ChatUtils.error("Failed to simulate key combination: ${e.message}")
        }
    }

    /**
     * Convert key name to KeyEvent code
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
     * Handle guild dodge messages
     */
    private fun handleGuildDodge(message: String) {
        try {
            // Check for guild message pattern: "Guild > [VIP] dystopiankyo: m182BH ve9F4OqlN"
            val guildPattern = Regex("Guild > (?:\\[[^\\]]+\\] )?([^:]+): (.+)")
            val guildMatch = guildPattern.find(message)
            
            if (guildMatch != null) {
                val senderName = guildMatch.groupValues[1].trim()
                val fullMessage = guildMatch.groupValues[2].trim()
                
                // Skip if the message is from ourselves
                val playerName = mc.thePlayer?.name
                val playerDisplayName = mc.thePlayer?.displayNameString
                
                // Clean sender name by removing color codes and rank prefixes like [VIP], [MVP+], etc.
                var cleanSenderName = net.minecraft.util.StringUtils.stripControlCodes(senderName).trim()
                // Remove rank prefixes like [VIP], [MVP+], [MVP++], etc.
                cleanSenderName = cleanSenderName.replace(Regex("\\[[^\\]]+\\]\\s*"), "").trim()
                
                if ((playerName != null && cleanSenderName.equals(playerName, ignoreCase = true)) ||
                    (playerDisplayName != null && cleanSenderName.equals(playerDisplayName, ignoreCase = true))) {
                    ChatUtils.info("Skipping guild dodge - message from self")
                    return
                }
                
                // Extract only the first word (before any space)
                val serverFromGuild = fullMessage.split(" ")[0].trim()
                
                // Only process messages that start with "mini" or "m"
                if (serverFromGuild.isNotBlank() && (serverFromGuild.lowercase().startsWith("mini") || serverFromGuild.lowercase().startsWith("m"))) {
                    // Convert guild message from "minixxx" to "mxxx" format for consistent matching
                    val normalizedServerFromGuild = if (serverFromGuild.lowercase().startsWith("mini")) {
                        "m" + serverFromGuild.substring(4) // Remove "mini" and add "m"
                    } else {
                        serverFromGuild
                    }
                    
                    ChatUtils.info("Guild server info received: $serverFromGuild (normalized: $normalizedServerFromGuild)")
                    
                    // Use currentServer from scoreboard instead of /whereami command
                    if (currentServer != null) {
                        // Normalize current server for comparison
                        val normalizedCurrentServer = if (currentServer!!.lowercase().startsWith("mini")) {
                            "m" + currentServer!!.substring(4) // Remove "mini" and add "m"
                        } else {
                            currentServer!!
                        }
                        
                        ChatUtils.info("Current server: $currentServer (normalized: $normalizedCurrentServer), Expected: $serverFromGuild (normalized: $normalizedServerFromGuild)")
                        
                        if (normalizedCurrentServer.equals(normalizedServerFromGuild, ignoreCase = true)) {
                            ChatUtils.info("Server match confirmed! Checking game state...")
                            
                            // Check if we're within 1 second after beforeStart() was called
                            val currentTime = System.currentTimeMillis()
                            val timeSinceBeforeStart = if (beforeStartTime > 0) currentTime - beforeStartTime else Long.MAX_VALUE
                            val isNearBeforeStart = beforeStartTime > 0 && timeSinceBeforeStart <= 1500
                            
                            // Check if not currently playing, not in lobby, not near beforeStart
                            if (StateManager.state != StateManager.States.PLAYING && 
                                StateManager.state != StateManager.States.LOBBY && 
                                !isNearBeforeStart) {
                                TimeUtils.setTimeout({
                                    ChatUtils.sendAsPlayer(queueCommand)
                                    ChatUtils.info("dodging guild...")
                                }, RandomUtils.randomIntInRange(200, 500))
                            } else if (StateManager.state == StateManager.States.LOBBY) {
                                ChatUtils.info("In lobby - skipping dodge")
                            } else if (isNearBeforeStart) {
                                ChatUtils.info("Near beforeStart (${timeSinceBeforeStart}ms) - skipping dodge")
                            } else {
                                ChatUtils.info("Already in game - skipping join command")
                            }
                        } else {
                            ChatUtils.info("Server mismatch - no dodge needed")
                        }
                    } else {
                        ChatUtils.info("Current server not available from scoreboard yet")
                    }
                }
                return
            }
            
        } catch (e: Exception) {
            ChatUtils.error("Error in guild dodge: ${e.message}")
        }
    }

    /**
     * Handle DM dodge messages
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
                var cleanSenderName = net.minecraft.util.StringUtils.stripControlCodes(senderName).trim()
                
                if ((playerName != null && cleanSenderName.equals(playerName, ignoreCase = true)) ||
                    (playerDisplayName != null && cleanSenderName.equals(playerDisplayName, ignoreCase = true))) {
                    ChatUtils.info("Skipping DM dodge - message from self")
                    return
                }
                
                // Extract only the first word (before any space)
                val serverFromDM = fullMessage.split(" ")[0].trim()
                
                // Auto reply to non-server ID DMs
                if (CatDueller.config?.autoReplyDM == true && fullMessage.isNotBlank() && 
                    !fullMessage.lowercase().startsWith("mini") && !fullMessage.lowercase().startsWith("m")) {
                    ChatUtils.info("Auto replying to DM from $cleanSenderName: '$fullMessage'")
                    
                    // Send "?" after 3 seconds
                    TimeUtils.setTimeout({
                        ChatUtils.sendAsPlayer("/r ?")
                    }, 3000)
                    
                    // Send "ok" after 4 seconds (3 + 1)
                    TimeUtils.setTimeout({
                        ChatUtils.sendAsPlayer("/r ok")
                    }, 4000)
                }
                
                // Only process messages that start with "mini" or "m"
                if (serverFromDM.isNotBlank() && (serverFromDM.lowercase().startsWith("mini") || serverFromDM.lowercase().startsWith("m"))) {
                    // Convert DM message from "minixxx" to "mxxx" format for consistent matching
                    val normalizedServerFromDM = if (serverFromDM.lowercase().startsWith("mini")) {
                        "m" + serverFromDM.substring(4) // Remove "mini" and add "m"
                    } else {
                        serverFromDM
                    }
                    
                    ChatUtils.info("DM server info received from $cleanSenderName: $serverFromDM (normalized: $normalizedServerFromDM)")
                    
                    // Use currentServer from scoreboard instead of /whereami command
                    if (currentServer != null) {
                        // Normalize current server for comparison
                        val normalizedCurrentServer = if (currentServer!!.lowercase().startsWith("mini")) {
                            "m" + currentServer!!.substring(4) // Remove "mini" and add "m"
                        } else {
                            currentServer!!
                        }
                        
                        ChatUtils.info("Current server: $currentServer (normalized: $normalizedCurrentServer), Expected: $serverFromDM (normalized: $normalizedServerFromDM)")
                        
                        if (normalizedCurrentServer.equals(normalizedServerFromDM, ignoreCase = true)) {
                            ChatUtils.info("Server match confirmed! Checking game state...")
                            
                            // Check if we're within 1 second after beforeStart() was called
                            val currentTime = System.currentTimeMillis()
                            val timeSinceBeforeStart = if (beforeStartTime > 0) currentTime - beforeStartTime else Long.MAX_VALUE
                            val isNearBeforeStart = beforeStartTime > 0 && timeSinceBeforeStart <= 1500
                            
                            // Check if not currently playing, not in lobby, not near beforeStart
                            if (StateManager.state != StateManager.States.PLAYING && 
                                StateManager.state != StateManager.States.LOBBY && 
                                !isNearBeforeStart) {
                                TimeUtils.setTimeout({
                                    ChatUtils.sendAsPlayer(queueCommand)
                                    ChatUtils.info("dodging DM...")
                                }, RandomUtils.randomIntInRange(200, 500))
                            } else if (StateManager.state == StateManager.States.LOBBY) {
                                ChatUtils.info("In lobby - skipping dodge")
                            } else if (isNearBeforeStart) {
                                ChatUtils.info("Near beforeStart (${timeSinceBeforeStart}ms) - skipping dodge")
                            } else {
                                ChatUtils.info("Already in game - skipping join command")
                            }
                        } else {
                            ChatUtils.info("Server mismatch - no dodge needed")
                        }
                    } else {
                        ChatUtils.info("Current server not available from scoreboard yet")
                    }
                }
                return
            }
            
        } catch (e: Exception) {
            ChatUtils.error("Error in DM dodge: ${e.message}")
        }
    }

    private fun gameStart() {
        beforeStartTime = 0L
        
        if (toggled()) {
            if (CatDueller.config?.sendStartMessage == true) {
                TimeUtils.setTimeout(fun () {
                    val baseMessage = CatDueller.config?.startMessage ?: "glhf!"
                    val randomSuffix = best.spaghetcodes.catdueller.utils.RandomUtils.randomString(6, true, true)
                    val messageWithRandom = "$baseMessage $randomSuffix"
                    ChatUtils.sendAsPlayer("/ac $messageWithRandom")
                }, CatDueller.config?.startMessageDelay ?: 100)
            }

            val quickRefreshTimer = TimeUtils.setInterval(this::bakery, 200, 50)
            TimeUtils.setTimeout(fun () {
                quickRefreshTimer?.cancel()
                opponentTimer = TimeUtils.setInterval(this::bakery, 0, 500)
            }, quickRefresh)

            onGameStart()
        }
    }

    private fun gameEnd() {
        if (toggled()) {
            // Check if we're in big break time BEFORE calling onGameEnd()
            // This prevents any requeue logic from being triggered
            if (isInBigBreakTime()) {
                ChatUtils.info("Game ended during big break time - entering big break")
                
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

                TimeUtils.setTimeout(fun () {
                    ChatUtils.sendAsPlayer("/l duels")
                    TimeUtils.setTimeout(fun () {
                        enterBigBreak()
                    }, RandomUtils.randomIntInRange(2300, 5000))
                }, RandomUtils.randomIntInRange(900, 1700))
                return
            }
            
            onGameEnd()
            resetVars()

            // Game end celebration: sprint + forward + jump + random strafe for 1 second
            // Execute after onGameEnd() and resetVars() to avoid being cleared
            ChatUtils.info("Game ended - performing celebration movement")
            
            // First, clear all existing movements to ensure clean state
            Movement.clearAll()
            
            // Small delay to ensure clearing is complete, then start celebration
            TimeUtils.setTimeout({
                Movement.startSprinting()
                Movement.startForward()
                Movement.singleJump(150)
                
                // Add random strafe (left or right)
                if (RandomUtils.randomBool()) {
                    Movement.startLeft()
                    ChatUtils.info("Celebration: strafing left")
                } else {
                    Movement.startRight()
                    ChatUtils.info("Celebration: strafing right")
                }
            }, 100) // 400ms delay to ensure clearing is complete
            
            // Stop celebration after 1 second (plus the initial delay)
            TimeUtils.setTimeout({
                Movement.stopSprinting()
                Movement.stopForward()
                Movement.stopLeft()
                Movement.stopRight()
            }, 1000) // 1000ms celebration + 400ms initial delay

            if (CatDueller.config?.sendAutoGG == true) {
                TimeUtils.setTimeout(fun () {
                    ChatUtils.sendAsPlayer("/ac " + (CatDueller.config?.ggMessage ?: "gg"))
                }, CatDueller.config?.ggDelay ?: 100)
            }

            // Wait 300ms for WINNER message processing, then check ping and start requeue
            TimeUtils.setTimeout({
                checkInternetStabilityAndRequeue()
            }, 300) // Wait 300ms for WINNER message processing to complete
        }
    }

    private fun bakery() {
        if (StateManager.state == StateManager.States.PLAYING) {
            val entity = EntityUtils.getOpponentEntity()
            if (entity != null) {
                opponent = entity
                lastOpponentName = entity.displayNameString
                
                // Check if opponent is nicked using UUID pattern
                if (!calledFoundOpponent) {
                    val entityUUID = entity.uniqueID
                    if (entityUUID != null) {
                        isOpponentNicked = UUIDChecker.isNickedUUID(entityUUID)
                        val uuidStatus = UUIDChecker.getUUIDStatus(entityUUID)
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


    private fun leaveGame() {
        if (toggled() && StateManager.state != StateManager.States.PLAYING) {
            TimeUtils.setTimeout(fun () {
                ChatUtils.sendAsPlayer("/l")
            }, RandomUtils.randomIntInRange(100, 300))
        }
    }

    private fun joinGame(second: Boolean = false, applyLosingDelay: Boolean = false) {
        if (toggled() && StateManager.state != StateManager.States.PLAYING && !StateManager.gameFull) {
            
            // Calculate additional delay if we lost and the feature is enabled
            var additionalDelay = 0
            if (applyLosingDelay && lastGameWasLoss && CatDueller.config?.delayRequeueAfterLosing == true) {
                additionalDelay = (CatDueller.config?.losingRequeueDelay ?: 5) * 1000
                ChatUtils.info("joinGame: Adding ${additionalDelay}ms delay after losing")
            }
            
            if (StateManager.state == StateManager.States.GAME) {
                val paperRequeueEnabled = CatDueller.config?.paperRequeue == true
                
                if (paperRequeueEnabled) {
                    // Paper requeue is enabled, try to find paper with retries
                    tryPaperRequeue(second, additionalDelay)
                } else {
                    // Paper requeue disabled, use command requeue
                    TimeUtils.setTimeout({
                        ChatUtils.info("Using command requeue: $queueCommand")
                        ChatUtils.sendAsPlayer(queueCommand)
                    }, RandomUtils.randomIntInRange(100, 300) + additionalDelay)
                }
            } else {
                TimeUtils.setTimeout(fun () {
                    ChatUtils.info("Using command requeue: $queueCommand")
                    ChatUtils.sendAsPlayer(queueCommand)
                }, RandomUtils.randomIntInRange(100, 300) + additionalDelay)
            }
        }
    }



    private fun disconnect() {
        if (mc.theWorld != null) {
            mc.addScheduledTask(fun () {
                mc.theWorld.sendQuittingDisconnectingPacket()
                mc.loadWorld(null)
                mc.displayGuiScreen(GuiMultiplayer(GuiMainMenu()))
            })
        }
    }

     private fun reconnect() {
        if (mc.theWorld == null) {
            if (mc.currentScreen is GuiMultiplayer) {
                mc.addScheduledTask(fun () {
                    println("Reconnecting...")
                    FMLClientHandler.instance().setupServerList()
                    FMLClientHandler.instance().connectToServer(mc.currentScreen, ServerData("hypixel", "mc.hypixel.net", false))
                })
            } else {
                if (mc.theWorld == null && mc.currentScreen !is GuiConnecting) {
                    mc.addScheduledTask(fun () {
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
        spamTimer?.scheduleAtFixedRate(object : java.util.TimerTask() {
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
                        ChatUtils.sendAsPlayer("/p $allPlayers")
                        
                        TimeUtils.setTimeout({
                            ChatUtils.sendAsPlayer("/p disband")
                        }, 500)
                        
                        ChatUtils.info("Bot Crasher Mode: Invited ${allTargetPlayers.size} players: $allPlayers")
                        ChatUtils.info("  - Auto-detected: ${disconnectedPlayers.size}, Manual: ${manualPlayers.size}")
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
            ChatUtils.error("Failed to modify options.txt: ${e.message}")
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
                ChatUtils.info("Config not available, using default timing values")
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
                actualDisconnectMinutes = RandomUtils.randomIntInRange(minTime, maxTime)
                ChatUtils.info("Dynamic break: Will disconnect after $actualDisconnectMinutes minutes (base: $baseDisconnectMinutes ± $dynamicVariancePercent% = ±${varianceMinutes}min)")
            } else {
                actualDisconnectMinutes = 0
            }
            
            // Generate dynamic break wait time with variance (percentage-based)
            val baseWaitMinutes = config.reconnectWaitMinutes
            val waitVarianceMinutes = (baseWaitMinutes * dynamicVariancePercent / 100.0).toInt()
            val minWaitTime = (baseWaitMinutes - waitVarianceMinutes).coerceAtLeast(1)
            val maxWaitTime = baseWaitMinutes + waitVarianceMinutes
            actualReconnectWaitMinutes = RandomUtils.randomIntInRange(minWaitTime, maxWaitTime)
            if(config.autoReconnectAfterDisconnect == true){
                ChatUtils.info("Dynamic break: Will wait $actualReconnectWaitMinutes minutes before reconnect (base: $baseWaitMinutes ± $dynamicVariancePercent% = ±${waitVarianceMinutes}min)")
            }
            // Big break timing (no variance)
            if (config.bigBreakEnabled) {
                ChatUtils.info("Big break: Will break from ${config.bigBreakStartHour}:00 to ${config.bigBreakEndHour}:00")
            }
        } catch (e: Exception) {
            ChatUtils.error("Error generating randomized timings: ${e.message}")
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
                currentHour >= startHour && currentHour < endHour
            } else {
                // Overnight case: start late, end early next day
                currentHour >= startHour || currentHour < endHour
            }
        } catch (e: Exception) {
            ChatUtils.error("Error checking big break time: ${e.message}")
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
            ChatUtils.error("Error calculating minutes until big break ends: ${e.message}")
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
            ChatUtils.info("Dynamic break overlap detected:")
            ChatUtils.info("  Dynamic break would end at: ${java.text.SimpleDateFormat("HH:mm").format(java.util.Date(dynamicBreakEndTime))}")
            ChatUtils.info("  Next big break starts at: ${java.text.SimpleDateFormat("HH:mm").format(java.util.Date(nextBigBreakStart))}")
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
                ChatUtils.info("Dynamic break would overlap with big break - cancelling dynamic break")
                ChatUtils.info("Dynamic break: ${actualDisconnectMinutes}min + ${actualReconnectWaitMinutes}min wait")
                ChatUtils.info("Big break: ${minutesUntilBigBreakEnd}min duration")
                
                // Cancel dynamic break by resetting its timing
                actualDisconnectMinutes = 0
                actualReconnectWaitMinutes = 30 // Reset to default
                
                ChatUtils.info("Dynamic break cancelled - big break takes priority")
            }
        }
    }

    /**
     * Enter big break period - uses absolute time checking instead of timer
     */
    private fun enterBigBreak() {
        val minutesUntilEnd = getMinutesUntilBigBreakEnds()
        
        ChatUtils.info("Entering big break time! Break will end in $minutesUntilEnd minutes")
        
        // Cancel any existing dynamic break if it would overlap with big break
        cancelDynamicBreakIfOverlapping()
        
        // Send webhook notification
        if (CatDueller.config?.sendWebhookMessages == true && !CatDueller.config?.webhookURL.isNullOrBlank()) {
            val author = WebHook.buildAuthor("Cat Dueller - ${getName()}", "https://cdn.discordapp.com/icons/1359887726157238532/cbbde7905d56603d13d2a7a9e4d545be.png?size=1024")
            val thumbnail = WebHook.buildThumbnail("https://cdn.discordapp.com/icons/1359887726157238532/cbbde7905d56603d13d2a7a9e4d545be.png?size=1024")
            
            // Calculate end time in seconds for Discord timestamp
            val endTimeSeconds = ((System.currentTimeMillis() + (minutesUntilEnd * 60 * 1000)) / 1000).toInt()
            
            WebHook.sendEmbed(
                CatDueller.config?.webhookURL!!,
                WebHook.buildEmbed(
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
        
        ChatUtils.info("Big break will end at: ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date(bigBreakReconnectTime))}")
        
        // Use same disconnect approach as dynamic break
        ChatUtils.info("Big break: Disconnecting like dynamic break...")
        
        // Prevent force requeue when preparing to disconnect
        preventForceRequeue = true
        gameEndTime = 0L  // Reset to prevent force requeue
        forceRequeueScheduled = false
        
        TimeUtils.setTimeout(fun () {
            ChatUtils.sendAsPlayer("/l duels")
            TimeUtils.setTimeout(fun () {
                // Disconnect immediately like dynamic break
                ChatUtils.info("Big break: Disconnecting now...")
                disconnect()
            }, RandomUtils.randomIntInRange(2300, 5000))
        }, RandomUtils.randomIntInRange(900, 1700))
    }
    

    
    /**
     * Format opponent name for webhook display
     * Handles nicked players and rank display
     */
    private fun formatOpponentNameForWebhook(opponentName: String): String {
        return "`$opponentName`"
    }
    
    /**
     * Format opponent name for webhook display using saved values
     * Used when resetVars() has already been called
     */
    private fun formatOpponentNameForWebhookWithSavedValues(
        opponentName: String, 
        savedOpponentNameWithRank: String, 
        savedOpponentName: String, 
        savedIsOpponentNicked: Boolean
    ): String {
        return "`$opponentName`"
    }
    

    
    /**
     * Generate player head URL for webhook
     */
    private fun getPlayerHeadUrl(playerName: String): String {
        return "https://mc-heads.net/avatar/$playerName"
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
        } catch (e: Exception) {
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
     * Extract clean player name from ranked name
     * Example: "[MVP++] PlayerName" -> "PlayerName"
     */
    private fun extractCleanNameFromRanked(rankedName: String): String {
        return try {
            // Match pattern like "[VIP+] PlayerName" or "PlayerName"
            val pattern = Regex("(?:\\[[^\\]]+\\]\\s+)?(.+)")
            val match = pattern.find(rankedName.trim())
            match?.groupValues?.get(1)?.trim() ?: rankedName
        } catch (e: Exception) {
            rankedName
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
        
        ChatUtils.info("Lobby sit mode started - will repeat cycles for $waitMinutes minutes")
        
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
        ChatUtils.info("Lobby sit cycle: Sprint + Forward + Jump for 5 seconds")
        
        Movement.startSprinting()
        Movement.startForward()
        
        // Schedule jump spam
        lobbySitJumpTimer = TimeUtils.setInterval({
            if (lobbySitActive && lobbySitPhase == 0) {
                Movement.singleJump(150)
            }
        }, 0, 500) // Jump every 500ms
        
        // Schedule phase transition after 5 seconds
        TimeUtils.setTimeout({
            if (lobbySitActive) {
                // Stop first phase
                Movement.stopForward()
                Movement.stopSprinting()
                lobbySitJumpTimer?.cancel()
                
                ChatUtils.info("Lobby sit cycle: Waiting 1 second between phases...")
                
                // Wait 1 second before starting backward phase
                TimeUtils.setTimeout({
                    if (lobbySitActive) {
                        // Start second phase: backward for 10 seconds
                        lobbySitPhase = 1
                        lobbySitPhaseStartTime = System.currentTimeMillis()
                        ChatUtils.info("Lobby sit cycle: Backward for 10 seconds")
                        Movement.startBackward()
                        
                        // Schedule end of backward phase
                        TimeUtils.setTimeout({
                            if (lobbySitActive) {
                                Movement.stopBackward()
                                ChatUtils.info("Lobby sit cycle completed - waiting 1 second before next cycle...")
                                
                                // Wait 1 second before starting next cycle
                                TimeUtils.setTimeout({
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
            
            ChatUtils.info("Lobby sit mode ended - re-enabling bot")
            
            // Re-enable bot
            if (!toggled()) {
                toggle(false)  // Don't reset session start time
            }
            
            // Start normal operation
            TimeUtils.setTimeout({
                if (toggled()) {
                    joinGame(false, true)
                }
            }, RandomUtils.randomIntInRange(2000, 4000))
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
                ChatUtils.info("High ping detected ($currentPing ms) - pausing requeue until connection stabilizes")
                ChatUtils.info("Waiting for ping to stay below 250ms for 1 minute before resuming...")
                
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
                    ChatUtils.info("Ping improved to $currentPing ms - monitoring stability...")
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
        
        pingCheckTimer?.schedule(object : java.util.TimerTask() {
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
                        ChatUtils.info("Ping still high ($currentPing ms) - continuing to wait...")
                    }
                } else {
                    // Good ping
                    if (lastStablePingTime == 0L) {
                        // First time seeing good ping
                        lastStablePingTime = currentTime
                        ChatUtils.info("Ping improved to $currentPing ms - monitoring stability for 1 minute...")
                    } else {
                        // Check if we've had stable ping for 1 minute
                        val stableTime = currentTime - lastStablePingTime
                        if (stableTime >= 60000) { // 1 minute
                            // Connection is stable, resume requeuing
                            internetStabilityPaused = false
                            lastStablePingTime = 0L
                            ChatUtils.info("Connection stable for 1 minute (ping: $currentPing ms) - resuming requeue")
                            
                            // Send webhook notification for internet stability resume
                            sendInternetStabilityWebhook(currentPing, false)
                            
                            // Resume requeuing
                            proceedWithNormalRequeue()
                            cancel()
                        } else {
                            // Still monitoring
                            val remainingTime = (60000 - stableTime) / 1000
                            if (stableTime % 15000 < 5000) { // Log every 15 seconds
                                ChatUtils.info("Ping stable at $currentPing ms - ${remainingTime}s remaining...")
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
    private fun tryPaperRequeue(isRetry: Boolean = false, additionalDelay: Int = 0, attempt: Int = 1) {
        val maxAttempts = 3 // Try up to 3 times
        val baseDelay = if (attempt == 1) 200 else 400 // First attempt: 200ms, later attempts: 400ms
        
        TimeUtils.setTimeout({
            val hasPaper = Inventory.setInvItem("paper")
            
            if (hasPaper) {
                // Found paper, check if GUI is open
                if (isGuiOpen()) {
                    // Wait a bit for GUI to close, then try again
                    TimeUtils.setTimeout({
                        if (!isGuiOpen()) {
                            Mouse.rClick(RandomUtils.randomIntInRange(100, 300))
                        } else {
                            ChatUtils.info("GUI still open after waiting, using command requeue: $queueCommand")
                            ChatUtils.sendAsPlayer(queueCommand)
                        }
                    }, 300)
                } else {
                    // GUI is not open, use paper requeue
                    Mouse.rClick(RandomUtils.randomIntInRange(100, 300))
                }
            } else {
                // Paper not found
                if (attempt < maxAttempts) {
                    // Try again with longer delay
                    tryPaperRequeue(true, additionalDelay, attempt + 1)
                } else {
                    // Max attempts reached, fallback to command requeue
                    TimeUtils.setTimeout({
                        ChatUtils.info("Paper requeue failed after $maxAttempts attempts, using command requeue: $queueCommand")
                        ChatUtils.sendAsPlayer(queueCommand)
                    }, RandomUtils.randomIntInRange(100, 300))
                }
            }
        }, baseDelay + additionalDelay)
    }
    
    /**
     * Proceed with normal requeue logic
     */
    private fun proceedWithNormalRequeue() {
        if (CatDueller.config?.fastRequeue == true) {
            val fastDelay = RandomUtils.randomIntInRange(300, 500)
            TimeUtils.setTimeout({ joinGame(false, true) }, fastDelay)
        } else {
            val delay = CatDueller.config?.autoRqDelay ?: 2000
            TimeUtils.setTimeout({ joinGame(false, true) }, delay)
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
            val author = WebHook.buildAuthor("Cat Dueller - ${getName()}", "https://cdn.discordapp.com/icons/1359887726157238532/cbbde7905d56603d13d2a7a9e4d545be.png?size=1024")
            val thumbnail = WebHook.buildThumbnail("https://cdn.discordapp.com/icons/1359887726157238532/cbbde7905d56603d13d2a7a9e4d545be.png?size=1024")
            
            val title: String
            val description: String
            val color: Int
            
            if (isPaused) {
                title = ":warning: Internet Unstable - Paused"
                description = "High ping detected ($ping ms). Requeuing paused until connection stabilizes.\n\nWaiting for ping to stay below 250ms for 1 minute before resuming."
                color = 0xffa500 // Orange
            } else {
                title = ":white_check_mark: Internet Stable - Resumed"
                description = "Connection stabilized ($ping ms). Requeuing resumed after 1 minute of stable connection."
                color = 0x00ff00 // Green
            }
            
            WebHook.sendEmbed(
                CatDueller.config?.webhookURL!!,
                WebHook.buildEmbed(
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
            ChatUtils.error("Failed to send internet stability webhook: ${e.message}")
        }
    }
    
    /**
     * Check if movement should be cleared based on combo count
     * @param distance Distance to opponent
     * @param additionalConditions Additional conditions that must be false to clear movement
     * @return true if movement should be cleared
     */
    protected fun shouldClearMovementForCombo(distance: Float, vararg additionalConditions: Boolean): Boolean {
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
     * Check if combo jump strafe should be performed
     * @param distance Distance to opponent
     * @param player Player entity
     * @param nearEdge Whether player is near edge
     * @param airInFront Whether there's air in front
     * @return true if combo jump strafe should be performed
     */
    protected fun shouldPerformComboJumpStrafe(
        distance: Float, 
        player: net.minecraft.entity.player.EntityPlayer,
        nearEdge: Boolean = false,
        airInFront: Boolean = false
    ): Boolean {
        return combo >= 3 && 
               distance >= 3.2 && 
               player.onGround && 
               !nearEdge && 
               !airInFront
    }
    
    /**
     * Apply combo-based movement decisions
     * @param distance Distance to opponent
     * @param player Player entity
     * @param clearMovement Function to clear movement
     * @param stopForward Function to stop forward movement
     * @param startBackward Function to start backward movement
     * @param performJumpStrafe Function to perform jump strafe
     * @param blockingConditions Conditions that block movement changes
     */
    protected fun applyComboMovementDecisions(
        distance: Float,
        player: net.minecraft.entity.player.EntityPlayer,
        clearMovement: () -> Unit = {},
        stopForward: () -> Unit = {},
        startBackward: () -> Unit = {},
        performJumpStrafe: () -> Unit = {},
        vararg blockingConditions: Boolean
    ) {
        // Clear movement for high combo
        if (shouldClearMovementForCombo(distance, *blockingConditions)) {
            clearMovement()
        }
        
        // Stop forward movement for close combat with high combo
        if (shouldStopForwardForCombo(distance, *blockingConditions)) {
            stopForward()
            startBackward()
        }
        
        // Perform combo jump strafe if conditions are met
        if (shouldPerformComboJumpStrafe(distance, player)) {
            performJumpStrafe()
        }
    }
}

    /**
     * Handle anti ragebait functionality
     * Responds to ": L" or ": l" messages with random L spam
     */
    private fun handleAntiRagebait(message: String) {
        // Check if message ends with ": L" or ": l" (case sensitive, no trailing characters)
        if (message.endsWith(": L") || message.endsWith(": l")) {
            // Generate random number of L's between 3 and 10
            val lCount = RandomUtils.randomIntInRange(3, 10)
            val lSpam = "L".repeat(lCount)
            
            // Send the L spam with a small delay to avoid spam detection
            TimeUtils.setTimeout({
                ChatUtils.sendAsPlayer(lSpam)
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtils.combatInfo("Anti Ragebait: Responded to '$message' with $lCount L's")
                }
            }, RandomUtils.randomIntInRange(100, 300))
        }
    }