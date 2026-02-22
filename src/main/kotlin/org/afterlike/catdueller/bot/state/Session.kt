package org.afterlike.catdueller.bot.state

import net.minecraft.util.EnumChatFormatting
import org.afterlike.catdueller.CatDueller
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
     * Calculates wins per hour based on elapsed time since session start.
     * Uses current seconds elapsed to predict hourly rate, rounded to 1 decimal.
     */
    fun getWinsPerHour(): String {
        if (startTime <= 0) return "None"
        val uptimeMillis = System.currentTimeMillis() - startTime
        val hoursTotal = if (uptimeMillis > 0) uptimeMillis.toDouble() / (1000.0 * 60.0 * 60.0) else 0.0
        if (hoursTotal <= 0.001) return "None"
        val dfPerHour = DecimalFormat("#.#")
        dfPerHour.roundingMode = RoundingMode.DOWN
        return dfPerHour.format(wins.toDouble() / hoursTotal)
    }

    /**
     * Generates a formatted session statistics string.
     *
     * @param winstreak The current winstreak to display. Defaults to 0.
     * @return A formatted string containing wins, losses, W/L ratio, and optionally winstreak and WPH.
     */
    fun getSession(winstreak: Int = 0): String {
        val df = DecimalFormat("#.##")
        df.roundingMode = RoundingMode.DOWN
        val ratio = df.format(wins.toFloat() / (if (losses == 0) 1F else losses.toFloat()))
        var result = "Session: ${EnumChatFormatting.GREEN}Wins: $wins${EnumChatFormatting.RESET} - ${EnumChatFormatting.RED}Losses: $losses${EnumChatFormatting.RESET} - W/L: ${EnumChatFormatting.LIGHT_PURPLE}${ratio}${EnumChatFormatting.RESET}"
        if (CatDueller.config?.showWinstreak != false) {
            result += " - WS: ${EnumChatFormatting.YELLOW}${winstreak}${EnumChatFormatting.RESET}"
        }
        if (CatDueller.config?.showWinsPerHour != false) {
            result += " - WPH: ${EnumChatFormatting.AQUA}${getWinsPerHour()}${EnumChatFormatting.RESET}"
        }
        return result
    }

}
