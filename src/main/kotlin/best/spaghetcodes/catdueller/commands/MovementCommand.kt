package best.spaghetcodes.catdueller.commands

import best.spaghetcodes.catdueller.bot.MovementRecorder
import best.spaghetcodes.catdueller.bot.StateManager
import best.spaghetcodes.catdueller.utils.ChatUtils
import gg.essential.api.commands.Command
import gg.essential.api.commands.DefaultHandler
import gg.essential.api.commands.SubCommand

class MovementCommand : Command("movement") {

    @DefaultHandler
    fun handle() {
        ChatUtils.info("Lobby Movement Recording Commands:")
        ChatUtils.info("/movement record - Enable auto record lobby movement")
        ChatUtils.info("/movement stop - Disable auto record lobby movement")
        ChatUtils.info("/movement list - List all recorded patterns")
        ChatUtils.info("/movement delete <name> - Delete a pattern")
    }



    @SubCommand("list")
    fun listPatterns() {
        MovementRecorder.listPatterns()
    }

    @SubCommand("delete")
    fun deletePattern(name: String) {
        if (name.isBlank()) {
            ChatUtils.info("Please provide a pattern name to delete!")
            return
        }
        
        MovementRecorder.deletePattern(name)
    }



    @SubCommand("record")
    fun record() {
        MovementRecorder.enableAutoRecord()
    }

    @SubCommand("stop")
    fun stop() {
        MovementRecorder.disableAutoRecord()
    }


}