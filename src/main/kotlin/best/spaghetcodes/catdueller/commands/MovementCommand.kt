package best.spaghetcodes.catdueller.commands

import best.spaghetcodes.catdueller.bot.MovementRecorder
import best.spaghetcodes.catdueller.utils.ChatUtils
import gg.essential.api.commands.Command
import gg.essential.api.commands.DefaultHandler
import gg.essential.api.commands.SubCommand

/**
 * Command handler for managing lobby movement pattern recording.
 *
 * Provides the `/movement` command with subcommands to record, stop, list,
 * and delete movement patterns used for automated lobby navigation.
 */
class MovementCommand : Command("movement") {

    /**
     * Default command handler that displays all available movement subcommands.
     *
     * Invoked when the user runs `/movement` without any subcommands.
     */
    @DefaultHandler
    fun handle() {
        ChatUtils.info("Lobby Movement Recording Commands:")
        ChatUtils.info("/movement record - Enable auto record lobby movement")
        ChatUtils.info("/movement stop - Disable auto record lobby movement")
        ChatUtils.info("/movement list - List all recorded patterns")
        ChatUtils.info("/movement delete <name> - Delete a pattern")
    }

    /**
     * Lists all saved movement patterns.
     *
     * Displays the names of all recorded movement patterns stored by the MovementRecorder.
     */
    @SubCommand("list")
    fun listPatterns() {
        MovementRecorder.listPatterns()
    }

    /**
     * Deletes a specific movement pattern by name.
     *
     * @param name The name of the movement pattern to delete.
     */
    @SubCommand("delete")
    fun deletePattern(name: String) {
        if (name.isBlank()) {
            ChatUtils.info("Please provide a pattern name to delete!")
            return
        }

        MovementRecorder.deletePattern(name)
    }

    /**
     * Enables automatic movement recording.
     *
     * Starts recording the player's movement inputs for later playback.
     */
    @SubCommand("record")
    fun record() {
        MovementRecorder.enableAutoRecord()
    }

    /**
     * Disables automatic movement recording.
     *
     * Stops the current recording session and saves the recorded pattern.
     */
    @SubCommand("stop")
    fun stop() {
        MovementRecorder.disableAutoRecord()
    }
}