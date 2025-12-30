package best.spaghetcodes.catdueller.utils

import java.util.*
import java.util.concurrent.ConcurrentHashMap

object TimeUtils {

    // Track all active timers for proper cleanup
    private val activeTimers = ConcurrentHashMap<Timer, String>()

    /**
     * Call a function after delay ms
     */
    fun setTimeout(function: () -> Unit, delay: Int): Timer? {
        try {
            val timer = Timer("TimeUtils-setTimeout-${System.currentTimeMillis()}", true) // daemon thread
            activeTimers[timer] = "setTimeout-${delay}ms"
            
            timer.schedule(
                object : TimerTask() {
                    override fun run() {
                        try {
                            function()
                        } catch (e: Exception) {
                            println("Error in setTimeout callback: ${e.message}")
                            e.printStackTrace()
                        } finally {
                            // Auto-cleanup after execution
                            activeTimers.remove(timer)
                            try {
                                timer.cancel()
                            } catch (e: Exception) {
                                // Ignore cleanup errors
                            }
                        }
                    }
                }, delay.toLong()
            )
            return timer
        } catch (e: Exception) {
            println("Error scheduling timer with ${delay}ms: " + e.message)
            e.printStackTrace()
        }
        return null
    }

    /**
     * Call a function every interval ms after delay ms
     */
    fun setInterval(function: () -> Unit, delay: Int, interval: Int): Timer? {
        try {
            val timer = Timer("TimeUtils-setInterval-${System.currentTimeMillis()}", true) // daemon thread
            activeTimers[timer] = "setInterval-${delay}ms-${interval}ms"
            
            timer.schedule(
                object : TimerTask() {
                    override fun run() {
                        try {
                            function()
                        } catch (e: Exception) {
                            println("Error in setInterval callback: ${e.message}")
                            e.printStackTrace()
                            // Don't cancel interval timers on error, just log it
                        }
                    }
                }, delay.toLong(), interval.toLong()
            )
            return timer
        } catch (e: Exception) {
            println("Error scheduling timer with ${delay}ms delay and ${interval}ms interval: " + e.message)
            e.printStackTrace()
        }
        return null
    }

    /**
     * Cancel a specific timer and remove it from tracking
     */
    fun cancelTimer(timer: Timer?) {
        if (timer != null) {
            try {
                activeTimers.remove(timer)
                timer.cancel()
            } catch (e: Exception) {
                println("Error canceling timer: ${e.message}")
            }
        }
    }

    /**
     * Cancel all active timers - call this when shutting down or disabling bot
     */
    fun cancelAllTimers() {
        val timersToCancel = activeTimers.keys.toList()
        println("Canceling ${timersToCancel.size} active timers...")
        
        for (timer in timersToCancel) {
            try {
                timer.cancel()
            } catch (e: Exception) {
                println("Error canceling timer: ${e.message}")
            }
        }
        
        activeTimers.clear()
        println("All timers canceled")
    }

    /**
     * Get count of active timers for debugging
     */
    fun getActiveTimerCount(): Int {
        return activeTimers.size
    }

    /**
     * Get info about active timers for debugging
     */
    fun getActiveTimersInfo(): String {
        return "Active timers (${activeTimers.size}): ${activeTimers.values.joinToString(", ")}"
    }

}
