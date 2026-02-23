package org.afterlike.catdueller.core

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.afterlike.catdueller.CatDueller
import org.afterlike.catdueller.bot.impl.*
import java.io.File

/**
 * Configuration class for CatDueller mod settings.
 * 
 * All settings are stored as mutable properties and automatically
 * saved to a JSON file when modified through the ClickGUI.
 */
class Config {
    
    // General Settings
    var currentBot = 0
    var lobbyMovement = true
    var useRecordedMovement = false
    var combatLogs = true
    var serverIP = "mc.hypixel.net"
    var autoReconnectAfterDisconnect = true
    var reconnectWaitMinutes = 30
    var dynamicBreakVariance = 30
    var lobbySitDuringDynamicBreak = false
    var pauseWhenInternetUnstable = false
    var bigBreakEnabled = false
    var bigBreakStartHour = 13
    var bigBreakEndHour = 17
    var throwAfterGames = 0
    var disconnectAfterGames = 0
    var disconnectAfterMinutes = 0
    var clipLosses = false
    
    // Combat Settings
    var cps = 12.0f
    var lookSpeedHorizontal = 10
    var lookSpeedVertical = 5
    var lookRand = 0.3f
    var verticalMultipoint = true
    var disableAiming = false
    var maxDistanceLook = 150
    var maxDistanceAttack = 5
    
    // Classic Settings
    var predictionTicksBonus = 0
    var enableRodJump = true
    var rodJumpDelay = 200
    var enableWTap = true
    var wTapDelay = 100
    var sprintReset = false
    var holdLeftClick = false
    var keepDistanceMode = false
    var keepDistance = 6.0f
    var keepDistanceJumpOnRodHit: Boolean? = false
    var hitSelect = false
    var hitSelectDelay = 400
    var hitLaterInTrades = 0
    var waitForFirstHit = false
    var waitForFirstHitTimeout = 150
    var hitSelectCancelRate = 100
    var missedHitsCancelRate = 0
    var hurtStrafe = true
    var jumpVelocity = 0
    var enableRetreat = true
    var enableArrowBlocking = true
    var dodgeArrow = false
    
    // Blitz Settings
    var blitzKit = 0  // 0 = Fisherman, 1 = Necromancer
    var necromancerMelee = 0  // 0 = Sword, 1 = Shovel
    var placeMobs: Boolean? = false
    var placeMobsOnlyWhenBowing: Boolean? = true
    
    // Sumo Settings
    var hitSelectAtEdge = false
    var distance7Jump = true
    var sTap = true
    var sTapDistance = 3.7f
    var stopWhenOpponentAtEdge = false
    var stopAtEdgeDuration = 500
    var freezeWhenOffEdge = false
    var freezeBind = "F1"
    var sumoLongJump = false
    var randomStrafe = true
    var strafeSwitchDelay = 1000
    var enableStrafeSwitch = true
    
    // Toggling Settings
    var blinkAtEdge = false
    var blinkKey = "Q"
    var toggleBlatantAtEdge = false
    var toggleBlatantDistance = 6.0f
    var toggleBlatantOnBlacklisted = false
    var blatantToggleKey = "F1"
    var blacklistedPlayers = ""
    
    // Queue Dodging Settings
    var dodgeStandingStill = true
    var dodgeHuaxi = false
    var dodgeParticleType = 0
    var guildDodge = false
    var sendServerToGuild = false
    var dmDodge = false
    var sendServerToDM = false
    var dmTargetPlayer = ""
    var ircDodgeEnabled = true
    var ircServerHost = "catdueller.afterlike.org"
    var ircServerPort = 443
    var showIRCAlerts = false
    var autoIRCDodge = true
    
    // Auto Requeue Settings
    var autoRqDelay = 2500
    var rqNoGame = 30
    var paperRequeue = true
    var fastRequeue = true
    var forceRequeue = true
    var delayRequeueAfterLosing = false
    var losingRequeueDelay = 5
    
    // AutoGG Settings
    var sendAutoGG = true
    var ggMessage = "gg"
    var ggDelay = 100
    var sendStartMessage = false
    var startMessage = "GL HF!"
    var startMessageDelay = 100
    
    // Chat Messages Settings
    var enableTauntMessages = false
    var tauntThresholdSeconds = 20
    var autoReplyDM = false
    var antiRagebait = false
    
    // Webhook Settings
    var sendWebhookMessages = false
    var webhookURL = ""
    var showWinstreak: Boolean? = true
    var showWinsPerHour: Boolean? = true
    
    // Bot Crasher Settings
    var botCrasherMode = false
    var botCrasherAutoRequeue = true
    var botCrasherSpamPlayers = true
    var botCrasherTargetPlayers = ""
    
    // Hidden/Disabled Settings
    var blinkTap = false
    var blinkTapDistance = 8.0f
    var blinkTapKey = "Q"
    var blinkTapSecondPressDelay = 0
    var setMyRotation = false
    var myTargetYaw = 0.0f
    var myTargetPitch = 0.0f
    var myAngleTolerance = 0.01f
    var dodgeWrongRotation = false
    var dodgeEachOtherBot = false
    var expectedOpponentYaw = 0.0f
    var expectedOpponentPitch = 0.0f
    var opponentAngleTolerance = 0.5f
    var showRotationDebug = false
    var leaveWhenOpponentFreeze = false
    
    /**
     * Map of bot index to bot instance for each available game mode.
     * Uses lazy initialization to avoid circular dependency during config loading.
     * Marked as transient to exclude from JSON serialization.
     */
    @delegate:Transient
    val bots by lazy {
        mapOf(
            0 to Sumo(),
            1 to Classic(),
            2 to OP(),
            3 to UHC(),
            4 to Blitz(),
            5 to BowDuel()
        )
    }
    
    companion object {
        private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
        
        /**
         * Load configuration from file.
         */
        fun load(file: File): Config {
            return try {
                if (file.exists()) {
                    val json = file.readText()
                    gson.fromJson(json, Config::class.java) ?: Config()
                } else {
                    Config().also { it.save(file) }
                }
            } catch (e: Exception) {
                println("Failed to load config: ${e.message}")
                e.printStackTrace()
                Config()
            }
        }
    }
    
    /**
     * Save configuration to file.
     */
    fun save(file: File) {
        try {
            file.parentFile?.mkdirs()
            val json = gson.toJson(this)
            file.writeText(json)
        } catch (e: Exception) {
            println("Failed to save config: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Mark config as dirty (for compatibility with old code).
     */
    fun markDirty() {
        // No-op for compatibility
    }
    
    /**
     * Write data to file.
     */
    fun writeData() {
        save(File(CatDueller.CONFIG_LOCATION))
    }
    
    /**
     * Initialize config (for compatibility with old code).
     */
    fun initialize() {
        // No-op for compatibility
    }
    
    /**
     * GUI method (for compatibility with old code).
     * @return ClickGui instance
     */
    fun gui(): net.minecraft.client.gui.GuiScreen {
        return org.afterlike.catdueller.gui.ClickGui()
    }
}