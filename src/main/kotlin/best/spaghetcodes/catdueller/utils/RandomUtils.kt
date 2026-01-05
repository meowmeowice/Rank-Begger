package best.spaghetcodes.catdueller.utils

import java.util.*
import java.util.concurrent.ThreadLocalRandom

object RandomUtils {

    /**
     * Get a random integer in a certain range
     * @param min
     * @param max
     * @return int
     */
    fun randomIntInRange(min: Int, max: Int): Int {
        return ThreadLocalRandom.current().nextInt(min, max + 1)
    }

    /**
     * Get a random double in a certain range
     * @param min
     * @param max
     * @return double
     */
    fun randomDoubleInRange(min: Double, max: Double): Double {
        val r = Random()
        return min + (max - min) * r.nextDouble()
    }

    /**
     * Get a random boolean value
     * @return bool
     */
    fun randomBool(): Boolean {
        val r = Random()
        return r.nextBoolean()
    }

    /**
     * Generate a random string with specified length
     * @param length Length of the random string
     * @param useNumbers Include numbers in the random string
     * @param useLetters Include letters in the random string
     * @return Random string
     */
    fun randomString(length: Int = 6, useNumbers: Boolean = true, useLetters: Boolean = true): String {
        val chars = buildString {
            if (useLetters) append("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ")
            if (useNumbers) append("0123456789")
        }

        if (chars.isEmpty()) return ""

        return (1..length)
            .map { chars.random() }
            .joinToString("")
    }

}
