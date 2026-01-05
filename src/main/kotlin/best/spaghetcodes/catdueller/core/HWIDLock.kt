package best.spaghetcodes.catdueller.core

import best.spaghetcodes.catdueller.utils.ChatUtils
import best.spaghetcodes.catdueller.utils.MachineID
import java.net.HttpURLConnection
import java.net.URL

/**
 * HWID Lock system to restrict module usage to whitelisted hardware IDs
 */
object HWIDLock {

    // GitHub Gist whitelist URL
    private val WHITELIST_URLS = listOf(
        // GitHub Gist - using permanent URL without commit hash (always gets latest version)
        "https://gist.githubusercontent.com/meowmeowice/7c8c947acc796270e3dd6ea297ab53c2/raw/hwid_whitelist.txt",
    )


    // Cache for online whitelist
    private var cachedWhitelist: Set<String>? = null
    private var lastCacheUpdate = 0L
    private const val CACHE_DURATION = 5 * 60 * 1000L // 5 minutes

    // Emergency local whitelist (fallback when online sources fail)
    private val EMERGENCY_LOCAL_WHITELIST: Set<String> = setOf(
        // Add critical HWIDs here as emergency fallback
        // "HWID2"
    )

    private var currentHWID: String? = null
    private var isAuthorized: Boolean = false

    /**
     * Initialize HWID lock system
     * @return true if authorized, false if not
     */
    fun initialize(): Boolean {
        try {
            println("[HWIDLock] Starting HWID generation...")
            currentHWID = generateHWID()
            println("[HWIDLock] Generated HWID: $currentHWID")

            isAuthorized = currentHWID?.let { checkHWIDAuthorization(it) } ?: false
            println("[HWIDLock] Authorization result: $isAuthorized")

            if (isAuthorized) {
                ChatUtils.info("HWID verification successful")
                println("[HWIDLock] HWID verification successful")
            } else {
                ChatUtils.info("HWID verification failed")
                ChatUtils.info("Your HWID: $currentHWID")
                ChatUtils.error("This module is not authorized for your hardware")
                println("[HWIDLock] HWID verification failed - HWID: $currentHWID")
            }

            return isAuthorized
        } catch (e: Exception) {
            val errorMsg = "HWID verification error: ${e.message}"
            ChatUtils.error(errorMsg)
            println("[HWIDLock] $errorMsg")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Check if current session is authorized
     */
    fun isAuthorized(): Boolean {
        return isAuthorized
    }

    /**
     * Get current HWID for debugging purposes
     */
    fun getCurrentHWID(): String? {
        return currentHWID
    }

    /**
     * Generate cross-platform HWID using MachineID utility
     */
    private fun generateHWID(): String {
        try {
            println("[HWIDLock] Generating HWID using MachineID utility...")
            val machineId = MachineID.getMachineId()
            println("[HWIDLock] MachineID generated: $machineId")

            // Convert to uppercase for consistency with existing whitelist format
            val hwid = machineId.uppercase()

            ChatUtils.info("Using cross-platform Machine ID as HWID")
            println("[HWIDLock] Final HWID: $hwid")

            return hwid
        } catch (e: Exception) {
            val errorMsg = "Error getting Machine ID: ${e.message}"
            ChatUtils.error(errorMsg)
            ChatUtils.error("Machine ID generation failed")
            ChatUtils.error("Please contact support")
            println("[HWIDLock] $errorMsg")
            e.printStackTrace()

            return "HWID_ERROR_${System.getProperty("os.name")}_${System.currentTimeMillis()}"
        }
    }

    /**
     * Check HWID authorization using online whitelist and emergency fallback
     */
    private fun checkHWIDAuthorization(hwid: String): Boolean {
        // If HWID generation failed, deny access immediately
        if (hwid.startsWith("HWID_ERROR")) {
            println("[HWIDLock] HWID generation failed - denying access")
            ChatUtils.error("HWID generation failed - cannot verify authorization")
            return false
        }

        // Try online whitelist first
        val onlineResult = checkOnlineWhitelist(hwid)
        if (onlineResult != null) {
            println("[HWIDLock] Online whitelist check result: $onlineResult")
            return onlineResult
        }

        // If online check fails, try emergency local whitelist
        val isInEmergencyList = EMERGENCY_LOCAL_WHITELIST.contains(hwid.uppercase())
        if (isInEmergencyList) {
            println("[HWIDLock] HWID found in emergency local whitelist")
            ChatUtils.info("Using emergency local whitelist - online verification failed")
            return true
        }

        // No fallback available - deny access
        println("[HWIDLock] All whitelist checks failed - access denied")
        ChatUtils.error("Cannot connect to whitelist server and HWID not in emergency list - access denied")
        return false
    }

    /**
     * Check whitelist from online sources (GitHub Gist)
     */
    private fun checkOnlineWhitelist(hwid: String): Boolean? {
        return try {
            // Check cache first
            val currentTime = System.currentTimeMillis()
            if (cachedWhitelist != null && (currentTime - lastCacheUpdate) < CACHE_DURATION) {
                println("[HWIDLock] Using cached online whitelist")
                return cachedWhitelist!!.contains(hwid.uppercase())
            }

            println("[HWIDLock] Fetching online whitelist...")

            // Try each URL until one works
            for ((index, urlString) in WHITELIST_URLS.withIndex()) {
                try {
                    println("[HWIDLock] Trying source ${index + 1}/${WHITELIST_URLS.size}...")

                    val url = URL(urlString)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 5000 // 5 seconds timeout per URL
                    connection.readTimeout = 5000
                    connection.setRequestProperty("User-Agent", "CatDueller-HWID-Check/1.0")


                    val responseCode = connection.responseCode
                    println("[HWIDLock] Source ${index + 1} response: $responseCode")

                    if (responseCode == 200) {
                        val response = connection.inputStream.bufferedReader().readText()
                        println("[HWIDLock] Downloaded whitelist data (${response.length} chars)")

                        // Parse whitelist (expect one HWID per line)
                        val whitelistLines = response.split("\n")
                            .map { it.trim().uppercase() }
                            .filter { it.isNotEmpty() && !it.startsWith("#") } // Ignore comments
                            .map { line ->
                                // Remove inline comments (everything after #)
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