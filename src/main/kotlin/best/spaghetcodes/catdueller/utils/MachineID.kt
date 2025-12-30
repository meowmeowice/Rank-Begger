package best.spaghetcodes.catdueller.utils

import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.regex.Pattern

/**
 * Kotlin implementation of node-machine-id functionality.
 * <p>
 * Uses OS-native UUID/GUID methods:
 * - Windows: MachineGuid from HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Cryptography
 * - macOS: IOPlatformUUID (Hardware UUID) from ioreg
 * - Linux: /var/lib/dbus/machine-id
 */
object MachineID {
    private const val FALLBACK_ID = "0ed5c84c-3e16-4c44-927d-2d76d5cac79d"

    /**
     * Gets the machine ID using OS-native methods.
     * Formats it as a UUID-like string matching the Node.js implementation.
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
                formatAsCubelifyId(rawId)
            }
        } catch (_: Exception) {
            FALLBACK_ID
        }
    }

    /**
     * Gets Windows MachineGuid from registry.
     * Reads from HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Cryptography\MachineGuid
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
     * Gets macOS IOPlatformUUID (Hardware UUID) using ioreg command.
     * Equivalent to: ioreg -rd1 -c IOPlatformExpertDevice
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
     * Gets Linux machine ID from /var/lib/dbus/machine-id.
     * The file contains a 32-character hexadecimal string.
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
     * Formats the raw machine ID as a Cubelify-compatible UUID string.
     * Matches the Node.js implementation: takes first 16 bytes, pads to 32 hex chars,
     * then formats as UUID (8-4-4-4-12).
     */
    private fun formatAsCubelifyId(rawId: String): String {
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