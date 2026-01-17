package org.afterlike.catdueller.irc

import org.afterlike.catdueller.CatDueller
import org.afterlike.catdueller.core.HWIDLock
import org.afterlike.catdueller.utils.client.ChatUtil
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLContext

/**
 * WebSocket client for the dodge coordination system.
 *
 * Connects to the central server, authenticates via HWID, and
 * broadcasts/receives queue events to coordinate dodges with other users.
 */
object IRCDodgeClient {

    private var client: DodgeWebSocketClient? = null
    private var reconnectTimer: Timer? = null
    private var authenticated = false
    private var assignedUsername: String? = null
    private var lastServerId: String? = null

    // Connection configuration
    var serverHost: String = "localhost"
    var serverPort: Int = 8765
    var enabled: Boolean = true

    // Reconnection settings
    private const val RECONNECT_DELAY_MS = 5000L
    private const val MAX_RECONNECT_ATTEMPTS = 10
    private var reconnectAttempts = 0

    // Track online users
    private val onlineUsers = ConcurrentHashMap.newKeySet<String>()

    // Callback for dodge checks
    var onQueueAlert: ((username: String, gamemode: String, serverId: String, map: String) -> Unit)? = null

    /**
     * Initializes and connects the WebSocket client.
     */
    fun initialize() {
        loadConfig()
        connect()
    }

    /**
     * Loads configuration from the mod config.
     */
    private fun loadConfig() {
        val config = CatDueller.config ?: return

        try {
            serverHost = config.ircServerHost.ifBlank { "localhost" }
            serverPort = config.ircServerPort
            enabled = config.ircDodgeEnabled
        } catch (e: Exception) {
            println("[IRCDodge] Failed to load config: ${e.message}")
        }
    }

    /**
     * Connects to the server.
     */
    private fun connect() {
        try {
            val protocol = if (serverPort == 443 || serverHost.contains(".")) "wss" else "ws"
            val uri = URI("$protocol://$serverHost:$serverPort")
            println("[IRCDodge] Connecting to $uri...")

            client = DodgeWebSocketClient(uri)

            // Configure SSL for wss://
            if (protocol == "wss") {
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, null, null)
                client?.setSocketFactory(sslContext.socketFactory)
            }

            client?.connectBlocking()
            reconnectAttempts = 0

        } catch (e: Exception) {
            println("[IRCDodge] Connection failed: ${e.message}")
            e.printStackTrace()
            ChatUtil.error("IRC connection failed: ${e.message}")
            scheduleReconnect()
        }
    }

    /**
     * Disconnects from the server.
     */
    fun disconnect() {
        println("[IRCDodge] Disconnecting...")
        reconnectTimer?.cancel()
        reconnectTimer = null
        reconnectAttempts = MAX_RECONNECT_ATTEMPTS

        try {
            client?.close()
        } catch (e: Exception) {
            println("[IRCDodge] Error during disconnect: ${e.message}")
        }

        client = null
        authenticated = false
        assignedUsername = null
        onlineUsers.clear()
    }

    /**
     * Schedules a reconnection attempt.
     */
    private fun scheduleReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            println("[IRCDodge] Max reconnection attempts reached")
            ChatUtil.error("IRC: Max reconnection attempts reached")
            return
        }

        reconnectTimer?.cancel()
        reconnectTimer = Timer("IRC-Reconnect", true)
        reconnectTimer?.schedule(object : TimerTask() {
            override fun run() {
                reconnectAttempts++
                println("[IRCDodge] Reconnection attempt $reconnectAttempts/$MAX_RECONNECT_ATTEMPTS")
                connect()
            }
        }, RECONNECT_DELAY_MS)
    }

    /**
     * Sends authentication to the server.
     */
    private fun authenticate() {
        val hwid = HWIDLock.getCurrentHWID()
        if (hwid == null) {
            println("[IRCDodge] Cannot authenticate: HWID not available")
            ChatUtil.error("IRC: HWID not available")
            return
        }

        val message = IRCProtocol.buildAuthMessage(hwid)
        sendRaw(message)
        println("[IRCDodge] Sent authentication request")
    }

    /**
     * Sends a queue notification to all connected users.
     */
    fun sendQueueInfo(gamemode: String, serverId: String, map: String) {
        if (!authenticated || client?.isOpen != true) {
            return
        }

        if (serverId == lastServerId) {
            return
        }
        lastServerId = serverId

        val message = IRCProtocol.buildQueueMessage(gamemode, serverId, map)
        sendRaw(message)
        println("[IRCDodge] Sent queue info: $gamemode on $serverId ($map)")
    }

    /**
     * Sends a leave notification when leaving a game/queue.
     */
    fun sendLeaveInfo(serverId: String? = null) {
        if (!authenticated || client?.isOpen != true) {
            return
        }

        val server = serverId ?: lastServerId ?: return
        lastServerId = null

        val message = IRCProtocol.buildLeaveMessage(server)
        sendRaw(message)
        println("[IRCDodge] Sent leave info for server $server")
    }

    /**
     * Sends a raw message to the server.
     */
    private fun sendRaw(message: String) {
        try {
            client?.send(message)
        } catch (e: Exception) {
            println("[IRCDodge] Failed to send message: ${e.message}")
        }
    }

    /**
     * Checks if the client is connected and authenticated.
     */
    fun isReady(): Boolean {
        return authenticated && client?.isOpen == true
    }

    /**
     * Gets the assigned username.
     */
    fun getUsername(): String? = assignedUsername

    /**
     * Gets the set of online users.
     */
    fun getOnlineUsers(): Set<String> = onlineUsers.toSet()

    /**
     * WebSocket client implementation.
     */
    private class DodgeWebSocketClient(uri: URI) : WebSocketClient(uri) {

        override fun onOpen(handshakedata: ServerHandshake?) {
            println("[IRCDodge] Connected to server")
            ChatUtil.info("IRC: Connected to server")
            authenticate()
        }

        override fun onMessage(message: String?) {
            if (message != null) {
                handleMessage(message)
            }
        }

        override fun onClose(code: Int, reason: String?, remote: Boolean) {
            println("[IRCDodge] Disconnected: $reason (code: $code, remote: $remote)")
            ChatUtil.info("IRC: Disconnected")
            authenticated = false
            onlineUsers.clear()
            scheduleReconnect()
        }

        override fun onError(ex: Exception?) {
            println("[IRCDodge] Error: ${ex?.message}")
            ex?.printStackTrace()
        }
    }

    /**
     * Handles incoming messages from the server.
     */
    private fun handleMessage(rawMessage: String) {
        val (type, args) = IRCProtocol.parseServerMessage(rawMessage)

        when (type) {
            IRCProtocol.ServerMessages.AUTH_OK -> handleAuthOk(args)
            IRCProtocol.ServerMessages.AUTH_FAIL -> handleAuthFail(args)
            IRCProtocol.ServerMessages.QUEUE_ALERT -> handleQueueAlert(args)
            IRCProtocol.ServerMessages.LEAVE_ALERT -> handleLeaveAlert(args)
            IRCProtocol.ServerMessages.USER_JOIN -> handleUserJoin(args)
            IRCProtocol.ServerMessages.USER_LEAVE -> handleUserLeave(args)
            IRCProtocol.ServerMessages.USER_ONLINE -> handleUserOnline(args)
            IRCProtocol.ServerMessages.ERROR -> handleError(args)
        }
    }

    private fun handleAuthOk(args: List<String>) {
        assignedUsername = args.getOrNull(0) ?: "Unknown"
        authenticated = true
        reconnectAttempts = 0

        HWIDLock.setAuthorized(true, assignedUsername)

        println("[IRCDodge] Authenticated as $assignedUsername")
    }

    private fun handleAuthFail(args: List<String>) {
        val reason = args.getOrNull(0) ?: "Unknown reason"
        authenticated = false

        HWIDLock.setAuthorized(false, null)

        ChatUtil.error("Auth failed: $reason")
        println("[IRCDodge] Auth failed: $reason")

        reconnectTimer?.cancel()
    }

    private fun handleQueueAlert(args: List<String>) {
        val username = args.getOrNull(0) ?: return
        val gamemode = args.getOrNull(1) ?: return
        val serverId = args.getOrNull(2) ?: return
        val map = args.getOrNull(3) ?: "Unknown"

        if (username.equals(assignedUsername, ignoreCase = true)) {
            return
        }

        val config = CatDueller.config
        if (config?.showIRCAlerts == true) {
            ChatUtil.info("IRC: $username queued $gamemode on $serverId ($map)")
        }


        onQueueAlert?.invoke(username, gamemode, serverId, map)
    }

    private fun handleLeaveAlert(args: List<String>) {
        val username = args.getOrNull(0) ?: return
        val serverId = args.getOrNull(1) ?: return

        if (username.equals(assignedUsername, ignoreCase = true)) {
            return
        }

        val config = CatDueller.config
        if (config?.showIRCAlerts == true) {
            ChatUtil.info("IRC: $username left $serverId")
        }

    }

    private fun handleUserJoin(args: List<String>) {
        val username = args.getOrNull(0) ?: return
        onlineUsers.add(username)

        val config = CatDueller.config
        if (config?.showIRCAlerts == true) {
            ChatUtil.info("IRC: $username connected")
        }

    }

    private fun handleUserLeave(args: List<String>) {
        val username = args.getOrNull(0) ?: return
        onlineUsers.remove(username)

        val config = CatDueller.config
        if (config?.showIRCAlerts == true) {
            ChatUtil.info("IRC: $username disconnected")
        }

    }

    private fun handleUserOnline(args: List<String>) {
        val username = args.getOrNull(0) ?: return
        onlineUsers.add(username)
    }

    private fun handleError(args: List<String>) {
        val error = args.getOrNull(0) ?: "Unknown error"
        println("[IRCDodge] Server error: $error")
        ChatUtil.error("IRC error: $error")
    }
}
