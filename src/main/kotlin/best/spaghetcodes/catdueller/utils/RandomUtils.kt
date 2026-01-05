package best.spaghetcodes.catdueller.utils

import java.util.*
import java.util.concurrent.ThreadLocalRandom

/**
 * Utility object providing random number and string generation functions.
 *
 * Offers thread-safe random value generation for integers, doubles, booleans,
 * and alphanumeric strings with configurable parameters.
 */
object RandomUtils {

    /**
     * Generates a random integer within a specified inclusive range.
     *
     * Uses [ThreadLocalRandom] for thread-safe random number generation.
     *
     * @param min The minimum value (inclusive).
     * @param max The maximum value (inclusive).
     * @return A random integer between [min] and [max], inclusive.
     */
    fun randomIntInRange(min: Int, max: Int): Int {
        return ThreadLocalRandom.current().nextInt(min, max + 1)
    }

    /**
     * Generates a random double within a specified range.
     *
     * @param min The minimum value (inclusive).
     * @param max The maximum value (exclusive).
     * @return A random double between [min] (inclusive) and [max] (exclusive).
     */
    fun randomDoubleInRange(min: Double, max: Double): Double {
        val r = Random()
        return min + (max - min) * r.nextDouble()
    }

    /**
     * Generates a random boolean value.
     *
     * @return `true` or `false` with equal probability.
     */
    fun randomBool(): Boolean {
        val r = Random()
        return r.nextBoolean()
    }

    /**
     * Generates a random alphanumeric string with configurable character sets.
     *
     * @param length The length of the generated string. Defaults to 6.
     * @param useNumbers Whether to include digits (0-9) in the character pool. Defaults to `true`.
     * @param useLetters Whether to include letters (a-z, A-Z) in the character pool. Defaults to `true`.
     * @return A random string of the specified length, or an empty string if both
     *         [useNumbers] and [useLetters] are `false`.
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
