package best.spaghetcodes.catdueller.utils

import java.util.*

/**
 * Utility object for analyzing UUID version information to detect nicked players.
 *
 * UUIDs follow specific version formats where the 13th character (excluding dashes)
 * indicates the UUID version. Version 4 UUIDs are randomly generated and indicate
 * legitimate accounts, while version 2 UUIDs suggest a nicked/modified player.
 */
object UUIDChecker {

    /**
     * Determines whether a UUID indicates a nicked (disguised) player.
     *
     * Checks the UUID version digit at position 13 (0-indexed as 12, excluding dashes).
     * Version 4 ('4') indicates a valid randomly-generated UUID, while version 2 ('2')
     * indicates a nicked player.
     *
     * Example UUID: `3e2b6575-1d9d-48cd-9ae0-5fb50859d4bc`
     * The character at position 13 (no dashes) is '4', indicating a valid UUID.
     *
     * @param uuid The UUID to check.
     * @return `true` if the UUID indicates a nicked player (version 2), `false` otherwise.
     */
    fun isNickedUUID(uuid: UUID): Boolean {
        return try {
            val uuidString = uuid.toString().replace("-", "")
            if (uuidString.length >= 13) {
                val char13 = uuidString[12]
                char13 == '2'
            } else {
                false
            }
        } catch (e: Exception) {
            ChatUtils.info("Error checking UUID format: ${e.message}")
            false
        }
    }

    /**
     * Returns a human-readable status string describing the UUID type.
     *
     * Useful for debugging and logging purposes.
     *
     * @param uuid The UUID to analyze.
     * @return A status string: "VALID" for version 4, "NICKED" for version 2,
     *         "UNKNOWN" for other versions, "INVALID_FORMAT" for malformed UUIDs,
     *         or an error message if parsing fails.
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