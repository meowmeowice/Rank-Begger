package org.afterlike.catdueller.utils.client

import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Utility object for scheduling delayed and recurring function executions.
 *
 * Provides JavaScript-like `setTimeout` and `setInterval` functionality with
 * automatic timer tracking and cleanup capabilities. All timers run as daemon
 * threads and are tracked for bulk cancellation during shutdown.
 */
object TimerUtil {

    /**
     * Thread-safe map tracking all active timers for cleanup purposes.
     * Maps each [Timer] to a descriptive string identifier.
     */
    private val activeTimers = ConcurrentHashMap<Timer, String>()

    /**
     * Schedules a function to execute once after a specified delay.
     *
     * The timer automatically cleans itself up after execution. Errors in the
     * callback are caught and logged without propagating.
     *
     * @param function The callback function to execute after the delay.
     * @param delay The delay in milliseconds before executing the function.
     * @return The [Timer] instance for manual cancellation, or `null` if scheduling failed.
     */
    fun setTimeout(function: () -> Unit, delay: Int): Timer? {
        try {
            val timer = Timer("TimeUtils-setTimeout-${System.currentTimeMillis()}", true)
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
                            activeTimers.remove(timer)
                            try {
                                timer.cancel()
                            } catch (_: Exception) {
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
     * Schedules a function to execute repeatedly at a fixed interval.
     *
     * The function will first execute after the initial delay, then continue
     * executing at each interval. Errors in the callback are logged but do not
     * cancel the interval.
     *
     * @param function The callback function to execute at each interval.
     * @param delay The initial delay in milliseconds before the first execution.
     * @param interval The interval in milliseconds between subsequent executions.
     * @return The [Timer] instance for manual cancellation, or `null` if scheduling failed.
     */
    fun setInterval(function: () -> Unit, delay: Int, interval: Int): Timer? {
        try {
            val timer = Timer("TimeUtils-setInterval-${System.currentTimeMillis()}", true)
            activeTimers[timer] = "setInterval-${delay}ms-${interval}ms"

            timer.schedule(
                object : TimerTask() {
                    override fun run() {
                        try {
                            function()
                        } catch (e: Exception) {
                            println("Error in setInterval callback: ${e.message}")
                            e.printStackTrace()
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
     * Cancels all active timers created by this utility.
     *
     * Should be called during application shutdown or when disabling the bot
     * to ensure proper resource cleanup. Logs the number of timers canceled.
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

}
