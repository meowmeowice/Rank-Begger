package org.afterlike.catdueller.core

import org.afterlike.catdueller.CatDueller
import org.afterlike.catdueller.bot.impl.*
import gg.essential.universal.UScreen
import gg.essential.vigilance.Vigilant
import gg.essential.vigilance.data.Property
import gg.essential.vigilance.data.PropertyType
import java.io.File

/**
 * Configuration class for CatDueller mod settings.
 *
 * Extends Vigilant to provide a GUI-based configuration system with
 * persistent storage. All settings are organized into categories
 * and automatically saved to a TOML file.
 *
 * Categories include:
 * - General: Core bot behavior and session settings
 * - Combat: Attack timing, aiming, and combat mechanics
 * - Toggling: Automatic key press triggers
 * - Sumo/Classic: Game mode specific settings
 * - Queue Dodging: Opponent filtering options
 * - Auto Requeue: Automatic queue management
 * - AutoGG: Post-game messaging
 * - Chat Messages: Automated chat responses
 * - Webhook: Discord integration
 * - Bot Crasher: Anti-bot functionality
 */
class Config : Vigilant(File(CatDueller.CONFIG_LOCATION), sortingBehavior = ConfigSorter()) {

    @Property(
        type = PropertyType.SELECTOR,
        name = "Current Bot",
        description = "The bot you want to use",
        category = "General",
        options = ["Sumo", "Classic", "OP"]
    )
    val currentBot = 0


    @Property(
        type = PropertyType.SWITCH,
        name = "Lobby Movement",
        description = "Whether or not the bot should move in pre-game lobbies.",
        category = "General",
    )
    val lobbyMovement = true

    @Property(
        type = PropertyType.SWITCH,
        name = "Use Recorded Movement",
        description = "When enabled, bot will use recorded movement patterns in lobby instead of default movement. (/movement for more info)",
        category = "General",
    )
    val useRecordedMovement = false

    @Property(
        type = PropertyType.SWITCH,
        name = "Combat Logs",
        description = "When enabled, shows combat-related debug messages. Non-combat messages are always shown.",
        category = "General",
    )
    val combatLogs = true


    @Property(
        type = PropertyType.NUMBER,
        name = "Throw After X Games",
        description = "After X games the bot will underperform and throw the game. 0 = disabled.",
        category = "General",
        min = 0,
        max = 1000,
        increment = 10
    )
    val throwAfterGames = 0

    @Property(
        type = PropertyType.SLIDER,
        name = "Disconnect After X Games",
        description = "After X games the bot will toggle off and disconnect. 0 = disabled.",
        category = "General",
        min = 0,
        max = 10000
    )
    val disconnectAfterGames = 0

    @Property(
        type = PropertyType.NUMBER,
        name = "Disconnect After X Minutes",
        description = "After X minutes the bot will toggle off and disconnect. 0 = disabled",
        category = "General",
        min = 0,
        max = 500,
        increment = 30
    )
    val disconnectAfterMinutes = 0

    @Property(
        type = PropertyType.TEXT,
        name = "Server IP",
        description = "Server IP address to reconnect to (if you have your own prefered proxy ip)",
        category = "General"
    )
    val serverIP = "mc.hypixel.net"

    @Property(
        type = PropertyType.SWITCH,
        name = "Dynamic Break",
        description = "Whether to continue playing after certain wait time",
        category = "General"
    )
    val autoReconnectAfterDisconnect = true

    @Property(
        type = PropertyType.NUMBER,
        name = "Dynamic Break Wait Time (Minutes)",
        description = "How many minutes to wait before reconnecting after disconnect",
        category = "General",
        min = 0,
        max = 120,
        increment = 5
    )
    val reconnectWaitMinutes = 30

    @Property(
        type = PropertyType.NUMBER,
        name = "Dynamic Break Variance",
        description = "Random variance percentage for both disconnect time and wait time (±)",
        category = "General",
        min = 0,
        max = 50,
        increment = 5
    )
    val dynamicBreakVariance = 30

    @Property(
        type = PropertyType.SWITCH,
        name = "Lobby Sit During Dynamic Break",
        description = "Instead of disconnecting during dynamic break, stay in lobby and perform sitting movements",
        category = "General"
    )
    val lobbySitDuringDynamicBreak = false

    @Property(
        type = PropertyType.SWITCH,
        name = "Pause When Internet Unstable",
        description = "Pause requeuing when ping exceeds 250ms, resume after ping stays below 250ms for 1 minute",
        category = "General"
    )
    val pauseWhenInternetUnstable = false

    @Property(
        type = PropertyType.SWITCH,
        name = "Big Break Time",
        description = "Enable automatic big break during specified hours",
        category = "General"
    )
    val bigBreakEnabled = false

    @Property(
        type = PropertyType.NUMBER,
        name = "Big Break Start Hour",
        description = "Hour to start big break (0-23, 24-hour format)",
        category = "General",
        min = 0,
        max = 23
    )
    val bigBreakStartHour = 13

    @Property(
        type = PropertyType.NUMBER,
        name = "Big Break End Hour",
        description = "Hour to end big break (0-23, 24-hour format)",
        category = "General",
        min = 0,
        max = 23
    )
    val bigBreakEndHour = 17

    @Property(
        type = PropertyType.DECIMAL_SLIDER,
        name = "CPS",
        description = "Target clicks per second (with randomize)",
        category = "Combat",
        minF = 1.0f,
        maxF = 20.0f,
        decimalPlaces = 1
    )
    val cps = 12.0f

    @Property(
        type = PropertyType.NUMBER,
        name = "Horizontal Look Speed",
        description = "How fast the bot can look left/right (lower number = less snappy, slightly less accurate when teleporting)",
        category = "Combat",
        min = 5,
        max = 30,
        increment = 1
    )
    val lookSpeedHorizontal = 10

    @Property(
        type = PropertyType.NUMBER,
        name = "Vertical Look Speed",
        description = "How fast the bot can look up/down (lower number = less snappy, slightly less accurate when teleporting)",
        category = "Combat",
        min = 1,
        max = 20,
        increment = 1
    )
    val lookSpeedVertical = 5

    @Property(
        type = PropertyType.DECIMAL_SLIDER,
        name = "Look Randomization",
        description = "How much randomization should happen when looking (higher number = more jittery aim)",
        category = "Combat",
        minF = 0f,
        maxF = 2f,
    )
    val lookRand = 0.3f

    @Property(
        type = PropertyType.SWITCH,
        name = "Vertical Multipoint",
        description = "Enable vertical multipoint aiming (50 points). Disable to aim at head instead",
        category = "Combat",
    )
    val verticalMultipoint = true

    @Property(
        type = PropertyType.SWITCH,
        name = "Disable Aiming",
        description = "Disable mouse tracking/aiming (no aim assist)",
        category = "Combat",
    )
    val disableAiming = false

    @Property(
        type = PropertyType.NUMBER,
        name = "Max Look Distance",
        description = "How close the opponent has to be before the bot starts tracking them",
        category = "Combat",
        min = 120,
        max = 180,
        increment = 5
    )
    val maxDistanceLook = 150

    @Property(
        type = PropertyType.NUMBER,
        name = "Max Attack Distance",
        description = "How close the opponent has to be before the bot starts attacking them",
        category = "Combat",
        min = 3,
        max = 15,
        increment = 1
    )
    val maxDistanceAttack = 5

    @Property(
        type = PropertyType.NUMBER,
        name = "Projectiles Delayed Ticks",
        description = "Additional ticks to add to position prediction for high ping compensation (0 = default, higher values for higher ping)",
        category = "Classic",
        min = 0,
        max = 10,
        increment = 1
    )
    val predictionTicksBonus = 0

    @Property(
        type = PropertyType.DECIMAL_SLIDER,
        name = "Counter Strafe Multiplier",
        description = "Prediction multiplier when counter-strafing (both players moving away laterally)",
        category = "Classic",
        minF = 0.5f,
        maxF = 3.0f,
        decimalPlaces = 1
    )
    val counterStrafeBonus = 1.5f

    @Property(
        type = PropertyType.SWITCH,
        name = "Enable Rod Trick",
        description = "Enable jumping after using rod at close range (< 5 blocks)",
        category = "Classic"
    )
    val enableRodJump = true

    @Property(
        type = PropertyType.SLIDER,
        name = "Rod Trick Delay",
        description = "Delay in milliseconds before jumping after using rod at close range",
        category = "Classic",
        min = 0,
        max = 500,
        increment = 25
    )
    val rodJumpDelay = 200

    @Property(
        type = PropertyType.SWITCH,
        name = "Enable W-Tap",
        description = "Enable W-Tap/Sprint Reset after attacking",
        category = "Combat"
    )
    val enableWTap = true

    @Property(
        type = PropertyType.NUMBER,
        name = "W-Tap Delay",
        description = "Delay in milliseconds before executing W-Tap after attack",
        category = "Combat",
        min = 0,
        max = 300,
        increment = 25
    )
    val wTapDelay = 100

    @Property(
        type = PropertyType.SWITCH,
        name = "Sprint Reset (No Stop)",
        description = "Use sprint reset instead of W-Tap (stops sprinting during W-Tap period)",
        category = "Combat"
    )
    val sprintReset = false

    @Property(
        type = PropertyType.SWITCH,
        name = "Hold Left Click",
        description = "Hold Left Click if you wanna use external autoclicker (requires minecraft focused)",
        category = "Combat",
    )
    val holdLeftClick = false

    @Property(
        type = PropertyType.SWITCH,
        name = "Hit Select",
        description = "Enable hit select for optimal attack timing",
        category = "Combat"
    )
    val hitSelect = false

    @Property(
        type = PropertyType.SLIDER,
        name = "Hit Select Pause Duration",
        description = "Maximum time to wait for hit select conditions before forcing attack (ms)",
        category = "Combat",
        min = 0,
        max = 500
    )
    val hitSelectDelay = 400

    @Property(
        type = PropertyType.SLIDER,
        name = "Hit Later In Trades",
        description = "Wait this many ms after being hit before attacking in the 500ms cycle (0 = disabled)",
        category = "Combat",
        min = 0,
        max = 500
    )
    val hitLaterInTrades = 0

    @Property(
        type = PropertyType.SWITCH,
        name = "Wait For First Hit",
        description = "Wait for opponent to attack first before starting to attack (when not at edge and not in hit select cycle)",
        category = "Combat"
    )
    val waitForFirstHit = false

    @Property(
        type = PropertyType.SLIDER,
        name = "Wait For First Hit Timeout",
        description = "Maximum time to wait after crosshair is on opponent before forcing attack (ms)",
        category = "Combat",
        min = 50,
        max = 500
    )
    val waitForFirstHitTimeout = 150

    /** Blink tap feature toggle (currently disabled). */
    val blinkTap = false

    /** Distance threshold for blink tap activation in blocks. */
    val blinkTapDistance = 8.0f

    /** Key to press when blink tap triggers. */
    val blinkTapKey = "Q"

    /** Delay before second blink tap key press in milliseconds. */
    val blinkTapSecondPressDelay = 0


    @Property(
        type = PropertyType.SLIDER,
        name = "Hit Select Cancel Rate",
        description = "Chance to cancel a click from hit select",
        category = "Combat",
        min = 0,
        max = 100
    )
    val hitSelectCancelRate = 100

    @Property(
        type = PropertyType.SLIDER,
        name = "Missed Hits Cancel Rate",
        description = "Chance to cancel a click when target is in range but hit select fails",
        category = "Combat",
        min = 0,
        max = 100
    )
    val missedHitsCancelRate = 0


    @Property(
        type = PropertyType.SWITCH,
        name = "Hit Select At Edge",
        description = "Enable hit select when near edge (wait for opponent attack)",
        category = "Sumo"
    )
    val hitSelectAtEdge = false

    @Property(
        type = PropertyType.SWITCH,
        name = "Jump When Hit Selecting",
        description = "Jump when distance = 7 while edge hit selecting",
        category = "Sumo"
    )
    val distance7Jump = true

    @Property(
        type = PropertyType.SWITCH,
        name = "Hurt Strafe",
        description = "Enable hurt strafe - strafe on next hit when hurt",
        category = "Combat"
    )
    val hurtStrafe = true

    @Property(
        type = PropertyType.SWITCH,
        name = "S Tap",
        description = "Stop forward briefly when opponent at edge (no backward movement)",
        category = "Sumo"
    )
    val sTap = true

    @Property(
        type = PropertyType.DECIMAL_SLIDER,
        name = "S Tap Distance",
        description = "Distance threshold for S tap activation (blocks)",
        category = "Sumo",
        minF = 2.0f,
        maxF = 6.0f,
    )
    val sTapDistance = 3.7f

    @Property(
        type = PropertyType.SWITCH,
        name = "Stop When Opponent At Edge",
        description = "Stop forward movement when opponent is at edge until timeout or hurtTime > 0",
        category = "Sumo"
    )
    val stopWhenOpponentAtEdge = false

    @Property(
        type = PropertyType.NUMBER,
        name = "Stop At Edge Duration",
        description = "How long to stop forward movement when opponent at edge (milliseconds)",
        category = "Sumo",
        min = 100,
        max = 2000,
        increment = 100
    )
    val stopAtEdgeDuration = 500

    @Property(
        type = PropertyType.SWITCH,
        name = "Freeze When Off Edge",
        description = "Press freeze keybind when player is off edge",
        category = "Sumo"
    )
    val freezeWhenOffEdge = false

    @Property(
        type = PropertyType.TEXT,
        name = "Freeze Bind",
        description = "Keybind to press when off edge (e.g. F1, SPACE, etc.)",
        category = "Sumo"
    )
    val freezeBind = "F1"

    @Property(
        type = PropertyType.SLIDER,
        name = "Jump Reset",
        description = "Chance to jump reset when hit (above 30 may staff ban in sumo)",
        category = "Combat",
        min = 0,
        max = 100
    )
    val jumpVelocity = 0

    @Property(
        type = PropertyType.SWITCH,
        name = "Enable Retreat",
        description = "Back up and rod when low health and at disadvantage",
        category = "Classic"
    )
    val enableRetreat = true

    @Property(
        type = PropertyType.SWITCH,
        name = "Enable Arrow Blocking",
        description = "Block arrows when opponent is drawing bow (distance > 6 blocks)",
        category = "Classic"
    )
    val enableArrowBlocking = true

    @Property(
        type = PropertyType.SWITCH,
        name = "Dodge Arrows",
        description = "When opponent is drawing bow and distance > 6 blocks, randomly turn 90 degrees and move forward/backward",
        category = "Classic"
    )
    val dodgeArrow = false

    @Property(
        type = PropertyType.SWITCH,
        name = "Enable Taunt Messages",
        description = "Send taunt messages when game duration exceeds threshold",
        category = "Chat Messages"
    )
    val enableTauntMessages = false

    @Property(
        type = PropertyType.NUMBER,
        name = "Taunt Threshold (Seconds)",
        description = "Send taunt message when game duration exceeds this many seconds",
        category = "Chat Messages",
        min = 10,
        max = 120,
        increment = 5
    )
    val tauntThresholdSeconds = 20

    @Property(
        type = PropertyType.SWITCH,
        name = "Dodge Standing Still",
        description = "Dodge if opponent doesnt move in pre-game lobby",
        category = "Queue Dodging",
    )
    val dodgeStandingStill = true

    @Property(
        type = PropertyType.SWITCH,
        name = "Sumo Long Jump",
        description = "Open inventory and hold space + W before game start to long jump",
        category = "Sumo",
    )
    val sumoLongJump = false

    @Property(
        type = PropertyType.SWITCH,
        name = "Random Strafe",
        description = "Use random strafe when distance > 6 blocks",
        category = "Sumo",
    )
    val randomStrafe = true

    @Property(
        type = PropertyType.NUMBER,
        name = "Strafe Switch Delay",
        description = "How many ms after entering 6 blocks range to switch strafe direction",
        category = "Sumo",
        min = 0,
        max = 1500,
        increment = 100
    )
    val strafeSwitchDelay = 1000

    @Property(
        type = PropertyType.SWITCH,
        name = "Enable Strafe Switch",
        description = "Enable switching from mid-range strafe to close-range strafe. If disabled, always use mid-range strafe.",
        category = "Sumo"
    )
    val enableStrafeSwitch = true

    @Property(
        type = PropertyType.SWITCH,
        name = "Dodge Huaxi",
        description = "Automatically dodge when Huaxi server is detected in scoreboard",
        category = "Queue Dodging"
    )
    val dodgeHuaxi = false

    @Property(
        type = PropertyType.SELECTOR,
        name = "Dodge Particle Type",
        description = "Dodge when specific particle type detected within 1-20 blocks in lobby (excludes particles at player position)",
        category = "Queue Dodging",
        options = ["None", "Slime", "Portal", "Rainbow", "Heart", "Angry Villager"]
    )
    val dodgeParticleType = 0  // 0=None, 1=Slime, 2=Portal, 3=Rainbow, 4=Heart, 5=Angry Villager


    @Property(
        type = PropertyType.SWITCH,
        name = "Blink At Edge",
        description = "Automatically blink back to center when near edge or air",
        category = "Toggling"
    )

    val blinkAtEdge = false


    @Property(
        type = PropertyType.TEXT,
        name = "Blink Key",
        description = "Key to use for blinking (e.g., Q, E, R, F, etc.)",
        category = "Toggling"
    )

    val blinkKey = "Q"


    @Property(
        type = PropertyType.SWITCH,
        name = "Toggle Blatant at Edge",
        description = "Automatically press blatant toggle key when player is near edge",
        category = "Toggling",
    )
    val toggleBlatantAtEdge = false

    @Property(
        type = PropertyType.DECIMAL_SLIDER,
        name = "Toggle Blatant Distance",
        description = "Distance from center to trigger blatant mode (blocks)",
        category = "Toggling",
        minF = 1.0f,
        maxF = 10.0f,
    )
    val toggleBlatantDistance = 6.0f

    @Property(
        type = PropertyType.SWITCH,
        name = "Toggle Blatant on Blacklisted",
        description = "Automatically press a key when encountering players you've lost to before",
        category = "Toggling",
    )
    val toggleBlatantOnBlacklisted = false

    @Property(
        type = PropertyType.TEXT,
        name = "Blatant Toggle Key",
        description = "Key to press when encountering blacklisted players (e.g., 'F1', 'R', 'SPACE')",
        category = "Toggling",
    )
    val blatantToggleKey = "F1"

    @Property(
        type = PropertyType.TEXT,
        name = "Blacklisted Players",
        description = "Manually add players to always toggle blatant mode against (comma-separated). Auto-detected players are session-only.",
        category = "Toggling",
    )
    var blacklistedPlayers = ""

    @Property(
        type = PropertyType.SWITCH,
        name = "Clip Losses",
        description = "Press F8 when losing a game or when leaderboard winstreak is lower than session winstreak",
        category = "General",
    )
    val clipLosses = false

    @Property(
        type = PropertyType.SWITCH,
        name = "Bot Crasher Mode",
        description = "Enable bot crasher mode with configurable options below (requires having Accuracy in your ign and disable duels chat visibility)",
        category = "Bot Crasher",
    )
    val botCrasherMode = false

    @Property(
        type = PropertyType.SWITCH,
        name = "Auto Requeue",
        description = "Automatically requeue if game doesn't end within 5 seconds",
        category = "Bot Crasher",
    )
    val botCrasherAutoRequeue = true

    @Property(
        type = PropertyType.SWITCH,
        name = "Spam Players",
        description = "Spam disconnected and target players",
        category = "Bot Crasher",
    )
    val botCrasherSpamPlayers = true

    @Property(
        type = PropertyType.TEXT,
        name = "Target Players",
        description = "Manually add players to spam (comma-separated). These will be combined with auto-detected disconnected players.",
        category = "Bot Crasher",
    )
    var botCrasherTargetPlayers = ""

    @Property(
        type = PropertyType.SWITCH,
        name = "Enable AutoGG",
        description = "Send a gg message after every game",
        category = "AutoGG",
    )
    val sendAutoGG = true

    @Property(
        type = PropertyType.TEXT,
        name = "AutoGG Message",
        description = "AutoGG message the bot sends after every game",
        category = "AutoGG",
    )
    val ggMessage = "gg"

    @Property(
        type = PropertyType.NUMBER,
        name = "AutoGG Delay",
        description = "How long to wait after the game before sending the message",
        category = "AutoGG",
        min = 50,
        max = 1000,
        increment = 50
    )
    val ggDelay = 100

    @Property(
        type = PropertyType.SWITCH,
        name = "Game Start Message",
        description = "Send a message as soon as the game starts",
        category = "AutoGG",
    )
    val sendStartMessage = false

    @Property(
        type = PropertyType.TEXT,
        name = "Start Message",
        description = "Message to send at the beginning of the game",
        category = "AutoGG",
    )
    val startMessage = "GL HF!"

    @Property(
        type = PropertyType.NUMBER,
        name = "Start Message Delay",
        description = "How long to wait before sending the start message",
        category = "AutoGG",
        min = 50,
        max = 1000,
        increment = 50
    )
    val startMessageDelay = 100

    @Property(
        type = PropertyType.NUMBER,
        name = "Auto Requeue Delay",
        description = "How long to wait after a game before re-queueing",
        category = "Auto Requeue",
        min = 500,
        max = 5000,
        increment = 50
    )
    val autoRqDelay = 2500

    @Property(
        type = PropertyType.NUMBER,
        name = "Requeue After No Game",
        description = "How long to wait before re-queueing if no game starts",
        category = "Auto Requeue",
        min = 15,
        max = 60,
        increment = 5
    )
    val rqNoGame = 30

    @Property(
        type = PropertyType.SWITCH,
        name = "Paper Requeue",
        description = "Use the paper to requeue",
        category = "Auto Requeue",
    )
    val paperRequeue = true

    @Property(
        type = PropertyType.SWITCH,
        name = "Fast Requeue",
        description = "Faster Requeue (no rewards)",
        category = "Auto Requeue",
    )
    val fastRequeue = true

    @Property(
        type = PropertyType.SWITCH,
        name = "Force Requeue",
        description = "Automatically force requeue if normal requeue fails or takes too long",
        category = "Auto Requeue",
    )
    val forceRequeue = true


    @Property(
        type = PropertyType.SWITCH,
        name = "Delay Requeue After Losing",
        description = "Add extra delay before requeuing after losing a game",
        category = "Auto Requeue",
    )
    val delayRequeueAfterLosing = false

    @Property(
        type = PropertyType.NUMBER,
        name = "Losing Requeue Delay",
        description = "Extra delay in seconds before requeuing after losing",
        category = "Auto Requeue",
        min = 1,
        max = 30,
        increment = 1
    )
    val losingRequeueDelay = 5

    @Property(
        type = PropertyType.SWITCH,
        name = "Guild Dodge",
        description = "Dodge when receiving server ID from guild chat",
        category = "Queue Dodging",
    )
    val guildDodge = false

    @Property(
        type = PropertyType.SWITCH,
        name = "Send Server ID to Guild",
        description = "Automatically send current server ID to guild chat",
        category = "Queue Dodging",
    )
    val sendServerToGuild = false

    @Property(
        type = PropertyType.SWITCH,
        name = "DM Dodge",
        description = "Dodge when receiving server ID from DM",
        category = "Queue Dodging",
    )
    val dmDodge = false

    @Property(
        type = PropertyType.SWITCH,
        name = "Send Server ID to DM",
        description = "Automatically send current server ID to specified player via DM",
        category = "Queue Dodging",
    )
    val sendServerToDM = false

    @Property(
        type = PropertyType.TEXT,
        name = "DM Target Player",
        description = "Player name to send server ID to via DM",
        category = "Queue Dodging",
    )
    val dmTargetPlayer = ""

    // IRC Dodge System (IRC auth is always active)
    @Property(
        type = PropertyType.SWITCH,
        name = "IRC Dodge",
        description = "Enable queue dodging coordination with other mod users (auth always active)",
        category = "Queue Dodging",
    )
    val ircDodgeEnabled = true
    /*
    @Property(
        type = PropertyType.TEXT,
        name = "IRC Server",
        description = "IRC server hostname or IP address",
        category = "Queue Dodging",
    )
    */
    val ircServerHost = "catdueller.afterlike.org"
    /*
    @Property(
        type = PropertyType.NUMBER,
        name = "IRC Port",
        description = "IRC server port number (443 for wss://)",
        category = "Queue Dodging",
        min = 1,
        max = 65535,
    )
    */
    val ircServerPort = 443
    /*
    @Property(
        type = PropertyType.SWITCH,
        name = "Show IRC Alerts",
        description = "Display chat notifications when other users queue",
        category = "Queue Dodging",
    )
    */
    val showIRCAlerts = false
    /*
    @Property(
        type = PropertyType.SWITCH,
        name = "Auto IRC Dodge",
        description = "Automatically dodge when another user is on the same server",
        category = "Queue Dodging",
    )
    */
    val autoIRCDodge = true

    @Property(
        type = PropertyType.SWITCH,
        name = "? ok",
        description = "Auto reply ? and ok to dms from randoms",
        category = "Chat Messages",
    )
    val autoReplyDM = false

    @Property(
        type = PropertyType.SWITCH,
        name = "Anti Ragebait",
        description = "Send 'LLLLL' when someone says L",
        category = "Chat Messages",
    )
    val antiRagebait = false

    @Property(
        type = PropertyType.SWITCH,
        name = "Send Webhook Messages",
        description = "Whether or not the bot should send a discord webhook message after each game.",
        category = "Webhook",
    )
    val sendWebhookMessages = false

    @Property(
        type = PropertyType.TEXT,
        name = "Discord Webhook URL",
        description = "The webhook URL to send messages to.",
        category = "Webhook",
    )
    val webhookURL = ""


    /** Enable custom rotation setting (boosting feature, currently disabled). */
    val setMyRotation = false

    /** Target yaw angle for rotation setting. */
    val myTargetYaw = 0.0f

    /** Target pitch angle for rotation setting. */
    val myTargetPitch = 0.0f

    /** Angle tolerance for rotation matching. */
    val myAngleTolerance = 0.01f

    /** Dodge opponents with unexpected rotation (boosting feature, currently disabled). */
    val dodgeWrongRotation = false

    /** Dodge opponents with matching rotation (boosting feature, currently disabled). */
    val dodgeEachOtherBot = false

    /** Expected opponent yaw angle for rotation matching. */
    val expectedOpponentYaw = 0.0f

    /** Expected opponent pitch angle for rotation matching. */
    val expectedOpponentPitch = 0.0f

    /** Tolerance for opponent angle matching. */
    val opponentAngleTolerance = 0.5f

    /** Show rotation debug information. */
    val showRotationDebug = false

    /** Leave game when opponent freezes (boosting feature, currently disabled). */
    val leaveWhenOpponentFreeze = false

    /**
     * Map of bot index to bot instance for each available game mode.
     * Index corresponds to the selector options in currentBot property.
     */
    val bots = mapOf(0 to Sumo(), 1 to Classic(), 2 to OP(), 3 to Boxing(), 4 to Combo())

    init {
        try {
            addDependency("webhookURL", "sendWebhookMessages")
            addDependency("ggMessage", "sendAutoGG")
            addDependency("ggDelay", "sendAutoGG")
            addDependency("startMessage", "sendStartMessage")
            addDependency("startMessageDelay", "sendStartMessage")
            addDependency("tauntThresholdSeconds", "enableTauntMessages")
            addDependency("blatantToggleKey", "toggleBlatantOnBlacklisted")
            addDependency("blacklistedPlayers", "toggleBlatantOnBlacklisted")
            addDependency("blatantToggleKey", "toggleBlatantAtEdge")
            addDependency("toggleBlatantDistance", "toggleBlatantAtEdge")
            addDependency("dmTargetPlayer", "sendServerToDM")
            addDependency("botCrasherAutoRequeue", "botCrasherMode")
            addDependency("botCrasherSpamPlayers", "botCrasherMode")
            addDependency("botCrasherTargetPlayers", "botCrasherMode")
            addDependency("sTapDistance", "sTap")
            addDependency("stopAtEdgeDuration", "stopWhenOpponentAtEdge")
            addDependency("freezeBind", "freezeWhenOffEdge")
            addDependency("losingRequeueDelay", "delayRequeueAfterLosing")
            addDependency("wTapDelay", "enableWTap")
            addDependency("sprintReset", "enableWTap")
            addDependency("hitSelectDelay", "hitSelect")
            addDependency("hitLaterInTrades", "hitSelect")
            addDependency("waitForFirstHitTimeout", "waitForFirstHit")
            addDependency("distance7Jump", "hitSelectAtEdge")
        } catch (e: Exception) {
            println("Failed to add dependencies: ${e.message}")
            e.printStackTrace()
        }

        try {
            registerListener("currentBot") { bot: Int ->
                if (CatDueller.bot != null && bots.keys.contains(bot)) {
                    CatDueller.swapBot(bots[bot]!!)
                }
            }
        } catch (e: Exception) {
            println("Failed to register listener: ${e.message}")
            e.printStackTrace()
        }

        try {
            initialize()
        } catch (e: Exception) {
            println("Failed to initialize config: ${e.message}")
            e.printStackTrace()
        }
    }
}
