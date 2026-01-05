package best.spaghetcodes.catdueller.utils

import java.util.*

object UUIDChecker {

    /**
     * Check if a UUID indicates a nicked player
     * Valid UUIDs have '4' at position 13 (excluding dashes), nicked UUIDs have '2'
     * Example: 3e2b6575-1d9d-48cd-9ae0-5fb50859d4bc
     *          Position 13 (0-indexed, no dashes): 4 = valid, 2 = nicked
     */
    fun isNickedUUID(uuid: UUID): Boolean {
        return try {
            val uuidString = uuid.toString().replace("-", "")
            if (uuidString.length >= 13) {
                val char13 = uuidString[12] // 0-indexed, so position 13 is index 12
                char13 == '2'
            } else {
                false // Invalid UUID format
            }
        } catch (e: Exception) {
            ChatUtils.info("Error checking UUID format: ${e.message}")
            false
        }
    }

    /**
     * Get UUID status as string for debugging
     */
    fun getUUIDStatus(uuid: UUID): String {
        return try {
            val uuidString = uuid.toString().replace("-", "")
            if (uuidString.length >= 13) {
                when (val char13 = uuidString[12]) {
                    '4' -> "VALID"
                    '2' -> "NICKED"
                    else -> "UNKNOWN (char13: $char13)"
                }
            } else {
                "INVALID_FORMAT"
            }
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }
}