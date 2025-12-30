package best.spaghetcodes.catdueller.bot

import best.spaghetcodes.catdueller.CatDueller
import net.minecraftforge.client.event.ClientChatReceivedEvent
import net.minecraftforge.event.entity.EntityJoinWorldEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object StateManager {

    enum class States {
        LOBBY,
        GAME,
        PLAYING
    }

    private var _state = States.LOBBY
    val state: States get() = _state
    var gameFull = false
    var gameStartedAt = -1L
    var lastGameDuration = 0L

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
                   ev.message.formattedText.contains("§f§lUHC Duel §r§7-")) {
            setState(States.GAME)  // Game ended, back to game lobby
            gameFull = false
            lastGameDuration = System.currentTimeMillis() - gameStartedAt
        } else if (unformatted.contains("has quit!")) {
            gameFull = false
        }
    }

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
            val oldState = _state
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
