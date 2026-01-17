package org.afterlike.catdueller.utils.system

import org.afterlike.catdueller.utils.system.HWIDUtil.FALLBACK_ID
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.regex.Pattern

/**
 * Cross-platform machine identification utility.
 *
 * Retrieves a unique machine identifier using OS-native methods:
 * - Windows: MachineGuid from HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Cryptography
 * - macOS: IOPlatformUUID (Hardware UUID) from ioreg
 * - Linux: /var/lib/dbus/machine-id
 *
 * This implementation is based on the node-machine-id functionality and
 * formats the ID as a UUID-compatible string.
 */
object HWIDUtil {

    /** Fallback UUID used when the machine ID cannot be retrieved. */
    private const val FALLBACK_ID = "0ed5c84c-3e16-4c44-927d-2d76d5cac79d"

    /**
     * Retrieves the machine's unique identifier.
     *
     * Detects the operating system and calls the appropriate platform-specific
     * method to retrieve the machine ID. The result is formatted as a UUID string.
     *
     * @return The machine ID formatted as a UUID string, or [FALLBACK_ID] if retrieval fails.
     */
    fun getMachineId(): String {
        return try {
            val os = System.getProperty("os.name").lowercase()
            val rawId = when {
                os.contains("win") -> getWindowsMachineId()
                os.contains("mac") || os.contains("darwin") -> getMacMachineId()
                else -> getLinuxMachineId()
            }

            if (rawId.isNullOrEmpty()) {
                FALLBACK_ID
            } else {
                formatMachineId(rawId)
            }
        } catch (_: Exception) {
            FALLBACK_ID
        }
    }

    /**
     * Retrieves the Windows MachineGuid from the system registry.
     *
     * Executes a registry query to read the MachineGuid value from
     * HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Cryptography.
     *
     * @return The machine GUID as a lowercase hex string without dashes, or null if unavailable.
     */
    private fun getWindowsMachineId(): String? {
        return try {
            val process = Runtime.getRuntime().exec(
                arrayOf(
                    "reg", "query",
                    "HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Cryptography",
                    "/v", "MachineGuid"
                )
            )

            BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8)).use { reader ->
                reader.lineSequence().forEach { line ->
                    if (line.contains("MachineGuid")) {
                        val parts = line.trim().split("\\s+".toRegex())
                        for (i in parts.indices.reversed()) {
                            val part = parts[i].trim()
                            if (part.matches("^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$".toRegex())) {
                                return part.replace("-", "").lowercase()
                            }
                        }
                    }
                }
            }
            process.waitFor()
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Retrieves the macOS hardware UUID using the ioreg command.
     *
     * Executes `ioreg -rd1 -c IOPlatformExpertDevice` and parses the
     * IOPlatformUUID value from the output.
     *
     * @return The hardware UUID as a lowercase hex string without dashes, or null if unavailable.
     */
    private fun getMacMachineId(): String? {
        return try {
            val process = Runtime.getRuntime().exec(
                arrayOf("ioreg", "-rd1", "-c", "IOPlatformExpertDevice")
            )

            BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8)).use { reader ->
                reader.lineSequence().forEach { line ->
                    if (line.contains("IOPlatformUUID")) {
                        val pattern = Pattern.compile("\"IOPlatformUUID\"\\s*=\\s*\"([^\"]+)\"")
                        val matcher = pattern.matcher(line)
                        if (matcher.find()) {
                            return matcher.group(1).replace("-", "").lowercase()
                        }
                    }
                }
            }
            process.waitFor()
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Retrieves the Linux machine ID from the dbus machine-id file.
     *
     * Reads the contents of /var/lib/dbus/machine-id, which contains
     * a 32-character hexadecimal identifier.
     *
     * @return The machine ID as a lowercase hex string, or null if the file cannot be read.
     */
    private fun getLinuxMachineId(): String? {
        return try {
            val machineIdPath = "/var/lib/dbus/machine-id"
            val content = String(Files.readAllBytes(Paths.get(machineIdPath)), StandardCharsets.UTF_8)
            content.trim().replace("\\s+".toRegex(), "").lowercase()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Formats a raw machine ID as a UUID-formatted string.
     *
     * Normalizes the input to 32 hexadecimal characters (padding with zeros if shorter,
     * truncating if longer) and formats it in the standard UUID format (8-4-4-4-12).
     *
     * @param rawId The raw machine ID string to format.
     * @return The formatted UUID string.
     */
    private fun formatMachineId(rawId: String): String {
        var hex = rawId.replace("-", "").lowercase()

        hex = if (hex.length < 32) {
            String.format("%-32s", hex).replace(' ', '0')
        } else {
            hex.take(32)
        }

        @Suppress("ReplaceSubstringWithTake")
        return String.format(
            "%s-%s-%s-%s-%s",
            hex.substring(0, 8),
            hex.substring(8, 12),
            hex.substring(12, 16),
            hex.substring(16, 20),
            hex.substring(20, 32)
        )
    }
}