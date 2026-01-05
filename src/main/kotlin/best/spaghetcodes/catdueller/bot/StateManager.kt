package best.spaghetcodes.catdueller.bot

import best.spaghetcodes.catdueller.CatDueller
import net.minecraftforge.client.event.ClientChatReceivedEvent
import net.minecraftforge.event.entity.EntityJoinWorldEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

/**
 * Manages the current game state for the bot.
 *
 * Tracks transitions between lobby, pre-game, and active gameplay states by
 * monitoring chat messages and world events. Notifies the MovementRecorder
 * of state changes for recording coordination.
 *
 * States:
 * - LOBBY: In server lobby, waiting to queue
 * - GAME: In pre-game lobby, waiting for match to start
 * - PLAYING: Actively in a duel
 */
object StateManager {

    /**
     * Enumeration of possible game states.
     */
    enum class States {
        /** In server lobby, not queued for a game. */
        LOBBY,
        /** In pre-game lobby, waiting for opponent or game start. */
        GAME,
        /** Actively playing in a duel. */
        PLAYING
    }

    /** Internal backing field for current state. */
    private var _state = States.LOBBY

    /** The current game state. */
    val state: States get() = _state

    /** Whether the pre-game lobby has both players (2/2). */
    var gameFull = false

    /** Timestamp when the current game started, -1 if not in game. */
    var gameStartedAt = -1L

    /** Duration of the last completed game in milliseconds. */
    var lastGameDuration = 0L

    /**
     * Handles incoming chat messages to detect state transitions.
     *
     * Monitors for player join messages, game start indicators, and game end
     * messages to update the current state accordingly.
     *
     * @param ev The chat received event from Forge.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onChat(ev: ClientChatReceivedEvent) {
        val unformatted = ev.message.unformattedText
        if (unformatted.matches(Regex(".* has joined \\(./2\\)!"))) {
            setState(States.GAME)
            // Only call onJoinGame when someone actually joins
            MovementRecorder.onJoinGame()
            if (unformatted.matches(Regex(".* has joined \\(2/2\\)!"))) {
                gameFull = true
            }
        } else if (unformatted.contains("Opponent:")) {
            setState(States.PLAYING)
            gameStartedAt = System.currentTimeMillis()
        } else if (ev.message.formattedText.contains("§f§lSumo Duel §r§7- §r§a§l0") ||
            ev.message.formattedText.contains("§f§lOP Duel §r§7-") ||
            ev.message.formattedText.contains("§f§lClassic Duel §r§7-") ||
            ev.message.formattedText.contains("§f§lUHC Duel §r§7-")
        ) {
            setState(States.GAME)  // Game ended, back to game lobby
            gameFull = false
            lastGameDuration = System.currentTimeMillis() - gameStartedAt
        } else if (unformatted.contains("has quit!")) {
            gameFull = false
        }
    }

    /**
     * Handles world join events to reset state to LOBBY.
     *
     * When the player joins a new world (e.g., after disconnecting or teleporting),
     * resets all state tracking to initial values.
     *
     * @param ev The entity join world event from Forge.
     */
    @SubscribeEvent
    fun onJoinWorld(ev: EntityJoinWorldEvent) {
        if (CatDueller.mc.thePlayer != null && ev.entity == CatDueller.mc.thePlayer) {
            setState(States.LOBBY)
            gameFull = false
            gameStartedAt = -1L
        }
    }

    /**
     * Set state and notify MovementRecorder about state changes
     */
    private fun setState(newState: States) {
        if (_state != newState) {
            _state
            _state = newState

            // Notify MovementRecorder about state changes
            when (newState) {
                States.PLAYING -> {
                    // Game started
                    MovementRecorder.onBeforeStart()
                }

                else -> {}
            }
        }
    }

}
