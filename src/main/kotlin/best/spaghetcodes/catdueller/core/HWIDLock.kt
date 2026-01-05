package best.spaghetcodes.catdueller.core

import best.spaghetcodes.catdueller.utils.client.ChatUtil
import best.spaghetcodes.catdueller.utils.system.HWIDUtil
import java.net.HttpURLConnection
import java.net.URL

/**
 * Hardware ID (HWID) lock system for license verification.
 *
 * Restricts module usage to whitelisted hardware identifiers by checking
 * the current machine's HWID against an online whitelist hosted on GitHub Gist.
 * Provides fallback to a local emergency whitelist when network is unavailable.
 *
 * The verification flow:
 * 1. Generate a unique HWID for the current machine
 * 2. Check against cached online whitelist (if available and not expired)
 * 3. Fetch fresh whitelist from remote sources if cache is stale
 * 4. Fall back to emergency local whitelist if all remote sources fail
 */
object HWIDLock {

    /** URLs for fetching the HWID whitelist, tried in order until one succeeds. */
    private val WHITELIST_URLS = listOf(
        "https://gist.githubusercontent.com/meowmeowice/7c8c947acc796270e3dd6ea297ab53c2/raw/hwid_whitelist.txt",
    )

    /** Cached whitelist entries from the last successful fetch. */
    private var cachedWhitelist: Set<String>? = null

    /** Timestamp of the last cache update in milliseconds. */
    private var lastCacheUpdate = 0L

    /** Duration in milliseconds before the cache expires (5 minutes). */
    private const val CACHE_DURATION = 5 * 60 * 1000L

    /** Local fallback whitelist used when all remote sources are unreachable. */
    private val EMERGENCY_LOCAL_WHITELIST: Set<String> = setOf()

    /** The generated HWID for the current machine. */
    private var currentHWID: String? = null

    /** Whether the current machine is authorized to use the mod. */
    private var isAuthorized: Boolean = false

    /**
     * Initializes the HWID lock system and verifies authorization.
     *
     * Generates the current machine's HWID and checks it against the whitelist.
     * Displays appropriate messages in chat based on the verification result.
     *
     * @return True if the current machine is authorized, false otherwise.
     */
    fun initialize(): Boolean {
        try {
            println("[HWIDLock] Starting HWID generation...")
            currentHWID = generateHWID()
            println("[HWIDLock] Generated HWID: $currentHWID")

            isAuthorized = currentHWID?.let { checkHWIDAuthorization(it) } ?: false
            println("[HWIDLock] Authorization result: $isAuthorized")

            if (isAuthorized) {
                ChatUtil.info("HWID verification successful")
                println("[HWIDLock] HWID verification successful")
            } else {
                ChatUtil.info("HWID verification failed")
                ChatUtil.info("Your HWID: $currentHWID")
                ChatUtil.error("This module is not authorized for your hardware")
                println("[HWIDLock] HWID verification failed - HWID: $currentHWID")
            }

            return isAuthorized
        } catch (e: Exception) {
            val errorMsg = "HWID verification error: ${e.message}"
            ChatUtil.error(errorMsg)
            println("[HWIDLock] $errorMsg")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Checks if the current session is authorized.
     *
     * @return True if the HWID verification was successful, false otherwise.
     */
    fun isAuthorized(): Boolean {
        return isAuthorized
    }

    /**
     * Gets the current machine's HWID for display or debugging purposes.
     *
     * @return The generated HWID string, or null if not yet initialized.
     */
    fun getCurrentHWID(): String? {
        return currentHWID
    }

    /**
     * Generates a cross-platform hardware identifier using the MachineID utility.
     *
     * The HWID is converted to uppercase for consistent whitelist matching.
     * If generation fails, returns an error identifier containing OS info and timestamp.
     *
     * @return The generated HWID string in uppercase format.
     */
    private fun generateHWID(): String {
        try {
            println("[HWIDLock] Generating HWID using MachineID utility...")
            val machineId = HWIDUtil.getMachineId()
            println("[HWIDLock] MachineID generated: $machineId")

            val hwid = machineId.uppercase()

            ChatUtil.info("Using cross-platform Machine ID as HWID")
            println("[HWIDLock] Final HWID: $hwid")

            return hwid
        } catch (e: Exception) {
            val errorMsg = "Error getting Machine ID: ${e.message}"
            ChatUtil.error(errorMsg)
            ChatUtil.error("Machine ID generation failed")
            ChatUtil.error("Please contact support")
            println("[HWIDLock] $errorMsg")
            e.printStackTrace()

            return "HWID_ERROR_${System.getProperty("os.name")}_${System.currentTimeMillis()}"
        }
    }

    /**
     * Checks HWID authorization against online and local whitelists.
     *
     * Verification order:
     * 1. Reject immediately if HWID generation failed
     * 2. Check online whitelist sources
     * 3. Fall back to emergency local whitelist if online check fails
     *
     * @param hwid The hardware identifier to verify.
     * @return True if the HWID is whitelisted, false otherwise.
     */
    private fun checkHWIDAuthorization(hwid: String): Boolean {
        if (hwid.startsWith("HWID_ERROR")) {
            println("[HWIDLock] HWID generation failed - denying access")
            ChatUtil.error("HWID generation failed - cannot verify authorization")
            return false
        }

        val onlineResult = checkOnlineWhitelist(hwid)
        if (onlineResult != null) {
            println("[HWIDLock] Online whitelist check result: $onlineResult")
            return onlineResult
        }

        val isInEmergencyList = EMERGENCY_LOCAL_WHITELIST.contains(hwid.uppercase())
        if (isInEmergencyList) {
            println("[HWIDLock] HWID found in emergency local whitelist")
            ChatUtil.info("Using emergency local whitelist - online verification failed")
            return true
        }

        println("[HWIDLock] All whitelist checks failed - access denied")
        ChatUtil.error("Cannot connect to whitelist server and HWID not in emergency list - access denied")
        return false
    }

    /**
     * Fetches and checks the HWID against the online whitelist.
     *
     * Uses a cached whitelist if available and not expired. Otherwise,
     * attempts to fetch from each configured URL until successful.
     * The whitelist format expects one HWID per line, with support for
     * comments starting with '#'.
     *
     * @param hwid The hardware identifier to check.
     * @return True if found in whitelist, false if not found, null if fetch failed.
     */
    private fun checkOnlineWhitelist(hwid: String): Boolean? {
        return try {
            val currentTime = System.currentTimeMillis()
            if (cachedWhitelist != null && (currentTime - lastCacheUpdate) < CACHE_DURATION) {
                println("[HWIDLock] Using cached online whitelist")
                return cachedWhitelist!!.contains(hwid.uppercase())
            }

            println("[HWIDLock] Fetching online whitelist...")

            for ((index, urlString) in WHITELIST_URLS.withIndex()) {
                try {
                    println("[HWIDLock] Trying source ${index + 1}/${WHITELIST_URLS.size}...")

                    val url = URL(urlString)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.setRequestProperty("User-Agent", "CatDueller-HWID-Check/1.0")

                    val responseCode = connection.responseCode
                    println("[HWIDLock] Source ${index + 1} response: $responseCode")

                    if (responseCode == 200) {
                        val response = connection.inputStream.bufferedReader().readText()
                        println("[HWIDLock] Downloaded whitelist data (${response.length} chars)")

                        val whitelistLines = response.split("\n")
                            .map { it.trim().uppercase() }
                            .filter { it.isNotEmpty() && !it.startsWith("#") }
                            .map { line ->
                                val commentIndex = line.indexOf("#")
                                if (commentIndex != -1) line.substring(0, commentIndex).trim()
                                else line
                            }
                            .filter { it.isNotEmpty() }

                        if (whitelistLines.isNotEmpty()) {
                            cachedWhitelist = whitelistLines.toSet()
                            lastCacheUpdate = currentTime

                            println("[HWIDLock] Updated cache with ${cachedWhitelist!!.size} entries from source ${index + 1}")

                            val isFound = cachedWhitelist!!.contains(hwid.uppercase())
                            println("[HWIDLock] HWID verification result: $isFound")

                            return isFound
                        } else {
                            println("[HWIDLock] Source ${index + 1} returned empty whitelist")
                        }
                    } else {
                        println("[HWIDLock] Source ${index + 1} failed with code: $responseCode")
                    }
                } catch (e: Exception) {
                    println("[HWIDLock] Source ${index + 1} failed: ${e.message}")
                }
            }

            println("[HWIDLock] All whitelist sources failed")
            null
        } catch (e: Exception) {
            println("[HWIDLock] Online whitelist check failed: ${e.message}")
            null
        }
    }
}