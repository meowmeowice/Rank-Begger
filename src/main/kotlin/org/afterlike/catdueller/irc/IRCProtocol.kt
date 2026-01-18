package org.afterlike.catdueller.irc

/**
 * Protocol constants and message builders for the IRC dodge system.
 */
object IRCProtocol {

    // Message delimiters
    const val DELIMITER = "|"

    // Client -> Server commands
    object Commands {
        const val AUTH = "AUTH"
        const val QUEUE = "QUEUE"
        const val LEAVE = "LEAVE"
    }

    // Server -> Client message types
    object ServerMessages {
        const val AUTH_OK = "AUTH_OK"
        const val AUTH_FAIL = "AUTH_FAIL"
        const val QUEUE_ALERT = "QUEUE_ALERT"
        const val LEAVE_ALERT = "LEAVE_ALERT"
        const val USER_JOIN = "USER_JOIN"
        const val USER_LEAVE = "USER_LEAVE"
        const val USER_ONLINE = "USER_ONLINE"
        const val ERROR = "ERROR"
    }

    /**
     * Builds an authentication message.
     *
     * @param hwid The hardware identifier
     * @return The formatted auth message
     */
    fun buildAuthMessage(hwid: String): String {
        return "${Commands.AUTH}$DELIMITER$hwid"
    }

    /**
     * Builds a queue notification message.
     *
     * @param gamemode The game mode (e.g., "Sumo", "Classic")
     * @param serverId The Hypixel server ID
     * @param map The map name
     * @return The formatted queue message
     */
    fun buildQueueMessage(gamemode: String, serverId: String, map: String): String {
        return "${Commands.QUEUE}$DELIMITER$gamemode$DELIMITER$serverId$DELIMITER$map"
    }

    /**
     * Builds a leave notification message.
     *
     * @param serverId The server ID being left
     * @return The formatted leave message
     */
    fun buildLeaveMessage(serverId: String): String {
        return "${Commands.LEAVE}$DELIMITER$serverId"
    }

    /**
     * Parses a server message into its components.
     *
     * @param message The raw message from the server
     * @return Pair of message type and list of arguments
     */
    fun parseServerMessage(message: String): Pair<String, List<String>> {
        val parts = message.split(DELIMITER)
        val type = parts.getOrNull(0) ?: ""
        val args = parts.drop(1)
        return Pair(type, args)
    }
}
