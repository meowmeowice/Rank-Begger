package best.spaghetcodes.catdueller.core

import best.spaghetcodes.catdueller.utils.client.ChatUtil
import best.spaghetcodes.catdueller.utils.system.HWIDUtil

/**
 * Hardware ID (HWID) lock system for license verification.
 *
 * Restricts module usage to whitelisted hardware identifiers by checking
 * the current machine's HWID against the IRC server's whitelist database.
 *
 * The verification flow:
 * 1. Generate a unique HWID for the current machine
 * 2. Connect to IRC server and authenticate with HWID
 * 3. IRC server responds with AUTH_OK (with username) or AUTH_FAIL
 */
object HWIDLock {

    /** The generated HWID for the current machine. */
    private var currentHWID: String? = null

    /** Whether the current machine is authorized to use the mod. */
    private var isAuthorized: Boolean = false

    /** The username assigned by the IRC server after successful auth. */
    private var assignedUsername: String? = null

    /**
     * Initializes the HWID lock system by generating the machine's HWID.
     *
     * Actual authorization is handled by IRC - this just prepares the HWID.
     * The IRCDodgeClient will call setAuthorized() when auth response arrives.
     *
     * @return True if HWID was generated successfully, false otherwise.
     */
    fun initialize(): Boolean {
        try {
            println("[HWIDLock] Starting HWID generation...")
            currentHWID = generateHWID()
            println("[HWIDLock] Generated HWID: $currentHWID")

            if (currentHWID?.startsWith("HWID_ERROR") == true) {
                ChatUtil.error("HWID generation failed")
                return false
            }

            ChatUtil.info("Connecting to auth server...")
            println("[HWIDLock] HWID ready, auth will be handled by IRC server")
            return true
        } catch (e: Exception) {
            val errorMsg = "HWID generation error: ${e.message}"
            ChatUtil.error(errorMsg)
            println("[HWIDLock] $errorMsg")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Sets the authorization state from IRC server response.
     *
     * Called by IRCDodgeClient when AUTH_OK or AUTH_FAIL is received.
     *
     * @param authorized Whether the HWID is authorized
     * @param username The assigned username (if authorized)
     */
    fun setAuthorized(authorized: Boolean, username: String? = null) {
        isAuthorized = authorized
        assignedUsername = username

        if (authorized) {
            ChatUtil.info("HWID verification successful")
            ChatUtil.info("Welcome, $username!")
            println("[HWIDLock] Authorization granted: $username")
        } else {
            ChatUtil.error("HWID verification failed")
            ChatUtil.error("Your HWID: $currentHWID")
            ChatUtil.error("This module is not authorized for your hardware")
            println("[HWIDLock] Authorization denied")
        }
    }

    /**
     * Checks if the current session is authorized.
     *
     * @return True if the IRC server has authorized this HWID, false otherwise.
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
     * Gets the assigned username from the IRC server.
     *
     * @return The username if authorized, null otherwise.
     */
    fun getUsername(): String? {
        return assignedUsername
    }

    /**
     * Generates a cross-platform hardware identifier using the MachineID utility.
     *
     * The HWID is converted to uppercase for consistent matching.
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
            println("[HWIDLock] Final HWID: $hwid")

            return hwid
        } catch (e: Exception) {
            val errorMsg = "Error getting Machine ID: ${e.message}"
            ChatUtil.error(errorMsg)
            println("[HWIDLock] $errorMsg")
            e.printStackTrace()

            return "HWID_ERROR_${System.getProperty("os.name")}_${System.currentTimeMillis()}"
        }
    }
}
