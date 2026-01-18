package org.afterlike.catdueller.bot.state

import net.minecraft.util.EnumChatFormatting
import java.math.RoundingMode
import java.text.DecimalFormat

/**
 * Tracks session statistics for the current bot runtime.
 *
 * Records wins and losses since the bot was started, providing formatted
 * session summary strings with win/loss ratio calculations.
 */
object Session {

    /** Number of wins in the current session. */
    var wins = 0

    /** Number of losses in the current session. */
    var losses = 0

    /** Timestamp when the session started, -1 if not yet initialized. */
    var startTime: Long = -1

    /**
     * Generates a formatted session statistics string.
     *
     * Calculates the win/loss ratio and formats all statistics with Minecraft
     * color codes for display in chat.
     *
     * @param winstreak The current winstreak to display. Defaults to 0.
     * @return A formatted string containing wins, losses, W/L ratio, and winstreak.
     */
    fun getSession(winstreak: Int = 0): String {
        val df = DecimalFormat("#.##")
        df.roundingMode = RoundingMode.DOWN
        val ratio = df.format(wins.toFloat() / (if (losses == 0) 1F else losses.toFloat()))
        return "Session: ${EnumChatFormatting.GREEN}Wins: $wins${EnumChatFormatting.RESET} - ${EnumChatFormatting.RED}Losses: $losses${EnumChatFormatting.RESET} - W/L: ${EnumChatFormatting.LIGHT_PURPLE}${ratio}${EnumChatFormatting.RESET} - WS: ${EnumChatFormatting.YELLOW}${winstreak}${EnumChatFormatting.RESET}"
    }

}
